package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * A passage the user selected in an earlier reply and is asking about. [messageId] is the reply
 * it came from and [chatId] its conversation, so the sent turn can jump back to it; [version] is
 * the answer version that was on screen (null for the latest), so the jump shows those words.
 */
data class QuotedPassage(
    val text: String,
    val messageId: String,
    val chatId: String? = null,
    val version: Int? = null,
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
        if (text.length <= MAX_CHARS) return text
        // Never cut between the halves of a surrogate pair (most emoji), which would store a
        // broken character.
        val end = if (Character.isHighSurrogate(text[MAX_CHARS - 1])) MAX_CHARS - 1 else MAX_CHARS
        return text.take(end).trimEnd() + "…"
    }

    /**
     * A web-search query for a quoted turn: a short follow-up such as "Is this still current?"
     * means nothing without its subject, so the quotes lead, trimmed to keep the query short.
     */
    fun searchQuery(quotes: List<QuotedPassage>, typed: String): String {
        if (quotes.isEmpty()) return typed
        val subject = quotes.joinToString(" ") { it.text.replace(Regex("\\s+"), " ") }.take(SEARCH_SUBJECT_CHARS)
        return if (typed.isBlank()) subject else "$subject — $typed"
    }

    private const val SEARCH_SUBJECT_CHARS = 240

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
