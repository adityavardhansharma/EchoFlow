package com.echoflow.data

import kotlinx.coroutines.flow.Flow

/** One image-generation turn against an OpenRouter image model. */
data class ImageGenerationRequest(
    val chatId: String,
    val prompt: String,
    val modelId: String,
    val previousImage: GeneratedImage? = null,
    val apiKey: String = "",
    val history: List<ChatMessage> = emptyList(),
    val systemPrompt: String = "",
    val editImageDataUrl: String? = null,
    val referenceImageDataUrls: List<String> = emptyList(),
    val aspectRatio: String? = null,
    val params: InferenceParams? = null,
)

/** What an engine emits while a generation runs. Failures surface as flow exceptions. */
sealed interface ImageGenerationEvent {
    /** Streamed assistant text accompanying the image. */
    data class Text(val delta: String) : ImageGenerationEvent

    /** The finished image, already persisted to disk + Room by the engine. */
    data class ImageFile(val image: GeneratedImage) : ImageGenerationEvent
}

/** Typed domain errors so routing/UI can react without string-matching messages. */
sealed class ImageGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class GenerationFailed(message: String, cause: Throwable? = null) : ImageGenerationException(message, cause)
}

/**
 * A source of generated images. [generate] runs one turn; [cancel] aborts the active turn
 * where the backend allows it; [close] releases every held resource.
 */
interface ImageGenerationEngine {
    fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent>
    fun cancel()
    fun close()
}
