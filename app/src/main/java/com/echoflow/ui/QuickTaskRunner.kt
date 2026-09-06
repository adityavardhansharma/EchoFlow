package com.echoflow.ui

import android.content.Context
import com.echoflow.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.File

/** Resolve and capture each selected route before any request is started. Never changes chat selection. */
internal class QuickTaskRunner(
    private val context: Context,
    private val settings: SettingsRepository,
    private val localModels: LocalModelDao,
    private val cloud: LlmGateway,
    private val local: LlmGateway,
    private val custom: CustomProviderFlowRouter,
    private val gate: LocalInferenceGate,
) {
    suspend fun issue(model: String, input: SharedInput): String? = runCatching { prepare(model, "Review this material.", input); null }
        .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; it.message ?: "Model unavailable." }

    suspend fun prepare(model: String, prompt: String, input: SharedInput): Flow<StreamChunk> {
        require(model.isNotBlank()) { "Choose a model." }
        require(input.files.none { !it.mime.startsWith("image/") && it.text.isNullOrBlank() }) {
            "A shared document has no readable text. Save it to a project, or share readable text instead."
        }
        val config = settings.getCustomProviderConfigDirect()
        val provider = model.takeIf { it.startsWith("custom/") }?.removePrefix("custom/")?.substringBefore('/')
        val modelName = if (provider != null) model.removePrefix("custom/$provider/") else model
        val onDevice = model.startsWith("local/")
        val lm = if (onDevice) localModels.getLocalModelById(model) else null
        val apiKey = settings.getApiKeyDirect()
        if (onDevice) require(settings.getLocalModelsEnabledDirect()) { "Enable on-device models in Settings." }
        if (provider != null) {
            val enabled = when (provider) {
                "ollama" -> config.ollamaEnabled
                "openai-compatible" -> config.openAiCompatibleEnabled
                "openai" -> config.cloudApisEnabled && config.openAiEnabled
                "claude" -> config.cloudApisEnabled && config.claudeEnabled
                "gemini" -> config.cloudApisEnabled && config.geminiEnabled
                "cerebras" -> config.cloudApisEnabled && config.cerebrasEnabled
                "xai" -> config.cloudApisEnabled && config.xAiEnabled
                else -> false
            }
            require(enabled) { "Enable this provider in Settings." }
        }
        when {
            onDevice -> require(lm != null && File(context.filesDir, "models/${lm.fileName}").isFile) { "Download this local model before using it." }
            provider == "ollama" -> require(config.ollamaBaseUrl.isNotBlank()) { "Configure the Ollama server in Settings." }
            provider == "openai-compatible" -> require(config.openAiBaseUrl.isNotBlank()) { "Configure this endpoint in Settings." }
            provider != null -> {
                val key = when (provider) {
                    "openai" -> config.openAiApiKey
                    "claude" -> config.claudeApiKey
                    "gemini" -> config.geminiApiKey
                    "cerebras" -> config.cerebrasApiKey
                    "xai" -> config.xAiApiKey
                    else -> error("Unsupported provider.")
                }
                require(key.isNotBlank()) { "Connect $provider in Settings before using this model." }
            }
            else -> require(apiKey.isNotBlank()) { "Add your OpenRouter API key in Settings." }
        }
        val images = input.files.filter { it.mime.startsWith("image/") }
        if (images.isNotEmpty()) {
            require(!(onDevice || provider != null) || images.size == 1) { "This route supports one shared image at a time. Choose OpenRouter for multiple images." }
            val supported = when {
                onDevice -> lm!!.fileName.endsWith(".litertlm", true)
                provider == "ollama" -> config.ollamaImagesEnabled
                provider == "openai-compatible" -> config.openAiCompatibleImagesEnabled
                provider == "cerebras" -> CustomProviderCapabilities.cerebrasSupportsImages(modelName)
                provider == "xai" -> CustomProviderCapabilities.xAiSupportsImages(modelName)
                provider != null -> true // The provider validates support for the selected model.
                else -> OpenRouterModelDirectory.imageInputSupport(model) == true
            }
            require(supported) { if (provider == null && !onDevice && OpenRouterModelDirectory.imageInputSupport(model) == null)
                "Image support is not known for this model. Load model details or choose a known image-capable model."
                else "This model or endpoint is not configured to accept images." }
        }
        val params = settings.getInferenceParamsDirect(local = onDevice)
        val contextSize = if (onDevice) LocalLlmPrompting.effectiveMaxTokens(lm!!, params)
            else if (provider != null) 8192 else OpenRouterModelDirectory.contextTokens(model)
        val reserve = if (onDevice) (contextSize / 4).coerceIn(256, 1024)
            else params.maxTokens.takeIf { it > 0 }?.coerceAtMost(contextSize / 2) ?: 2048
        val first = images.firstOrNull()
        val message = ChatMessage("input", input.id, "user", QuickTaskPolicy.request(prompt, input), System.currentTimeMillis(),
            localAttachmentUri = first?.uri, localAttachmentMimeType = first?.mime, localAttachmentName = first?.name)
        message.extraAttachments = images.drop(1).map { LocalFileAttachment(it.uri, it.mime, it.name) }
        val prepared = RequestContextBudget.prepare(listOf(message), QuickTaskPolicy.SYSTEM, contextTokens = contextSize, outputTokens = reserve)
        val actualParams = InferenceLimits.coerce(params, ModelCapabilities(0, if (onDevice) InferenceLimits.LOCAL_TOP_K_MAX else InferenceLimits.CLOUD_TOP_K_MAX),
            if (onDevice) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS)
            .copy(maxTokens = if (onDevice) contextSize else reserve)
        val request = LlmStreamRequest(apiKey, model, "quick-${input.id}-$model", prepared.history, prepared.systemPrompt, actualParams, lm)
        return when {
            onDevice -> flow { gate.withExclusive("a shared task or comparison") { emitAll(local.stream(request)) } }
            provider != null -> custom.stream(provider, config, modelName, prepared.history, prepared.systemPrompt, actualParams)
            else -> cloud.stream(request)
        }
    }
}
