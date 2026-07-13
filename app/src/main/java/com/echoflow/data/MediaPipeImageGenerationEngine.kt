package com.echoflow.data

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random

/**
 * Pure prompt/parameter composition for the on-device engine — separated so the rules are
 * unit-testable without the MediaPipe SDK.
 */
internal object LocalImagePromptComposer {
    /** The model's activation phrase (when present) silently prefixes every prompt. */
    fun composePrompt(activationPhrase: String?, userPrompt: String): String {
        val phrase = activationPhrase?.trim().orEmpty()
        val prompt = userPrompt.trim()
        return when {
            phrase.isEmpty() -> prompt
            prompt.startsWith(phrase, ignoreCase = true) -> prompt
            else -> "$phrase, $prompt"
        }
    }

    /** Catalog negative prompt and the user's optional one combine, deduplicated blanks. */
    fun composeNegativePrompt(catalogNegative: String?, userNegative: String?): String =
        listOfNotNull(catalogNegative?.trim(), userNegative?.trim())
            .filter { it.isNotEmpty() }
            .joinToString(", ")

    fun clampIterations(value: Int): Int = value.coerceIn(
        SettingsRepository.LOCAL_IMAGE_ITERATIONS_MIN,
        SettingsRepository.LOCAL_IMAGE_ITERATIONS_MAX,
    )

    /** Fixed seed passes through; random mode draws a fresh non-negative seed per turn. */
    fun resolveSeed(fixedSeed: Int?, random: Random = Random.Default): Int =
        fixedSeed ?: random.nextInt(0, Int.MAX_VALUE)
}

/**
 * On-device diffusion through MediaPipe Image Generator. The generator initializes lazily
 * from an installed bundle's bins directory and is reused across generations with the same
 * model; switching models closes the old instance first. All SDK work runs off the main
 * thread and one generation runs at a time (callers additionally hold [LocalInferenceGate]
 * so local LLM inference can't overlap either).
 *
 * Intermediate diffusion previews are intentionally not enabled: publishing every
 * iteration slows generation and grows memory, and EchoFlow's own placeholder animation
 * already carries the wait.
 *
 * Note: the MediaPipe Android API takes only (prompt, iterations, seed) — the composed
 * negative prompt is kept in the request pipeline (and tested) so it can be forwarded the
 * moment the SDK exposes it, but it is not sent today.
 */
class MediaPipeImageGenerationEngine(
    private val context: Context,
    private val store: GeneratedImageStore,
) : ImageGenerationEngine {

    private val generatorLock = Mutex()
    private var generator: ImageGenerator? = null
    private var loadedBinsDir: String? = null
    @Volatile private var closed = false

    override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
        if (closed) throw CancellationException("The on-device image engine was closed.")
        val model = request.localModel
            ?: throw ImageGenerationException.ModelNotInstalled(
                "Pick an on-device image model in Settings → Image generation first."
            )
        val installDir = request.localModelInstallDir
            ?: throw ImageGenerationException.ModelNotInstalled(
                "${model.name} isn't installed. Download it in Settings → Image generation."
            )
        val binsDir = File(installDir, "bins").absolutePath
        if (!File(binsDir).isDirectory || File(binsDir).listFiles().isNullOrEmpty()) {
            throw ImageGenerationException.ModelNotInstalled(
                "${model.name}'s files are missing. Re-download it in Settings → Image generation."
            )
        }

        val prompt = LocalImagePromptComposer.composePrompt(model.activationPhrase, request.prompt)
        // Composed and validated even though today's SDK can't accept it (see class KDoc).
        LocalImagePromptComposer.composeNegativePrompt(model.defaultNegativePrompt, request.negativePrompt)
        val iterations = LocalImagePromptComposer.clampIterations(request.iterations)
        val seed = LocalImagePromptComposer.resolveSeed(request.seed)

        val bitmap: Bitmap = generatorLock.withLock {
            if (closed) throw CancellationException("The on-device image engine was closed.")
            try {
                val gen = obtainGenerator(binsDir, model.name)
                val result = gen.generate(prompt, iterations, seed)
                val mpImage = result.generatedImage()
                    ?: throw ImageGenerationException.GenerationFailed(
                        "${model.name} finished without producing an image. Try again."
                    )
                BitmapExtractor.extract(mpImage)
            } catch (e: ImageGenerationException) {
                throw e
            } catch (e: Exception) {
                throw ImageGenerationException.GenerationFailed(
                    "On-device generation failed: ${e.message ?: "unknown error"}", e
                )
            } finally {
                if (closed) releaseGeneratorLocked()
            }
        }

        val saved = store.saveBitmap(
            chatId = request.chatId,
            prompt = request.prompt,
            bitmap = bitmap,
            parentId = request.previousImage?.id,
        )
        emit(ImageGenerationEvent.ImageFile(saved))
    }.flowOn(Dispatchers.Default)

    private fun obtainGenerator(binsDir: String, modelName: String): ImageGenerator {
        val current = generator
        if (current != null && loadedBinsDir == binsDir) return current
        // Model switch: release the old native instance before loading the new one.
        current?.let { runCatching { it.close() } }
        generator = null
        loadedBinsDir = null
        return try {
            val options = ImageGenerator.ImageGeneratorOptions.builder()
                .setImageGeneratorModelDirectory(binsDir)
                .build()
            ImageGenerator.createFromOptions(context, options).also {
                generator = it
                loadedBinsDir = binsDir
            }
        } catch (e: Exception) {
            throw ImageGenerationException.InitializationFailed(
                "Couldn't load $modelName. Try re-downloading it in Settings → Image generation.", e
            )
        }
    }

    /**
     * The one-shot MediaPipe generate call has no mid-run abort; cancelling the collecting
     * coroutine drops the result. Partial output is never surfaced as a completed image.
     */
    override fun cancel() = Unit

    override fun close() {
        closed = true
        if (!generatorLock.tryLock()) return
        try {
            releaseGeneratorLocked()
        } finally {
            generatorLock.unlock()
        }
    }

    private fun releaseGeneratorLocked() {
        val current = generator
        generator = null
        loadedBinsDir = null
        current?.let { runCatching { it.close() } }
    }
}
