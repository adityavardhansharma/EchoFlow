package com.echoflow.ui

import com.echoflow.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

internal class CustomProviderFlowRouter(private val service: CustomProviderService) {
    fun stream(provider: String?, config: CustomProviderConfig, model: String, history: List<ChatMessage>, prompt: String, params: InferenceParams): Flow<StreamChunk> = when (provider) {
        "openai" -> service.streamOpenAi(config.openAiApiKey, model, history, prompt, params)
        "claude" -> service.streamClaude(config.claudeApiKey, model, history, prompt, params)
        "gemini" -> service.streamGemini(config.geminiApiKey, model, history, prompt, params)
        "cerebras" -> service.streamCerebras(config.cerebrasApiKey, model, history, prompt, params)
        "sarvam" -> flow {
            config.sarvamConfigurationError()?.let { error(it) }
            emitAll(service.streamOpenAiCompatible("https://api.sarvam.ai/v1", config.sarvamApiKey, model, textOnlyHistory(history), prompt, params))
        }
        "xai" -> service.streamOpenAiCompatible("https://api.x.ai/v1", config.xAiApiKey, model, history, prompt, params)
        "vercel" -> service.streamOpenAiCompatible(CustomProviderConfig.VERCEL_BASE_URL, config.vercelApiKey, model, history, prompt, params)
        "groq" -> service.streamOpenAiCompatible(CustomProviderConfig.GROQ_BASE_URL, config.groqApiKey, model, history, prompt, params)
        "together" -> service.streamOpenAiCompatible(CustomProviderConfig.TOGETHER_BASE_URL, config.togetherApiKey, model, history, prompt, params)
        "cloudflare" -> flow {
            if (config.cloudflareAccountId.isBlank()) error("Cloudflare account ID is missing. Add it under Settings → Custom → Cloudflare Workers AI.")
            emitAll(service.streamOpenAiCompatible(config.cloudflareBaseUrl, config.cloudflareApiKey, model, history, prompt, params))
        }
        "ollama" -> service.streamOllama(config.ollamaBaseUrl, model, history, prompt, params)
        "openai-compatible" -> service.streamOpenAiCompatible(config.openAiBaseUrl, config.openAiCompatibleApiKey, model, history, prompt, params)
        else -> flow { throw Exception("Unknown custom endpoint provider.") }
    }

    fun streamWithTools(provider: String?, config: CustomProviderConfig, model: String, history: List<ChatMessage>, prompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> = when (provider) {
        "openai" -> service.streamOpenAiResponsesTools(config.openAiApiKey, model, history, prompt, params, search)
        "cerebras" -> service.streamOpenAiTools("https://api.cerebras.ai/v1", config.cerebrasApiKey, model, history, prompt, params, search)
        "sarvam" -> flow {
            config.sarvamConfigurationError()?.let { error(it) }
            emitAll(service.streamOpenAiTools("https://api.sarvam.ai/v1", config.sarvamApiKey, model, textOnlyHistory(history), prompt, params, search))
        }
        "xai" -> service.streamOpenAiTools("https://api.x.ai/v1", config.xAiApiKey, model, history, prompt, params, search)
        "vercel" -> service.streamOpenAiTools(CustomProviderConfig.VERCEL_BASE_URL, config.vercelApiKey, model, history, prompt, params, search)
        "groq" -> service.streamOpenAiTools(CustomProviderConfig.GROQ_BASE_URL, config.groqApiKey, model, history, prompt, params, search)
        "together" -> service.streamOpenAiTools(CustomProviderConfig.TOGETHER_BASE_URL, config.togetherApiKey, model, history, prompt, params, search)
        // Workers AI supports tool calls on only a few models, so it keeps the plain stream.
        "openai-compatible" -> service.streamOpenAiTools(config.openAiBaseUrl, config.openAiCompatibleApiKey, model, history, prompt, params, search)
        "ollama" -> service.streamOllamaTools(config.ollamaBaseUrl, model, history, prompt, params, search)
        "claude" -> service.streamClaudeTools(config.claudeApiKey, model, history, prompt, params, search)
        "gemini" -> service.streamGeminiTools(config.geminiApiKey, model, history, prompt, params, search)
        else -> stream(provider, config, model, history, prompt, params)
    }
    // Copies preserve text without mutating stored history; transient extraAttachments reset too.
    private fun textOnlyHistory(history: List<ChatMessage>): List<ChatMessage> = history.map {
        it.copy(localAttachmentUri = null, localAttachmentMimeType = null,
            localAttachmentName = null, attachmentsJson = null)
    }
}
