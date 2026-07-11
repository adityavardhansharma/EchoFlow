package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Executes and decodes one OpenRouter streaming turn. */
internal class OpenRouterStreamTransport(
    private val dynamicAdapter: JsonAdapter<Any>,
    private val streamChunkAdapter: JsonAdapter<OpenRouterService.OpenRouterStreamEvent>,
    private val requestFactory: (String, String) -> Request,
    private val pdfPluginEnabler: (MutableMap<String, Any>, Boolean) -> Unit,
    private val errorParser: (String) -> String?,
) {
    private fun buildHttpRequest(apiKey: String, payload: String) = requestFactory(apiKey, payload)
    private fun addPdfPluginIfNeeded(request: MutableMap<String, Any>, enabled: Boolean) = pdfPluginEnabler(request, enabled)
    private fun parseErrorMessage(body: String) = errorParser(body)

    private fun parseQueryArgument(argsJson: String): String? = runCatching {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("query") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parsePromptArgument(argsJson: String): String? = runCatching {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("prompt") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseUrlArgument(argsJson: String): String? = runCatching {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("url") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun parseTaskArgs(argsJson: String): Pair<String, String>? = runCatching {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        val name = (map?.get("task_name") as? String)?.takeIf { it.isNotBlank() }
        val description = (map?.get("task_description") as? String).orEmpty()
        name?.let { it to description }
    }.getOrNull()

    suspend fun streamCompletion(
        apiKey: String,
        model: String,
        payloadMessages: List<Map<String, Any>>,
        tools: List<Map<String, Any>>?,
        params: InferenceParams?,
        toolChoice: String? = null,
        echo: OpenRouterService.EchoContext? = null,
        agentWorkerModel: String? = null,
        pdfPluginEnabled: Boolean = false,
        httpClient: OkHttpClient,
        onChunk: suspend (StreamChunk) -> Unit
    ): OpenRouterService.TurnResult {
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to payloadMessages,
            "stream" to true,
            // Ask OpenRouter to stream reasoning tokens for reasoning-capable models. Models that
            // don't support it simply ignore this and never send a `reasoning` delta.
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true)
        )
        addPdfPluginIfNeeded(requestMap, pdfPluginEnabled)
        if (tools != null) {
            requestMap["tools"] = tools
        }
        // For Echo Adviser/Fusion the user made a deliberate, cost-heavy choice from the "+"
        // menu — so we force the tool to actually run rather than leaving it to the model.
        if (toolChoice != null) {
            requestMap["tool_choice"] = toolChoice
        }
        // The user's global cloud sampler settings. top_k / max_tokens are only sent when set
        // (0 means "leave it to the model"); temperature / top_p always apply.
        if (params != null) {
            requestMap["temperature"] = params.temperature
            requestMap["top_p"] = params.topP
            if (params.topK > 0) requestMap["top_k"] = params.topK
            if (params.maxTokens > 0) requestMap["max_tokens"] = params.maxTokens
        }
    
        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val request = buildHttpRequest(apiKey, jsonPayload)
    
        val contentBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        val toolCallBuilders = sortedMapOf<Int, OpenRouterService.ToolCallBuilder>()
        val collectedSources = mutableListOf<SearchSource>()
        val seenSourceUrls = mutableSetOf<String>()
        var finishReason: String? = null
        var lastAnnouncedQuery: String? = null
    
        // Echo Adviser / Fusion: announce the consult/deliberation the moment its tool call
        // appears, then resolve once the (beta, undocumented-shape) result is seen in the stream.
        var advisorAnnounced = false
        var advisorResolved = false
        var advisorPrompt: String? = null
        var fusionAnnounced = false
        var fusionResolved = false
    
        // Echo Agent: the orchestrator may delegate several tasks per turn, so these track
        // per-tool-call (announce) and per-task-name (resolve) rather than a single flag.
        val subagentAnnounced = mutableMapOf<Int, Boolean>()
        val subagentResolved = mutableSetOf<String>()
    
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorString = response.body?.string().orEmpty()
                val parsedErrorMsg = parseErrorMessage(errorString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized keys. Verify your OpenRouter API key."
                    404 -> "Model: \"$model\" is unavailable."
                    403 -> "Your OpenRouter balance might be empty."
                    else -> parsedErrorMsg ?: "HTTP ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }
    
            val body = response.body ?: throw Exception("Empty stream body received.")
            val reader = body.charStream().buffered()
            var line: String?
    
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (!currentLine.startsWith("data: ")) continue
                val dataPart = currentLine.substring(6).trim()
                if (dataPart == "[DONE]" || dataPart.startsWith("[DONE]")) break
    
                try {
                    val event = streamChunkAdapter.fromJson(dataPart)
                    val choice = event?.choices?.firstOrNull()
                    val delta = choice?.delta
    
                    // Reasoning tokens (different providers name the field differently).
                    val reasoning = delta?.reasoning ?: delta?.reasoning_content
                    if (!reasoning.isNullOrEmpty()) {
                        reasoningBuf.append(reasoning)
                        onChunk(StreamChunk.Reasoning(reasoning))
                    }
    
                    val content = delta?.content
                    if (!content.isNullOrEmpty()) {
                        contentBuf.append(content)
                        onChunk(StreamChunk.Content(content))
                    }
    
                    // Tool call deltas: fragments accumulate per index. Used both by the
                    // OpenRouter server tool (search runs server-side mid-stream) and by
                    // client function calling (we run the search between requests).
                    val toolCallDeltas = delta?.tool_calls
                    toolCallDeltas?.forEach { call ->
                        val index = call.index ?: 0
                        val builder = toolCallBuilders.getOrPut(index) { OpenRouterService.ToolCallBuilder() }
                        call.id?.let { builder.id = it }
                        call.type?.let { builder.type = it }
                        call.function?.name?.let { builder.name = it }
                        call.function?.arguments?.let { builder.args.append(it) }
    
                        if (!builder.announced && builder.looksLikeWebSearch()) {
                            val query = parseQueryArgument(builder.args.toString())
                            if (query != null) {
                                builder.announced = true
                                lastAnnouncedQuery = query
                                onChunk(StreamChunk.SearchStarted(query))
                            }
                        }
                        // web_fetch reads a specific URL: surface it on the same search timeline
                        // as a one-URL "search" so citations and the activity card just work.
                        if (!builder.announced && builder.looksLikeWebFetch()) {
                            val url = parseUrlArgument(builder.args.toString())
                            if (url != null) {
                                builder.announced = true
                                lastAnnouncedQuery = url
                                onChunk(StreamChunk.SearchStarted(url))
                            }
                        }
                        if (echo?.advisorName != null && !advisorAnnounced && builder.looksLikeAdvisor()) {
                            val prompt = parsePromptArgument(builder.args.toString())
                            if (prompt != null) {
                                advisorAnnounced = true
                                advisorPrompt = prompt
                                onChunk(StreamChunk.AdvisorStarted(echo.advisorName, echo.advisorModel.orEmpty(), prompt))
                            }
                        }
                        if (echo?.fusionPanelName != null && !fusionAnnounced && builder.looksLikeFusion()) {
                            fusionAnnounced = true
                            onChunk(StreamChunk.FusionStarted(echo.fusionPanelName, echo.fusionModels))
                        }
                        // Echo Agent delegation: announce the moment the task brief parses, then
                        // the worker runs server-side and its outcome arrives as a tool result.
                        if (agentWorkerModel != null && subagentAnnounced[index] != true && builder.looksLikeSubagent()) {
                            parseTaskArgs(builder.args.toString())?.let { (name, desc) ->
                                subagentAnnounced[index] = true
                                onChunk(StreamChunk.SubagentStarted(name, desc, agentWorkerModel))
                            }
                        }
                    }
    
                    // url_citation annotations may arrive on the delta or on a message object.
                    val annotations = delta?.annotations ?: choice?.message?.annotations
                    if (annotations != null) {
                        val fresh = mutableListOf<SearchSource>()
                        annotations.forEach { ann ->
                            if (ann.type != "url_citation") return@forEach
                            val cite = ann.url_citation ?: return@forEach
                            val url = cite.url ?: return@forEach
                            if (!seenSourceUrls.add(url)) return@forEach
                            val source = SearchSource(
                                title = cite.title.orEmpty().ifBlank { url },
                                url = url,
                                snippet = cite.content
                            )
                            collectedSources.add(source)
                            fresh.add(source)
                        }
                        if (fresh.isNotEmpty()) {
                            onChunk(StreamChunk.SearchSources(lastAnnouncedQuery.orEmpty(), fresh))
                        }
                    }
    
                    // Echo Adviser/Fusion results arrive as a tool result somewhere in the chunk
                    // tree. The exact shape is beta/undocumented, so we walk the whole chunk
                    // defensively for the signature keys and degrade to "consulted, no body" if
                    // nothing parses — never a crash. (Verify shapes against a live key.)
                    val needsDynamicScan = echo != null || agentWorkerModel != null
                    val map = if (needsDynamicScan) dynamicAdapter.fromJson(dataPart) as? Map<*, *> else null
                    if (echo != null && map != null) {
                        if (echo.advisorName != null && !advisorResolved) {
                            OpenRouterEchoDecoder.scanForAdvisorResult(map)?.let { (advisorModel, advice) ->
                                advisorResolved = true
                                onChunk(
                                    StreamChunk.AdvisorResolved(
                                        AdvisorAdvice(
                                            advisorName = echo.advisorName,
                                            advisorModel = advisorModel ?: echo.advisorModel.orEmpty(),
                                            prompt = advisorPrompt.orEmpty(),
                                            advice = advice,
                                        )
                                    )
                                )
                            }
                        }
                        if (echo.fusionPanelName != null && !fusionResolved) {
                            OpenRouterEchoDecoder.scanForFusionResult(map)?.let { analysis ->
                                fusionResolved = true
                                onChunk(
                                    StreamChunk.FusionResolved(
                                        analysis.copy(
                                            panelName = echo.fusionPanelName,
                                            models = echo.fusionModels.ifEmpty { analysis.models },
                                        )
                                    )
                                )
                            }
                        }
                    }
    
                    // Echo Agent: a worker delegation finished somewhere in this chunk. Emit each
                    // distinct result once (keyed by task_name) so its card flips from running.
                    if (agentWorkerModel != null && map != null) {
                        val found = mutableListOf<SubagentResult>()
                        OpenRouterEchoDecoder.scanForSubagentResults(map, out = found)
                        found.forEach { r ->
                            if (subagentResolved.add(r.taskName)) {
                                onChunk(StreamChunk.SubagentResolved(r.copy(workerModel = r.workerModel.ifBlank { agentWorkerModel })))
                            }
                        }
                    }
    
                    choice?.finish_reason?.let { finishReason = it }
                } catch (e: Exception) {
                    // Resilient inline SSE fail ignores
                }
            }
        }
    
        val completedCalls = toolCallBuilders.entries.mapNotNull { (index, builder) ->
            val name = builder.name ?: builder.type ?: return@mapNotNull null
            OpenRouterService.CompletedToolCall(
                id = builder.id ?: "call_$index",
                name = name,
                arguments = builder.args.toString().ifBlank { "{}" },
                announced = builder.announced
            )
        }
    
        return OpenRouterService.TurnResult(
            content = contentBuf.toString(),
            reasoning = reasoningBuf.toString(),
            toolCalls = completedCalls,
            finishReason = finishReason,
            sources = collectedSources
        )
    }
    
    /**
     * Streaming completion. With [serverWebSearch] the OpenRouter `openrouter:web_search`
     * server tool is attached: the model can decide to search the web zero, one, or many
     * times during the answer; searches execute on OpenRouter's side and surface here as
     * SearchStarted/SearchSources chunks.
     */
}
