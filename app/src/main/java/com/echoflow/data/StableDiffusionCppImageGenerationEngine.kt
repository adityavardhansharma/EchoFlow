package com.echoflow.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Experimental on-device image engine backed by stable-diffusion.cpp. Native execution is
 * delegated to [StableDiffusionGenerationService], which lives in a private process so an
 * unsupported checkpoint or device driver cannot terminate EchoFlow's main process.
 */
class StableDiffusionCppImageGenerationEngine(
    context: Context,
    private val store: GeneratedImageStore,
) : ImageGenerationEngine {
    private val client = StableDiffusionGenerationClient(context.applicationContext)

    override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw ImageGenerationException.DeviceUnsupported(
                "Experimental image models need Android 8.0 or newer."
            )
        }
        val model = request.localModel
            ?: throw ImageGenerationException.ModelNotInstalled(
                "Pick an on-device image model in Settings → Image generation first."
            )
        val installDir = request.localModelInstallDir?.let(::File)
            ?: throw ImageGenerationException.ModelNotInstalled(
                "${model.name} isn't installed. Download it in Settings → Image generation."
            )
        val fileName = model.modelFileName
            ?: throw ImageGenerationException.InitializationFailed(
                "${model.name} has no runnable model file. Re-download it after updating EchoFlow."
            )
        val modelFile = File(installDir, fileName)
        if (!modelFile.isFile) {
            throw ImageGenerationException.ModelNotInstalled(
                "${model.name}'s files are missing. Re-download it in Settings → Image generation."
            )
        }

        val prompt = LocalImagePromptComposer.composePrompt(model.activationPhrase, request.prompt)
        val negativePrompt = LocalImagePromptComposer.composeNegativePrompt(
            model.defaultNegativePrompt,
            request.negativePrompt,
        )
        val iterations = LocalImagePromptComposer.clampIterations(request.iterations)
        val seed = LocalImagePromptComposer.resolveSeed(request.seed).toLong()

        val pendingPath = try {
            client.generate(
                modelPath = modelFile.absolutePath,
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = IMAGE_SIZE,
                height = IMAGE_SIZE,
                steps = iterations,
                cfgScale = CFG_SCALE,
                seed = seed,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ImageGenerationException.GenerationFailed(
                e.message ?: "The experimental image runtime stopped unexpectedly.",
                e,
            )
        }

        val saved = store.savePngFile(
            chatId = request.chatId,
            prompt = request.prompt,
            pendingFile = File(pendingPath),
            parentId = request.previousImage?.id,
        )
        emit(ImageGenerationEvent.ImageFile(saved))
    }.flowOn(Dispatchers.Default)

    override fun cancel() = client.cancel()

    override fun close() = client.close()

    private companion object {
        const val IMAGE_SIZE = 512
        const val CFG_SCALE = 7.0f
    }
}

/** One reusable binding to the private native process. */
private class StableDiffusionGenerationClient(private val context: Context) {
    private val connectionMutex = Mutex()
    private val stateLock = Any()

    @Volatile
    private var remote: IStableDiffusionGenerationService? = null

    private var connection: ServiceConnection? = null
    private var pendingConnection: CompletableDeferred<IStableDiffusionGenerationService>? = null
    private var pendingGeneration: CompletableDeferred<String>? = null

    suspend fun generate(
        modelPath: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
    ): String {
        val service = service()
        val result = CompletableDeferred<String>()
        synchronized(stateLock) {
            if (remote !== service || connection == null || !service.asBinder().isBinderAlive) {
                throw nativeStopped()
            }
            check(pendingGeneration == null) { "Another on-device image is still being generated." }
            pendingGeneration = result
        }
        val callback = object : IStableDiffusionGenerationCallback.Stub() {
            override fun onSuccess(pngPath: String) {
                result.complete(pngPath)
            }

            override fun onError(message: String) {
                result.completeExceptionally(
                    IllegalStateException(message.ifBlank { "On-device image generation failed." })
                )
            }
        }
        try {
            try {
                service.generate(
                    modelPath,
                    prompt,
                    negativePrompt,
                    width,
                    height,
                    steps,
                    cfgScale,
                    seed,
                    callback,
                )
            } catch (error: Exception) {
                throw nativeStopped(error)
            }
            return try {
                result.await()
            } catch (error: CancellationException) {
                cancel()
                throw error
            }
        } finally {
            synchronized(stateLock) {
                if (pendingGeneration === result) pendingGeneration = null
            }
        }
    }

    private suspend fun service(): IStableDiffusionGenerationService {
        remote?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return connectionMutex.withLock {
            remote?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            connect()
        }
    }

    private suspend fun connect(): IStableDiffusionGenerationService {
        val result = CompletableDeferred<IStableDiffusionGenerationService>()
        lateinit var candidate: ServiceConnection
        candidate = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = IStableDiffusionGenerationService.Stub.asInterface(binder)
                if (service == null) {
                    bindingFailed(this, result, "The experimental image runtime did not start.")
                    return
                }
                val accepted = synchronized(stateLock) {
                    if (connection === this && pendingConnection === result) {
                        remote = service
                        pendingConnection = null
                        true
                    } else {
                        false
                    }
                }
                if (accepted) {
                    if (!result.complete(service)) detachConnected(this)
                } else {
                    unbind(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = disconnected(this)

            override fun onBindingDied(name: ComponentName?) = disconnected(this)

            override fun onNullBinding(name: ComponentName?) {
                bindingFailed(this, result, "The experimental image runtime is unavailable.")
            }
        }

        val staleConnection = synchronized(stateLock) {
            val stale = connection
            remote = null
            connection = candidate
            pendingConnection?.cancel()
            pendingConnection = result
            stale
        }
        staleConnection?.let(::unbind)

        val bound = runCatching {
            context.bindService(
                Intent(context, StableDiffusionGenerationService::class.java),
                candidate,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!bound) {
            bindingFailed(
                candidate,
                result,
                "The experimental image runtime could not be started.",
            )
        }

        return try {
            result.await()
        } catch (error: CancellationException) {
            detachPendingBinding(candidate, result)
            throw error
        }
    }

    private fun bindingFailed(
        candidate: ServiceConnection,
        result: CompletableDeferred<IStableDiffusionGenerationService>,
        message: String,
    ) {
        val wasCurrent = synchronized(stateLock) {
            if (connection === candidate && pendingConnection === result) {
                remote = null
                connection = null
                pendingConnection = null
                true
            } else {
                false
            }
        }
        if (wasCurrent) result.completeExceptionally(IllegalStateException(message))
        unbind(candidate)
    }

    private fun disconnected(candidate: ServiceConnection) {
        val pending = synchronized(stateLock) {
            if (connection !== candidate) return
            remote = null
            connection = null
            val connecting = pendingConnection
            val generating = pendingGeneration
            pendingConnection = null
            pendingGeneration = null
            connecting to generating
        }
        unbind(candidate)
        val error = nativeStopped()
        pending.first?.completeExceptionally(error)
        pending.second?.completeExceptionally(error)
    }

    private fun detachPendingBinding(
        candidate: ServiceConnection,
        result: CompletableDeferred<IStableDiffusionGenerationService>,
    ) {
        val wasCurrent = synchronized(stateLock) {
            if (connection === candidate && pendingConnection === result) {
                remote = null
                connection = null
                pendingConnection = null
                true
            } else {
                false
            }
        }
        if (wasCurrent) unbind(candidate)
    }

    private fun detachConnected(candidate: ServiceConnection) {
        val wasCurrent = synchronized(stateLock) {
            if (connection === candidate) {
                remote = null
                connection = null
                true
            } else {
                false
            }
        }
        if (wasCurrent) unbind(candidate)
    }

    fun cancel() {
        val state = clearState()
        val cancellation = CancellationException("On-device image generation was stopped.")
        state.connecting?.cancel(cancellation)
        state.generating?.cancel(cancellation)
        val current = state.remote
        runCatching { current?.cancel() }
        state.connection?.let(::unbind)
    }

    fun close() {
        val state = clearState()
        val cancellation = CancellationException("The on-device image runtime was closed.")
        state.connecting?.cancel(cancellation)
        state.generating?.cancel(cancellation)
        val current = state.remote
        runCatching { current?.release() }
        state.connection?.let(::unbind)
    }

    private fun clearState(): ClientState = synchronized(stateLock) {
        ClientState(remote, connection, pendingConnection, pendingGeneration).also {
            remote = null
            connection = null
            pendingConnection = null
            pendingGeneration = null
        }
    }

    private fun unbind(candidate: ServiceConnection) {
        runCatching { context.unbindService(candidate) }
    }

    private fun nativeStopped(cause: Throwable? = null): IllegalStateException =
        IllegalStateException(
            "The experimental model stopped. It may need more memory than this phone has.",
            cause,
        )

    private data class ClientState(
        val remote: IStableDiffusionGenerationService?,
        val connection: ServiceConnection?,
        val connecting: CompletableDeferred<IStableDiffusionGenerationService>?,
        val generating: CompletableDeferred<String>?,
    )
}
