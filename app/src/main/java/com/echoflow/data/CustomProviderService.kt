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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.net.InetAddress
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
    val ollamaBaseUrl: String,
    val ollamaModel: String,
    val ollamaModels: String,
    val ollamaSelectedModels: String,
    val ollamaImagesEnabled: Boolean,
    val ollamaPdfsEnabled: Boolean,
    val openAiBaseUrl: String,
    val openAiCompatibleApiKey: String,
    val openAiCompatibleModel: String,
    val openAiCompatibleModels: String,
    val openAiCompatibleSelectedModels: String,
    val openAiCompatibleImagesEnabled: Boolean,
    val openAiCompatiblePdfsEnabled: Boolean,
) {
    companion object {
        const val PREFIX_OPENAI = "custom/openai/"
        const val PREFIX_CLAUDE = "custom/claude/"
        const val PREFIX_GEMINI = "custom/gemini/"
        const val PREFIX_CEREBRAS = "custom/cerebras/"
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

enum class CustomModelProvider { OpenAi, Claude, Gemini, Cerebras, Ollama, OpenAiCompatible }

data class ProviderValidationResult(
    val ok: Boolean,
    val message: String,
)

class CustomProviderService(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
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
    ): Flow<StreamChunk> =
        streamOpenAiCompatible("https://api.openai.com/v1", apiKey, model, history, systemPrompt, params)

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
        val payload = mutableMapOf<String, Any>(
            "model" to model.trim(),
            "messages" to messages,
            "stream" to true,
            "max_tokens" to params.maxTokens.coerceAtLeast(256),
            "temperature" to params.temperature,
        )
        if (systemPrompt.isNotBlank()) payload["system"] = systemPrompt
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
            .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
            .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
            .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
                .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
                .post(dynamicAdapter.toJson(payload).toRequestBody("application/json".toMediaType()))
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
            if (base64 != null && msg.role == "user" && !msg.localAttachmentMimeType.equals("application/pdf", ignoreCase = true)) {
                val mime = msg.localAttachmentMimeType ?: "image/jpeg"
                out.add(
                    mapOf(
                        "role" to msg.role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to msg.content),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$base64")),
                        ),
                    )
                )
            } else {
                out.add(mapOf("role" to msg.role, "content" to msg.content))
            }
        }
        return out
    }

    private fun buildSimpleMessages(history: List<ChatMessage>, systemPrompt: String): List<Map<String, String>> =
        buildList {
            if (systemPrompt.isNotBlank()) add(mapOf("role" to "system", "content" to systemPrompt))
            history.forEach { add(mapOf("role" to it.role, "content" to it.content)) }
        }

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
            return parseModelIds(body)
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
            return parseModelIds(body)
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
            val raw = parseModelIds(body)
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
            return parseModelIds(body)
        }
    }

    private fun parseModelIds(json: String): List<String> {
        val map = dynamicAdapter.fromJson(json) as? Map<*, *> ?: return emptyList()
        val rawList = (map["data"] as? List<*>) ?: (map["models"] as? List<*>) ?: return emptyList()
        return rawList.mapNotNull { raw ->
            when (raw) {
                is String -> raw
                is Map<*, *> -> (raw["id"] as? String) ?: (raw["name"] as? String) ?: (raw["model"] as? String)
                else -> null
            }
        }.distinct()
    }

    private fun getBase64FromUri(uriString: String?): String? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = context?.contentResolver?.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        } catch (_: Exception) {
            null
        }
    }

    private fun joinUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (cleanBase.endsWith("/v1") || normalizedPath.startsWith("api/")) "$cleanBase/$normalizedPath"
        else "$cleanBase/v1/$normalizedPath"
    }

    private fun validateBaseUrl(raw: String): ProviderValidationResult {
        val url = raw.trim().toHttpUrlOrNull()
            ?: return ProviderValidationResult(false, "Enter a valid http:// or https:// base URL.")
        if (url.scheme != "http" && url.scheme != "https") {
            return ProviderValidationResult(false, "Base URL must start with http:// or https://.")
        }
        if (url.scheme == "http" && !isLocalOrPrivateHost(url.host)) {
            return ProviderValidationResult(false, "Use HTTPS for internet providers. Plain HTTP is allowed only for localhost or private LAN addresses.")
        }
        return ProviderValidationResult(true, "URL looks good.")
    }

    private fun isLocalOrPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "127.0.0.1" || h == "::1") return true
        return runCatching {
            val address = InetAddress.getByName(h)
            val bytes = address.address.map { it.toInt() and 0xff }
            address.isSiteLocalAddress ||
                address.isLoopbackAddress ||
                (bytes.size == 4 && bytes[0] == 169 && bytes[1] == 254)
        }.getOrDefault(false)
    }

    private fun customError(label: String, code: Int, body: String): String {
        val parsed = runCatching {
            val map = dynamicAdapter.fromJson(body) as? Map<*, *>
            val error = map?.get("error")
            when (error) {
                is String -> error
                is Map<*, *> -> error["message"] as? String
                else -> map?.get("message") as? String
            }
        }.getOrNull()
        return when (code) {
            401, 403 -> "$label rejected the API key or request."
            404 -> "$label endpoint or model was not found."
            else -> parsed ?: "$label returned HTTP $code."
        }
    }
}
