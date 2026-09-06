package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
            ?: return ProviderValidationResult(false, "Enter a valid https:// provider base URL.")
        if (url.scheme != "https") {
            return ProviderValidationResult(false, "Provider endpoints must use HTTPS so prompts and credentials stay encrypted.")
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

    fun errorMessage(label: String, code: Int, body: String): String {
        val parsed = runCatching {
            val map = json.fromJson(body) as? Map<*, *>
            when (val error = map?.get("error")) {
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
