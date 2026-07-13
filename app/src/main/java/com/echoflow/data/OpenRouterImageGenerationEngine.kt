package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cloud image generation, unchanged in behaviour: one streaming chat completion with
 * `modalities: ["image","text"]` through [OpenRouterService.sendImageGeneration]
 * (conversational editing included via [ImageGenerationRequest.editImageDataUrl]). The
 * base64 payload is persisted through [GeneratedImageStore] here so raw multi-MB data
 * URLs never travel further up than this class.
 */
class OpenRouterImageGenerationEngine(
    private val service: OpenRouterService,
    private val store: GeneratedImageStore,
) : ImageGenerationEngine {

    override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
        service.sendImageGeneration(
            apiKey = request.apiKey,
            model = request.modelId,
            history = request.history,
            systemPrompt = request.systemPrompt,
            editImageDataUrl = request.editImageDataUrl,
            params = request.params,
        ).collect { chunk ->
            when (chunk) {
                is StreamChunk.Content -> emit(ImageGenerationEvent.Text(chunk.text))
                is StreamChunk.ImageGenerated -> {
                    val saved = store.save(
                        chatId = request.chatId,
                        prompt = request.prompt,
                        dataUrl = chunk.dataUrl,
                        parentId = request.previousImage?.id,
                    )
                    emit(ImageGenerationEvent.ImageFile(saved))
                }
                else -> Unit // reasoning/search chunks don't occur on the image path
            }
        }
    }

    /** The one-shot HTTP call is cancelled by cancelling the collecting coroutine. */
    override fun cancel() = Unit

    override fun close() = Unit
}
