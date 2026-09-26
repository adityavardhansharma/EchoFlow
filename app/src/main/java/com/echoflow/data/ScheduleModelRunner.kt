package com.echoflow.data

import android.content.Context
import com.echoflow.ui.CustomProviderFlowRouter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.withContext

/** One process-scoped local engine is shared by chat, schedule creation, warm-up and due runs. */
object ScheduleLocalRuntime {
    val gate = LocalInferenceGate()
    @Volatile private var service: LocalLlmService? = null

    fun service(context: Context): LocalLlmService = service ?: synchronized(this) {
        service ?: LocalLlmService(context.applicationContext).also { service = it }
    }
}

/**
 * How a schedule may reach the web for a given model. There is no per-schedule switch: the
 * model decides whether a run needs current information, and this decides only what it can use.
 */
sealed interface ScheduleWebAccess {
    /** No usable provider is configured; the model is told so and must not fabricate current facts. */
    data object None : ScheduleWebAccess
    /** OpenRouter's server-side search, which the model invokes on its own. */
    data object Server : ScheduleWebAccess
    /** A client-side provider EchoFlow calls when the model asks via the `web_search` tool. */
    data class Client(val provider: String) : ScheduleWebAccess
}

/** Headless text generation through the same providers and sampler settings as chat. */
class ScheduleModelRunner(private val context: Context) {
    private val settings = SettingsRepository(context.applicationContext)

    /** Schedules respect the active search setting; a saved but disabled provider stays off. */
    fun activeSearchProvider(modelId: String): String? = when (val access = webAccess(modelId)) {
        ScheduleWebAccess.None -> null
        ScheduleWebAccess.Server -> "openrouter"
        is ScheduleWebAccess.Client -> access.provider
    }

    fun webAccess(modelId: String): ScheduleWebAccess {
        val provider = settings.getWebSearchProviderDirect()
        return when {
            provider == "openrouter" && !modelId.startsWith("local/") &&
                !modelId.startsWith("custom/") &&
                settings.getApiKeyDirect().isNotBlank() -> ScheduleWebAccess.Server
            ClientSearchProviders.isReady(provider, settings.getSearchApiKeyDirect(provider)) -> ScheduleWebAccess.Client(provider)
            else -> ScheduleWebAccess.None
        }
    }

    suspend fun search(provider: String, query: String): List<SearchSource> =
        WebSearchService().search(provider, settings.getSearchApiKeyDirect(provider), query)

    /**
     * Streams the visible text deltas of one completion. [history] is the whole conversation,
     * oldest first; the local path shares the process-wide engine and its exclusive gate.
     */
    fun stream(
        modelId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        runId: String = UUID.randomUUID().toString(),
        serverWebSearch: Boolean = false,
        localWaitTimeoutMillis: Long = 0,
    ): Flow<String> = flow {
        val localModel = if (modelId.startsWith("local/")) AppDatabase.getDatabase(context).localModelDao()
            .getLocalModelById(modelId) ?: error("The selected on-device model is no longer installed.")
            else null
        if (localModel != null) {
            val engine = ScheduleLocalRuntime.service(context)
            require(engine.modelFileExists(localModel)) { "The selected on-device model file is missing." }
            ScheduleLocalRuntime.gate.withExclusive("a scheduled task", localWaitTimeoutMillis) {
                engine.generate(localModel, runId, history, systemPrompt, localParams(localModel)).collect { chunk ->
                    if (chunk is StreamChunk.Content) emit(chunk.text)
                }
            }
        } else {
            val params = InferenceLimits.coerce(
                settings.getInferenceParamsDirect(false),
                ModelCapabilities(0, InferenceLimits.CLOUD_TOP_K_MAX),
                InferenceLimits.CLOUD_DEFAULTS,
            )
            cloudFlow(modelId, history, systemPrompt, params, serverWebSearch).collect { chunk ->
                if (chunk is StreamChunk.Content) emit(chunk.text)
            }
        }
    }

    /** One-shot completion of a single prompt. */
    suspend fun complete(
        modelId: String,
        userText: String,
        systemPrompt: String,
        runId: String = UUID.randomUUID().toString(),
        localWaitTimeoutMillis: Long = 0,
    ): String = stream(
        modelId, listOf(message("user", userText, runId)), systemPrompt, runId,
        localWaitTimeoutMillis = localWaitTimeoutMillis,
    ).fold(StringBuilder()) { acc, delta -> acc.append(delta) }.toString().trim()
        .ifBlank { error("The model returned no answer.") }

    /** Loads [modelId] ahead of a due run and keeps it loaded for at least [keepForMillis]. */
    suspend fun prewarm(modelId: String, keepForMillis: Long = 0) {
        val model = AppDatabase.getDatabase(context).localModelDao().getLocalModelById(modelId)
            ?: return
        val params = localParams(model)
        if (ScheduleLocalRuntime.gate.isBusy) return
        ScheduleLocalRuntime.gate.withExclusive("scheduled model warm-up") {
            withContext(Dispatchers.IO) { ScheduleLocalRuntime.service(context).prewarm(model, params, keepForMillis) }
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
            CustomProviderConfig.PREFIX_VERCEL to "vercel",
            CustomProviderConfig.PREFIX_GROQ to "groq",
            CustomProviderConfig.PREFIX_TOGETHER to "together",
            CustomProviderConfig.PREFIX_CLOUDFLARE to "cloudflare",
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

    companion object {
        fun message(role: String, content: String, chatId: String = "schedule", at: Long = System.currentTimeMillis()) =
            ChatMessage(id = UUID.randomUUID().toString(), chatId = chatId, role = role, content = content, createdAt = at)
    }
}
