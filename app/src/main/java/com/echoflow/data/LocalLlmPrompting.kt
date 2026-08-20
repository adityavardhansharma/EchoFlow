package com.echoflow.data

internal enum class LocalLlmRuntime { MEDIAPIPE, LITERT, GGUF }

/** Pure runtime selection, token-budget and prompt formatting rules. */
internal object LocalLlmPrompting {
    fun runtimeFor(model: LocalModel): LocalLlmRuntime = when {
        LocalModelCatalog.isGguf(model.fileName) -> LocalLlmRuntime.GGUF
        LocalModelCatalog.isLiteRtLm(model.fileName) -> LocalLlmRuntime.LITERT
        else -> LocalLlmRuntime.MEDIAPIPE
    }

    fun effectiveMaxTokens(model: LocalModel, params: InferenceParams): Int =
        (params.maxTokens.takeIf { it > 0 }
            ?: model.maxTokens
            ?: LocalModelCatalog.maxTokensFor(model.id, model.fileName))
            .coerceAtMost(InferenceLimits.LOCAL_MAX_TOKENS_CEIL)

    fun transcriptOf(turns: List<ChatMessage>): String = turns.joinToString("\n") { message ->
        val speaker = if (message.role == "user") "Human" else "EchoFlow"
        "$speaker: ${message.content}"
    }

    fun userTurnPrompt(content: String): String =
        "Human message:\n$content\n\nEchoFlow reply:"

    /**
     * Typed text plus parsed attachment Markdown. Local runtimes cannot take raw files.
     * Combined attachment body is capped at [MAX_ATTACHMENT_CONTEXT_CHARS] (same ballpark as
     * project-doc injection) so a large PDF cannot crowd out the user's question.
     */
    fun contentWithAttachments(message: ChatMessage): String {
        val docs = message.attachments.filter { !it.extractedText.isNullOrBlank() }
        if (docs.isEmpty()) return message.content
        var budget = MAX_ATTACHMENT_CONTEXT_CHARS
        val blocks = buildList {
            for (attachment in docs) {
                if (budget <= 0) {
                    add("[Some attached files were omitted to fit the context window.]")
                    break
                }
                val body = attachment.extractedText.orEmpty()
                val slice = if (body.length > budget) body.take(budget) else body
                budget -= slice.length
                val truncatedMark = if (slice.length < body.length) "\n[…document truncated…]" else ""
                add(
                    "--- Attached file: ${attachment.name} ---\n$slice$truncatedMark\n--- End of ${attachment.name} ---"
                )
            }
        }
        val joined = blocks.joinToString("\n\n")
        return if (message.content.isBlank()) joined else "${message.content}\n\n$joined"
    }

    /**
     * Request-only history for cloud models that read attachments via anydoc text injection
     * (Echo Lumen). Strips raw attachment URIs so OpenRouter never receives Tier-3 file parts.
     */
    fun historyWithInjectedDocs(history: List<ChatMessage>): List<ChatMessage> =
        history.map { message ->
            val cleared = message.also { it.extraAttachments = emptyList() }
            if (cleared.attachments.none { !it.extractedText.isNullOrBlank() }) cleared
            else cleared.copy(
                content = contentWithAttachments(cleared),
                localAttachmentUri = null,
                localAttachmentMimeType = null,
                localAttachmentName = null,
                attachmentsJson = null,
            ).also { it.extraAttachments = emptyList() }
        }

    /** Combined Markdown budget injected from one turn's attachments into a local prompt. */
    internal const val MAX_ATTACHMENT_CONTEXT_CHARS = 24_000
}
