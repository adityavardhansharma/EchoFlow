package com.echoflow.data

/** Conservative text estimate; provider tokenizers and multimodal costs can still differ. */
internal object RequestContextBudget {
    data class Prepared(val systemPrompt: String, val history: List<ChatMessage>, val omitted: Boolean)
    fun estimate(text: String): Int = (text.toByteArray(Charsets.UTF_8).size + 1) / 2

    fun prepare(
        history: List<ChatMessage>, system: String, references: String = "",
        contextTokens: Int, outputTokens: Int,
    ): Prepared {
        require(history.isNotEmpty()) { "There is no message to send." }
        val inputLimit = contextTokens - outputTokens - 128
        val latest = history.last()
        fun cost(m: ChatMessage) = 16 + estimate(LocalLlmPrompting.contentWithAttachments(m)) +
            m.attachments.count { it.extractedText.isNullOrBlank() } * 1024 + m.extraAttachments.size * 1024
        val required = estimate(system) + cost(latest)
        require(required <= inputLimit) {
            "This message and its attachments exceed the model's context budget. Shorten the message, remove files, or choose a model with a larger context."
        }
        var remaining = inputLimit - required
        // Reference material cannot consume the entire remaining conversation budget.
        val referenceBudget = remaining / 2
        val fittedReferences = fitText(references, referenceBudget)
        remaining -= estimate(fittedReferences)
        val selected = mutableListOf(latest)
        // Keep complete user/assistant groups. Never start the request on an orphan assistant.
        val prior = history.dropLast(1)
        val starts = prior.indices.filter { prior[it].role == "user" }
        for (index in starts.indices.reversed()) {
            val group = prior.subList(starts[index], starts.getOrNull(index + 1) ?: prior.size)
            val size = group.sumOf(::cost)
            if (size > remaining) break
            selected.addAll(0, group)
            remaining -= size
        }
        val omitted = selected.size != history.size || fittedReferences != references
        val notice = if (omitted) "\n[Older context or reference text was omitted to fit the model.]" else ""
        // The fixed 128-token framing reserve covers this notice and transport role delimiters.
        return Prepared(system + fittedReferences + notice, selected, omitted)
    }

    /** Check the final wire payload on every tool round, including newly added results. */
    fun <T : Map<String, *>> checkedPayload(payload: T, contextTokens: Int = 8192): T {
        fun cost(value: Any?): Long = when (value) {
            is String -> if (value.startsWith("data:")) 1024L else estimate(value).toLong()
            is Map<*, *> -> when {
                value["type"] == "image_url" || value["type"] == "image" || value.containsKey("inline_data") -> 1024L
                value["type"] == "file" || value["type"] == "document" -> 4096L
                else -> 8L + value.values.sumOf { cost(it) }
            }
            is Iterable<*> -> value.sumOf { cost(it) }
            else -> 0L
        }
        val generation = payload["generationConfig"] as? Map<*, *>
        val options = payload["options"] as? Map<*, *>
        val output = ((payload["max_tokens"] ?: payload["max_output_tokens"] ?:
            generation?.get("maxOutputTokens") ?: options?.get("num_predict")) as? Number)
            ?.toInt()?.takeIf { it > 0 } ?: 2048
        require(cost(payload) + output + 128 <= contextTokens) {
            "The conversation or tool results exceed this model's context budget. Start a shorter conversation or select a model with a larger known context."
        }
        return payload
    }

    fun fitText(text: String, tokens: Int): String {
        if (estimate(text) <= tokens) return text
        if (tokens < 32) return ""
        val suffix = "\n[Reference text truncated.]"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (estimate(text.take(mid) + suffix) <= tokens) low = mid else high = mid - 1
        }
        if (low > 0 && Character.isHighSurrogate(text[low - 1])) low--
        return text.take(low) + suffix
    }
}
