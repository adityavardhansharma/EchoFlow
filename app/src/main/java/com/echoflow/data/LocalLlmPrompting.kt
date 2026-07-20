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

/**
 * Detects only sustained, exact suffix loops. It intentionally requires four repeats of a
 * reasonably-sized fragment so normal emphasis, short acknowledgements, and lists are left alone.
 */
internal class SevereRepetitionDetector(
    private val minimumUnitChars: Int = 12,
    private val repeatsRequired: Int = 4,
    private val retainedChars: Int = 4_096,
) {
    private val output = StringBuilder()

    fun append(chunk: String): Boolean {
        if (chunk.isEmpty()) return false
        output.append(chunk)
        if (output.length > retainedChars) {
            output.delete(0, output.length - retainedChars)
        }

        val text = output.toString()
        val largestUnit = minOf(256, text.length / repeatsRequired)
        if (largestUnit < minimumUnitChars) return false

        for (unitLength in minimumUnitChars..largestUnit) {
            val repeatedLength = unitLength * repeatsRequired
            val start = text.length - repeatedLength
            val unit = text.substring(text.length - unitLength)
            if (unit.count { !it.isWhitespace() } < 6) continue

            var matches = true
            for (repeatIndex in 0 until repeatsRequired - 1) {
                val candidateStart = start + repeatIndex * unitLength
                if (!text.regionMatches(candidateStart, unit, 0, unitLength)) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }
}
