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
}
