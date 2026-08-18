package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal object OpenRouterPayloads {
    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun isPdf(mime: String?): Boolean = mime.equals("application/pdf", ignoreCase = true)

    fun historyHasPdf(history: List<ChatMessage>): Boolean =
        history.any { message ->
            message.role == "user" && (
                (message.localAttachmentUri != null && isPdf(message.localAttachmentMimeType)) ||
                    message.extraAttachments.any { isPdf(it.mimeType) }
                )
        }

    fun attachmentPart(mime: String, name: String?, encoded: String): Map<String, Any> =
        if (isPdf(mime)) {
            mapOf(
                "type" to "file",
                "file" to mapOf(
                    "filename" to (name?.takeIf(String::isNotBlank) ?: "document.pdf"),
                    "file_data" to "[PDF attachment removed — 0 KB]$encoded",
                ),
            )
        } else {
            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:$mime;base64,$encoded"))
        }

    fun enablePdfPlugin(request: MutableMap<String, Any>, enabled: Boolean) {
        if (!enabled) return
        val pdfPlugin = mapOf("id" to "file-parser", "pdf" to mapOf("engine" to "cloudflare-ai"))
        @Suppress("UNCHECKED_CAST")
        val existing = (request["plugins"] as? List<*>)?.filterIsInstance<Map<String, Any>>().orEmpty()
        // Merge so callers that already set plugins (e.g. fusion) keep them.
        request["plugins"] = existing + pdfPlugin
    }

    fun messages(
        history: List<ChatMessage>,
        systemPrompt: String? = null,
        readBase64: (String?) -> String?,
    ): MutableList<Map<String, Any>> = buildList {
        if (!systemPrompt.isNullOrBlank()) add(mapOf("role" to "system", "content" to systemPrompt))
        history.forEach { message ->
            val encoded = readBase64(message.localAttachmentUri)
            val extras = if (message.role == "user") {
                message.extraAttachments.mapNotNull { extra ->
                    val bytes = readBase64(extra.uri) ?: return@mapNotNull null
                    attachmentPart(extra.mimeType, extra.name, bytes)
                }
            } else {
                emptyList()
            }
            if (encoded != null && message.role == "user") {
                val mime = message.localAttachmentMimeType ?: "image/jpeg"
                add(
                    mapOf(
                        "role" to message.role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to message.content),
                            attachmentPart(mime, message.localAttachmentName, encoded),
                        ) + extras,
                    )
                )
            } else if (extras.isNotEmpty()) {
                add(
                    mapOf(
                        "role" to message.role,
                        "content" to listOf(mapOf("type" to "text", "text" to message.content)) + extras,
                    )
                )
            } else {
                add(mapOf("role" to message.role, "content" to message.content))
            }
        }
    }.toMutableList()

    /**
     * Image edit turns send the chat's latest generated image with the newest user message so
     * the model revises it in place. Rewrites the final user message into multi-part content
     * (its existing parts are preserved — a user photo attachment rides along untouched).
     */
    fun attachImageToLastUserMessage(messages: MutableList<Map<String, Any>>, dataUrl: String) {
        val index = messages.indexOfLast { it["role"] == "user" }
        if (index < 0) return
        val message = messages[index]
        val parts = when (val content = message["content"]) {
            is List<*> -> content.filterIsInstance<Map<String, Any>>().toMutableList()
            is String -> mutableListOf<Map<String, Any>>(mapOf("type" to "text", "text" to content))
            else -> mutableListOf()
        }
        parts.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl)))
        messages[index] = mapOf("role" to "user", "content" to parts)
    }

    fun errorMessage(body: String): String? = runCatching {
        val root = json.fromJson(body) as? Map<*, *>
        (root?.get("error") as? Map<*, *>)?.get("message") as? String
    }.getOrNull()
}
