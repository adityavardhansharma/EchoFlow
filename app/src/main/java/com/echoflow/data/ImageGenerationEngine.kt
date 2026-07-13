package com.echoflow.data

import kotlinx.coroutines.flow.Flow

/**
 * One image-generation turn, engine-agnostic. Cloud-only fields ([apiKey], [history],
 * [systemPrompt], [editImageDataUrl]) are ignored by the on-device engine; local-only
 * fields ([localModel], [iterations], [seed]) are ignored by the cloud engine.
 */
data class ImageGenerationRequest(
    val chatId: String,
    val prompt: String,
    val negativePrompt: String? = null,
    val modelId: String,
    val iterations: Int = SettingsRepository.LOCAL_IMAGE_ITERATIONS_DEFAULT,
    val seed: Int? = null, // null = random per generation
    val previousImage: GeneratedImage? = null,
    // Cloud (OpenRouter) context:
    val apiKey: String = "",
    val history: List<ChatMessage> = emptyList(),
    val systemPrompt: String = "",
    val editImageDataUrl: String? = null,
    val params: InferenceParams? = null,
    // On-device context. The selected model carries its own runtime discriminator.
    val localModel: LocalImageModel? = null,
    val localModelInstallDir: String? = null,
)

/** What an engine emits while a generation runs. Failures surface as flow exceptions. */
sealed interface ImageGenerationEvent {
    /** Streamed assistant text accompanying the image (cloud engines only). */
    data class Text(val delta: String) : ImageGenerationEvent

    /** The finished image, already persisted to disk + Room by the engine. */
    data class ImageFile(val image: GeneratedImage) : ImageGenerationEvent
}

/** Typed domain errors so routing/UI can react without string-matching messages. */
sealed class ImageGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotInstalled(message: String) : ImageGenerationException(message)
    class InitializationFailed(message: String, cause: Throwable? = null) : ImageGenerationException(message, cause)
    class GenerationFailed(message: String, cause: Throwable? = null) : ImageGenerationException(message, cause)
    class DeviceUnsupported(message: String) : ImageGenerationException(message)
}

/**
 * A source of generated images. [generate] runs one turn; [cancel] aborts the active turn
 * where the backend allows it; [close] releases every held resource (loaded models, native
 * memory). Implementations include OpenRouter and the model-routed on-device engines.
 */
interface ImageGenerationEngine {
    fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent>
    fun cancel()
    fun close()
}
