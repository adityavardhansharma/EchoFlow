package com.echoflow.ui

import com.echoflow.data.CustomProviderConfig
import com.echoflow.data.CustomProviderModel

internal object CustomProviderModelCatalog {
    fun entries(config: CustomProviderConfig): List<CustomProviderModel> = buildList {
        fun addProvider(enabled: Boolean, selected: String, manual: String, prefix: String, group: String, local: Boolean) {
            if (!enabled) return
            modelIds(selected, manual).forEach { add(CustomProviderModel(prefix + it, it, group, local)) }
        }
        addProvider(config.cloudApisEnabled && config.openAiEnabled, config.openAiSelectedModels, config.openAiModel, CustomProviderConfig.PREFIX_OPENAI, "OpenAI", false)
        addProvider(config.cloudApisEnabled && config.claudeEnabled, config.claudeSelectedModels, config.claudeModel, CustomProviderConfig.PREFIX_CLAUDE, "Claude", false)
        addProvider(config.cloudApisEnabled && config.geminiEnabled, config.geminiSelectedModels, config.geminiModel, CustomProviderConfig.PREFIX_GEMINI, "Gemini", false)
        addProvider(config.cloudApisEnabled && config.cerebrasEnabled, config.cerebrasSelectedModels, config.cerebrasModel, CustomProviderConfig.PREFIX_CEREBRAS, "Cerebras", false)
        addProvider(config.cloudApisEnabled && config.xAiEnabled, config.xAiSelectedModels, config.xAiModel, CustomProviderConfig.PREFIX_XAI, "xAI", false)
        addProvider(config.ollamaEnabled, config.ollamaSelectedModels, config.ollamaModel, CustomProviderConfig.PREFIX_OLLAMA, "Ollama", true)
        addProvider(config.openAiCompatibleEnabled, config.openAiCompatibleSelectedModels, config.openAiCompatibleModel, CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE, "OpenAI-compatible", true)
    }

    private fun modelIds(selected: String, manual: String): List<String> =
        (listOf(manual.trim()) + selected.lineSequence().map(String::trim))
            .filter(String::isNotEmpty).distinct()
}
