package com.echoflow.ui

/** Pure policies for converting provider output into stable chat UI/persistence state. */
internal object ChatResponsePolicy {
    fun normalizeForPersistence(segments: List<StreamSegment>): List<StreamSegment> {
        if (segments.any { it is StreamSegment.Text && it.text.isNotBlank() }) return segments
        val index = segments.indexOfLast { it is StreamSegment.Reasoning && it.text.isNotBlank() }
        if (index < 0) return segments

        val normalized = segments.toMutableList()
        val reasoning = normalized[index] as StreamSegment.Reasoning
        val (remainingReasoning, answer) = splitReasoningOnlyAnswer(reasoning.text)
        val replacement = buildList {
            if (remainingReasoning.isNotBlank()) add(StreamSegment.Reasoning(remainingReasoning))
            add(StreamSegment.Text(answer))
        }
        normalized.removeAt(index)
        normalized.addAll(index, replacement)
        return normalized
    }

    fun splitReasoningOnlyAnswer(text: String): Pair<String, String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "" to ""
        val thinkEnd = trimmed.lastIndexOf("</think>", ignoreCase = true)
        if (thinkEnd >= 0) {
            val answer = trimmed.substring(thinkEnd + "</think>".length).trim()
            if (answer.isNotEmpty()) return trimmed.substring(0, thinkEnd + "</think>".length).trim() to answer
        }
        val marker = Regex("(?im)^\\s*(final\\s+answer|answer|response)\\s*:\\s*")
            .findAll(trimmed).lastOrNull()
        if (marker != null) {
            val answer = trimmed.substring(marker.range.last + 1).trim()
            if (answer.isNotEmpty()) return trimmed.substring(0, marker.range.first).trim() to answer
        }
        return "" to trimmed
    }

    fun extractSearchQuery(text: String): String? {
        Regex("<search>(.*?)</search>", RegexOption.DOT_MATCHES_ALL).find(text)?.let {
            return firstNonBlankLine(it.groupValues[1])
        }
        val open = text.indexOf("<search>")
        if (open >= 0) return firstNonBlankLine(text.substring(open + "<search>".length).substringBefore("</search"))
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        return Regex("^\\s*search:\\s*(.+)$", RegexOption.IGNORE_CASE).find(first)
            ?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun firstNonBlankLine(value: String): String? =
        value.trim().lineSequence().firstOrNull()?.trim()?.takeIf(String::isNotBlank)
}
