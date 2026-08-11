package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Deep Research is an opt-in, power-user mode separate from normal chat. Two execution
 * kinds are supported:
 *
 *  - **provider** — a search provider (Exa / Parallel / Firecrawl) runs the whole research
 *    on its own infrastructure and returns a finished report. EchoFlow just creates the
 *    job and polls it.
 *  - **agent** — a cloud chat model orchestrates: it plans sub-questions, EchoFlow runs
 *    each search via [WebSearchService], then the model synthesizes a report. The user
 *    adds these models separately from normal chat models.
 *
 * Provider-native engines appear in the Deep Research model picker as if they were models,
 * but only when the matching provider API key is configured.
 */
data class DrEngine(
    val id: String,
    val name: String,
    /** "exa" | "parallel" | "firecrawl" for provider engines; the model id for agent. */
    val provider: String,
    /** Provider-specific model/processor token sent to the API (provider kind only). */
    val providerModel: String,
    val description: String,
)

object DeepResearchCatalog {

    /** Built-in provider-native engines, grouped by the key they require. */
    val providerEngines: List<DrEngine> = listOf(
        DrEngine(
            id = "exa-deep-lite",
            name = "Exa · Deep Lite",
            provider = "exa",
            providerModel = "deep-lite",
            description = "Fast deep search with light synthesis",
        ),
        DrEngine(
            id = "exa-deep",
            name = "Exa · Deep",
            provider = "exa",
            providerModel = "deep",
            description = "In-depth research with synthesis",
        ),
        DrEngine(
            id = "exa-deep-reasoning",
            name = "Exa · Deep Reasoning",
            provider = "exa",
            providerModel = "deep-reasoning",
            description = "Deep search with enhanced reasoning — recommended",
        ),
        DrEngine(
            id = "exa-agent",
            name = "Exa Agent",
            provider = "exa",
            // Routed to the Exa Agent API (/agent/runs); depth is set by the effort pill.
            providerModel = "agent",
            description = "Autonomous agent — searches, reasons, sets its own depth",
        ),
        DrEngine(
            id = "parallel-pro",
            name = "Parallel · Pro",
            provider = "parallel",
            providerModel = "pro",
            description = "Objective-driven deep web research",
        ),
        DrEngine(
            id = "parallel-ultra",
            name = "Parallel · Ultra",
            provider = "parallel",
            providerModel = "ultra",
            description = "State-of-the-art accuracy, slowest and priciest",
        ),
        DrEngine(
            id = "firecrawl-research",
            name = "Firecrawl Research",
            provider = "firecrawl",
            providerModel = "firecrawl",
            description = "Autonomous crawl + synthesis over full pages",
        ),
    )

    fun providerEngineById(id: String): DrEngine? = providerEngines.firstOrNull { it.id == id }

    /** True when [id] is one of the built-in provider-native engines. */
    fun isProviderEngine(id: String): Boolean = providerEngineById(id) != null

    /** True when [id] is the Exa Agent engine (the only one that uses the effort pill). */
    fun usesEffort(id: String): Boolean = id == "exa-agent"

    /** Which providers have at least one provider-native engine. */
    val knownProviders = setOf("exa", "parallel", "firecrawl")
}

/** Exa Agent effort levels (cost/depth dial). "auto" lets Exa pick. */
object ExaEffort {
    val levels = listOf("auto", "low", "medium", "high", "xhigh")
    const val DEFAULT = "auto"
    fun label(level: String): String = when (level) {
        "auto" -> "Auto"
        "low" -> "Low"
        "medium" -> "Medium"
        "high" -> "High"
        "xhigh" -> "X-High"
        else -> level.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Data Agent is a separate, opt-in mode (off by default) for *extracting* data from the web
 * — distinct from Deep Research, which answers questions. It is Firecrawl-only, using the
 * Firecrawl Agent (`/v2/agent`), which returns structured data shaped by the request.
 */
object DataAgentCatalog {
    val engines: List<DrEngine> = listOf(
        DrEngine(
            id = "firecrawl-agent-mini",
            name = "Firecrawl · Faster",
            provider = "firecrawl",
            providerModel = "spark-1-mini",
            description = "Cheaper — good for straightforward extraction",
        ),
        DrEngine(
            id = "firecrawl-agent-pro",
            name = "Firecrawl · Accurate",
            provider = "firecrawl",
            providerModel = "spark-1-pro",
            description = "Higher accuracy for complex, multi-site tasks",
        ),
    )

    fun byId(id: String): DrEngine? = engines.firstOrNull { it.id == id }
}

/** Resolved, ready-to-run configuration for a single Deep Research / Data Agent request. */
data class DeepResearchConfig(
    val engineId: String,
    val engineKind: String, // "provider" | "agent" | "data-agent"
    val engineLabel: String,
    /** provider kind: search provider that runs it. agent kind: the chat model id. */
    val provider: String,
    /** provider kind: API model/processor token (e.g. Exa type, Parallel processor, Firecrawl model). */
    val providerModel: String,
    /** agent kind: search provider backing the tool (resolved from "auto"). */
    val searchProvider: String?,
    /** Exa Agent effort ("auto".."xhigh"); null for engines that don't use it. */
    val level: String? = null,
    val maxSearches: Int,
    val maxSources: Int,
    val maxCredits: Int = 2500, // Data Agent spend cap (Firecrawl credits)
)

/** Moshi helpers for the JSON columns on [ResearchRun]. */
/**
 * One stage of a research run, as rendered by the live timeline.
 *
 * Steps are upserted by [id]: an engine announces a step as [STATE_ACTIVE], then re-emits the
 * same id with [STATE_DONE] and a filled-in [detail] once it finishes, so the row mutates in
 * place instead of the timeline growing a duplicate. The whole list is mirrored into
 * `research_runs.stepsJson` after every event, which is what lets a backgrounded run replay its
 * full history rather than just its current phase.
 */
data class ResearchStep(
    val id: String,
    val kind: String = KIND_SEARCH,
    val label: String,
    val state: String = STATE_ACTIVE,
    /** Short right-aligned meta, e.g. "12 sources" or "68%". */
    val detail: String? = null,
    /** URLs this step contributed, resolved against the run's accumulated sources for display. */
    val sourceUrls: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val endedAt: Long? = null,
) {
    val isTerminal: Boolean get() = state == STATE_DONE || state == STATE_FAILED

    companion object {
        const val KIND_PLAN = "plan"
        const val KIND_SEARCH = "search"
        const val KIND_READ = "read"
        const val KIND_ANALYZE = "analyze"
        const val KIND_SYNTHESIZE = "synthesize"

        const val STATE_PENDING = "pending"
        const val STATE_ACTIVE = "active"
        const val STATE_DONE = "done"
        const val STATE_FAILED = "failed"
    }
}

object ResearchJson {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val stepsAdapter: JsonAdapter<List<String>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, String::class.java)
    )
    private val sourcesAdapter: JsonAdapter<List<SearchSource>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, SearchSource::class.java)
    )
    private val timelineAdapter: JsonAdapter<List<ResearchStep>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, ResearchStep::class.java)
    )

    fun stepsToJson(steps: List<String>): String? =
        if (steps.isEmpty()) null else stepsAdapter.toJson(steps)

    fun stepsFromJson(json: String?): List<String> =
        json?.let { runCatching { stepsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun sourcesToJson(sources: List<SearchSource>): String? =
        if (sources.isEmpty()) null else sourcesAdapter.toJson(sources)

    fun sourcesFromJson(json: String?): List<SearchSource> =
        json?.let { runCatching { sourcesAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun timelineToJson(steps: List<ResearchStep>): String? =
        if (steps.isEmpty()) null else timelineAdapter.toJson(steps)

    fun timelineFromJson(json: String?): List<ResearchStep> =
        json?.let { runCatching { timelineAdapter.fromJson(it) }.getOrNull() } ?: emptyList()
}
