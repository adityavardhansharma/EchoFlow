package com.echoflow.data

import android.content.SharedPreferences

/**
 * The direct-provider settings (keys, models, switches) and their preference keys. Split out of
 * [SettingsRepository], which still owns trimming, the flow and the side effects of a save.
 * The keys are unchanged, so existing installs read the same values.
 */
internal class CustomProviderSettingsStore(private val prefs: SharedPreferences) {
    fun read(): CustomProviderConfig =
        CustomProviderConfig(
            cloudApisEnabled = prefs.getBoolean("labs_cloud_apis_enabled", false),
            ollamaEnabled = prefs.getBoolean("labs_ollama_enabled", false),
            openAiCompatibleEnabled = prefs.getBoolean("labs_openai_compatible_enabled", false),
            openAiEnabled = prefs.getBoolean("direct_openai_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            openAiApiKey = prefs.getString("direct_openai_api_key", "").orEmpty(),
            openAiModel = prefs.getString("direct_openai_model", "").orEmpty(),
            openAiModels = prefs.getString("direct_openai_models", "").orEmpty(),
            openAiSelectedModels = prefs.getString("direct_openai_selected_models", "").orEmpty(),
            claudeEnabled = prefs.getBoolean("direct_claude_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            claudeApiKey = prefs.getString("direct_claude_api_key", "").orEmpty(),
            claudeModel = prefs.getString("direct_claude_model", "").orEmpty(),
            claudeModels = prefs.getString("direct_claude_models", "").orEmpty(),
            claudeSelectedModels = prefs.getString("direct_claude_selected_models", "").orEmpty(),
            geminiEnabled = prefs.getBoolean("direct_gemini_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            geminiApiKey = prefs.getString("direct_gemini_api_key", "").orEmpty(),
            geminiModel = prefs.getString("direct_gemini_model", "").orEmpty(),
            geminiModels = prefs.getString("direct_gemini_models", "").orEmpty(),
            geminiSelectedModels = prefs.getString("direct_gemini_selected_models", "").orEmpty(),
            cerebrasEnabled = prefs.getBoolean("direct_cerebras_enabled", false),
            cerebrasApiKey = prefs.getString("direct_cerebras_api_key", "").orEmpty(),
            cerebrasModel = prefs.getString("direct_cerebras_model", "").orEmpty(),
            cerebrasModels = prefs.getString("direct_cerebras_models", "").orEmpty(),
            cerebrasSelectedModels = prefs.getString("direct_cerebras_selected_models", "").orEmpty(),
            sarvamEnabled = prefs.getBoolean("direct_sarvam_enabled", false),
            sarvamApiKey = prefs.getString("direct_sarvam_api_key", "").orEmpty(),
            sarvamModel = prefs.getString("direct_sarvam_model", "").orEmpty(),
            sarvamModels = prefs.getString("direct_sarvam_models", "sarvam-105b\nsarvam-105b-conversations").orEmpty(),
            sarvamSelectedModels = prefs.getString("direct_sarvam_selected_models", "sarvam-105b").orEmpty(),
            deepgramEnabled = prefs.getBoolean("direct_deepgram_enabled", false),
            deepgramApiKey = prefs.getString("direct_deepgram_api_key", "").orEmpty(),
            elevenLabsEnabled = prefs.getBoolean("direct_elevenlabs_enabled", false),
            elevenLabsApiKey = prefs.getString("direct_elevenlabs_api_key", "").orEmpty(),
            xAiEnabled = prefs.getBoolean("direct_xai_enabled", false),
            xAiApiKey = prefs.getString("direct_xai_api_key", "").orEmpty(),
            xAiModel = prefs.getString("direct_xai_model", "").orEmpty(),
            xAiModels = prefs.getString("direct_xai_models", "").orEmpty(),
            xAiSelectedModels = prefs.getString("direct_xai_selected_models", "").orEmpty(),
            vercelEnabled = prefs.getBoolean("direct_vercel_enabled", false),
            vercelApiKey = prefs.getString("direct_vercel_api_key", "").orEmpty(),
            vercelModel = prefs.getString("direct_vercel_model", "").orEmpty(),
            vercelModels = prefs.getString("direct_vercel_models", "").orEmpty(),
            vercelSelectedModels = prefs.getString("direct_vercel_selected_models", "").orEmpty(),
            groqEnabled = prefs.getBoolean("direct_groq_enabled", false),
            groqApiKey = prefs.getString("direct_groq_api_key", "").orEmpty(),
            groqModel = prefs.getString("direct_groq_model", "").orEmpty(),
            groqModels = prefs.getString("direct_groq_models", "").orEmpty(),
            groqSelectedModels = prefs.getString("direct_groq_selected_models", "").orEmpty(),
            togetherEnabled = prefs.getBoolean("direct_together_enabled", false),
            togetherApiKey = prefs.getString("direct_together_api_key", "").orEmpty(),
            togetherModel = prefs.getString("direct_together_model", "").orEmpty(),
            togetherModels = prefs.getString("direct_together_models", "").orEmpty(),
            togetherSelectedModels = prefs.getString("direct_together_selected_models", "").orEmpty(),
            enclaveEnabled = prefs.getBoolean("direct_enclave_enabled", false),
            enclaveApiKey = prefs.getString("direct_enclave_api_key", "").orEmpty(),
            enclaveModel = prefs.getString("direct_enclave_model", "").orEmpty(),
            enclaveModels = prefs.getString("direct_enclave_models", "").orEmpty(),
            enclaveSelectedModels = prefs.getString("direct_enclave_selected_models", "").orEmpty(),
            experientialEnabled = prefs.getBoolean("direct_experiential_enabled", false),
            experientialApiKey = prefs.getString("direct_experiential_api_key", "").orEmpty(),
            experientialModel = prefs.getString("direct_experiential_model", "").orEmpty(),
            experientialModels = prefs.getString("direct_experiential_models", "").orEmpty(),
            experientialSelectedModels = prefs.getString("direct_experiential_selected_models", "").orEmpty(),
            cloudflareEnabled = prefs.getBoolean("direct_cloudflare_enabled", false),
            cloudflareApiKey = prefs.getString("direct_cloudflare_api_key", "").orEmpty(),
            cloudflareModel = prefs.getString("direct_cloudflare_model", "").orEmpty(),
            cloudflareModels = prefs.getString("direct_cloudflare_models", "").orEmpty(),
            cloudflareSelectedModels = prefs.getString("direct_cloudflare_selected_models", "").orEmpty(),
            cloudflareAccountId = prefs.getString("direct_cloudflare_account_id", "").orEmpty(),
            ollamaBaseUrl = prefs.getString("ollama_base_url", "http://localhost:11434").orEmpty(),
            ollamaModel = prefs.getString("ollama_model", "").orEmpty(),
            ollamaModels = prefs.getString("ollama_models", "").orEmpty(),
            ollamaSelectedModels = prefs.getString("ollama_selected_models", "").orEmpty(),
            ollamaImagesEnabled = prefs.getBoolean("ollama_images_enabled", false),
            ollamaPdfsEnabled = prefs.getBoolean("ollama_pdfs_enabled", false),
            ollamaToolCallingEnabled = prefs.getBoolean("ollama_tool_calling_enabled", false),
            openAiBaseUrl = prefs.getString("openai_compatible_base_url", "http://localhost:1234/v1").orEmpty(),
            openAiCompatibleApiKey = prefs.getString("openai_compatible_api_key", "").orEmpty(),
            openAiCompatibleModel = prefs.getString("openai_compatible_model", "").orEmpty(),
            openAiCompatibleModels = prefs.getString("openai_compatible_models", "").orEmpty(),
            openAiCompatibleSelectedModels = prefs.getString("openai_compatible_selected_models", "").orEmpty(),
            openAiCompatibleImagesEnabled = prefs.getBoolean("openai_compatible_images_enabled", false),
            openAiCompatiblePdfsEnabled = prefs.getBoolean("openai_compatible_pdfs_enabled", false),
            openAiCompatibleToolCallingEnabled = prefs.getBoolean("openai_compatible_tool_calling_enabled", false),
        )

    fun write(clean: CustomProviderConfig) {
        prefs.edit()
            .putBoolean("labs_cloud_apis_enabled", clean.cloudApisEnabled)
            .putBoolean("labs_ollama_enabled", clean.ollamaEnabled)
            .putBoolean("labs_openai_compatible_enabled", clean.openAiCompatibleEnabled)
            .putBoolean("direct_openai_enabled", clean.openAiEnabled)
            .putString("direct_openai_api_key", clean.openAiApiKey)
            .putString("direct_openai_model", clean.openAiModel)
            .putString("direct_openai_models", clean.openAiModels)
            .putString("direct_openai_selected_models", clean.openAiSelectedModels)
            .putBoolean("direct_claude_enabled", clean.claudeEnabled)
            .putString("direct_claude_api_key", clean.claudeApiKey)
            .putString("direct_claude_model", clean.claudeModel)
            .putString("direct_claude_models", clean.claudeModels)
            .putString("direct_claude_selected_models", clean.claudeSelectedModels)
            .putBoolean("direct_gemini_enabled", clean.geminiEnabled)
            .putString("direct_gemini_api_key", clean.geminiApiKey)
            .putString("direct_gemini_model", clean.geminiModel)
            .putString("direct_gemini_models", clean.geminiModels)
            .putString("direct_gemini_selected_models", clean.geminiSelectedModels)
            .putBoolean("direct_cerebras_enabled", clean.cerebrasEnabled)
            .putString("direct_cerebras_api_key", clean.cerebrasApiKey)
            .putString("direct_cerebras_model", clean.cerebrasModel)
            .putString("direct_cerebras_models", clean.cerebrasModels)
            .putString("direct_cerebras_selected_models", clean.cerebrasSelectedModels)
            .putBoolean("direct_sarvam_enabled", clean.sarvamEnabled)
            .putString("direct_sarvam_api_key", clean.sarvamApiKey)
            .putString("direct_sarvam_model", clean.sarvamModel)
            .putString("direct_sarvam_models", clean.sarvamModels)
            .putString("direct_sarvam_selected_models", clean.sarvamSelectedModels)
            .putBoolean("direct_deepgram_enabled", clean.deepgramEnabled)
            .putString("direct_deepgram_api_key", clean.deepgramApiKey)
            .putBoolean("direct_elevenlabs_enabled", clean.elevenLabsEnabled)
            .putString("direct_elevenlabs_api_key", clean.elevenLabsApiKey)
            .putBoolean("direct_xai_enabled", clean.xAiEnabled)
            .putString("direct_xai_api_key", clean.xAiApiKey)
            .putString("direct_xai_model", clean.xAiModel)
            .putString("direct_xai_models", clean.xAiModels)
            .putString("direct_xai_selected_models", clean.xAiSelectedModels)
            .putBoolean("direct_vercel_enabled", clean.vercelEnabled)
            .putString("direct_vercel_api_key", clean.vercelApiKey)
            .putString("direct_vercel_model", clean.vercelModel)
            .putString("direct_vercel_models", clean.vercelModels)
            .putString("direct_vercel_selected_models", clean.vercelSelectedModels)
            .putBoolean("direct_groq_enabled", clean.groqEnabled)
            .putString("direct_groq_api_key", clean.groqApiKey)
            .putString("direct_groq_model", clean.groqModel)
            .putString("direct_groq_models", clean.groqModels)
            .putString("direct_groq_selected_models", clean.groqSelectedModels)
            .putBoolean("direct_together_enabled", clean.togetherEnabled)
            .putString("direct_together_api_key", clean.togetherApiKey)
            .putString("direct_together_model", clean.togetherModel)
            .putString("direct_together_models", clean.togetherModels)
            .putString("direct_together_selected_models", clean.togetherSelectedModels)
            .putBoolean("direct_enclave_enabled", clean.enclaveEnabled)
            .putString("direct_enclave_api_key", clean.enclaveApiKey)
            .putString("direct_enclave_model", clean.enclaveModel)
            .putString("direct_enclave_models", clean.enclaveModels)
            .putString("direct_enclave_selected_models", clean.enclaveSelectedModels)
            .putBoolean("direct_experiential_enabled", clean.experientialEnabled)
            .putString("direct_experiential_api_key", clean.experientialApiKey)
            .putString("direct_experiential_model", clean.experientialModel)
            .putString("direct_experiential_models", clean.experientialModels)
            .putString("direct_experiential_selected_models", clean.experientialSelectedModels)
            .putBoolean("direct_cloudflare_enabled", clean.cloudflareEnabled)
            .putString("direct_cloudflare_api_key", clean.cloudflareApiKey)
            .putString("direct_cloudflare_model", clean.cloudflareModel)
            .putString("direct_cloudflare_models", clean.cloudflareModels)
            .putString("direct_cloudflare_selected_models", clean.cloudflareSelectedModels)
            .putString("direct_cloudflare_account_id", clean.cloudflareAccountId)
            .putString("ollama_base_url", clean.ollamaBaseUrl)
            .putString("ollama_model", clean.ollamaModel)
            .putString("ollama_models", clean.ollamaModels)
            .putString("ollama_selected_models", clean.ollamaSelectedModels)
            .putBoolean("ollama_images_enabled", clean.ollamaImagesEnabled)
            .putBoolean("ollama_pdfs_enabled", clean.ollamaPdfsEnabled)
            .putBoolean("ollama_tool_calling_enabled", clean.ollamaToolCallingEnabled)
            .putString("openai_compatible_base_url", clean.openAiBaseUrl)
            .putString("openai_compatible_api_key", clean.openAiCompatibleApiKey)
            .putString("openai_compatible_model", clean.openAiCompatibleModel)
            .putString("openai_compatible_models", clean.openAiCompatibleModels)
            .putString("openai_compatible_selected_models", clean.openAiCompatibleSelectedModels)
            .putBoolean("openai_compatible_images_enabled", clean.openAiCompatibleImagesEnabled)
            .putBoolean("openai_compatible_pdfs_enabled", clean.openAiCompatiblePdfsEnabled)
            .putBoolean("openai_compatible_tool_calling_enabled", clean.openAiCompatibleToolCallingEnabled)
            .apply()
    }
}

