package com.echoflow.data.voice

/**
 * Picks the key details out of what the user is saying, while they say it: amounts ("3 days"),
 * dates ("March", "Friday") and names ("Lisbon"). The voice screen shows them as small chips on
 * the line, so the user sees they were understood before the agent answers.
 *
 * Runs on the device over the live transcript, with no model: it only needs to be right often
 * enough to feel attentive, and it never sends anything anywhere.
 */
internal object HeardChips {
    const val MAX = 3

    fun extract(text: String, max: Int = MAX): List<String> {
        val words = WORD.findAll(text).map { Word(it.value, it.range.first) }.toList()
        if (words.isEmpty()) return emptyList()
        val chips = ArrayList<String>()
        val seen = HashSet<String>()
        fun add(chip: String) {
            val key = chip.lowercase()
            if (chips.size < max && seen.add(key)) chips.add(chip)
        }
        var i = 0
        while (i < words.size && chips.size < max) {
            val word = words[i]
            val lower = word.text.lowercase()
            val next = words.getOrNull(i + 1)
            val number = numberOf(lower)
            when {
                // "three days", "2 tickets", "5 pm", "$40", "10:30"
                word.text.startsWith("$") || TIME.matches(word.text) -> add(word.text)
                number != null && next != null && sameClause(text, word, next) &&
                    next.text.lowercase() !in STOP && numberOf(next.text.lowercase()) == null -> {
                    val unit = next.text.lowercase().let { if (it in AMPM) it.replace(".", "") else it }
                    add("$number $unit")
                    i++
                }
                lower in MONTHS || lower in DAYS -> add(capitalise(lower))
                lower in WHEN -> add(lower)
                isName(text, word) -> {
                    // A run of capitalised words is one name: "New York", "Belém Tower".
                    var end = i
                    while (end + 1 < words.size && isCapital(words[end + 1].text) &&
                        sameClause(text, words[end], words[end + 1]) &&
                        words[end + 1].text.lowercase() !in MONTHS && words[end + 1].text.lowercase() !in DAYS
                    ) end++
                    add(words.subList(i, end + 1).joinToString(" ") { it.text })
                    i = end
                }
            }
            i++
        }
        return chips
    }

    private data class Word(val text: String, val start: Int)

    private fun numberOf(word: String): String? = word.toIntOrNull()?.takeIf { it in 1..9999 }?.toString()
        ?: NUMBER_WORDS[word]?.toString()

    /** A capitalised word that isn't the start of a sentence and isn't "I", "I'm", "OK"… */
    private fun isName(text: String, word: Word): Boolean {
        if (!isCapital(word.text) || word.text.lowercase() in NOT_NAMES) return false
        if (word.text.length < 2) return false
        val before = text.substring(0, word.start).trimEnd()
        return before.isNotEmpty() && before.last() !in ".!?"
    }

    private fun isCapital(word: String) = word.first().isUpperCase() && word.drop(1).any { it.isLowerCase() }

    /** No comma or full stop between [a] and [b]: "three, days" isn't "three days". */
    private fun sameClause(text: String, a: Word, b: Word): Boolean =
        text.substring(a.start + a.text.length, b.start).none { it in ",.;!?" }

    private fun capitalise(word: String) = word.replaceFirstChar { it.uppercase() }

    private val WORD = Regex("""\$?[\p{L}\p{N}][\p{L}\p{N}'’:.]*[\p{L}\p{N}]|\$?[\p{L}\p{N}]""")
    private val TIME = Regex("""\d{1,2}:\d{2}""")
    private val AMPM = setOf("am", "pm", "a.m", "p.m")

    private val NUMBER_WORDS = mapOf(
        "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "hundred" to 100,
    )
    private val MONTHS = setOf(
        "january", "february", "march", "april", "june", "july", "august", "september",
        "october", "november", "december",
    )
    private val DAYS = setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val WHEN = setOf("tomorrow", "tonight", "weekend")
    private val STOP = setOf(
        "of", "the", "a", "an", "and", "or", "to", "in", "on", "at", "for", "with", "is", "are", "was",
        "it", "that", "this", "more", "times", "time", "things", "thing", "so", "but", "i", "you", "we",
        "they", "he", "she", "me", "my", "your", "our", "their", "if", "then", "than", "too",
    )
    private val NOT_NAMES = setOf(
        "i", "i'm", "i've", "i'd", "i'll", "ok", "okay", "hey", "hi", "hello", "yes", "no", "please",
        "thanks", "echo",
    )
}
