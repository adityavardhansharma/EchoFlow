package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** One source returned by a web search, normalized across providers. */
data class SearchSource(
    val title: String,
    val url: String,
    val snippet: String? = null,
    val publishedDate: String? = null
)

/** A piece of a streamed completion. The UI renders these in arrival order. */
sealed class StreamChunk {
    data class Reasoning(val text: String) : StreamChunk()
    data class Content(val text: String) : StreamChunk()

    /** The model decided to run a web search; the query is known but results are pending. */
    data class SearchStarted(val query: String) : StreamChunk()

    /** Results for a previously started search (may be empty if the search failed). */
    data class SearchSources(val query: String, val sources: List<SearchSource>) : StreamChunk()

    /** Transient status line, not persisted (e.g. "Search limit reached"). */
    data class StatusNote(val text: String) : StreamChunk()
}

/**
 * Numbered result block fed back to a model after a search. The numbering matches the
 * `[n](url)` citation format that SystemPrompts instructs models to use.
 */
fun formatSearchResultsForModel(sources: List<SearchSource>): String {
    if (sources.isEmpty()) return "No results found for this search."
    return sources.mapIndexed { i, s ->
        buildString {
            append("[${i + 1}] ${s.title} — ${s.url}")
            s.publishedDate?.let { append(" (published $it)") }
            s.snippet?.takeIf { it.isNotBlank() }?.let { append("\n").append(it.trim()) }
        }
    }.joinToString("\n\n")
}

/** A persisted record of one search the model ran while producing an answer. */
data class ToolEvent(
    val type: String = "search",
    val query: String,
    val sources: List<SearchSource>,
    val orderIndex: Int
)

/** A deduplicated source citation attached to a finished answer. */
data class Citation(
    val title: String,
    val url: String
)

/** Shared Moshi adapters for the ChatMessage.toolEventsJson / citationsJson columns. */
object ToolEventJson {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val toolEventsAdapter: JsonAdapter<List<ToolEvent>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ToolEvent::class.java)
    )

    private val citationsAdapter: JsonAdapter<List<Citation>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, Citation::class.java)
    )

    fun toolEventsToJson(events: List<ToolEvent>): String? =
        if (events.isEmpty()) null else toolEventsAdapter.toJson(events)

    fun toolEventsFromJson(json: String?): List<ToolEvent> =
        json?.let { runCatching { toolEventsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun citationsToJson(citations: List<Citation>): String? =
        if (citations.isEmpty()) null else citationsAdapter.toJson(citations)

    fun citationsFromJson(json: String?): List<Citation> =
        json?.let { runCatching { citationsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()
}
