package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * A passage the user selected in an earlier reply and is asking about. [messageId] is the reply
 * it came from, so the sent turn can jump back to it.
 */
data class QuotedPassage(
    val text: String,
    val messageId: String,
)

/**
 * Quote & reply. Quotes ride on the user row in [ChatMessage.quotesJson] and never touch
 * [ChatMessage.content], so editing, copying, search and titles all see only what was typed. The
 * model sees each quote as a Markdown blockquote ahead of the message — plain text every provider
 * and on-device engine reads the same way.
 */
object QuotedPassages {
    /** How many passages one message can quote. */
    const val MAX = 3

    /** Longest quote kept; a whole-reply selection is trimmed rather than sent in full. */
    private const val MAX_CHARS = 1_500

    private val adapter: JsonAdapter<List<QuotedPassage>> = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Types.newParameterizedType(List::class.java, QuotedPassage::class.java))

    fun toJson(quotes: List<QuotedPassage>): String? = quotes.takeIf { it.isNotEmpty() }?.let(adapter::toJson)

    fun fromJson(json: String?): List<QuotedPassage> =
        json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }.orEmpty()

    /** Selected text, tidied: trimmed, runs of blank lines collapsed, and capped. Null when nothing is left. */
    fun clean(raw: String): String? {
        val text = raw.replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (text.isEmpty()) return null
        return if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS).trimEnd() + "…"
    }

    /** The message as the model reads it: each quote as a blockquote, then what the user typed. */
    fun forModel(quotes: List<QuotedPassage>, content: String): String {
        if (quotes.isEmpty()) return content
        val blocks = quotes.joinToString("\n\n") { quote ->
            quote.text.lines().joinToString("\n") { line -> if (line.isBlank()) ">" else "> $line" }
        }
        return if (content.isBlank()) blocks else "$blocks\n\n$content"
    }
}

/** This row with its quotes folded into [ChatMessage.content], for a request payload only. */
fun ChatMessage.withQuotesForModel(): ChatMessage {
    if (quotesJson == null) return this
    val quotes = QuotedPassages.fromJson(quotesJson)
    return if (quotes.isEmpty()) this else copy(content = QuotedPassages.forModel(quotes, content))
}
