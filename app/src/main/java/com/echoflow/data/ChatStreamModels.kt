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

    // ── Echo Adviser (openrouter:advisor) ──────────────────────────────────────────
    /** The answering model consulted its advisor; the question is known, advice pending. */
    data class AdvisorStarted(val advisorName: String, val advisorModel: String, val prompt: String) : StreamChunk()

    /** The advisor replied. [advice] may be blank if the result body could not be parsed. */
    data class AdvisorResolved(val advice: AdvisorAdvice) : StreamChunk()

    // ── Echo Fusion (openrouter:fusion) ────────────────────────────────────────────
    /** A fusion panel started deliberating; the roster is known, analysis pending. */
    data class FusionStarted(val panelName: String, val models: List<String>) : StreamChunk()

    /** The fusion judge returned its structured analysis (and per-model responses). */
    data class FusionResolved(val analysis: FusionAnalysis) : StreamChunk()

    // ── Echo Agent (openrouter:subagent) ───────────────────────────────────────────
    /** Echo Agents run kicked off — a branded "deploying agents" banner shows while the
     *  (non-streaming) request is in flight, before any delegation/reasoning/answer arrives. */
    object AgentRunStarted : StreamChunk()

    /** The orchestrator delegated a self-contained task to its worker; the brief is known,
     *  the worker's outcome is pending. Fires once per delegation (there may be several). */
    data class SubagentStarted(val taskName: String, val taskDescription: String, val workerModel: String) : StreamChunk()

    /** The worker finished a delegated task. [result] carries the outcome (or an error). */
    data class SubagentResolved(val result: SubagentResult) : StreamChunk()

    // ── Artifacts (sentinel-delimited, extracted from the content stream) ────────────
    /** The model opened an artifact block; [title]/[artifactType] are known, body pending. */
    data class ArtifactStarted(val title: String, val artifactType: String) : StreamChunk()

    /** The artifact body is growing; [charCount] is how much has streamed so far (for the card). */
    data class ArtifactProgress(val charCount: Int) : StreamChunk()

    /**
     * The artifact block closed (or the stream ended mid-artifact). [content] is the full body.
     * [artifactId]/[version] are filled in by the ViewModel once the version is persisted, so the
     * resulting card and timeline segment can deep-link into the workspace.
     */
    data class ArtifactCompleted(
        val title: String,
        val artifactType: String,
        val content: String,
        val artifactId: String? = null,
        val version: Int = 0,
        val truncated: Boolean = false,
    ) : StreamChunk()

    // ── Image generation (OpenRouter multimodal output) ─────────────────────────────
    /**
     * An image-mode turn is in flight — the placeholder card shows immediately. [pattern] is
     * the dot animation chosen for this generation ("ripple" or "rain"); [previousImagePath]
     * is set on edit turns so the placeholder can show the version being reworked.
     */
    data class ImageGenStarted(
        val pattern: String,
        val editing: Boolean,
        val previousImagePath: String? = null,
    ) : StreamChunk()

    /**
     * The model returned an image. The transport emits it with only [dataUrl] (base64);
     * the ViewModel persists the file and re-emits with [filePath]/[imageId] filled in,
     * which is what resolves the pending placeholder segment.
     */
    data class ImageGenerated(
        val dataUrl: String,
        val filePath: String? = null,
        val imageId: String? = null,
    ) : StreamChunk()

    // ── Video generation (OpenRouter async job API) ──────────────────────────────────
    /**
     * A video-mode turn is in flight — the placeholder card shows immediately. [videoId] is
     * the durable job row, known before the provider is even called, so the card survives a
     * process death and can pick the run back up. [aspectRatio] is what the placeholder
     * expands to once the clip lands.
     */
    data class VideoGenStarted(
        val videoId: String,
        val pattern: String,
        val aspectRatio: String,
    ) : StreamChunk()

    /**
     * The job changed state. Mostly non-terminal progress ("in_progress", "downloading"), but
     * a failure is also delivered here — with the provider's [error] — so the card can settle
     * into its failed form instead of dancing on forever behind an error banner.
     */
    data class VideoGenProgress(
        val videoId: String,
        val status: String,
        val error: String? = null,
    ) : StreamChunk()

    /** The clip finished and the MP4 is on disk at [filePath]. */
    data class VideoGenerated(
        val videoId: String,
        val filePath: String,
    ) : StreamChunk()
}

// ── Echo Adviser / Echo Fusion result records ──────────────────────────────────────

/** One advisor consultation: what the model asked and what the advisor (a stronger model) said. */
data class AdvisorAdvice(
    val advisorName: String, // the profile name, e.g. "Coding"
    val advisorModel: String, // the OpenRouter model id that served as advisor
    val prompt: String, // the question the answering model asked
    val advice: String, // the advice text (may be blank if not captured from the stream)
)

/** One Echo Agent delegation: the task the orchestrator handed off and what the worker returned. */
data class SubagentResult(
    val taskName: String, // the orchestrator's short label for the task
    val taskDescription: String = "", // the full brief the worker was given (worker sees only this)
    val workerModel: String, // the OpenRouter model id that did the work
    val outcome: String, // the worker's result text (blank on error)
    val error: String? = null, // present when the delegation failed
)

/** One panel model's full answer in a fusion deliberation. */
data class FusionResponse(val model: String, val content: String)

/** A point where the panel models disagreed, with each model's stance. */
data class FusionContradiction(val topic: String, val stances: List<String>)

/** An observation only one model made. */
data class FusionInsight(val model: String, val insight: String)

/** The fusion judge's structured comparison of the panel plus the raw per-model answers. */
data class FusionAnalysis(
    val panelName: String,
    val judgeModel: String?,
    val models: List<String>, // the panel roster (OpenRouter ids)
    val consensus: List<String> = emptyList(),
    val contradictions: List<FusionContradiction> = emptyList(),
    val partialCoverage: List<String> = emptyList(),
    val uniqueInsights: List<FusionInsight> = emptyList(),
    val blindSpots: List<String> = emptyList(),
    val responses: List<FusionResponse> = emptyList(),
    val failedModels: List<String> = emptyList(),
) {
    /** True when the judge produced no structured analysis (only raw panel responses, or nothing). */
    val isEmpty: Boolean
        get() = consensus.isEmpty() && contradictions.isEmpty() && partialCoverage.isEmpty() &&
            uniqueInsights.isEmpty() && blindSpots.isEmpty()
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

/** A reference to a persisted [com.echoflow.data.Artifact] version, embedded in a reply's timeline. */
data class ArtifactRef(
    val artifactId: String,
    val title: String,
    val type: String, // Artifact.TYPE_* — selects the render view
    val version: Int,
)

/** A reference to a persisted [com.echoflow.data.GeneratedImage], embedded in a reply's timeline. */
data class ImageRef(
    val imageId: String,
    val filePath: String,
)

/**
 * A reference to a persisted [com.echoflow.data.GeneratedVideo], embedded in a reply's
 * timeline. Unlike [ImageRef] this stores the row id rather than only a path: a video row can
 * still be mid-render when the message is written, so the renderer resolves the current state
 * (and file) from the database rather than trusting a snapshot.
 */
data class VideoRef(
    val videoId: String,
    val filePath: String? = null,
)

/**
 * One block of a finished reply, persisted in arrival order so the rendered timeline
 * (reason → search → reason → search → answer) survives exactly as it streamed instead
 * of being merged into "all reasoning, then all searches, then text".
 */
data class PersistedSegment(
    val type: String, // "text" | "reasoning" | "search" | "advisor" | "fusion" | "subagent" | "artifact" | "image" | "video" | "stopped"
    val text: String? = null,
    val query: String? = null,
    val sources: List<SearchSource>? = null,
    val advisor: AdvisorAdvice? = null, // present when type == "advisor"
    val fusion: FusionAnalysis? = null, // present when type == "fusion"
    val subagent: SubagentResult? = null, // present when type == "subagent"
    val artifact: ArtifactRef? = null, // present when type == "artifact"
    val image: ImageRef? = null, // present when type == "image"
    val video: VideoRef? = null // present when type == "video"
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

    private val segmentsAdapter: JsonAdapter<List<PersistedSegment>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, PersistedSegment::class.java)
    )

    fun toolEventsToJson(events: List<ToolEvent>): String? =
        if (events.isEmpty()) null else toolEventsAdapter.toJson(events)

    fun toolEventsFromJson(json: String?): List<ToolEvent> =
        json?.let { runCatching { toolEventsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun citationsToJson(citations: List<Citation>): String? =
        if (citations.isEmpty()) null else citationsAdapter.toJson(citations)

    fun citationsFromJson(json: String?): List<Citation> =
        json?.let { runCatching { citationsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun segmentsToJson(segments: List<PersistedSegment>): String? =
        if (segments.isEmpty()) null else segmentsAdapter.toJson(segments)

    fun segmentsFromJson(json: String?): List<PersistedSegment> =
        json?.let { runCatching { segmentsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()
}
