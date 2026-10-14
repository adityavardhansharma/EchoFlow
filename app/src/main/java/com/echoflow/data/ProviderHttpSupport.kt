package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

/** Pure protocol helpers shared by custom-provider transports. */
internal object ProviderHttpSupport {
    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun simpleMessages(history: List<ChatMessage>, systemPrompt: String): List<Map<String, String>> = buildList {
        if (systemPrompt.isNotBlank()) add(mapOf("role" to "system", "content" to systemPrompt))
        history.forEach { add(mapOf("role" to it.role, "content" to it.content)) }
    }

    fun joinApiUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (cleanBase.endsWith("/v1") || normalizedPath.startsWith("api/")) {
            "$cleanBase/$normalizedPath"
        } else {
            "$cleanBase/v1/$normalizedPath"
        }
    }

    fun validateBaseUrl(raw: String): ProviderValidationResult {
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

    fun parseModelIds(body: String): List<String> {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val items = (map["data"] as? List<*>) ?: (map["models"] as? List<*>) ?: return emptyList()
        return items.mapNotNull {
            when (it) {
                is String -> it
                is Map<*, *> -> (it["id"] as? String) ?: (it["name"] as? String) ?: (it["model"] as? String)
                else -> null
            }
        }.distinct()
    }

    /** Like [parseModelIds], but drops entries whose `type` says they aren't language models. */
    fun parseLanguageModelIds(body: String): List<String> {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val items = map["data"] as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val type = entry["type"] as? String
            if (type != null && type != "language") return@mapNotNull null
            entry["id"] as? String
        }.distinct()
    }

    /** Together's `/models` is a bare array; keeps the chat, language and code models. */
    fun parseTogetherChatModelIds(body: String): List<String> {
        val items = runCatching { json.fromJson(body) as? List<*> }.getOrNull() ?: return emptyList()
        return items.mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val type = entry["type"] as? String
            if (type != null && type !in setOf("chat", "language", "code")) return@mapNotNull null
            entry["id"] as? String
        }.distinct()
    }

    /**
     * Gemini's `/v1beta/models` lists every model the key can reach: embeddings, Imagen, Veo,
     * Lyria music, TTS, Live/native-audio, image output, and deprecated or shut-down models.
     * Keeps the text chat models that are still current, without the `models/` prefix.
     */
    fun parseGeminiChatModelIds(body: String): List<String> {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val items = map["models"] as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val id = (entry["name"] as? String)?.removePrefix("models/") ?: return@mapNotNull null
            val methods = (entry["supportedGenerationMethods"] as? List<*>).orEmpty()
            if ("generateContent" !in methods) return@mapNotNull null
            val description = (entry["description"] as? String).orEmpty().lowercase()
            if (GEMINI_RETIRED_DESCRIPTION.any { it in description }) return@mapNotNull null
            if (!isGeminiChatModelId(id)) return@mapNotNull null
            id
        }.distinct()
    }

    /** Next-page token from a Gemini model list, or null on the last page. */
    fun geminiNextPageToken(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        return (map["nextPageToken"] as? String)?.takeIf { it.isNotBlank() }
    }

    /**
     * New models pass automatically; only non-chat families and models Google has deprecated or
     * shut down are dropped. The API has no deprecation field, so [GEMINI_DEPRECATED_IDS] mirrors
     * https://ai.google.dev/gemini-api/docs/deprecations and needs updating when Google adds to it.
     */
    internal fun isGeminiChatModelId(id: String): Boolean {
        val lower = id.lowercase()
        if (GEMINI_NON_CHAT_MARKERS.any { it in lower }) return false
        if (GEMINI_RETIRED_PREFIXES.any { lower.startsWith(it) }) return false
        if (lower in GEMINI_DEPRECATED_IDS) return false
        return true
    }

    private val GEMINI_NON_CHAT_MARKERS = listOf(
        "embedding", "imagen", "veo-", "lyria", "tts", "native-audio", "-live", "-audio", "transcribe",
        "-image", "nano-banana", "omni", "aqa", "computer-use", "robotics", "deep-research", "antigravity",
    )

    /** Whole generations Google has shut down. */
    private val GEMINI_RETIRED_PREFIXES = listOf(
        "gemini-1.", "gemini-2.0-", "gemini-pro-vision", "learnlm", "chat-bison", "text-bison",
    )
    private val GEMINI_RETIRED_DESCRIPTION = listOf("deprecated", "discontinued", "will be retired", "shut down")

    /** Chat models on Google's deprecations page: shut down, scheduled to shut down, or auto-routed. */
    private val GEMINI_DEPRECATED_IDS = setOf(
        "gemini-2.5-flash-preview-05-20",
        "gemini-2.5-flash-preview-09-25",
        "gemini-2.5-flash-preview-09-2025",
        "gemini-2.5-flash-lite-preview-09-2025",
        "gemini-2.5-pro-preview-03-25",
        "gemini-2.5-pro-preview-05-06",
        "gemini-2.5-pro-preview-06-05",
        "gemini-3-pro-preview",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.7-flash",
    )

    /** Workers AI model search: `{"result":[{"name":"@cf/meta/llama-3.1-8b-instruct",…}]}`. */
    fun parseCloudflareModelNames(body: String): List<String> {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val items = map["result"] as? List<*> ?: return emptyList()
        return items.mapNotNull { (it as? Map<*, *>)?.get("name") as? String }.distinct()
    }

    fun errorMessage(label: String, code: Int, body: String): String {
        val parsed = runCatching {
            val map = json.fromJson(body) as? Map<*, *>
            when (val error = map?.get("error")) {
                is String -> error
                is Map<*, *> -> error["message"] as? String
                // Deepgram reports `err_msg`; most other providers use `message`.
                else -> (map?.get("message") ?: map?.get("err_msg")) as? String
            }
        }.getOrNull()
        return when (code) {
            401, 403 -> "$label rejected the API key or request."
            404 -> "$label endpoint or model was not found."
            else -> parsed ?: "$label returned HTTP $code."
        }
    }

    private fun isLocalOrPrivateHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1") return true
        return runCatching {
            val address = InetAddress.getByName(normalized)
            val bytes = address.address.map { it.toInt() and 0xff }
            address.isSiteLocalAddress || address.isLoopbackAddress ||
                (bytes.size == 4 && bytes[0] == 169 && bytes[1] == 254)
        }.getOrDefault(false)
    }
}
