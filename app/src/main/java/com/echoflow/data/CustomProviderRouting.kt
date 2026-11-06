package com.echoflow.data

/**
 * Which direct provider a picked model runs on, and what that provider accepts. Chat routing
 * used to spell each of these out as its own `when` over provider ids; this is the one table.
 */
object CustomProviderRouting {
    /** Provider id → the model-id prefix that selects it. Order matters only for readability. */
    private val prefixes: List<Pair<String, String>> = listOf(
        "openai" to CustomProviderConfig.PREFIX_OPENAI,
        "claude" to CustomProviderConfig.PREFIX_CLAUDE,
        "gemini" to CustomProviderConfig.PREFIX_GEMINI,
        "cerebras" to CustomProviderConfig.PREFIX_CEREBRAS,
        "sarvam" to CustomProviderConfig.PREFIX_SARVAM,
        "xai" to CustomProviderConfig.PREFIX_XAI,
        "vercel" to CustomProviderConfig.PREFIX_VERCEL,
        "groq" to CustomProviderConfig.PREFIX_GROQ,
        "together" to CustomProviderConfig.PREFIX_TOGETHER,
        "enclave" to CustomProviderConfig.PREFIX_ENCLAVE,
        "experiential" to CustomProviderConfig.PREFIX_EXPERIENTIAL,
        "cloudflare" to CustomProviderConfig.PREFIX_CLOUDFLARE,
        "ollama" to CustomProviderConfig.PREFIX_OLLAMA,
        "openai-compatible" to CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE,
    )

    /** Open-weight hosts whose vision support is decided by the model family. */
    private val openModelHosts = setOf("groq", "together", "enclave", "cloudflare")

    /** The direct provider [selectedModel] runs on, or null for OpenRouter / on-device. */
    fun providerFor(selectedModel: String): String? =
        prefixes.firstOrNull { (_, prefix) -> selectedModel.startsWith(prefix) }?.first

    /** The model id the provider's own API expects. */
    fun requestModel(provider: String?, selectedModel: String): String =
        prefixes.firstOrNull { it.first == provider }?.let { selectedModel.removePrefix(it.second) } ?: selectedModel

    fun imagesAllowed(provider: String?, model: String, config: CustomProviderConfig): Boolean = when (provider) {
        "openai", "claude", "gemini" -> true
        "cerebras" -> CustomProviderCapabilities.cerebrasSupportsImages(model)
        "xai" -> CustomProviderCapabilities.xAiSupportsImages(model)
        "vercel" -> CustomProviderCapabilities.vercelSupportsImages(model)
        "experiential" -> CustomProviderCapabilities.experientialSupportsImages(model)
        in openModelHosts -> CustomProviderCapabilities.openModelSupportsImages(model)
        "ollama" -> config.ollamaImagesEnabled
        "openai-compatible" -> config.openAiCompatibleImagesEnabled
        else -> false
    }

    fun pdfsAllowed(provider: String?, model: String, config: CustomProviderConfig): Boolean = when (provider) {
        "openai", "claude", "gemini" -> true
        "cerebras" -> CustomProviderCapabilities.cerebrasSupportsPdfs(model)
        "xai" -> CustomProviderCapabilities.xAiSupportsPdfs(model)
        "vercel" -> CustomProviderCapabilities.vercelSupportsPdfs(model)
        "ollama" -> config.ollamaPdfsEnabled
        "openai-compatible" -> config.openAiCompatiblePdfsEnabled
        else -> false
    }

    /**
     * Sarvam never sends raw files (docs ride as anydoc Markdown, images as OCR text), so the
     * raw-attachment checks don't apply to it.
     */
    fun sendsRawAttachments(provider: String?): Boolean = provider != null && provider != "sarvam"

    /** Where the user turns a switchable capability on for [provider]. */
    private fun settingsPath(provider: String?): String =
        if (provider == "ollama" || provider == "openai-compatible") "Settings → Echo Labs → Custom API Endpoint"
        else "Settings → Custom"

    fun pdfOffMessage(provider: String?): String =
        "PDF is off for this custom endpoint. Turn it on in ${settingsPath(provider)}."

    fun imageOffMessage(provider: String?, model: String): String = when (provider) {
        "xai" -> "$model does not support image attachments. Choose an xAI vision model such as grok-4.5."
        in openModelHosts -> "$model does not support image attachments. Choose a vision model, such as a Llama 4 model."
        else -> "Images are off for this custom endpoint. Turn them on in ${settingsPath(provider)}."
    }
}
