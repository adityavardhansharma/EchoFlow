package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cloud image generation. Chat-native image models (Gemini Flash Image, GPT-5 Image) still
 * stream a chat completion with `modalities: ["image","text"]`. Dedicated Image API models
 * (Muse, Flux, Seedream, gpt-image-*) POST `/api/v1/images` instead — they are not in the
 * chat catalog and do not speak that protocol.
 */
class OpenRouterImageGenerationEngine(
    private val service: OpenRouterService,
    private val store: GeneratedImageStore,
    private val directory: OpenRouterModelDirectory = OpenRouterModelDirectory(),
) : ImageGenerationEngine {

    override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> = flow {
        val chunks = if (usesDedicatedImageApi(request.modelId)) {
            service.sendDedicatedImageGeneration(
                apiKey = request.apiKey,
                model = request.modelId,
                prompt = request.prompt,
                aspectRatio = request.aspectRatio,
                referenceImageDataUrls = listOfNotNull(request.editImageDataUrl) + request.referenceImageDataUrls,
            )
        } else {
            service.sendImageGeneration(
                apiKey = request.apiKey,
                model = request.modelId,
                history = request.history,
                systemPrompt = request.systemPrompt,
                editImageDataUrl = request.editImageDataUrl,
                params = request.params,
            )
        }
        chunks.collect { chunk ->
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
                else -> Unit
            }
        }
    }

    private suspend fun usesDedicatedImageApi(modelId: String): Boolean {
        val listed = runCatching { directory.imageModels() }.getOrNull()
            ?.firstOrNull { it.id == modelId }
        return listed?.usesDedicatedImageApi
            ?: OpenRouterModelDirectory.fallbackUsesDedicatedImageApi(modelId)
    }

    /** The one-shot HTTP call is cancelled by cancelling the collecting coroutine. */
    override fun cancel() = Unit

    override fun close() = Unit
}
