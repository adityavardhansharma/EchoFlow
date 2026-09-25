package com.echoflow.data

/**
 * Custom vocabulary for dictation models that accept keyword biasing (today: Gemini 3.5
 * Transcribe). Users type terms separated by commas or new lines; this normalizes them into the
 * clean string list the provider expects as `custom_vocabulary`.
 *
 * Google accepts up to 1,000 terms but recommends around 100 for best accuracy, so the list is
 * capped at [MAX_TERMS]. Duplicates are dropped case-insensitively, keeping the user's first
 * spelling, because the spelling is the whole point: "Jyoti", not "jyoti".
 */
object DictationVocabulary {
    const val MAX_TERMS = 100
    const val MAX_TERM_LENGTH = 60

    private val separators = Regex("[,\\n;]")

    /** Splits raw user input on commas, semicolons, or new lines into trimmed terms. */
    fun split(raw: String): List<String> =
        raw.split(separators).map(::clean).filter { it.isNotEmpty() }

    /** Merges [incoming] into [existing], deduplicated and capped. Existing order is kept. */
    fun merge(existing: List<String>, incoming: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>(existing.size + incoming.size)
        for (term in existing + incoming) {
            val cleaned = clean(term)
            if (cleaned.isEmpty() || !seen.add(cleaned.lowercase())) continue
            out.add(cleaned)
            if (out.size == MAX_TERMS) break
        }
        return out
    }

    fun normalize(terms: List<String>): List<String> = merge(emptyList(), terms)

    /** Persisted one term per line: terms never contain new lines, and commas stay readable. */
    fun encode(terms: List<String>): String = normalize(terms).joinToString("\n")

    fun decode(stored: String?): List<String> =
        if (stored.isNullOrBlank()) emptyList() else normalize(stored.split('\n'))

    private fun clean(term: String): String =
        term.trim().replace(Regex("\\s+"), " ").take(MAX_TERM_LENGTH).trim()
}
