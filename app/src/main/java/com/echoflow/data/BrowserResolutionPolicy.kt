package com.echoflow.data

/** Pure website-resolution rules kept separate from session orchestration and persistence. */
internal object BrowserResolutionPolicy {
    private val stopwords = setOf(
        "go", "to", "the", "open", "visit", "find", "and", "check", "on", "for", "of",
        "in", "me", "please", "navigate", "search", "website", "site", "official",
    )

    fun websiteQuery(instruction: String): String {
        val cleaned = instruction.replace(
            Regex("^(go to|open|visit|check|navigate to|browse|launch|head to)\\s+", RegexOption.IGNORE_CASE),
            "",
        )
        val name = cleaned.split(Regex("\\s+(and|then|to find|find|search|,)\\s+", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim().orEmpty()
        val base = if (name.length in 2..40) name else cleaned.take(40)
        return "$base official website"
    }

    fun isConfident(top: BrowserCandidate, instruction: String): Boolean {
        if (BrowserResolver.isDispreferred(top.url)) return false
        val tokens = instruction.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in stopwords }
        return tokens.any { top.domain.contains(it) }
    }
}
