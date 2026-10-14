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
     * Lyria music, TTS, Live/native-audio, image output, and older Gemini generations.
     * Keeps current text chat models, without the `models/` prefix.
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
     * New models pass automatically; non-chat families and Gemini generations older than
     * [GEMINI_MIN_MAJOR].[GEMINI_MIN_MINOR] are dropped. Bump the floor when a newer series replaces the current one.
     */
    internal fun isGeminiChatModelId(id: String): Boolean {
        val lower = id.lowercase()
        if (GEMINI_NON_CHAT_MARKERS.any { it in lower }) return false
        if (GEMINI_RETIRED_PREFIXES.any { lower.startsWith(it) }) return false
        val version = GEMINI_VERSION.find(lower) ?: return true
        val major = version.groupValues[1].toInt()
        val minor = version.groupValues[2].toIntOrNull() ?: 0
        return major > GEMINI_MIN_MAJOR || (major == GEMINI_MIN_MAJOR && minor >= GEMINI_MIN_MINOR)
    }

    /** Oldest Gemini series shown in the chat catalogue: 3.6. */
    private const val GEMINI_MIN_MAJOR = 3
    private const val GEMINI_MIN_MINOR = 6

    private val GEMINI_VERSION = Regex("""^gemini-(\d+)(?:\.(\d+))?-""")

    private val GEMINI_NON_CHAT_MARKERS = listOf(
        "embedding", "imagen", "veo-", "lyria", "tts", "native-audio", "-live", "-audio", "transcribe",
        "-image", "nano-banana", "omni", "aqa", "computer-use", "robotics", "deep-research", "antigravity",
    )

    /** Older model families that carry no version number. */
    private val GEMINI_RETIRED_PREFIXES = listOf("gemini-pro-vision", "learnlm", "chat-bison", "text-bison")
    private val GEMINI_RETIRED_DESCRIPTION = listOf("deprecated", "discontinued", "will be retired", "shut down")

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
