package com.echoflow.data

import android.content.Context
import com.echoflow.ui.CustomProviderFlowRouter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/** One process-scoped local engine is shared by chat, schedule creation, warm-up and due runs. */
object ScheduleLocalRuntime {
    val gate = LocalInferenceGate()
    @Volatile private var service: LocalLlmService? = null

    fun service(context: Context): LocalLlmService = service ?: synchronized(this) {
        service ?: LocalLlmService(context.applicationContext).also { service = it }
    }
}

/** Headless text generation through the same providers and sampler settings as chat. */
class ScheduleModelRunner(private val context: Context) {
    private val settings = SettingsRepository(context.applicationContext)

    /** Schedules respect the active search setting; a saved but disabled provider stays off. */
    fun activeSearchProvider(modelId: String): String? {
        val provider = settings.getWebSearchProviderDirect()
        return when {
            provider == "openrouter" && !modelId.startsWith("local/") &&
                !modelId.startsWith("custom/") && settings.getApiKeyDirect().isNotBlank() -> provider
            ClientSearchProviders.isReady(provider, settings.getSearchApiKeyDirect(provider)) -> provider
            else -> null
        }
    }

    suspend fun complete(
        modelId: String,
        userText: String,
        systemPrompt: String,
        runId: String = UUID.randomUUID().toString(),
        searchQuery: String? = null,
    ): String {
        val provider = searchQuery?.let { activeSearchProvider(modelId) }
        val serverWebSearch = provider == "openrouter"
        var searchUnavailable = searchQuery != null && provider == null
        val searchContext = if (searchQuery != null && provider != null && !serverWebSearch) {
            val sources = try {
                WebSearchService().search(provider, settings.getSearchApiKeyDirect(provider), searchQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (sources.isEmpty()) searchUnavailable = true
            sources.take(8).mapIndexed { index, source ->
                "[${index + 1}] ${source.title}\n${source.url}\n${source.snippet.orEmpty().take(1000)}"
            }.joinToString("\n\n").takeIf { it.isNotBlank() }
        } else null
        val prompt = if (serverWebSearch) {
            "$systemPrompt\n\nSearch the web for current information before answering. Cite source URLs. If search returns no evidence, say that current information could not be verified."
        } else if (searchContext != null) {
            "$systemPrompt\n\nCurrent search results supplied by EchoFlow. Base current claims on these results and cite their URLs. If they do not support a claim, say so.\n\n$searchContext"
        } else if (searchUnavailable) {
            "$systemPrompt\n\nWeb search is unavailable for this run. Complete the task using only available context. Do not invent current facts, listings, prices, or sources. Say clearly when current information could not be verified."
        } else systemPrompt
        val history = listOf(ChatMessage(
            id = UUID.randomUUID().toString(), chatId = runId, role = "user",
            content = userText, createdAt = System.currentTimeMillis(),
        ))
        val local = modelId.startsWith("local/")
        val localModel = if (local) AppDatabase.getDatabase(context).localModelDao()
            .getLocalModelById(modelId) ?: error("The selected on-device model is no longer installed.")
            else null
        val params = if (localModel != null) localParams(localModel) else InferenceLimits.coerce(
            settings.getInferenceParamsDirect(false),
            ModelCapabilities(0, InferenceLimits.CLOUD_TOP_K_MAX),
            InferenceLimits.CLOUD_DEFAULTS,
        )
        val output = StringBuilder()
        var sourcesFound = false
        if (local) {
            val model = localModel!!
            val engine = ScheduleLocalRuntime.service(context)
            require(engine.modelFileExists(model)) { "The selected on-device model file is missing." }
            ScheduleLocalRuntime.gate.withExclusive("a scheduled task") {
                engine.generate(model, runId, history, prompt, params).collect { chunk ->
                    if (chunk is StreamChunk.Content) output.append(chunk.text)
                }
            }
        } else {
            cloudFlow(modelId, history, prompt, params, serverWebSearch).collect { chunk ->
                if (chunk is StreamChunk.Content) output.append(chunk.text)
                if (chunk is StreamChunk.SearchSources && chunk.sources.isNotEmpty()) sourcesFound = true
            }
        }
        if (serverWebSearch && !sourcesFound) {
            searchUnavailable = true
            output.clear()
            val offlinePrompt = "$systemPrompt\n\nWeb search returned no sources. Complete the task using only available context. Do not invent current facts, listings, prices, or sources. Say clearly when current information could not be verified."
            cloudFlow(modelId, history, offlinePrompt, params, false).collect { chunk ->
                if (chunk is StreamChunk.Content) output.append(chunk.text)
            }
        }
        val answer = output.toString().trim().ifBlank { error("The model returned no answer.") }
        return if (searchUnavailable) {
            "Web search was unavailable for this run. Current information could not be verified.\n\n$answer"
        } else answer
    }

    suspend fun prewarm(modelId: String) {
        val model = AppDatabase.getDatabase(context).localModelDao().getLocalModelById(modelId)
            ?: return
        val params = localParams(model)
        if (ScheduleLocalRuntime.gate.isBusy) return
        ScheduleLocalRuntime.gate.withExclusive("scheduled model warm-up") {
            withContext(Dispatchers.IO) { ScheduleLocalRuntime.service(context).prewarm(model, params) }
        }
    }

    private fun localParams(model: LocalModel): InferenceParams = InferenceLimits.coerce(
        settings.getInferenceParamsDirect(true),
        ModelCapabilities(
            (model.maxTokens ?: LocalModelCatalog.maxTokensFor(model.id, model.fileName))
                .coerceAtMost(InferenceLimits.LOCAL_MAX_TOKENS_CEIL),
            InferenceLimits.LOCAL_TOP_K_MAX,
        ),
        InferenceLimits.LOCAL_DEFAULTS,
    )

    private fun cloudFlow(
        modelId: String,
        history: List<ChatMessage>,
        prompt: String,
        params: InferenceParams,
        serverWebSearch: Boolean,
    ): Flow<StreamChunk> {
        val prefix = listOf(
            CustomProviderConfig.PREFIX_OPENAI to "openai",
            CustomProviderConfig.PREFIX_CLAUDE to "claude",
            CustomProviderConfig.PREFIX_GEMINI to "gemini",
            CustomProviderConfig.PREFIX_CEREBRAS to "cerebras",
            CustomProviderConfig.PREFIX_SARVAM to "sarvam",
            CustomProviderConfig.PREFIX_XAI to "xai",
            CustomProviderConfig.PREFIX_OLLAMA to "ollama",
            CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE to "openai-compatible",
        ).firstOrNull { modelId.startsWith(it.first) }
        if (prefix != null) {
            return CustomProviderFlowRouter(CustomProviderService(context)).stream(
                prefix.second, settings.getCustomProviderConfigDirect(),
                modelId.removePrefix(prefix.first), history, prompt, params,
            )
        }
        return OpenRouterService(context).sendChatMessageStream(
            settings.getApiKeyDirect(), modelId, history, prompt, params = params,
            serverWebSearch = serverWebSearch,
        )
    }
}
