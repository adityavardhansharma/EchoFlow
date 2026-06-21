package com.echoflow.data

import kotlinx.coroutines.flow.Flow

interface LlmGateway {
    fun stream(request: LlmStreamRequest): Flow<StreamChunk>
}

data class LlmStreamRequest(
    val apiKey: String = "",
    val model: String,
    val chatId: String,
    val history: List<ChatMessage>,
    val systemPrompt: String,
    val params: InferenceParams,
    val localModel: LocalModel? = null,
    val serverWebSearch: Boolean = false,
)

class OpenRouterGateway(
    private val service: OpenRouterService,
) : LlmGateway {
    override fun stream(request: LlmStreamRequest): Flow<StreamChunk> =
        service.sendChatMessageStream(
            apiKey = request.apiKey,
            model = request.model,
            history = request.history,
            systemPrompt = request.systemPrompt,
            serverWebSearch = request.serverWebSearch,
            params = request.params,
        )
}

class LocalLlmGateway(
    private val service: LocalLlmService,
) : LlmGateway {
    override fun stream(request: LlmStreamRequest): Flow<StreamChunk> {
        val model = request.localModel ?: error("Local model is required for local gateway requests.")
        return service.generate(
            model = model,
            chatId = request.chatId,
            history = request.history,
            systemPrompt = request.systemPrompt,
            params = request.params,
        )
    }
}

