package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Progress/result events emitted by [DeepResearchEngine] for the service to persist. */
sealed class ResearchEvent {
    /** Provider-native job created — persist [jobId] so the run can resume by re-polling. */
    data class ProviderJob(val jobId: String) : ResearchEvent()
    data class Plan(val steps: List<String>) : ResearchEvent()
    data class Phase(
        val status: String,
        val label: String,
        val done: Int = 0,
        val total: Int = 0,
    ) : ResearchEvent()

    /** Newly discovered sources to merge into the accumulated set. */
    data class Sources(val sources: List<SearchSource>) : ResearchEvent()

    /** Final report markdown. */
    data class Report(val text: String) : ResearchEvent()

    /** Final structured result (JSON string) from the Data Agent. */
    data class Structured(val json: String) : ResearchEvent()

    /** Live cost/credits meter text, e.g. "$0.12" or "84 credits". */
    data class Cost(val label: String) : ResearchEvent()

    data class Failed(val message: String) : ResearchEvent()
}

/**
 * Runs one Deep Research request and emits [ResearchEvent]s. Two paths:
 *  - **provider**: create a job on Exa/Parallel/Firecrawl, then poll until it finishes.
 *  - **agent**: plan sub-questions with a cloud chat model, run each search locally via
 *    [WebSearchService], then synthesize a cited report with the same model.
 *
 * Stateless: all durable state lives in [ResearchRun]; the caller persists what we emit and
 * can re-invoke with an [existing] run to resume.
 */
class DeepResearchEngine(
    private val openRouterService: OpenRouterService,
    private val webSearchService: WebSearchService,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        // Exa's deep-reasoning /search is synchronous and can take 12–40s (sometimes more),
        // so the read timeout is generous.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    fun run(
        config: DeepResearchConfig,
        topic: String,
        openRouterKey: String,
        searchKey: String,
        existing: ResearchRun?,
    ): Flow<ResearchEvent> = flow {
        try {
            when (config.engineKind) {
                "provider" -> runProvider(config, topic, searchKey, existing)
                "data-agent" -> runDataAgent(config, topic, searchKey, existing)
                else -> runAgent(config, topic, openRouterKey, searchKey, existing)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ResearchEvent.Failed(e.message ?: "Deep research failed."))
        }
    }.flowOn(Dispatchers.IO)

    // ── Provider-native ──────────────────────────────────────────────────────────────

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.runProvider(
        config: DeepResearchConfig,
        topic: String,
        apiKey: String,
        existing: ResearchRun?,
    ) {
        if (apiKey.isBlank()) {
            emit(ResearchEvent.Failed("No ${config.provider} API key — add one in Settings → Web search."))
            return
        }
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Starting ${config.engineLabel}…"))

        if (config.provider == "exa") {
            if (config.providerModel == "agent") {
                // Exa Agent: async POST /agent/runs, depth set by `effort`.
                runExaAgent(apiKey, config, topic, existing)
            } else {
                // Exa Deep Reasoning: a single synchronous /search call (no job, no polling).
                emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Researching with Exa…"))
                val done = exaDeepSearch(apiKey, config.providerModel, topic, config.maxSources)
                if (done.sources.isNotEmpty()) emit(ResearchEvent.Sources(done.sources))
                emit(ResearchEvent.Report(done.report))
            }
            return
        }

        // Parallel and Firecrawl create a job, then we poll until it finishes.
        val jobId = existing?.providerJobId ?: when (config.provider) {
            "parallel" -> createParallel(apiKey, config.providerModel, topic)
            "firecrawl" -> createFirecrawl(apiKey, topic, config.maxSources)
            else -> throw Exception("Unknown research provider: ${config.provider}")
        }
        emit(ResearchEvent.ProviderJob(jobId))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Researching the web…"))

        // Poll until the provider finishes. Capped so a stuck job can't poll forever.
        val deadline = System.currentTimeMillis() + 40 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val result = when (config.provider) {
                "parallel" -> pollParallel(apiKey, jobId)
                else -> pollFirecrawl(apiKey, jobId)
            }
            when (result) {
                is PollResult.Pending -> {
                    result.label?.let { emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, it)) }
                }
                is PollResult.Done -> {
                    if (result.sources.isNotEmpty()) emit(ResearchEvent.Sources(result.sources))
                    emit(ResearchEvent.Report(result.report))
                    return
                }
                is PollResult.Error -> {
                    emit(ResearchEvent.Failed(result.message))
                    return
                }
            }
        }
        emit(ResearchEvent.Failed("Research timed out. Try a narrower question or a faster engine."))
    }

    private sealed class PollResult {
        data class Pending(val label: String?) : PollResult()
        data class Done(val report: String, val sources: List<SearchSource>) : PollResult()
        data class Error(val message: String) : PollResult()
    }

    // Exa deep research is a single synchronous POST /search with a deep `type`
    // ("deep" | "deep-lite" | "deep-reasoning"). With outputSchema {type:"text"} the
    // synthesized report comes back in output.content and the sources in results[].
    private fun exaDeepSearch(apiKey: String, type: String, topic: String, numResults: Int): PollResult.Done {
        val json = post(
            url = "https://api.exa.ai/search",
            headers = mapOf("x-api-key" to apiKey),
            body = mapOf(
                "query" to topic,
                "type" to type,
                "numResults" to numResults.coerceIn(1, 100),
                "outputSchema" to mapOf("type" to "text"),
                "contents" to mapOf(
                    "highlights" to true,
                    "text" to mapOf("maxCharacters" to 2000),
                ),
            ),
            label = "Exa",
        )
        val output = json["output"] as? Map<*, *>
        val report = when (val content = output?.get("content")) {
            is String -> content
            is Map<*, *> -> anyAdapter.toJson(content)
            else -> ""
        }
        val sources = (json["results"] as? List<*>).orEmpty().mapNotNull { raw ->
            val r = raw as? Map<*, *> ?: return@mapNotNull null
            val url = r["url"] as? String ?: return@mapNotNull null
            val snippet = (r["text"] as? String)?.take(2000)
                ?: (r["highlights"] as? List<*>)?.filterIsInstance<String>()?.joinToString("\n")?.take(2000)
                ?: (r["summary"] as? String)
            SearchSource(
                title = (r["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = snippet,
                publishedDate = r["publishedDate"] as? String,
            )
        }.distinctBy { it.url }
        if (report.isBlank()) throw Exception("Exa returned an empty report.")
        return PollResult.Done(report, sources)
    }

    // Parallel: POST /v1/tasks/runs then poll GET /v1/tasks/runs/{id}, then GET .../result
    private fun createParallel(apiKey: String, processor: String, topic: String): String {
        val json = post(
            url = "https://api.parallel.ai/v1/tasks/runs",
            headers = mapOf("x-api-key" to apiKey),
            body = mapOf(
                "input" to topic,
                "processor" to processor,
                "task_spec" to mapOf("output_schema" to mapOf("type" to "text")),
            ),
            label = "Parallel",
        )
        return (json["run_id"] as? String) ?: (json["id"] as? String)
            ?: throw Exception("Parallel did not return a run id.")
    }

    private fun pollParallel(apiKey: String, id: String): PollResult {
        val status = get("https://api.parallel.ai/v1/tasks/runs/$id", mapOf("x-api-key" to apiKey), "Parallel")
        return when (val s = (status["status"] as? String).orEmpty()) {
            "completed" -> {
                val result = get("https://api.parallel.ai/v1/tasks/runs/$id/result", mapOf("x-api-key" to apiKey), "Parallel")
                val output = result["output"] as? Map<*, *>
                val content = output?.get("content")
                val report = when (content) {
                    is String -> content
                    is Map<*, *> -> anyAdapter.toJson(content)
                    else -> ""
                }
                val basis = (output?.get("basis") as? List<*>).orEmpty()
                val sources = basis.flatMap { b ->
                    val citations = ((b as? Map<*, *>)?.get("citations") as? List<*>).orEmpty()
                    citations.mapNotNull { raw ->
                        val c = raw as? Map<*, *> ?: return@mapNotNull null
                        val url = c["url"] as? String ?: return@mapNotNull null
                        SearchSource(
                            title = (c["title"] as? String).orEmpty().ifBlank { url },
                            url = url,
                            snippet = (c["excerpts"] as? List<*>)?.filterIsInstance<String>()?.joinToString("\n")?.take(2000),
                        )
                    }
                }.distinctBy { it.url }
                if (report.isBlank()) PollResult.Error("Parallel returned an empty report.")
                else PollResult.Done(report, sources)
            }
            "failed", "cancelled", "cancelling" -> PollResult.Error("Parallel research failed.")
            else -> PollResult.Pending(if (s == "running") "Researching with Parallel…" else null)
        }
    }

    // Firecrawl: POST /v1/deep-research then GET /v1/deep-research/{id}
    private fun createFirecrawl(apiKey: String, topic: String, maxSources: Int): String {
        val json = post(
            url = "https://api.firecrawl.dev/v1/deep-research",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = mapOf(
                "query" to topic,
                "maxUrls" to maxSources.coerceIn(1, 120),
                "maxDepth" to 5,
                "timeLimit" to 270,
            ),
            label = "Firecrawl",
        )
        return (json["id"] as? String) ?: ((json["data"] as? Map<*, *>)?.get("id") as? String)
            ?: throw Exception("Firecrawl did not return a job id.")
    }

    private fun pollFirecrawl(apiKey: String, id: String): PollResult {
        val json = get("https://api.firecrawl.dev/v1/deep-research/$id", mapOf("Authorization" to "Bearer $apiKey"), "Firecrawl")
        val data = json["data"] as? Map<*, *>
        return when ((json["status"] as? String).orEmpty()) {
            "completed" -> {
                val report = (data?.get("finalAnalysis") as? String).orEmpty()
                val sources = (data?.get("sources") as? List<*>).orEmpty().mapNotNull { raw ->
                    val s = raw as? Map<*, *> ?: return@mapNotNull null
                    val url = s["url"] as? String ?: return@mapNotNull null
                    SearchSource(
                        title = (s["title"] as? String).orEmpty().ifBlank { url },
                        url = url,
                        snippet = s["description"] as? String,
                    )
                }.distinctBy { it.url }
                if (report.isBlank()) PollResult.Error("Firecrawl returned an empty report.")
                else PollResult.Done(report, sources)
            }
            "failed", "cancelled", "error" -> PollResult.Error("Firecrawl research failed.")
            else -> {
                val depth = (data?.get("currentDepth") as? Double)?.toInt()
                PollResult.Pending(depth?.let { "Crawling the web (depth $it)…" })
            }
        }
    }

    // Exa Agent: async POST /agent/runs then poll GET /agent/runs/{id}. Depth = effort.
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.runExaAgent(
        apiKey: String,
        config: DeepResearchConfig,
        topic: String,
        existing: ResearchRun?,
    ) {
        val headers = mapOf("x-api-key" to apiKey, "Exa-Beta" to EXA_AGENT_BETA)
        val jobId = existing?.providerJobId ?: run {
            val created = post(
                url = "https://api.exa.ai/agent/runs",
                headers = headers,
                body = mapOf("query" to topic, "effort" to (config.level ?: "auto")),
                label = "Exa",
            )
            (created["id"] as? String) ?: throw Exception("Exa Agent did not return a run id.")
        }
        emit(ResearchEvent.ProviderJob(jobId))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Exa agent is researching…"))

        val deadline = System.currentTimeMillis() + 40 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val json = get("https://api.exa.ai/agent/runs/$jobId", headers, "Exa")
            (json["costDollars"] as? Double)?.let { emit(ResearchEvent.Cost("$" + "%.2f".format(it))) }
            when ((json["status"] as? String).orEmpty()) {
                "completed" -> {
                    val output = json["output"] as? Map<*, *>
                    val text = (output?.get("text") as? String).orEmpty()
                    val structured = output?.get("structured")
                    val report = when {
                        text.isNotBlank() -> text
                        structured != null -> anyAdapter.toJson(structured)
                        else -> ""
                    }
                    val sources = collectSources(output?.get("grounding"))
                    if (sources.isNotEmpty()) emit(ResearchEvent.Sources(sources))
                    if (report.isBlank()) emit(ResearchEvent.Failed("Exa Agent returned an empty result."))
                    else emit(ResearchEvent.Report(report))
                    return
                }
                "failed", "cancelled", "canceled", "error" -> {
                    emit(ResearchEvent.Failed("Exa Agent run failed."))
                    return
                }
                else -> Unit
            }
        }
        emit(ResearchEvent.Failed("Exa Agent timed out. Try a lower effort or a narrower question."))
    }

    // ── Data Agent (Firecrawl /v2/agent — structured extraction) ─────────────────────

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.runDataAgent(
        config: DeepResearchConfig,
        topic: String,
        apiKey: String,
        existing: ResearchRun?,
    ) {
        if (apiKey.isBlank()) {
            emit(ResearchEvent.Failed("No Firecrawl API key — add one in Settings → Web search."))
            return
        }
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Starting the data agent…"))
        val headers = mapOf("Authorization" to "Bearer $apiKey")
        val jobId = existing?.providerJobId ?: run {
            val created = post(
                url = "https://api.firecrawl.dev/v2/agent",
                headers = headers,
                body = mapOf("prompt" to topic, "model" to config.providerModel, "maxCredits" to config.maxCredits),
                label = "Firecrawl",
            )
            (created["id"] as? String) ?: ((created["data"] as? Map<*, *>)?.get("id") as? String)
                ?: throw Exception("Firecrawl agent did not return a job id.")
        }
        emit(ResearchEvent.ProviderJob(jobId))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Collecting data…"))

        val deadline = System.currentTimeMillis() + 40 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val json = get("https://api.firecrawl.dev/v2/agent/$jobId", headers, "Firecrawl")
            (json["creditsUsed"] as? Double)?.let { emit(ResearchEvent.Cost("${it.toInt()} credits")) }
            when ((json["status"] as? String).orEmpty()) {
                "completed" -> {
                    val data = json["data"]
                    val sources = (collectSources(data) + collectSources(json["sources"])).distinctBy { it.url }
                    if (sources.isNotEmpty()) emit(ResearchEvent.Sources(sources))
                    when (data) {
                        is String -> if (data.isBlank()) emit(ResearchEvent.Failed("The data agent returned no data.")) else emit(ResearchEvent.Report(data))
                        null -> emit(ResearchEvent.Failed("The data agent returned no data."))
                        else -> emit(ResearchEvent.Structured(anyAdapter.toJson(data)))
                    }
                    return
                }
                "failed", "cancelled", "error" -> {
                    emit(ResearchEvent.Failed("The data agent failed."))
                    return
                }
                else -> Unit
            }
        }
        emit(ResearchEvent.Failed("The data agent timed out."))
    }

    /** Recursively pull any {url,title,…} objects out of an arbitrary JSON node (citations/sources). */
    private fun collectSources(node: Any?): List<SearchSource> {
        val out = mutableListOf<SearchSource>()
        fun walk(n: Any?) {
            when (n) {
                is Map<*, *> -> {
                    val url = n["url"] as? String
                    if (url != null && url.startsWith("http")) {
                        out.add(
                            SearchSource(
                                title = (n["title"] as? String).orEmpty().ifBlank { url },
                                url = url,
                                snippet = (n["snippet"] as? String) ?: (n["text"] as? String),
                            )
                        )
                    }
                    n.values.forEach { walk(it) }
                }
                is List<*> -> n.forEach { walk(it) }
            }
        }
        walk(node)
        return out.distinctBy { it.url }
    }

    // ── Agentic (plan → search → synthesize) ─────────────────────────────────────────

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.runAgent(
        config: DeepResearchConfig,
        topic: String,
        openRouterKey: String,
        searchKey: String,
        existing: ResearchRun?,
    ) {
        if (openRouterKey.isBlank()) {
            emit(ResearchEvent.Failed("OpenRouter API key is missing — add it in Settings → Cloud models."))
            return
        }
        val searchProvider = config.searchProvider
        if (searchProvider.isNullOrBlank() || searchKey.isBlank()) {
            emit(ResearchEvent.Failed("No search provider is configured for Deep Research. Add an Exa, Parallel or Firecrawl key in Settings → Web search."))
            return
        }

        // Resume: if we already gathered sources for a plan, jump straight to synthesis.
        val resumedSources = ResearchJson.sourcesFromJson(existing?.sourcesJson)
        if (existing != null && resumedSources.isNotEmpty() && existing.report.isNullOrBlank()) {
            emit(ResearchEvent.Phase(ResearchRun.STATUS_SYNTHESIZING, "Writing the report…"))
            emit(ResearchEvent.Report(synthesize(config.provider, openRouterKey, topic, resumedSources)))
            return
        }

        emit(ResearchEvent.Phase(ResearchRun.STATUS_PLANNING, "Planning the research…"))
        val planRaw = openRouterService.complete(
            apiKey = openRouterKey,
            model = config.provider,
            systemPrompt = SystemPrompts.deepResearchPlanner(topic, config.maxSearches),
            userPrompt = topic,
        )
        val steps = parsePlan(planRaw, config.maxSearches)
        if (steps.isEmpty()) {
            emit(ResearchEvent.Failed("The model did not produce a research plan. Try rephrasing your question."))
            return
        }
        emit(ResearchEvent.Plan(steps))

        val perSearch = (config.maxSources / steps.size).coerceIn(3, 10)
        val gathered = LinkedHashMap<String, SearchSource>()
        steps.forEachIndexed { i, step ->
            emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Searching ${i + 1} of ${steps.size}", i, steps.size))
            val found = try {
                webSearchService.search(searchProvider, searchKey, step, perSearch)
            } catch (e: Exception) {
                emptyList()
            }
            val added = mutableListOf<SearchSource>()
            found.forEach { source ->
                if (source.url !in gathered && gathered.size < config.maxSources) {
                    gathered[source.url] = source
                    added.add(source)
                }
            }
            if (added.isNotEmpty()) emit(ResearchEvent.Sources(added))
        }
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Reviewed ${gathered.size} sources", steps.size, steps.size))

        if (gathered.isEmpty()) {
            emit(ResearchEvent.Failed("No sources were found. Check your search provider key, or try a different question."))
            return
        }

        emit(ResearchEvent.Phase(ResearchRun.STATUS_SYNTHESIZING, "Writing the report…"))
        emit(ResearchEvent.Report(synthesize(config.provider, openRouterKey, topic, gathered.values.toList())))
    }

    private suspend fun synthesize(
        model: String,
        apiKey: String,
        topic: String,
        sources: List<SearchSource>,
    ): String = openRouterService.complete(
        apiKey = apiKey,
        model = model,
        systemPrompt = SystemPrompts.deepResearchSynthesis(topic),
        userPrompt = "Numbered search results:\n\n" + formatSearchResultsForModel(sources),
    )

    private fun parsePlan(raw: String, max: Int): List<String> =
        raw.lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .map { it.replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.length > 4 }
            .distinct()
            .take(max)
            .toList()

    // ── HTTP plumbing ────────────────────────────────────────────────────────────────

    private fun post(url: String, headers: Map<String, String>, body: Map<String, Any>, label: String): Map<*, *> {
        val reqBody = anyAdapter.toJson(body).toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(url).addHeader("Content-Type", "application/json").post(reqBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build(), label)
    }

    private fun get(url: String, headers: Map<String, String>, label: String): Map<*, *> {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build(), label)
    }

    private fun execute(request: Request, label: String): Map<*, *> {
        client.newCall(request).execute().use { response ->
            val str = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "Invalid $label API key — check Settings."
                    429 -> "$label rate limit reached — try again shortly."
                    else -> "$label request failed (HTTP ${response.code})."
                }
                throw Exception(message)
            }
            return anyAdapter.fromJson(str) as? Map<*, *>
                ?: throw Exception("$label returned an unreadable response.")
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private const val EXA_AGENT_BETA = "agent-2026-05-07"
    }
}
