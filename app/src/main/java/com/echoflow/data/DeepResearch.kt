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

    /** Which providers have at least one provider-native engine. */
    val knownProviders = setOf("exa", "parallel", "firecrawl")
}

/** Resolved, ready-to-run configuration for a single Deep Research request. */
data class DeepResearchConfig(
    val engineId: String,
    val engineKind: String, // "provider" | "agent"
    val engineLabel: String,
    /** provider kind: search provider that runs it. agent kind: the chat model id. */
    val provider: String,
    /** provider kind: API model/processor token. */
    val providerModel: String,
    /** agent kind: search provider backing the tool (resolved from "auto"). */
    val searchProvider: String?,
    val maxSearches: Int,
    val maxSources: Int,
)

/** Moshi helpers for the JSON columns on [ResearchRun]. */
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

    fun stepsToJson(steps: List<String>): String? =
        if (steps.isEmpty()) null else stepsAdapter.toJson(steps)

    fun stepsFromJson(json: String?): List<String> =
        json?.let { runCatching { stepsAdapter.fromJson(it) }.getOrNull() } ?: emptyList()

    fun sourcesToJson(sources: List<SearchSource>): String? =
        if (sources.isEmpty()) null else sourcesAdapter.toJson(sources)

    fun sourcesFromJson(json: String?): List<SearchSource> =
        json?.let { runCatching { sourcesAdapter.fromJson(it) }.getOrNull() } ?: emptyList()
}
