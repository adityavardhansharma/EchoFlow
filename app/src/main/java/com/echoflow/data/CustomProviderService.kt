package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CustomProviderConfig(
    val cloudApisEnabled: Boolean,
    val ollamaEnabled: Boolean,
    val openAiCompatibleEnabled: Boolean,
    val openAiEnabled: Boolean,
    val openAiApiKey: String,
    val openAiModel: String,
    val openAiModels: String,
    val openAiSelectedModels: String,
    val claudeEnabled: Boolean,
    val claudeApiKey: String,
    val claudeModel: String,
    val claudeModels: String,
    val claudeSelectedModels: String,
    val geminiEnabled: Boolean,
    val geminiApiKey: String,
    val geminiModel: String,
    val geminiModels: String,
    val geminiSelectedModels: String,
    val cerebrasEnabled: Boolean,
    val cerebrasApiKey: String,
    val cerebrasModel: String,
    val cerebrasModels: String,
    val cerebrasSelectedModels: String,
    val xAiEnabled: Boolean,
    val xAiApiKey: String,
    val xAiModel: String,
    val xAiModels: String,
    val xAiSelectedModels: String,
    val ollamaBaseUrl: String,
    val ollamaModel: String,
    val ollamaModels: String,
    val ollamaSelectedModels: String,
    val ollamaImagesEnabled: Boolean,
    val ollamaPdfsEnabled: Boolean,
    val ollamaToolCallingEnabled: Boolean,
    val openAiBaseUrl: String,
    val openAiCompatibleApiKey: String,
    val openAiCompatibleModel: String,
    val openAiCompatibleModels: String,
    val openAiCompatibleSelectedModels: String,
    val openAiCompatibleImagesEnabled: Boolean,
    val openAiCompatiblePdfsEnabled: Boolean,
    val openAiCompatibleToolCallingEnabled: Boolean,
) {
    companion object {
        const val PREFIX_OPENAI = "custom/openai/"
        const val PREFIX_CLAUDE = "custom/claude/"
        const val PREFIX_GEMINI = "custom/gemini/"
        const val PREFIX_CEREBRAS = "custom/cerebras/"
        const val PREFIX_XAI = "custom/xai/"
        const val PREFIX_OLLAMA = "custom/ollama/"
        const val PREFIX_OPENAI_COMPATIBLE = "custom/openai-compatible/"
    }
}

data class CustomProviderModel(
    val id: String,
    val name: String,
    val group: String,
    val isLocalLike: Boolean,
)

enum class CustomModelProvider { OpenAi, Claude, Gemini, Cerebras, XAi, Ollama, OpenAiCompatible }

object CustomProviderCapabilities {
    fun cerebrasSupportsImages(model: String): Boolean {
        val id = model.trim().lowercase()
        return id.startsWith("gemma") || id.contains("/gemma")
    }

    fun cerebrasSupportsPdfs(model: String): Boolean = false

    /**
     * xAI's model list includes text-only and multimodal entries. Keep this allowlist
     * conservative so unknown or newly-added model IDs fail locally instead of sending an
     * unsupported image_url payload to the chat completions endpoint.
     */
    fun xAiSupportsImages(model: String): Boolean {
        val id = model.trim().lowercase()
        if (id == "latest" || id == "grok-latest") return true
        return listOf("grok-4.3", "grok-4.20", "grok-4.5").any { family ->
            id == family || id.startsWith("$family-")
        }
    }

    fun xAiSupportsPdfs(model: String): Boolean = false

    /**
     * Anthropic rejects `temperature` / `top_p` / `top_k` on Claude Opus 4.7 and later
     * (including Sonnet 5, Fable 5, Mythos). Sending them returns HTTP 400:
     * "temperature is deprecated for this model." Older Claude 4.6 / 4.5 / 3.x IDs
     * still accept temperature, so keep sending it there.
     */
    fun claudeSupportsSamplingParams(model: String): Boolean {
        val id = model.trim().lowercase().substringAfterLast('/')
        if (id.contains("mythos") || id.contains("glasswing")) return false
        val version = claudeModelVersion(id) ?: return true
        return version < ClaudeVersion(4, 7)
    }

    fun putClaudeSampling(payload: MutableMap<String, Any?>, model: String, params: InferenceParams) {
        if (claudeSupportsSamplingParams(model)) {
            payload["temperature"] = params.temperature
        }
    }

    private data class ClaudeVersion(val major: Int, val minor: Int) : Comparable<ClaudeVersion> {
        override fun compareTo(other: ClaudeVersion) = compareValuesBy(this, other, { it.major }, { it.minor })
    }

    // claude-sonnet-4-6, claude-opus-4.8, claude-sonnet-5, claude-3-7-sonnet-20250219
    private val CLAUDE_VERSION = Regex(
        """claude-(?:opus-|sonnet-|haiku-|fable-|mythos-)?(\d+)(?:[.\-](\d+))?""",
    )

    private fun claudeModelVersion(id: String): ClaudeVersion? {
        val match = CLAUDE_VERSION.find(id) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        return ClaudeVersion(major, minor)
    }
}

data class ProviderValidationResult(
    val ok: Boolean,
    val message: String,
)

class CustomProviderService(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            if (!chain.request().url.isHttps) {
                require(chain.request().header("Authorization").isNullOrBlank()) {
                    "HTTP provider endpoints cannot send API credentials. Use HTTPS for authenticated providers."
                }
                val address = chain.connection()?.route()?.socketAddress?.address
                require(address != null && (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress)) {
                    "Plain HTTP is allowed only for localhost or private LAN providers."
                }
            }
            chain.proceed(chain.request())
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dynamicAdapter = moshi.adapter(Any::class.java)
    private val openAiStreamAdapter = moshi.adapter(OpenAiStreamEvent::class.java)
    private val ollamaStreamAdapter = moshi.adapter(OllamaStreamEvent::class.java)
    private val geminiStreamAdapter = moshi.adapter(GeminiStreamEvent::class.java)
    private val toolStreamer by lazy {
        CustomProviderToolStreamer(
            client = client,
            dynamicAdapter = dynamicAdapter,
            openAiMessages = ::buildOpenAiMessages,
            openAiResponsesInput = { history, _ -> OpenAiResponses.inputItems(history, ::getBase64FromUri) },
            simpleMessages = ::buildSimpleMessages,
            urlJoiner = ::joinUrl,
            baseUrlValidator = ::validateBaseUrl,
            errorDecoder = ::customError,
        )
    }

    @JsonClass(generateAdapter = true)
    data class OpenAiStreamEvent(val choices: List<OpenAiChoice>? = null)

    @JsonClass(generateAdapter = true)
    data class OpenAiChoice(val delta: OpenAiDelta? = null, val message: OpenAiMessage? = null)

    @JsonClass(generateAdapter = true)
    data class OpenAiDelta(
        val content: String? = null,
        val reasoning: String? = null,
        val reasoning_content: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenAiMessage(val content: String? = null)

    @JsonClass(generateAdapter = true)
    data class OllamaStreamEvent(
        val message: OllamaMessage? = null,
        val response: String? = null,
        val done: Boolean? = null,
        val error: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OllamaMessage(val content: String? = null)

    @JsonClass(generateAdapter = true)
    data class GeminiStreamEvent(val candidates: List<GeminiCandidate>? = null)

    @JsonClass(generateAdapter = true)
    data class GeminiCandidate(val content: GeminiContent? = null)

    @JsonClass(generateAdapter = true)
    data class GeminiContent(val parts: List<GeminiPart>? = null)

    @JsonClass(generateAdapter = true)
    data class GeminiPart(val text: String? = null)

    fun streamOpenAi(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("OpenAI API key is missing.")
        if (model.isBlank()) throw Exception("Enter an OpenAI model name.")
        val payload = OpenAiResponses.request(
            model = model,
            input = OpenAiResponses.inputItems(history, ::getBase64FromUri),
            instructions = systemPrompt,
            params = params,
        )
        val request = Request.Builder()
            .url(joinUrl(OpenAiResponses.DEFAULT_BASE_URL, OpenAiResponses.PATH))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(customError("OpenAI", response.code, response.body?.string().orEmpty()))
            val body = response.body ?: throw Exception("Empty stream body received.")
            body.source().use { source ->
                OpenAiResponses.consumeStream(source) { event ->
                    when (event) {
                        is OpenAiResponses.Event.Content -> emit(StreamChunk.Content(event.text))
                        is OpenAiResponses.Event.Reasoning -> emit(StreamChunk.Reasoning(event.text))
                        else -> Unit
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun streamClaude(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("Claude API key is missing.")
        if (model.isBlank()) throw Exception("Enter a Claude model name.")
        val messages = history.filter { it.role != "system" }.map {
            mapOf("role" to if (it.role == "assistant") "assistant" else "user", "content" to it.content)
        }
        val payload = mutableMapOf<String, Any?>(
            "model" to model.trim(),
            "messages" to messages,
            "stream" to true,
            "max_tokens" to params.maxTokens.coerceAtLeast(256),
        )
        CustomProviderCapabilities.putClaudeSampling(payload, model, params)
        if (systemPrompt.isNotBlank()) payload["system"] = systemPrompt
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(customError("Claude", response.code, response.body?.string().orEmpty()))
            val body = response.body ?: throw Exception("Empty stream body received.")
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val map = runCatching { dynamicAdapter.fromJson(data) as? Map<*, *> }.getOrNull() ?: continue
                    val delta = map["delta"] as? Map<*, *>
                    (delta?.get("text") as? String)?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Content(it)) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun streamGemini(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("Gemini API key is missing.")
        if (model.isBlank()) throw Exception("Enter a Gemini model name.")
        val contents = history.filter { it.role != "system" }.map {
            mapOf(
                "role" to if (it.role == "assistant") "model" else "user",
                "parts" to listOf(mapOf("text" to it.content)),
            )
        }
        val payload = mutableMapOf<String, Any>(
            "contents" to contents,
            "generationConfig" to mapOf(
                "temperature" to params.temperature,
                "topP" to params.topP,
                "maxOutputTokens" to params.maxTokens.coerceAtLeast(256),
            ),
        )
        if (systemPrompt.isNotBlank()) {
            payload["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to systemPrompt)))
        }
        val cleanModel = model.removePrefix("models/")
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?key=${apiKey.trim()}&alt=sse")
            .addHeader("Content-Type", "application/json")
            .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(customError("Gemini", response.code, response.body?.string().orEmpty()))
            val body = response.body ?: throw Exception("Empty stream body received.")
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val event = runCatching { geminiStreamAdapter.fromJson(data) }.getOrNull() ?: continue
                    event.candidates?.flatMap { it.content?.parts.orEmpty() }?.forEach { part ->
                        part.text?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Content(it)) }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun streamCerebras(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> =
        streamOpenAiCompatible("https://api.cerebras.ai/v1", apiKey, model, history, systemPrompt, params)

    fun streamOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = flow {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        if (model.isBlank()) throw Exception("Enter a model name for the OpenAI-compatible provider.")

        val payload = mutableMapOf<String, Any>(
            "model" to model.trim(),
            "messages" to buildOpenAiMessages(history, systemPrompt),
            "stream" to true,
            "temperature" to params.temperature,
            "top_p" to params.topP,
        )
        if (params.maxTokens > 0) payload["max_tokens"] = params.maxTokens
        val request = Request.Builder()
            .url(joinUrl(baseUrl, "chat/completions"))
            .addHeader("Content-Type", "application/json")
            .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { addHeader("Authorization", "Bearer $it") } }
            .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(customError("OpenAI-compatible provider", response.code, response.body?.string().orEmpty()))
            val body = response.body ?: throw Exception("Empty stream body received.")
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val event = runCatching { openAiStreamAdapter.fromJson(data) }.getOrNull() ?: continue
                    event.choices?.forEach { choice ->
                        choice.delta?.reasoning?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Reasoning(it)) }
                        choice.delta?.reasoning_content?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Reasoning(it)) }
                        choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Content(it)) }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun streamOllama(
        baseUrl: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = flow {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        if (model.isBlank()) throw Exception("Enter an Ollama model name.")

        val payload = mapOf(
            "model" to model.trim(),
            "messages" to buildSimpleMessages(history, systemPrompt),
            "stream" to true,
            "options" to mapOf(
                "temperature" to params.temperature,
                "top_p" to params.topP,
                "top_k" to params.topK,
                "num_predict" to params.maxTokens,
            ),
        )
        val request = Request.Builder()
            .url(joinUrl(baseUrl, "api/chat"))
            .addHeader("Content-Type", "application/json")
            .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception(customError("Ollama", response.code, response.body?.string().orEmpty()))
            val body = response.body ?: throw Exception("Empty stream body received.")
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val event = runCatching { ollamaStreamAdapter.fromJson(line) }.getOrNull() ?: continue
                    event.error?.takeIf { it.isNotBlank() }?.let { throw Exception(it) }
                    event.message?.content?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Content(it)) }
                    event.response?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Content(it)) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Native tool calling (web search) ──────────────────────────────────────────────
    //
    // Separate from the plain stream* methods above: these expose a single `web_search` tool to
    // the model and run a request → tool_call → search → feed-back loop until the model answers.
    // Each provider family needs its own wire format (OpenAI tool_calls, Claude tool_use blocks,
    // Gemini functionCall parts). [search] runs the actual query via the app's client search
    // provider. Results are fed back with [formatSearchResultsForModel] so the numbered `[n](url)`
    // citation contract the system prompt describes lines up.

    fun streamOpenAiTools(baseUrl: String, apiKey: String, model: String, history: List<ChatMessage>, systemPrompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> =
        toolStreamer.streamOpenAiTools(baseUrl, apiKey, model, history, systemPrompt, params, search)

    fun streamOpenAiResponsesTools(apiKey: String, model: String, history: List<ChatMessage>, systemPrompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> =
        toolStreamer.streamOpenAiResponsesTools(apiKey, model, history, systemPrompt, params, search)

    fun streamOllamaTools(baseUrl: String, model: String, history: List<ChatMessage>, systemPrompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> =
        toolStreamer.streamOllamaTools(baseUrl, model, history, systemPrompt, params, search)

    fun streamClaudeTools(apiKey: String, model: String, history: List<ChatMessage>, systemPrompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> =
        toolStreamer.streamClaudeTools(apiKey, model, history, systemPrompt, params, search)

    fun streamGeminiTools(apiKey: String, model: String, history: List<ChatMessage>, systemPrompt: String, params: InferenceParams, search: suspend (String) -> List<SearchSource>): Flow<StreamChunk> =
        toolStreamer.streamGeminiTools(apiKey, model, history, systemPrompt, params, search)
    suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String, model: String): ProviderValidationResult = withContext(Dispatchers.IO) {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { return@withContext it }
        if (model.isBlank()) return@withContext ProviderValidationResult(false, "Enter a model name first.")
        runCatching {
            val payload = mapOf(
                "model" to model.trim(),
                "messages" to listOf(mapOf("role" to "user", "content" to "Reply with OK.")),
                "stream" to false,
                "max_tokens" to 8,
            )
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "chat/completions"))
                .addHeader("Content-Type", "application/json")
                .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { addHeader("Authorization", "Bearer $it") } }
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) ProviderValidationResult(false, customError("OpenAI-compatible provider", response.code, response.body?.string().orEmpty()))
                else ProviderValidationResult(true, "Connection works.")
            }
        }.getOrElse { ProviderValidationResult(false, it.message ?: "Connection failed.") }
    }

    suspend fun fetchModels(provider: CustomModelProvider, baseUrl: String = "", apiKey: String = ""): ProviderValidationResult = withContext(Dispatchers.IO) {
        runCatching {
            val models = when (provider) {
                CustomModelProvider.OpenAi -> fetchOpenAiStyleModels("https://api.openai.com/v1", apiKey)
                CustomModelProvider.Claude -> fetchClaudeModels(apiKey)
                CustomModelProvider.Gemini -> fetchGeminiModels(apiKey)
                CustomModelProvider.Cerebras -> fetchOpenAiStyleModels("https://api.cerebras.ai/v1", apiKey)
                CustomModelProvider.XAi -> fetchOpenAiStyleModels("https://api.x.ai/v1", apiKey)
                CustomModelProvider.Ollama -> fetchOllamaModels(baseUrl)
                CustomModelProvider.OpenAiCompatible -> fetchOpenAiCompatibleModels(baseUrl, apiKey)
            }
            if (models.isEmpty()) ProviderValidationResult(false, "No models were returned. You can still enter one manually.")
            else ProviderValidationResult(true, models.joinToString("\n"))
        }.getOrElse { ProviderValidationResult(false, it.message ?: "Could not fetch models.") }
    }

    suspend fun testOllama(baseUrl: String, model: String): ProviderValidationResult = withContext(Dispatchers.IO) {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { return@withContext it }
        if (model.isBlank()) return@withContext ProviderValidationResult(false, "Enter an Ollama model name first.")
        runCatching {
            val payload = mapOf(
                "model" to model.trim(),
                "messages" to listOf(mapOf("role" to "user", "content" to "Reply with OK.")),
                "stream" to false,
                "options" to mapOf("num_predict" to 8),
            )
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "api/chat"))
                .addHeader("Content-Type", "application/json")
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) ProviderValidationResult(false, customError("Ollama", response.code, response.body?.string().orEmpty()))
                else ProviderValidationResult(true, "Connection works.")
            }
        }.getOrElse { ProviderValidationResult(false, it.message ?: "Connection failed.") }
    }

    private fun buildOpenAiMessages(history: List<ChatMessage>, systemPrompt: String): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        if (systemPrompt.isNotBlank()) out.add(mapOf("role" to "system", "content" to systemPrompt))
        history.forEach { msg ->
            val base64 = getBase64FromUri(msg.localAttachmentUri)
            val extraImages = if (msg.role == "user") {
                msg.extraAttachments.mapNotNull { extra ->
                    if (extra.mimeType.equals("application/pdf", ignoreCase = true)) return@mapNotNull null
                    val encoded = getBase64FromUri(extra.uri) ?: return@mapNotNull null
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:${extra.mimeType};base64,$encoded"),
                    )
                }
            } else {
                emptyList()
            }
            if (base64 != null && msg.role == "user" && !msg.localAttachmentMimeType.equals("application/pdf", ignoreCase = true)) {
                val mime = msg.localAttachmentMimeType ?: "image/jpeg"
                out.add(
                    mapOf(
                        "role" to msg.role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to msg.content),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$base64")),
                        ) + extraImages,
                    )
                )
            } else if (extraImages.isNotEmpty()) {
                out.add(
                    mapOf(
                        "role" to msg.role,
                        "content" to listOf(mapOf("type" to "text", "text" to msg.content)) + extraImages,
                    )
                )
            } else {
                out.add(mapOf("role" to msg.role, "content" to msg.content))
            }
        }
        return out
    }

    private fun buildSimpleMessages(history: List<ChatMessage>, systemPrompt: String): List<Map<String, String>> =
        ProviderHttpSupport.simpleMessages(history, systemPrompt)

    private fun fetchOpenAiCompatibleModels(baseUrl: String, apiKey: String): List<String> {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        val clean = baseUrl.trim().trimEnd('/')
        val candidates = if (clean.endsWith("/v1")) {
            listOf("$clean/models")
        } else {
            listOf("$clean/v1/models", "$clean/models", "$clean/api/v1/models")
        }
        val errors = mutableListOf<String>()
        for (url in candidates.distinct()) {
            val models = runCatching { fetchModelsFromUrl(url, apiKey) }
                .onFailure { errors.add("${url.substringAfter("://").substringAfter("/")}: ${it.message}") }
                .getOrNull()
            if (!models.isNullOrEmpty()) return models
        }
        throw Exception("Could not fetch models from /v1/models, /models, or /api/v1/models.")
    }

    private fun fetchOpenAiStyleModels(baseUrl: String, apiKey: String): List<String> {
        if (apiKey.isBlank()) throw Exception("API key is missing.")
        return fetchModelsFromUrl("${baseUrl.trimEnd('/')}/models", apiKey)
    }

    private fun fetchModelsFromUrl(url: String, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(url)
            .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { addHeader("Authorization", "Bearer $it") } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return ProviderHttpSupport.parseModelIds(body)
        }
    }

    private fun fetchClaudeModels(apiKey: String): List<String> {
        if (apiKey.isBlank()) throw Exception("Claude API key is missing.")
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception(customError("Claude", response.code, body))
            return ProviderHttpSupport.parseModelIds(body)
        }
    }

    private fun fetchGeminiModels(apiKey: String): List<String> {
        if (apiKey.isBlank()) throw Exception("Gemini API key is missing.")
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception(customError("Gemini", response.code, body))
            val raw = ProviderHttpSupport.parseModelIds(body)
            return raw.map { it.removePrefix("models/") }
        }
    }

    private fun fetchOllamaModels(baseUrl: String): List<String> {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        val request = Request.Builder()
            .url("${baseUrl.trim().trimEnd('/')}/api/tags")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw Exception(customError("Ollama", response.code, body))
            return ProviderHttpSupport.parseModelIds(body)
        }
    }

    private fun getBase64FromUri(uriString: String?): String? {
        if (uriString == null) return null
        return try {
            val bytes = CappedAttachmentBytes.read(uriString) { uri ->
                context?.contentResolver?.openInputStream(uri)
            }
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (_: Exception) {
            null
        }
    }

    private fun joinUrl(baseUrl: String, path: String): String = ProviderHttpSupport.joinApiUrl(baseUrl, path)

    private fun validateBaseUrl(raw: String): ProviderValidationResult = ProviderHttpSupport.validateBaseUrl(raw)

    private fun customError(label: String, code: Int, body: String): String =
        ProviderHttpSupport.errorMessage(label, code, body)
}
