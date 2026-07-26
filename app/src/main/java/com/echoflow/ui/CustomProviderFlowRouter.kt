package com.echoflow.ui

import com.echoflow.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class CustomProviderFlowRouter(private val service: CustomProviderService) {
    fun stream(provider: String?, config: CustomProviderConfig, model: String, history: List<ChatMessage>, prompt: String, params: InferenceParams): Flow<StreamChunk> = when (provider) {
        "openai" -> service.streamOpenAi(config.openAiApiKey, model, history, prompt, params)
        "claude" -> service.streamClaude(config.claudeApiKey, model, history, prompt, params)
        "gemini" -> service.streamGemini(config.geminiApiKey, model, history, prompt, params)
        "cerebras" -> service.streamCerebras(config.cerebrasApiKey, model, history, prompt, params)
        "xai" -> service.streamOpenAiCompatible("https://api.x.ai/v1", config.xAiApiKey, model, history, prompt, params)
        "ollama" -> service.streamOllama(config.ollamaBaseUrl, model, history, prompt, params)
        "openai-compatible" -> service.streamOpenAiCompatible(config.openAiBaseUrl, config.openAiCompatibleApiKey, model, history, prompt, params)
        else -> flow { throw Exception("Unknown custom endpoint provider.") }
    }

    fun streamWithTools(provider: String?, config: CustomProviderConfig, model: String, history: List<ChatMessage>, prompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> = when (provider) {
        "openai" -> service.streamOpenAiTools("https://api.openai.com/v1", config.openAiApiKey, model, history, prompt, params, search)
        "cerebras" -> service.streamOpenAiTools("https://api.cerebras.ai/v1", config.cerebrasApiKey, model, history, prompt, params, search)
        "xai" -> service.streamOpenAiTools("https://api.x.ai/v1", config.xAiApiKey, model, history, prompt, params, search)
        "openai-compatible" -> service.streamOpenAiTools(config.openAiBaseUrl, config.openAiCompatibleApiKey, model, history, prompt, params, search)
        "ollama" -> service.streamOllamaTools(config.ollamaBaseUrl, model, history, prompt, params, search)
        "claude" -> service.streamClaudeTools(config.claudeApiKey, model, history, prompt, params, search)
        "gemini" -> service.streamGeminiTools(config.geminiApiKey, model, history, prompt, params, search)
        else -> stream(provider, config, model, history, prompt, params)
    }
}
