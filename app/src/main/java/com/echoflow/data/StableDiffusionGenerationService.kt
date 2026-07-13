package com.echoflow.data

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.llamatik.library.platform.StableDiffusionBridge
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Validation kept outside JNI so malformed Binder calls cannot reach the native runtime. */
internal object StableDiffusionRequestValidator {
    private const val MAX_PROMPT_CHARS = 8_192
    private const val MAX_STEPS = 50
    private const val MAX_CFG_SCALE = 30f

    fun validate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
    ) {
        require(width in 256..1024 && width % 64 == 0) { "Unsupported image width." }
        require(height in 256..1024 && height % 64 == 0) { "Unsupported image height." }
        require(steps in 1..MAX_STEPS) { "Unsupported generation step count." }
        require(cfgScale.isFinite() && cfgScale in 0f..MAX_CFG_SCALE) {
            "Unsupported guidance scale."
        }
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS) {
            "The image prompt is empty or too long."
        }
        require(negativePrompt.length <= MAX_PROMPT_CHARS) {
            "The negative prompt is too long."
        }
    }
}

/**
 * Hosts the experimental native image runtime in a private process. A malformed model,
 * driver failure, or native out-of-memory crash can therefore take down this service
 * without closing the main EchoFlow process or losing chat data.
 *
 * The bound service keeps its model loaded for follow-up generations until
 * [StableDiffusionGenerationClient.close] releases it with the owning chat ViewModel. Calls are
 * serialized because the upstream bridge owns one native context per process.
 */
class StableDiffusionGenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generationLock = Any()

    @Volatile
    private var activeJob: Job? = null

    private var idleReleaseJob: Job? = null
    private var closing = false
    private var loadedModelPath: String? = null

    private val binder = object : IStableDiffusionGenerationService.Stub() {
        override fun generate(
            modelPath: String,
            prompt: String,
            negativePrompt: String,
            width: Int,
            height: Int,
            steps: Int,
            cfgScale: Float,
            seed: Long,
            callback: IStableDiffusionGenerationCallback,
        ) {
            val job = synchronized(generationLock) {
                idleReleaseJob?.cancel()
                idleReleaseJob = null
                if (closing) {
                    runCatching { callback.onError("The on-device image runtime is closing.") }
                    return
                }
                if (activeJob?.isCompleted == false) {
                    runCatching { callback.onError("Another on-device image is still being generated.") }
                    return
                }
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            "Experimental image models need Android 8.0 or newer."
                        }
                        require(File(modelPath).isFile) { "The downloaded model file is missing." }
                        StableDiffusionRequestValidator.validate(
                            prompt,
                            negativePrompt,
                            width,
                            height,
                            steps,
                            cfgScale,
                        )

                        if (loadedModelPath != modelPath) {
                            if (loadedModelPath != null) StableDiffusionBridge.release()
                            loadedModelPath = null
                            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
                            if (!StableDiffusionBridge.initModel(modelPath, threads)) {
                                error("This model could not be loaded on this phone.")
                            }
                            loadedModelPath = modelPath
                        }

                        val rgba = StableDiffusionBridge.txt2img(
                            prompt = prompt,
                            negativePrompt = negativePrompt.ifBlank { null },
                            width = width,
                            height = height,
                            steps = steps,
                            cfgScale = cfgScale,
                            seed = seed,
                        )
                        val expectedBytes = width.toLong() * height.toLong() * 4L
                        if (rgba.size.toLong() != expectedBytes) {
                            error("The native image runtime returned an incomplete image.")
                        }
                        val png = writePendingPng(rgba, width, height)
                        runCatching { callback.onSuccess(png.absolutePath) }
                            .onFailure { png.delete() }
                    } catch (_: Error) {
                        // OOM, linkage errors and other fatal VM/native failures can leave the
                        // process-global context unsafe. Sacrifice only this private process.
                        synchronized(generationLock) { closing = true }
                        Process.killProcess(Process.myPid())
                    } catch (e: Exception) {
                        runCatching {
                            callback.onError(e.message?.takeIf { it.isNotBlank() }
                                ?: "On-device image generation failed.")
                        }
                    } finally {
                        synchronized(generationLock) {
                            activeJob = null
                            scheduleIdleReleaseLocked()
                        }
                    }
                }.also { activeJob = it }
            }
            job.start()
        }

        override fun cancel() {
            // The bridge version shipped for the prerelease does not expose native
            // cancellation. Ending only this private process is the one reliable way to
            // stop a long diffusion pass without risking the main application process.
            Process.killProcess(Process.myPid())
        }

        override fun release() {
            val releaseState = synchronized(generationLock) {
                closing = true
                idleReleaseJob?.cancel()
                idleReleaseJob = null
                (activeJob?.isCompleted == false) to (loadedModelPath != null)
            }
            if (releaseState.first) {
                Process.killProcess(Process.myPid())
                return
            }
            if (releaseState.second) runCatching { StableDiffusionBridge.release() }
            loadedModelPath = null
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        pendingDir().listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > PENDING_MAX_AGE_MS) file.delete()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            scope.launch { releaseLoadedModelIfIdle() }
        }
    }

    private fun scheduleIdleReleaseLocked() {
        if (closing || loadedModelPath == null) return
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(MODEL_IDLE_TIMEOUT_MS)
            releaseLoadedModelIfIdle()
        }
    }

    private fun releaseLoadedModelIfIdle() {
        synchronized(generationLock) {
            if (closing || activeJob?.isCompleted == false || loadedModelPath == null) return
            runCatching { StableDiffusionBridge.release() }
            loadedModelPath = null
            idleReleaseJob = null
        }
    }

    private fun pendingDir(): File =
        File(filesDir, "generated_images/.native_pending").apply { mkdirs() }

    private fun writePendingPng(rgba: ByteArray, width: Int, height: Int): File {
        val pixels = IntArray(width * height)
        var source = 0
        for (index in pixels.indices) {
            val red = rgba[source++].toInt() and 0xff
            val green = rgba[source++].toInt() and 0xff
            val blue = rgba[source++].toInt() and 0xff
            val alpha = rgba[source++].toInt() and 0xff
            pixels[index] = Color.argb(alpha, red, green, blue)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val directory = pendingDir()
        val finalFile = File(directory, "${UUID.randomUUID()}.png")
        val tempFile = File(directory, "${finalFile.name}.tmp")
        try {
            tempFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode the generated image."
                }
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            return finalFile
        } finally {
            bitmap.recycle()
            tempFile.delete()
        }
    }

    override fun onDestroy() {
        val nativeCallMayBeActive = synchronized(generationLock) {
            closing = true
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            activeJob?.isCompleted == false
        }
        activeJob?.cancel()
        // Coroutine cancellation cannot interrupt txt2img. Releasing its process-global context
        // while JNI is still using it is unsafe, so let process teardown reclaim it instead.
        if (!nativeCallMayBeActive && loadedModelPath != null) {
            runCatching { StableDiffusionBridge.release() }
        }
        loadedModelPath = null
        scope.cancel()
        super.onDestroy()
        if (nativeCallMayBeActive) Process.killProcess(Process.myPid())
    }

    private companion object {
        const val PENDING_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        const val MODEL_IDLE_TIMEOUT_MS = 2L * 60L * 1_000L
    }
}
