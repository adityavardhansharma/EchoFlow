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

    /**
     * Separate client for server-sent event feeds. A research stream is idle between events for
     * minutes at a time, so the shared client's 120s read timeout would tear it down mid-run; the
     * bound that matters here is the whole-call one.
     */
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.MINUTES)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    // ── Timeline narration ───────────────────────────────────────────────────────────
    // Every engine describes itself as upserted ResearchSteps, which the service mirrors into
    // research_runs.stepsJson. How much detail each one can honestly give varies a lot: the
    // agentic path owns its loop and narrates every sub-question, Firecrawl exposes an activity
    // feed, and the rest can only say "still working" — those get a single step rather than
    // invented stages.

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.step(
        id: String,
        label: String,
        state: String,
        kind: String = ResearchStep.KIND_SEARCH,
        detail: String? = null,
        sourceUrls: List<String> = emptyList(),
    ) {
        val now = System.currentTimeMillis()
        emit(
            ResearchEvent.Steps(
                listOf(
                    ResearchStep(
                        id = id,
                        kind = kind,
                        label = label,
                        state = state,
                        detail = detail,
                        sourceUrls = sourceUrls,
                        startedAt = now,
                        endedAt = if (state == ResearchStep.STATE_DONE || state == ResearchStep.STATE_FAILED) now else null,
                    )
                )
            )
        )
    }

    fun run(
        config: DeepResearchConfig,
        topic: String,
        openRouterKey: String,
        searchKey: String,
        existing: ResearchRun?,
    ): Flow<ResearchEvent> = flow {
        try {
            val attachment = existing?.localAttachmentForOpenRouter()
            when (config.engineKind) {
                "provider" -> runProvider(config, topic, searchKey, existing)
                "data-agent" -> runDataAgent(config, topic, searchKey, existing)
                else -> runAgent(config, topic, openRouterKey, searchKey, existing, attachment)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ResearchEvent.Failed(e.message ?: "Deep research failed."))
        }
    }.flowOn(Dispatchers.IO)

    // ── Server-sent events ───────────────────────────────────────────────────────────

    /**
     * Read an SSE response frame by frame, invoking [onEvent] per complete frame. Returning false
     * from [onEvent] closes the stream early.
     *
     * Named-event streams are the reason this exists rather than reusing the chat transport's
     * `data:`-only loop: Parallel and Exa both put the meaning in the `event:` line.
     */
    private suspend fun streamSse(
        request: Request,
        label: String,
        onEvent: suspend (SseEvent) -> Boolean,
    ) {
        streamClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("$label event stream failed (HTTP ${response.code}).")
            }
            val reader = (response.body ?: throw Exception("$label returned an empty event stream."))
                .charStream().buffered()
            val decoder = SseDecoder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val frame = decoder.accept(line!!) ?: continue
                if (!onEvent(frame)) return
            }
            decoder.flush()?.let { onEvent(it) }
        }
    }

    private fun parseFrame(data: String): Map<*, *>? =
        runCatching { anyAdapter.fromJson(data) as? Map<*, *> }.getOrNull()

    /**
     * Pull a human-readable line out of an event payload. Both providers nest their message under
     * different keys depending on the event subtype, and neither shape is contractually stable, so
     * this reads the plausible ones and gives up quietly rather than guessing wrong loudly.
     */
    private fun messageOf(node: Any?): String? {
        when (node) {
            is String -> return node.takeIf { it.isNotBlank() }
            is Map<*, *> -> {
                MESSAGE_KEYS.forEach { key ->
                    (node[key] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
                }
                // One level down: {"progress_msg": {"message": "..."}} and friends.
                NESTED_KEYS.forEach { key ->
                    (node[key] as? Map<*, *>)?.let { nested ->
                        MESSAGE_KEYS.forEach { inner ->
                            (nested[inner] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }

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
                // There is genuinely nothing to narrate in between, so it stays one step.
                emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Researching with Exa…"))
                step(STEP_PROVIDER, "Researching with Exa", ResearchStep.STATE_ACTIVE, ResearchStep.KIND_ANALYZE)
                val done = exaDeepSearch(apiKey, config.providerModel, topic, config.maxSources)
                if (done.sources.isNotEmpty()) emit(ResearchEvent.Sources(done.sources))
                step(
                    STEP_PROVIDER, "Researched with Exa", ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE,
                    detail = "${done.sources.size} sources",
                    sourceUrls = done.sources.map { it.url },
                )
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
        val runningLabel = "Researching with ${config.engineLabel}"
        step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_ANALYZE)

        // Parallel narrates itself over SSE. Consume that first for the timeline, then fall
        // through to polling for the terminal state and result. Strictly an enhancement: any
        // failure here leaves the run exactly as it behaves without it.
        // True once a real per-step feed has taken over, so the generic placeholder row stops
        // being refreshed as active underneath the steps that replaced it.
        var feedTookOver = false
        if (config.provider == "parallel") {
            try {
                streamParallelEvents(apiKey, jobId)
                feedTookOver = true
                step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Stream unavailable — the poll loop below still finishes the run.
            }
        }

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
                    // Firecrawl reports an activity feed; everything else can only refresh the
                    // one running step's detail line.
                    if (result.steps.isNotEmpty()) {
                        // The feed supersedes the placeholder, which would otherwise sit spinning
                        // beside the very rows meant to replace it for the rest of the run.
                        if (!feedTookOver) {
                            feedTookOver = true
                            step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE)
                        }
                        emit(ResearchEvent.Steps(result.steps))
                    } else if (!feedTookOver) {
                        result.label?.let {
                            step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_ANALYZE, detail = it)
                        }
                    }
                }
                is PollResult.Done -> {
                    if (result.sources.isNotEmpty()) emit(ResearchEvent.Sources(result.sources))
                    step(
                        STEP_PROVIDER, runningLabel, ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE,
                        detail = "${result.sources.size} sources",
                        sourceUrls = result.sources.map { it.url },
                    )
                    emit(ResearchEvent.Report(result.report))
                    return
                }
                is PollResult.Error -> {
                    step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_ANALYZE)
                    emit(ResearchEvent.Failed(result.message))
                    return
                }
            }
        }
        step(STEP_PROVIDER, runningLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_ANALYZE, detail = "timed out")
        emit(ResearchEvent.Failed("Research timed out. Try a narrower question or a faster engine."))
    }

    private sealed class PollResult {
        data class Pending(val label: String?, val steps: List<ResearchStep> = emptyList()) : PollResult()
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
                "systemPrompt" to SystemPrompts.exaResearchSystemPrompt(),
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
                // Auto-enabled for pro and above (which is every processor we ship), but sent
                // explicitly so the feed doesn't depend on that default holding.
                "enable_events" to true,
                "task_spec" to mapOf("output_schema" to mapOf("type" to "text")),
            ),
            label = "Parallel",
        )
        return (json["run_id"] as? String) ?: (json["id"] as? String)
            ?: throw Exception("Parallel did not return a run id.")
    }

    /**
     * Parallel's progress feed: `GET /v1/tasks/runs/{id}/events`.
     *
     * The raw feed is a full execution log (plan lines, every Objective/Query, tools, intermediate
     * findings). [ParallelProgressCollapser] compresses that into a few chapters before we emit
     * steps — same ballpark as Firecrawl's activity list and the agentic plan — so the timeline
     * stays scannable. The stream still replays from the start on reattach; collapsed ids are
     * stable, so a resume upserts rather than doubles.
     *
     * Progress only. Terminal state and result still come from the poll path; if the stream
     * fails, the run falls back to the single "Researching with Parallel" row.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.streamParallelEvents(
        apiKey: String,
        jobId: String,
    ) {
        val request = Request.Builder()
            .url("https://api.parallel.ai/v1/tasks/runs/$jobId/events")
            .addHeader("x-api-key", apiKey)
            .addHeader("Accept", "text/event-stream")
            .get()
            .build()

        val collapser = ParallelProgressCollapser()

        streamSse(request, "Parallel") { frame ->
            val json = parseFrame(frame.data)
            val type = frame.name ?: (json?.get("type") as? String).orEmpty()
            when {
                type.startsWith("task_run.progress_msg") -> {
                    val message = messageOf(json) ?: return@streamSse true
                    val subtype = type.substringAfterLast('.')
                    emitParallelSteps(collapser.onProgressMessage(subtype, message))
                }
                type == "task_run.progress_stats" -> {
                    val read = (json?.get("num_sources_read") as? Double)?.toInt()
                        ?: (json?.get("num_sources_read") as? Number)?.toInt()
                    val considered = (json?.get("num_sources_considered") as? Double)?.toInt()
                        ?: (json?.get("num_sources_considered") as? Number)?.toInt()
                    emitParallelSteps(collapser.onStats(read, considered))
                }
                type == "task_run.state" -> {
                    val status = ((json?.get("run") as? Map<*, *>)?.get("status") as? String)
                        ?: (json?.get("status") as? String).orEmpty()
                    if (status in PARALLEL_TERMINAL) {
                        emitParallelSteps(collapser.onTerminal(failed = status != "completed"))
                        return@streamSse false
                    }
                }
                type == "error" -> {
                    emitParallelSteps(collapser.onTerminal(failed = true))
                    return@streamSse false
                }
            }
            true
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.emitParallelSteps(
        steps: List<ResearchStep>,
    ) {
        if (steps.isEmpty()) return
        emit(ResearchEvent.Steps(steps))
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
                PollResult.Pending(
                    label = depth?.let { "Crawling the web (depth $it)…" },
                    steps = firecrawlActivitySteps(data),
                )
            }
        }
    }

    /**
     * Firecrawl's poll response carries a cumulative `activities[]` feed (search / scrape /
     * analyse entries with human messages) that we previously discarded in favour of the crawl
     * depth alone. Parsed defensively: anything we can't read degrades to no steps at all, which
     * leaves the single "Researching with Firecrawl" row in place rather than breaking the run.
     *
     * Unverified against a live key — the shape below is what the documented response carries.
     */
    private fun firecrawlActivitySteps(data: Map<*, *>?): List<ResearchStep> {
        val activities = (data?.get("activities") as? List<*>) ?: return emptyList()
        val entries = activities.filterIsInstance<Map<*, *>>()
        if (entries.isEmpty()) return emptyList()
        // These bypass step(), so they have to stamp their own times — the service only ever
        // preserves an existing non-zero startedAt, so a zero here would never be repaired and the
        // workspace could not show a duration for any Firecrawl step.
        val now = System.currentTimeMillis()
        return entries.mapIndexedNotNull { index, entry ->
            val label = (entry["message"] as? String)?.takeIf { it.isNotBlank() }
                ?: (entry["type"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val last = index == entries.lastIndex
            val status = (entry["status"] as? String).orEmpty()
            val state = when {
                status.equals("error", true) || status.equals("failed", true) -> ResearchStep.STATE_FAILED
                status.equals("complete", true) || status.equals("completed", true) -> ResearchStep.STATE_DONE
                last -> ResearchStep.STATE_ACTIVE
                // The feed is append-only, so anything behind the newest entry is finished.
                else -> ResearchStep.STATE_DONE
            }
            ResearchStep(
                id = activityStepId(index),
                kind = when (entry["type"] as? String) {
                    "search" -> ResearchStep.KIND_SEARCH
                    "scrape", "extract" -> ResearchStep.KIND_READ
                    else -> ResearchStep.KIND_ANALYZE
                },
                label = label,
                state = state,
                detail = (entry["depth"] as? Double)?.toInt()?.let { "depth $it" },
                startedAt = now,
                endedAt = if (state == ResearchStep.STATE_ACTIVE) null else now,
            )
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
        val agentLabel = "Exa agent is researching"

        // Exa streams by holding the *create* request open, so — unlike Parallel's separate
        // events endpoint — the feed cannot be re-attached to a run that already exists. A fresh
        // run streams its search trace; a run resumed after the app was killed can only poll, and
        // the steps it emitted before the kill are gone. That asymmetry is the API's, not ours.
        val jobId = existing?.providerJobId ?: run {
            val streamed = try {
                streamExaAgentRun(headers, config, topic)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            streamed ?: run {
                val created = post(
                    url = "https://api.exa.ai/agent/runs",
                    headers = headers,
                    body = mapOf(
                        "query" to topic + "\n\n" + SystemPrompts.exaResearchSystemPrompt(),
                        "effort" to (config.level ?: "auto"),
                    ),
                    label = "Exa",
                )
                (created["id"] as? String) ?: throw Exception("Exa Agent did not return a run id.")
            }
        }
        emit(ResearchEvent.ProviderJob(jobId))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Exa agent is researching…"))
        if (existing?.providerJobId != null) {
            // Resumed: no stream available, so this is the honest single row.
            step(STEP_PROVIDER, agentLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_ANALYZE)
        }

        val deadline = System.currentTimeMillis() + 40 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val json = get("https://api.exa.ai/agent/runs/$jobId", headers, "Exa")
            (json["costDollars"] as? Double)?.let {
                // Locale.US to match the hardcoded "$": on a comma-decimal locale the default
                // would render "$0,12".
                val cost = "$" + "%.2f".format(java.util.Locale.US, it)
                emit(ResearchEvent.Cost(cost))
                step(STEP_PROVIDER, agentLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_ANALYZE, detail = cost)
            }
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
                    if (report.isBlank()) {
                        step(STEP_PROVIDER, agentLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_ANALYZE, detail = "empty result")
                        emit(ResearchEvent.Failed("Exa Agent returned an empty result."))
                    } else {
                        step(
                            STEP_PROVIDER, "Exa agent researched", ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE,
                            detail = "${sources.size} sources",
                            sourceUrls = sources.map { it.url },
                        )
                        emit(ResearchEvent.Report(report))
                    }
                    return
                }
                "failed", "cancelled", "canceled", "error" -> {
                    step(STEP_PROVIDER, agentLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_ANALYZE)
                    emit(ResearchEvent.Failed("Exa Agent run failed."))
                    return
                }
                else -> Unit
            }
        }
        step(STEP_PROVIDER, agentLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_ANALYZE, detail = "timed out")
        emit(ResearchEvent.Failed("Exa Agent timed out. Try a lower effort or a narrower question."))
    }

    /**
     * Create an Exa Agent run with `Accept: text/event-stream` and narrate its search trace.
     *
     * Returns the run id (captured from `agent_run.created`, and emitted as a [ResearchEvent
     * .ProviderJob] the moment it arrives so a kill mid-stream can still resume by polling), or
     * null if the stream never identified a run — in which case the caller creates one normally.
     *
     * Sources announced by `agent_run.source.added` are merged live, so the favicon stack fills in
     * while the agent works instead of appearing all at once at the end.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ResearchEvent>.streamExaAgentRun(
        headers: Map<String, String>,
        config: DeepResearchConfig,
        topic: String,
    ): String? {
        val body = anyAdapter.toJson(
            mapOf(
                "query" to topic + "\n\n" + SystemPrompts.exaResearchSystemPrompt(),
                "effort" to (config.level ?: "auto"),
                "stream" to true,
            )
        ).toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url("https://api.exa.ai/agent/runs")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        var runId: String? = null
        var index = 0
        var lastId: String? = null
        var lastLabel: String? = null

        streamSse(builder.build(), "Exa") { frame ->
            val json = parseFrame(frame.data)
            val type = frame.name ?: (json?.get("type") as? String).orEmpty()
            when {
                type == "agent_run.created" || type == "agent_run.started" -> {
                    val id = (json?.get("id") as? String)
                        ?: ((json?.get("run") as? Map<*, *>)?.get("id") as? String)
                    if (id != null && runId == null) {
                        runId = id
                        emit(ResearchEvent.ProviderJob(id))
                    }
                }
                type == "agent_run.source.added" -> {
                    val sources = collectSources(json)
                    if (sources.isNotEmpty()) {
                        emit(ResearchEvent.Sources(sources))
                        lastId?.let { id ->
                            step(
                                id, lastLabel ?: "Reading sources", ResearchStep.STATE_ACTIVE,
                                ResearchStep.KIND_READ,
                                detail = "${sources.size} found",
                                sourceUrls = sources.map { it.url },
                            )
                        }
                    }
                }
                type.startsWith("agent_run.completed") ||
                    type.startsWith("agent_run.failed") ||
                    type.startsWith("agent_run.cancelled") -> {
                    lastId?.let { id ->
                        val ok = type.startsWith("agent_run.completed")
                        step(
                            id, lastLabel ?: "Researching",
                            if (ok) ResearchStep.STATE_DONE else ResearchStep.STATE_FAILED,
                            ResearchStep.KIND_ANALYZE,
                        )
                    }
                    return@streamSse false
                }
                else -> {
                    // Tool progress and search-trace events. Exa notes these can arrive out of
                    // order relative to the source events they describe, so they are appended in
                    // arrival order and never used to reorder anything.
                    val message = messageOf(json) ?: return@streamSse true
                    lastId?.let { id ->
                        step(id, lastLabel ?: message, ResearchStep.STATE_DONE, ResearchStep.KIND_ANALYZE)
                    }
                    val id = feedStepId(index++)
                    lastId = id
                    lastLabel = message
                    step(id, message, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_SEARCH)
                }
            }
            true
        }
        return runId
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
                body = mapOf(
                    "prompt" to topic + SystemPrompts.dataAgentPromptSuffix(),
                    "model" to config.providerModel,
                    "maxCredits" to config.maxCredits,
                ),
                label = "Firecrawl",
            )
            (created["id"] as? String) ?: ((created["data"] as? Map<*, *>)?.get("id") as? String)
                ?: throw Exception("Firecrawl agent did not return a job id.")
        }
        emit(ResearchEvent.ProviderJob(jobId))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Collecting data…"))
        val collectLabel = "Collecting data"
        step(STEP_PROVIDER, collectLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_READ)

        val deadline = System.currentTimeMillis() + 40 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val json = get("https://api.firecrawl.dev/v2/agent/$jobId", headers, "Firecrawl")
            (json["creditsUsed"] as? Double)?.let {
                val credits = "${it.toInt()} credits"
                emit(ResearchEvent.Cost(credits))
                step(STEP_PROVIDER, collectLabel, ResearchStep.STATE_ACTIVE, ResearchStep.KIND_READ, detail = credits)
            }
            when ((json["status"] as? String).orEmpty()) {
                "completed" -> {
                    val data = json["data"]
                    val sources = (collectSources(data) + collectSources(json["sources"])).distinctBy { it.url }
                    if (sources.isNotEmpty()) emit(ResearchEvent.Sources(sources))
                    val empty = data == null || (data is String && data.isBlank())
                    step(
                        STEP_PROVIDER,
                        if (empty) collectLabel else "Collected data",
                        if (empty) ResearchStep.STATE_FAILED else ResearchStep.STATE_DONE,
                        ResearchStep.KIND_READ,
                        detail = if (empty) "no data" else "${sources.size} sources",
                        sourceUrls = sources.map { it.url },
                    )
                    when (data) {
                        is String -> if (data.isBlank()) emit(ResearchEvent.Failed("The data agent returned no data.")) else emit(ResearchEvent.Report(data))
                        null -> emit(ResearchEvent.Failed("The data agent returned no data."))
                        else -> emit(ResearchEvent.Structured(anyAdapter.toJson(data)))
                    }
                    return
                }
                "failed", "cancelled", "error" -> {
                    step(STEP_PROVIDER, collectLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_READ)
                    emit(ResearchEvent.Failed("The data agent failed."))
                    return
                }
                else -> Unit
            }
        }
        step(STEP_PROVIDER, collectLabel, ResearchStep.STATE_FAILED, ResearchStep.KIND_READ, detail = "timed out")
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
        attachment: OpenRouterService.LocalAttachment?,
    ) {
        if (openRouterKey.isBlank()) {
            emit(ResearchEvent.Failed("OpenRouter API key is missing — add it in Settings → Models → OpenRouter."))
            return
        }
        val searchProvider = config.searchProvider
        if (searchProvider.isNullOrBlank() || searchKey.isBlank()) {
            emit(ResearchEvent.Failed("No search provider is configured for Deep Research. Add an Exa, Parallel or Firecrawl key in Settings → Web search."))
            return
        }

        val resumedSources = ResearchJson.sourcesFromJson(existing?.sourcesJson)
        val savedPlan = ResearchJson.stepsFromJson(existing?.planJson)
        val completedSearches = ResearchResumePolicy.completed(savedPlan, ResearchJson.timelineFromJson(existing?.stepsJson))
        emit(ResearchEvent.Phase(ResearchRun.STATUS_PLANNING, "Planning the research…"))
        step(STEP_PLAN, "Planning the research", ResearchStep.STATE_ACTIVE, ResearchStep.KIND_PLAN)
        val planningPrompt = if (attachment != null) {
            "User research request: $topic\n\nUse the attached PDF as primary context while planning the research."
        } else topic
        val planRaw = if (savedPlan.isNotEmpty()) "" else openRouterService.complete(
            apiKey = openRouterKey,
            model = config.provider,
            systemPrompt = SystemPrompts.deepResearchPlanner(topic, config.maxSearches),
            userPrompt = planningPrompt,
            attachment = attachment,
        )
        val steps = savedPlan.ifEmpty { parsePlan(planRaw, config.maxSearches) }
        if (steps.isEmpty()) {
            step(STEP_PLAN, "Planning the research", ResearchStep.STATE_FAILED, ResearchStep.KIND_PLAN, detail = "no plan")
            emit(ResearchEvent.Failed("The model did not produce a research plan. Try rephrasing your question."))
            return
        }
        emit(ResearchEvent.Plan(steps))
        step(
            STEP_PLAN, "Planned the research", ResearchStep.STATE_DONE, ResearchStep.KIND_PLAN,
            detail = "${steps.size} question${if (steps.size == 1) "" else "s"}",
        )

        // Seed the whole plan as pending rows in one write, so the timeline shows where the run
        // is going rather than growing a line at a time.
        //
        // replaceKinds drops any search rows from a previous attempt first. A run interrupted
        // during planning re-plans on resume, and step ids are keyed by position, so without the
        // reset a shorter new plan would leave orphaned pending rows behind and a reworded one
        // would silently relabel rows that belonged to different questions.
        emit(
            ResearchEvent.Steps(
                steps = steps.mapIndexed { i, question ->
                    ResearchStep(
                        id = searchStepId(i),
                        kind = ResearchStep.KIND_SEARCH,
                        label = question,
                        state = if (searchStepId(i) in completedSearches) ResearchStep.STATE_DONE else ResearchStep.STATE_PENDING,
                    )
                },
                replaceKinds = setOf(ResearchStep.KIND_SEARCH),
            )
        )

        val perSearch = (config.maxSources / steps.size).coerceIn(3, 10)
        val gathered = LinkedHashMap<String, SearchSource>()
        resumedSources.forEach { gathered[it.url] = it }
        steps.forEachIndexed { i, question ->
            if (searchStepId(i) in completedSearches) return@forEachIndexed
            emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Searching ${i + 1} of ${steps.size}", i, steps.size))
            step(searchStepId(i), question, ResearchStep.STATE_ACTIVE)
            val found = try {
                webSearchService.search(searchProvider, searchKey, question, perSearch)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead provider key used to vanish into an empty result set and read as a
                // question that simply found nothing. Say what actually happened.
                step(
                    searchStepId(i), question, ResearchStep.STATE_FAILED,
                    detail = e.message?.take(60) ?: "search failed",
                )
                return@forEachIndexed
            }
            val added = mutableListOf<SearchSource>()
            found.forEach { source ->
                if (source.url !in gathered && gathered.size < config.maxSources) {
                    gathered[source.url] = source
                    added.add(source)
                }
            }
            if (added.isNotEmpty()) emit(ResearchEvent.Sources(added))
            step(
                searchStepId(i), question, ResearchStep.STATE_DONE,
                detail = "${added.size} source${if (added.size == 1) "" else "s"}",
                sourceUrls = added.map { it.url },
            )
        }
        emit(ResearchEvent.Phase(ResearchRun.STATUS_RESEARCHING, "Reviewed ${gathered.size} sources", steps.size, steps.size))

        if (gathered.isEmpty()) {
            emit(ResearchEvent.Failed("No sources were found. Check your search provider key, or try a different question."))
            return
        }

        emit(ResearchEvent.Phase(ResearchRun.STATUS_SYNTHESIZING, "Writing the report…"))
        step(
            STEP_SYNTHESIZE, "Writing the report", ResearchStep.STATE_ACTIVE, ResearchStep.KIND_SYNTHESIZE,
            detail = "${gathered.size} sources",
        )
        emit(ResearchEvent.Report(synthesize(config.provider, openRouterKey, topic, gathered.values.toList(), attachment)))
    }

    private suspend fun synthesize(
        model: String,
        apiKey: String,
        topic: String,
        sources: List<SearchSource>,
        attachment: OpenRouterService.LocalAttachment?,
    ): String = openRouterService.complete(
        apiKey = apiKey,
        model = model,
        systemPrompt = SystemPrompts.deepResearchSynthesis(topic),
        userPrompt = buildString {
            append("User research request: ")
            append(topic)
            append("\n\n")
            if (attachment != null) {
                append("Use the attached PDF as primary context and combine it with these numbered search results.\n\n")
            }
            append("Numbered search results:\n\n")
            append(formatSearchResultsForModel(sources))
        },
        attachment = attachment,
    )

    private fun ResearchRun.localAttachmentForOpenRouter(): OpenRouterService.LocalAttachment? {
        val uri = localAttachmentUri ?: return null
        if (!localAttachmentMimeType.equals("application/pdf", ignoreCase = true)) return null
        return OpenRouterService.LocalAttachment(uri, localAttachmentMimeType, localAttachmentName)
    }

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

        // Stable timeline step ids. They must not change between an interrupted run and its
        // resume, or the same stage would appear twice in the persisted timeline.
        internal const val STEP_PLAN = "plan"
        internal const val STEP_SYNTHESIZE = "synthesize"
        internal const val STEP_PROVIDER = "provider"

        internal fun searchStepId(index: Int): String = "search-$index"
        internal fun activityStepId(index: Int): String = "activity-$index"
        internal fun feedStepId(index: Int): String = "feed-$index"

        /** Keys either provider might carry its human-readable progress line under. */
        private val MESSAGE_KEYS = listOf("message", "msg", "text", "content", "description", "summary", "objective", "query")
        private val NESTED_KEYS = listOf("data", "progress_msg", "payload", "detail", "body")

        private val PARALLEL_TERMINAL = setOf("completed", "failed", "cancelled", "cancelling")
    }
}
