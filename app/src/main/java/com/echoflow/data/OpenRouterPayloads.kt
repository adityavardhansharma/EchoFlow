package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal object OpenRouterPayloads {
    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun isPdf(mime: String?): Boolean = mime.equals("application/pdf", ignoreCase = true)

    fun historyHasPdf(history: List<ChatMessage>): Boolean =
        history.any { it.role == "user" && it.localAttachmentUri != null && isPdf(it.localAttachmentMimeType) }

    fun enablePdfPlugin(request: MutableMap<String, Any>, enabled: Boolean) {
        if (enabled) request["plugins"] = listOf(
            mapOf("id" to "file-parser", "pdf" to mapOf("engine" to "cloudflare-ai"))
        )
    }

    fun messages(
        history: List<ChatMessage>,
        systemPrompt: String? = null,
        readBase64: (String?) -> String?,
    ): MutableList<Map<String, Any>> = buildList {
        if (!systemPrompt.isNullOrBlank()) add(mapOf("role" to "system", "content" to systemPrompt))
        history.forEach { message ->
            val encoded = readBase64(message.localAttachmentUri)
            if (encoded != null && message.role == "user") {
                val mime = message.localAttachmentMimeType ?: "image/jpeg"
                val attachment = if (isPdf(mime)) {
                    mapOf("type" to "file", "file" to mapOf(
                        "filename" to (message.localAttachmentName?.takeIf(String::isNotBlank) ?: "document.pdf"),
                        "file_data" to "data:application/pdf;base64,$encoded",
                    ))
                } else {
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$encoded"))
                }
                add(mapOf("role" to message.role, "content" to listOf(
                    mapOf("type" to "text", "text" to message.content), attachment,
                )))
            } else {
                add(mapOf("role" to message.role, "content" to message.content))
            }
        }
    }.toMutableList()

    fun errorMessage(body: String): String? = runCatching {
        val root = json.fromJson(body) as? Map<*, *>
        (root?.get("error") as? Map<*, *>)?.get("message") as? String
    }.getOrNull()
}
