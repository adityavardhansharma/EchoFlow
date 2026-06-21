package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

class OpenRouterService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    // Echo Adviser/Fusion run a whole multi-model loop server-side in one (non-streaming)
    // request, which can take well over a minute — they get a client with long timeouts.
    private val echoClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(360, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dynamicAdapter = moshi.adapter(Any::class.java)
    private val streamChunkAdapter = moshi.adapter(OpenRouterStreamEvent::class.java)

    data class LocalAttachment(
        val uri: String,
        val mimeType: String?,
        val name: String?,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterStreamEvent(
        val choices: List<OpenRouterStreamChoice>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterStreamChoice(
        val delta: OpenRouterStreamDelta? = null,
        val message: OpenRouterStreamMessage? = null,
        val finish_reason: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterStreamDelta(
        val content: String? = null,
        val reasoning: String? = null,
        val reasoning_content: String? = null,
        val tool_calls: List<OpenRouterToolCallDelta>? = null,
        val annotations: List<OpenRouterAnnotation>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterStreamMessage(
        val annotations: List<OpenRouterAnnotation>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterToolCallDelta(
        val index: Int? = null,
        val id: String? = null,
        val type: String? = null,
        val function: OpenRouterFunctionDelta? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterFunctionDelta(
        val name: String? = null,
        val arguments: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterAnnotation(
        val type: String? = null,
        val url_citation: OpenRouterUrlCitation? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterUrlCitation(
        val title: String? = null,
        val url: String? = null,
        val content: String? = null,
    )

    /**
     * Converts a local attachment URI to standard raw Base64 string.
     */
    private fun getBase64FromUri(uriString: String?): String? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isPdfMime(mime: String?): Boolean = mime.equals("application/pdf", ignoreCase = true)

    private fun pdfPlugins(): List<Map<String, Any>> = listOf(
        mapOf(
            "id" to "file-parser",
            "pdf" to mapOf("engine" to "cloudflare-ai"),
        )
    )

    private fun historyHasPdfAttachment(history: List<ChatMessage>): Boolean =
        history.any { it.role == "user" && it.localAttachmentUri != null && isPdfMime(it.localAttachmentMimeType) }

    private fun addPdfPluginIfNeeded(requestMap: MutableMap<String, Any>, hasPdf: Boolean) {
        if (hasPdf) requestMap["plugins"] = pdfPlugins()
    }

    /**
     * Transforms database messages into OpenRouter compliant multi-modal format structure.
     * The system prompt (when present) is prepended for the request only — never persisted.
     */
    private fun buildMessagesPayload(history: List<ChatMessage>, systemPrompt: String? = null): MutableList<Map<String, Any>> {
        val payload = mutableListOf<Map<String, Any>>()
        if (!systemPrompt.isNullOrBlank()) {
            payload.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        history.forEach { msg ->
            val base64 = getBase64FromUri(msg.localAttachmentUri)
            payload.add(
                if (base64 != null && msg.role == "user") {
                    val mime = msg.localAttachmentMimeType ?: "image/jpeg"
                    val attachmentPart = if (isPdfMime(mime)) {
                        mapOf(
                            "type" to "file",
                            "file" to mapOf(
                                "filename" to (msg.localAttachmentName?.takeIf { it.isNotBlank() } ?: "document.pdf"),
                                "file_data" to "data:application/pdf;base64,$base64",
                            )
                        )
                    } else {
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to "data:$mime;base64,$base64")
                        )
                    }
                    mapOf(
                        "role" to msg.role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to msg.content),
                            attachmentPart
                        )
                    )
                } else {
                    mapOf(
                        "role" to msg.role,
                        "content" to msg.content
                    )
                }
            )
        }
        return payload
    }

    /**
     * Parse errors returned from OpenRouter payload
     */
    private fun parseErrorMessage(errJson: String): String? {
        return try {
            val map = dynamicAdapter.fromJson(errJson) as? Map<*, *>
            val errorMap = map?.get("error") as? Map<*, *>
            val message = errorMap?.get("message") as? String
            message
        } catch (e: Exception) {
            null
        }
    }

    private fun buildHttpRequest(apiKey: String, jsonPayload: String): Request {
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://localhost")
            .addHeader("X-Title", "EchoFlow")
            .post(requestBody)
            .build()
    }

    /**
     * Run standard non-streaming api completion (used for title generation).
     */
    suspend fun sendChatMessage(apiKey: String, model: String, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history),
            "stream" to false
        )
        addPdfPluginIfNeeded(requestMap, historyHasPdfAttachment(history))

        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val request = buildHttpRequest(apiKey, jsonPayload)

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedErrorMsg = parseErrorMessage(responseString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized keys. Verify your OpenRouter API key in Settings."
                    404 -> "Model: \"$model\" is unavailable."
                    403 -> "Action forbidden. Your OpenRouter credit might be depleted."
                    else -> parsedErrorMsg ?: "HTTP code ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }

            try {
                val responseMap = dynamicAdapter.fromJson(responseString) as? Map<*, *>
                val choices = responseMap?.get("choices") as? List<*>
                val choice = choices?.firstOrNull() as? Map<*, *>
                val message = choice?.get("message") as? Map<*, *>
                val content = message?.get("content") as? String
                content ?: throw Exception("No Response content received.")
            } catch (e: Exception) {
                throw Exception("Response Parsing failure: ${e.message}")
            }
        }
    }

    /**
     * Single non-streaming completion from a system + user prompt. Used by Deep Research's
     * planner and synthesis stages, which need a whole answer at once rather than a stream.
     */
    suspend fun complete(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        attachment: LocalAttachment? = null,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }
        val promptHistory = listOf(
            ChatMessage(
                id = "completion_user",
                chatId = "completion",
                role = "user",
                content = userPrompt,
                createdAt = System.currentTimeMillis(),
                localAttachmentUri = attachment?.uri,
                localAttachmentMimeType = attachment?.mimeType,
                localAttachmentName = attachment?.name,
            )
        )
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(promptHistory, systemPrompt),
            "stream" to false
        )
        addPdfPluginIfNeeded(requestMap, attachment != null && isPdfMime(attachment.mimeType))
        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val request = buildHttpRequest(apiKey, jsonPayload)

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedErrorMsg = parseErrorMessage(responseString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized — verify your OpenRouter API key in Settings."
                    404 -> "Model \"$model\" is unavailable."
                    403 -> "Action forbidden — your OpenRouter credit might be depleted."
                    else -> parsedErrorMsg ?: "HTTP ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }
            val responseMap = dynamicAdapter.fromJson(responseString) as? Map<*, *>
            val choices = responseMap?.get("choices") as? List<*>
            val choice = choices?.firstOrNull() as? Map<*, *>
            val message = choice?.get("message") as? Map<*, *>
            (message?.get("content") as? String) ?: throw Exception("No response content received.")
        }
    }

    // ---------------------------------------------------------------------------------
    // Streaming internals
    // ---------------------------------------------------------------------------------

    private data class CompletedToolCall(
        val id: String,
        val name: String,
        val arguments: String,
        /** True when SearchStarted was already emitted for this call during arg streaming. */
        val announced: Boolean
    )

    private class ToolCallBuilder {
        var id: String? = null
        var type: String? = null
        var name: String? = null
        val args = StringBuilder()
        var announced = false

        private fun mentions(token: String): Boolean =
            (type?.contains(token) == true) || (name?.contains(token) == true)

        fun looksLikeWebSearch(): Boolean = mentions("web_search")
        fun looksLikeWebFetch(): Boolean = mentions("web_fetch")
        fun looksLikeAdvisor(): Boolean = mentions("advisor")
        fun looksLikeFusion(): Boolean = mentions("fusion")
        fun looksLikeSubagent(): Boolean = mentions("subagent")
    }

    /**
     * Labels for the active Echo Adviser/Fusion server tool so the stream can tag its chunks
     * with the user-facing profile/panel names (the raw stream only carries model ids).
     */
    data class EchoContext(
        val advisorName: String? = null,
        val advisorModel: String? = null,
        val fusionPanelName: String? = null,
        val fusionModels: List<String> = emptyList(),
    )

    private data class TurnResult(
        val content: String,
        val reasoning: String,
        val toolCalls: List<CompletedToolCall>,
        val finishReason: String?,
        val sources: List<SearchSource>
    )

    private fun parseQueryArgument(argsJson: String): String? = try {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("query") as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun parsePromptArgument(argsJson: String): String? = try {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("prompt") as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun parseUrlArgument(argsJson: String): String? = try {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        (map?.get("url") as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /** Parse a subagent tool call's arguments `{task_name, task_description}`. */
    private fun parseTaskArgs(argsJson: String): Pair<String, String>? = try {
        val map = dynamicAdapter.fromJson(argsJson) as? Map<*, *>
        val name = (map?.get("task_name") as? String)?.takeIf { it.isNotBlank() }
        val desc = (map?.get("task_description") as? String).orEmpty()
        if (name != null) name to desc else null
    } catch (e: Exception) {
        null
    }

    /** Parse a string only if it looks like a JSON object/array (some tool results are stringified). */
    private fun parseMaybeJson(s: String): Any? {
        val t = s.trim()
        if (!(t.startsWith("{") || t.startsWith("["))) return null
        return try { dynamicAdapter.fromJson(t) } catch (e: Exception) { null }
    }

    /** Walks an SSE chunk for an advisor result `{model?, advice}`. Returns (advisorModel, advice). */
    private fun scanForAdvisorResult(node: Any?, depth: Int = 0): Pair<String?, String>? {
        if (depth > 8) return null
        when (node) {
            is Map<*, *> -> {
                (node["advice"] as? String)?.takeIf { it.isNotBlank() }?.let { advice ->
                    return (node["model"] as? String) to advice
                }
                (node["content"] as? String)?.let { c ->
                    parseMaybeJson(c)?.let { scanForAdvisorResult(it, depth + 1)?.let { r -> return r } }
                }
                for (v in node.values) scanForAdvisorResult(v, depth + 1)?.let { return it }
            }
            is List<*> -> for (v in node) scanForAdvisorResult(v, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Walks an SSE chunk for subagent results `{status, model, task_name, outcome}` (or an
     * `{status:"error", task_name, error}`). Appends every distinct one found to [out]; the
     * caller dedupes by task_name. The shape is documented (unlike advisor/fusion), so this is
     * a straightforward keyed scan rather than a guess.
     */
    private fun scanForSubagentResults(node: Any?, depth: Int = 0, out: MutableList<SubagentResult>) {
        if (depth > 8) return
        when (node) {
            is Map<*, *> -> {
                val taskName = (node["task_name"] as? String)?.takeIf { it.isNotBlank() }
                val hasOutcome = node.containsKey("outcome")
                val errorMsg = (node["error"] as? String)?.takeIf { it.isNotBlank() }
                if (taskName != null && (hasOutcome || errorMsg != null)) {
                    out.add(
                        SubagentResult(
                            taskName = taskName,
                            workerModel = (node["model"] as? String).orEmpty(),
                            outcome = (node["outcome"] as? String).orEmpty(),
                            error = errorMsg?.takeIf { (node["status"] as? String) == "error" || !hasOutcome },
                        )
                    )
                }
                (node["content"] as? String)?.let { c -> parseMaybeJson(c)?.let { scanForSubagentResults(it, depth + 1, out) } }
                for (v in node.values) scanForSubagentResults(v, depth + 1, out)
            }
            is List<*> -> for (v in node) scanForSubagentResults(v, depth + 1, out)
        }
    }

    private fun isFusionResponseList(list: List<*>?): Boolean =
        list != null && list.isNotEmpty() && list.all {
            (it as? Map<*, *>)?.containsKey("content") == true && (it as? Map<*, *>)?.containsKey("model") == true
        }

    /** Walks an SSE chunk for a fusion result `{analysis?, responses?, failed_models?}`. */
    private fun scanForFusionResult(node: Any?, depth: Int = 0): FusionAnalysis? {
        if (depth > 8) return null
        when (node) {
            is Map<*, *> -> {
                val analysisObj = node["analysis"] as? Map<*, *>
                val responses = node["responses"] as? List<*>
                if (analysisObj != null || isFusionResponseList(responses)) {
                    return buildFusionAnalysis(node)
                }
                (node["content"] as? String)?.let { c ->
                    parseMaybeJson(c)?.let { scanForFusionResult(it, depth + 1)?.let { r -> return r } }
                }
                for (v in node.values) scanForFusionResult(v, depth + 1)?.let { return it }
            }
            is List<*> -> for (v in node) scanForFusionResult(v, depth + 1)?.let { return it }
        }
        return null
    }

    private fun buildFusionAnalysis(result: Map<*, *>): FusionAnalysis {
        val analysis = result["analysis"] as? Map<*, *>
        fun strList(key: String): List<String> =
            (analysis?.get(key) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val contradictions = (analysis?.get("contradictions") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val topic = m["topic"] as? String ?: return@mapNotNull null
            val stances = (m["stances"] as? List<*>)?.map { it.toString() } ?: emptyList()
            FusionContradiction(topic, stances)
        } ?: emptyList()

        val partial = (analysis?.get("partial_coverage") as? List<*>)?.mapNotNull { raw ->
            when (raw) {
                is Map<*, *> -> raw["point"] as? String
                is String -> raw
                else -> null
            }
        } ?: emptyList()

        val insights = (analysis?.get("unique_insights") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val insight = m["insight"] as? String ?: return@mapNotNull null
            FusionInsight((m["model"] as? String).orEmpty(), insight)
        } ?: emptyList()

        val responses = (result["responses"] as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val content = m["content"] as? String ?: return@mapNotNull null
            FusionResponse((m["model"] as? String).orEmpty(), content)
        } ?: emptyList()

        val failed = (result["failed_models"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        return FusionAnalysis(
            panelName = "",
            judgeModel = null,
            models = responses.map { it.model }.filter { it.isNotBlank() },
            consensus = strList("consensus"),
            contradictions = contradictions,
            partialCoverage = partial,
            uniqueInsights = insights,
            blindSpots = strList("blind_spots"),
            responses = responses,
            failedModels = failed,
        )
    }

    /**
     * Executes one streaming chat completion, emitting chunks as they arrive and returning
     * the accumulated turn. Handles plain content/reasoning deltas, OpenRouter server-tool
     * activity (`openrouter:web_search`), client function tool_calls deltas, and
     * url_citation annotations. All parsing is defensive: unknown shapes degrade to plain
     * text streaming, never a crash.
     */
    private suspend fun streamCompletion(
        apiKey: String,
        model: String,
        payloadMessages: List<Map<String, Any>>,
        tools: List<Map<String, Any>>?,
        params: InferenceParams?,
        toolChoice: String? = null,
        echo: EchoContext? = null,
        agentWorkerModel: String? = null,
        pdfPluginEnabled: Boolean = false,
        httpClient: OkHttpClient = client,
        onChunk: suspend (StreamChunk) -> Unit
    ): TurnResult {
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
        val toolCallBuilders = sortedMapOf<Int, ToolCallBuilder>()
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
                        val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
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
                            scanForAdvisorResult(map)?.let { (advisorModel, advice) ->
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
                            scanForFusionResult(map)?.let { analysis ->
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
                        scanForSubagentResults(map, out = found)
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
            CompletedToolCall(
                id = builder.id ?: "call_$index",
                name = name,
                arguments = builder.args.toString().ifBlank { "{}" },
                announced = builder.announced
            )
        }

        return TurnResult(
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
    fun sendChatMessageStream(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String? = null,
        serverWebSearch: Boolean = false,
        params: InferenceParams? = null
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val tools = if (serverWebSearch) openRouterWebTools() else null
        val hasPdf = historyHasPdfAttachment(history)

        streamCompletion(apiKey, model, buildMessagesPayload(history, systemPrompt), tools, params, pdfPluginEnabled = hasPdf) { emit(it) }
    }.flowOn(Dispatchers.IO)

    /**
     * The OpenRouter server-side web toolset: `web_search` (the model searches the web zero,
     * one or many times) plus `web_fetch` (the model pulls a specific URL's full content).
     * Both run on OpenRouter's side and surface here as SearchStarted/SearchSources chunks.
     */
    private fun openRouterWebTools(): List<Map<String, Any>> = listOf(
        mapOf(
            "type" to "openrouter:web_search",
            // max_total_results backs up the prompt's "at most 3 searches" rule:
            // 3 searches × 5 results caps what OpenRouter will return overall.
            "parameters" to mapOf(
                "max_results" to 5,
                "max_total_results" to 15
            )
        ),
        mapOf(
            "type" to "openrouter:web_fetch",
            "parameters" to mapOf(
                "engine" to "auto",
                "max_uses" to 3
            )
        )
    )

    /** Config for one Echo Agent turn: the worker (subagent) model and its tool-call budget. */
    data class AgentRequest(val workerModel: String, val workerModelName: String, val maxToolCalls: Int)

    /**
     * Echo Agents. The selected cloud model orchestrates a full toolbox: `openrouter:web_search`,
     * `openrouter:web_fetch`, and `openrouter:subagent` (a cheaper agent it delegates self-contained
     * tasks to). tool_choice is auto — the model decides whether/how often to delegate.
     *
     * "Option B" hybrid: the request IS streamed, but only so the UI can react the instant a
     * delegation happens (SubagentStarted/Resolved + web-search activity ping straight through).
     * The reasoning and answer deltas are swallowed and accumulate in the [TurnResult], then are
     * replayed once the run finishes — reasoning first, then the answer as a paced reveal — so the
     * answer reads as non-streamed and reasoning can never trail it. A reconcile pass guarantees a
     * card for every delegation even if its start/result never surfaced as a delta (the subagent
     * SSE shape is beta). Uses the long-timeout [echoClient] because a delegation can stall the
     * stream while the agent works.
     */
    fun sendWithAgentTools(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        agent: AgentRequest,
        params: InferenceParams? = null,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }
        val tools = mutableListOf<Map<String, Any>>()
        tools.addAll(openRouterWebTools()) // the main model's own web_search + web_fetch
        val subagentParams = mutableMapOf<String, Any>(
            "model" to agent.workerModel,
            "max_tool_calls" to agent.maxToolCalls.coerceIn(1, 25),
            // The agent gets its own web toolbox so it can research while doing the task.
            "tools" to listOf(
                mapOf("type" to "openrouter:web_search"),
                mapOf("type" to "openrouter:web_fetch"),
            ),
        )
        tools.add(mapOf("type" to "openrouter:subagent", "parameters" to subagentParams))

        val hasPdf = historyHasPdfAttachment(history)

        // Option B: we DO stream the request, but only to catch the live "an agent was called"
        // event the instant the delegate tool-call appears — that pings SubagentStarted/Resolved
        // straight through to the UI. Reasoning and answer chunks are swallowed here (they
        // accumulate in the returned TurnResult) and replayed AFTER the run in a controlled order,
        // so the answer reads as non-streamed and reasoning can never trail it. tool_choice stays
        // auto — the main model decides whether/how often to delegate.
        val announced = mutableSetOf<String>()
        val resolvedNames = mutableSetOf<String>()
        val result = streamCompletion(
            apiKey = apiKey,
            model = model,
            payloadMessages = buildMessagesPayload(history, systemPrompt),
            tools = tools,
            params = params,
            agentWorkerModel = agent.workerModel,
            pdfPluginEnabled = hasPdf,
            httpClient = echoClient,
        ) { chunk ->
            when (chunk) {
                is StreamChunk.SubagentStarted -> { announced.add(chunk.taskName); emit(chunk) }
                is StreamChunk.SubagentResolved -> { resolvedNames.add(chunk.result.taskName); emit(chunk) }
                is StreamChunk.SearchStarted, is StreamChunk.SearchSources, is StreamChunk.StatusNote -> emit(chunk)
                else -> {} // swallow reasoning + content; replayed below in controlled order
            }
        }

        // Reconcile: make sure every delegation has a card even if its start/result never surfaced
        // as a stream delta (the subagent SSE shape is beta). The completed tool-calls carry the
        // task briefs; an outcome that didn't stream shows as "ran, no result text".
        result.toolCalls.filter { it.name.contains("subagent") }.forEach { call ->
            val (name, desc) = parseTaskArgs(call.arguments) ?: ("Task" to "")
            if (announced.add(name)) emit(StreamChunk.SubagentStarted(name, desc, agent.workerModel))
            if (resolvedNames.add(name)) {
                emit(StreamChunk.SubagentResolved(SubagentResult(name, desc, agent.workerModel, outcome = "")))
            }
        }

        // Now replay reasoning (so it sits above the answer), then the answer as a paced reveal.
        if (result.reasoning.isNotBlank()) emit(StreamChunk.Reasoning(result.reasoning))
        if (result.content.isNotBlank()) {
            val answer = result.content
            val step = 18
            var idx = 0
            while (idx < answer.length) {
                val end = (idx + step).coerceAtMost(answer.length)
                emit(StreamChunk.Content(answer.substring(idx, end)))
                idx = end
                delay(14)
            }
        } else if (announced.isEmpty()) {
            throw Exception("No response content received.")
        }
    }.flowOn(Dispatchers.IO)

    /** Config for one Echo Adviser consult: which profile (display name) and advisor model. */
    data class AdvisorRequest(val name: String, val model: String)

    /** Config for one Echo Fusion deliberation: the panel (display name + roster) and judge. */
    data class FusionRequest(val panelName: String, val models: List<String>, val judge: String?)

    /**
     * Echo Adviser / Echo Fusion. Both are OpenRouter-only server tools forced via
     * `tool_choice: "required"` (the user opted into the cost-heavy mode from "+"). Unlike
     * normal chat these use a **non-streaming** request: the server-tool result (the advice /
     * the panel analysis) is only delivered in the documented final response, and a single
     * round-trip lets us lay the timeline out deterministically — Echo card → reasoning →
     * answer — so reasoning can never trail the answer the way the streamed loop did.
     *
     * We still drive a live experience: the consult/deliberation card is announced immediately
     * (so the distinct "Echo is running" UI shows during the wait), then the structured result
     * populates it, then the final answer is revealed with a paced typewriter so it reads as a
     * live stream. The model resolves as: advisor → the answering model; fusion → the judge.
     */
    fun sendWithEchoTools(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        advisor: AdvisorRequest? = null,
        fusion: FusionRequest? = null,
        params: InferenceParams? = null,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val tools = mutableListOf<Map<String, Any>>()

        if (advisor != null) {
            val advisorParams = mutableMapOf<String, Any>(
                "model" to advisor.model,
                "name" to advisor.name,
                "forward_transcript" to true,
                "tools" to listOf(
                    mapOf("type" to "openrouter:web_search"),
                    mapOf("type" to "openrouter:web_fetch"),
                ),
            )
            tools.add(mapOf("type" to "openrouter:advisor", "parameters" to advisorParams))
            tools.addAll(openRouterWebTools())
            // Announce the consult up-front so the distinct Adviser card shows during the wait.
            emit(StreamChunk.AdvisorStarted(advisor.name, advisor.model, ""))
        }

        if (fusion != null) {
            val fusionParams = mutableMapOf<String, Any>(
                "analysis_models" to fusion.models,
            )
            fusion.judge?.takeIf { it.isNotBlank() }?.let { fusionParams["model"] = it }
            tools.add(mapOf("type" to "openrouter:fusion", "parameters" to fusionParams))
            emit(StreamChunk.FusionStarted(fusion.panelName, fusion.models))
        }

        val hasPdf = historyHasPdfAttachment(history)
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history, systemPrompt),
            "stream" to false,
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true),
            "tools" to tools,
            "tool_choice" to "required",
        )
        addPdfPluginIfNeeded(requestMap, hasPdf)
        if (params != null) {
            requestMap["temperature"] = params.temperature
            requestMap["top_p"] = params.topP
            if (params.topK > 0) requestMap["top_k"] = params.topK
            if (params.maxTokens > 0) requestMap["max_tokens"] = params.maxTokens
        }

        val response = postForResponse(apiKey, requestMap)
        val choice = (response["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val message = choice?.get("message") as? Map<*, *>
        val answer = (message?.get("content") as? String).orEmpty().trim()
        val reasoning = ((message?.get("reasoning") as? String)
            ?: (message?.get("reasoning_content") as? String)).orEmpty().trim()

        // Resolve the server-tool result. Scan the whole response defensively because the exact
        // location (a `role:"tool"` message, a tool_calls entry, or inline) is beta/undocumented.
        if (advisor != null) {
            val asked = findAskedPrompt(message)
            val result = scanForAdvisorResult(response)
            emit(
                StreamChunk.AdvisorResolved(
                    AdvisorAdvice(
                        advisorName = advisor.name,
                        advisorModel = result?.first ?: advisor.model,
                        prompt = asked,
                        advice = result?.second.orEmpty(),
                    )
                )
            )
        }
        if (fusion != null) {
            val parsed = scanForFusionResult(response)
            val analysis = (parsed ?: FusionAnalysis(panelName = fusion.panelName, judgeModel = fusion.judge, models = fusion.models))
                .copy(
                    panelName = fusion.panelName,
                    judgeModel = fusion.judge ?: parsed?.judgeModel,
                    models = fusion.models.ifEmpty { parsed?.models ?: emptyList() },
                )
            emit(StreamChunk.FusionResolved(analysis))
        }

        // Any url_citation annotations the answer carries become a source card.
        (message?.get("annotations") as? List<*>)?.let { annotations ->
            val sources = mutableListOf<SearchSource>()
            val seen = mutableSetOf<String>()
            annotations.forEach { rawAnn ->
                val ann = rawAnn as? Map<*, *> ?: return@forEach
                if ((ann["type"] as? String) != "url_citation") return@forEach
                val cite = ann["url_citation"] as? Map<*, *> ?: return@forEach
                val url = cite["url"] as? String ?: return@forEach
                if (!seen.add(url)) return@forEach
                sources.add(SearchSource((cite["title"] as? String).orEmpty().ifBlank { url }, url, cite["content"] as? String))
            }
            if (sources.isNotEmpty()) emit(StreamChunk.SearchSources("", sources))
        }

        // Reasoning first (so it sits above the answer), then reveal the answer as a gentle live
        // stream — a non-streaming response with a streamed feel.
        if (reasoning.isNotBlank()) emit(StreamChunk.Reasoning(reasoning))
        if (answer.isNotBlank()) {
            val step = 18
            var idx = 0
            while (idx < answer.length) {
                val end = (idx + step).coerceAtMost(answer.length)
                emit(StreamChunk.Content(answer.substring(idx, end)))
                idx = end
                delay(14)
            }
        } else if (advisor == null && fusion == null) {
            throw Exception("No response content received.")
        }
    }.flowOn(Dispatchers.IO)

    /** One blocking non-streaming POST for the Echo modes; returns the parsed response map. */
    private fun postForResponse(apiKey: String, requestMap: Map<String, Any>): Map<*, *> {
        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val request = buildHttpRequest(apiKey, jsonPayload)
        echoClient.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedErrorMsg = parseErrorMessage(responseString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized keys. Verify your OpenRouter API key."
                    402, 403 -> "Your OpenRouter balance might be too low for this mode."
                    404 -> "Model is unavailable."
                    else -> parsedErrorMsg ?: "HTTP ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }
            return dynamicAdapter.fromJson(responseString) as? Map<*, *>
                ?: throw Exception("OpenRouter returned an unreadable response.")
        }
    }

    /** Each subagent delegation's (task_name, task_description), from the assistant's tool_calls. */
    private fun extractSubagentDelegations(message: Map<*, *>?): List<Pair<String, String>> {
        val calls = message?.get("tool_calls") as? List<*> ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (raw in calls) {
            val m = raw as? Map<*, *> ?: continue
            val type = (m["type"] as? String).orEmpty()
            val fn = m["function"] as? Map<*, *>
            val name = (fn?.get("name") as? String).orEmpty()
            val looksAgent = type.contains("subagent") || name.contains("subagent") || name.contains("delegate")
            if (!looksAgent) continue
            val args = fn?.get("arguments") as? String ?: continue
            parseTaskArgs(args)?.let { out.add(it) }
        }
        return out
    }

    /** The prompt the answering model sent its advisor, from the assistant message's tool_calls. */
    private fun findAskedPrompt(message: Map<*, *>?): String {
        val calls = message?.get("tool_calls") as? List<*> ?: return ""
        for (raw in calls) {
            val fn = (raw as? Map<*, *>)?.get("function") as? Map<*, *> ?: continue
            val args = fn["arguments"] as? String ?: continue
            parsePromptArgument(args)?.let { return it }
        }
        return ""
    }

    /**
     * Streaming completion with a client-executed `web_search` function tool (Exa, Parallel
     * or Firecrawl via [runSearch]). Implements an agentic loop: the model may request
     * searches across several rounds; each round's results are appended as tool messages
     * and the conversation is re-sent until the model produces a final answer.
     */
    fun sendWithClientSearch(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams? = null,
        runSearch: suspend (String) -> List<SearchSource>
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val hasPdf = historyHasPdfAttachment(history)
        val messages: MutableList<Map<String, Any>> = buildMessagesPayload(history, systemPrompt)
        val toolSpec = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "web_search",
                    "description" to "Search the web for current, factual information. " +
                        "Returns a numbered list of results with titles, URLs and content snippets. " +
                        "Call again with a refined query if the first results are insufficient.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "The search query"
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )

        // Mirrors the "HARD LIMIT: at most 3 searches per answer" rule in SystemPrompts —
        // the prompt asks nicely, this enforces it.
        val maxSearches = 3
        var searchesUsed = 0
        val maxRounds = 5
        for (round in 0 until maxRounds) {
            // Once the budget is spent (or on the last round) drop the tool so the model
            // is forced to answer with what it has.
            val tools = if (searchesUsed >= maxSearches || round == maxRounds - 1) null else toolSpec
            val result = streamCompletion(apiKey, model, messages, tools, params, pdfPluginEnabled = hasPdf) { emit(it) }

            val searchCalls = result.toolCalls
            if (result.finishReason != "tool_calls" || searchCalls.isEmpty()) break

            val assistantMsg = mutableMapOf<String, Any>(
                "role" to "assistant",
                "tool_calls" to searchCalls.map { call ->
                    mapOf(
                        "id" to call.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to call.name,
                            "arguments" to call.arguments
                        )
                    )
                }
            )
            if (result.content.isNotBlank()) assistantMsg["content"] = result.content
            messages.add(assistantMsg)

            for (call in searchCalls) {
                val query = parseQueryArgument(call.arguments)
                if (query == null) {
                    messages.add(toolResultMessage(call.id, "Invalid tool arguments. Call web_search with {\"query\": \"...\"}."))
                    continue
                }
                if (searchesUsed >= maxSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached (3 per answer)"))
                    messages.add(toolResultMessage(call.id, "Search limit reached: at most 3 searches per answer. Do not search again — answer now with the information you already have."))
                    continue
                }
                searchesUsed++
                if (!call.announced) emit(StreamChunk.SearchStarted(query))
                val sources = try {
                    runSearch(query)
                } catch (e: Exception) {
                    emit(StreamChunk.SearchSources(query, emptyList()))
                    emit(StreamChunk.StatusNote("Search failed: ${e.message}"))
                    messages.add(toolResultMessage(call.id, "Search failed: ${e.message}. Answer from your own knowledge and tell the user you could not verify."))
                    continue
                }
                emit(StreamChunk.SearchSources(query, sources))
                messages.add(toolResultMessage(call.id, formatSearchResultsForModel(sources)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun toolResultMessage(toolCallId: String, content: String): Map<String, Any> = mapOf(
        "role" to "tool",
        "tool_call_id" to toolCallId,
        "content" to content
    )

    /**
     * Title generation request handler.
     */
    suspend fun generateTitle(apiKey: String, model: String, firstUserMessage: String): String {
        return try {
            val titleRule = "Generate a short title of 3 to 6 words for this chat based on the user message. " +
                    "Return ONLY the title, with NO explanation, NO headers, NO quotes, and NO markdown. " +
                    "User message: $firstUserMessage"

            val promptHistory = listOf(
                ChatMessage(
                    id = "title_gen",
                    chatId = "temp",
                    role = "user",
                    content = titleRule,
                    createdAt = System.currentTimeMillis()
                )
            )
            val rawTitle = sendChatMessage(apiKey, model, promptHistory).trim()
            // Clean simple quote wraps
            rawTitle.removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (e: Exception) {
            // Fallback: Use first few words of the user's message
            val words = firstUserMessage.split("\\s+".toRegex())
            val fallback = words.take(4).joinToString(" ") + if (words.size > 4) "..." else ""
            fallback
        }
    }
}
