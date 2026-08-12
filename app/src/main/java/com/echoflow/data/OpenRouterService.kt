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
    private val streamTransport by lazy {
        OpenRouterStreamTransport(
            dynamicAdapter = dynamicAdapter,
            streamChunkAdapter = streamChunkAdapter,
            requestFactory = ::buildHttpRequest,
            pdfPluginEnabler = ::addPdfPluginIfNeeded,
            errorParser = ::parseErrorMessage,
        )
    }

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
        val images: List<OpenRouterImagePayload>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class OpenRouterStreamMessage(
        val annotations: List<OpenRouterAnnotation>? = null,
        val images: List<OpenRouterImagePayload>? = null,
    )

    /**
     * One generated image on a delta or final message. The documented shape is
     * `{type: "image_url", image_url: {url: "data:image/png;base64,..."}}`; [url] covers a
     * flattened variant seen in beta payloads. (Verify against a live key in Android Studio.)
     */
    @JsonClass(generateAdapter = true)
    data class OpenRouterImagePayload(
        val type: String? = null,
        val image_url: OpenRouterImageUrl? = null,
        val url: String? = null,
    ) {
        val dataUrl: String? get() = image_url?.url ?: url
    }

    @JsonClass(generateAdapter = true)
    data class OpenRouterImageUrl(
        val url: String? = null,
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

    private fun isPdfMime(mime: String?): Boolean = OpenRouterPayloads.isPdf(mime)

    private fun historyHasPdfAttachment(history: List<ChatMessage>): Boolean = OpenRouterPayloads.historyHasPdf(history)

    private fun addPdfPluginIfNeeded(requestMap: MutableMap<String, Any>, hasPdf: Boolean) {
        OpenRouterPayloads.enablePdfPlugin(requestMap, hasPdf)
    }

    /**
     * Transforms database messages into OpenRouter compliant multi-modal format structure.
     * The system prompt (when present) is prepended for the request only — never persisted.
     */
    private fun buildMessagesPayload(history: List<ChatMessage>, systemPrompt: String? = null): MutableList<Map<String, Any>> {
        return OpenRouterPayloads.messages(history, systemPrompt, ::getBase64FromUri)
    }

    /**
     * Parse errors returned from OpenRouter payload
     */
    private fun parseErrorMessage(errJson: String): String? {
        return OpenRouterPayloads.errorMessage(errJson)
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

    internal data class CompletedToolCall(
        val id: String,
        val name: String,
        val arguments: String,
        /** True when SearchStarted was already emitted for this call during arg streaming. */
        val announced: Boolean
    )

    internal class ToolCallBuilder {
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

    internal data class TurnResult(
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
        extraBody: Map<String, Any>? = null,
        httpClient: OkHttpClient = client,
        onChunk: suspend (StreamChunk) -> Unit,
    ): TurnResult = streamTransport.streamCompletion(
        apiKey, model, payloadMessages, tools, params, toolChoice, echo,
        agentWorkerModel, pdfPluginEnabled, extraBody, httpClient, onChunk,
    )
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

    /**
     * Image generation / conversational editing via a multimodal-output model (Nano Banana
     * family). One streaming chat completion with `modalities: ["image","text"]`: history
     * rides along as text, and on edit turns [editImageDataUrl] (the chat's latest generated
     * image) is attached to the newest user message so the model revises it in place. Text
     * deltas stream as usual; the finished image surfaces as [StreamChunk.ImageGenerated].
     * Uses the long-timeout [echoClient] — generation regularly takes 15–30s.
     */
    fun sendImageGeneration(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        editImageDataUrl: String? = null,
        params: InferenceParams? = null,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }
        val messages = buildMessagesPayload(history, systemPrompt)
        if (editImageDataUrl != null) {
            OpenRouterPayloads.attachImageToLastUserMessage(messages, editImageDataUrl)
        }
        streamCompletion(
            apiKey = apiKey,
            model = model,
            payloadMessages = messages,
            tools = null,
            params = params,
            extraBody = mapOf("modalities" to listOf("image", "text")),
            httpClient = echoClient,
        ) { emit(it) }
    }.flowOn(Dispatchers.IO)

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
     * Echo Adviser / Echo Fusion. OpenRouter-only server tools. Non-streaming so the tool
     * result and final answer can be ordered deterministically (Echo card → reasoning → answer).
     *
     * **Fusion guarantee:** the user opted into a named panel. We force fusion with
     * `tool_choice: "required"`, retry once if no tool payload appears, and if the panel ran
     * but the model left no final text (e.g. after a capped second fusion call), we synthesize
     * the answer in a follow-up request **without** tools. A silent single-model reply without
     * deliberation is never treated as a successful fuse — the UI is told via
     * [FusionAnalysis.deliberationSkipped].
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

        // ── Fusion path (guaranteed single panel deliberation) ─────────────────────────
        if (fusion != null) {
            emit(StreamChunk.FusionStarted(fusion.panelName, fusion.models))
            val (analysis, answer, reasoning, annotations) = runFusionWithGuarantee(
                apiKey = apiKey,
                model = model,
                history = history,
                systemPrompt = systemPrompt,
                fusion = fusion,
                params = params,
            )
            emit(StreamChunk.FusionResolved(analysis))
            emitAnnotationsAsSearch(annotations)
            if (reasoning.isNotBlank()) emit(StreamChunk.Reasoning(reasoning))
            if (answer.isNotBlank()) {
                // May still be a single-model reply if deliberation was skipped — UI discloses that.
                emitPacedContent(answer)
            } else if (analysis.panelDidNotRun) {
                throw Exception(
                    "Echo Fusion did not run your panel. Try again — multi-model deliberation is required in this mode.",
                )
            } else if (!analysis.hasUsableDetail) {
                throw Exception("No response content received from Echo Fusion.")
            }
            return@flow
        }

        // ── Adviser path ───────────────────────────────────────────────────────────────
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
            emit(StreamChunk.AdvisorStarted(advisor.name, advisor.model, ""))
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
        applyInferenceParams(requestMap, params)

        val response = postForResponse(apiKey, requestMap)
        val choice = (response["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val message = choice?.get("message") as? Map<*, *>
        val answer = (message?.get("content") as? String).orEmpty().trim()
        val reasoning = ((message?.get("reasoning") as? String)
            ?: (message?.get("reasoning_content") as? String)).orEmpty().trim()

        if (advisor != null) {
            val asked = findAskedPrompt(message)
            val result = OpenRouterEchoDecoder.scanForAdvisorResult(response)
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

        emitAnnotationsAsSearch(message?.get("annotations") as? List<*>)
        if (reasoning.isNotBlank()) emit(StreamChunk.Reasoning(reasoning))
        if (answer.isNotBlank()) emitPacedContent(answer)
        else throw Exception("No response content received.")
    }.flowOn(Dispatchers.IO)

    /**
     * Force fusion at least once, recover a final answer if the outer model stalls after the
     * panel, and surface an honest skip when deliberation never ran.
     */
    private fun runFusionWithGuarantee(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        params: InferenceParams?,
    ): FusionRunOutcome {
        // Pass 1: force the fusion tool (user selected a panel — do not leave it to chance).
        var response = postForResponse(
            apiKey,
            buildFusionToolRequest(
                model = model,
                history = history,
                systemPrompt = systemPrompt,
                fusion = fusion,
                params = params,
                toolChoice = "required",
            ),
        )
        var parsed = preferredFusionFrom(response)

        // Pass 2: if required still produced no payload, retry once with an explicit system nudge.
        if (parsed == null) {
            response = postForResponse(
                apiKey,
                buildFusionToolRequest(
                    model = model,
                    history = history,
                    systemPrompt = systemPrompt + "\n\n" + FUSION_FORCE_NUDGE,
                    fusion = fusion,
                    params = params,
                    toolChoice = "required",
                ),
            )
            parsed = preferredFusionFrom(response)
        }

        val deliberationSkipped = parsed == null
        val analysis = (parsed ?: FusionAnalysis(
            panelName = fusion.panelName,
            judgeModel = fusion.judge,
            models = fusion.models,
            toolResultFound = false,
            deliberationSkipped = true,
        )).copy(
            panelName = fusion.panelName,
            judgeModel = fusion.judge ?: parsed?.judgeModel,
            models = fusion.models.ifEmpty { parsed?.models ?: emptyList() },
            toolResultFound = parsed != null,
            deliberationSkipped = deliberationSkipped,
        )

        var choice = (response["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        var message = choice?.get("message") as? Map<*, *>
        var answer = (message?.get("content") as? String).orEmpty().trim()
        var reasoning = ((message?.get("reasoning") as? String)
            ?: (message?.get("reasoning_content") as? String)).orEmpty().trim()
        var annotations = message?.get("annotations") as? List<*>

        // Pass 3: panel ran but no user-facing answer (common after a capped second fusion call).
        // Synthesize from the panel result with tools disabled so fusion cannot be invoked again.
        if (answer.isBlank() && analysis.hasUsableDetail) {
            val synth = postForResponse(
                apiKey,
                buildFusionAnswerRequest(
                    model = model,
                    history = history,
                    systemPrompt = systemPrompt,
                    fusion = fusion,
                    analysis = analysis,
                    params = params,
                ),
            )
            choice = (synth["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
            message = choice?.get("message") as? Map<*, *>
            answer = (message?.get("content") as? String).orEmpty().trim()
            reasoning = ((message?.get("reasoning") as? String)
                ?: (message?.get("reasoning_content") as? String)).orEmpty().ifBlank { reasoning }
            annotations = message?.get("annotations") as? List<*> ?: annotations
        }

        return FusionRunOutcome(analysis, answer, reasoning, annotations)
    }

    private data class FusionRunOutcome(
        val analysis: FusionAnalysis,
        val answer: String,
        val reasoning: String,
        val annotations: List<*>?,
    )

    private fun preferredFusionFrom(response: Map<*, *>): FusionAnalysis? =
        OpenRouterEchoDecoder.selectPreferredFusionResult(
            OpenRouterEchoDecoder.scanForAllFusionResults(response),
        )

    private fun buildFusionToolRequest(
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        params: InferenceParams?,
        toolChoice: Any,
    ): MutableMap<String, Any> {
        val fusionParams = mutableMapOf<String, Any>("analysis_models" to fusion.models)
        fusion.judge?.takeIf { it.isNotBlank() }?.let { fusionParams["model"] = it }
        val fusionPlugin = mutableMapOf<String, Any>(
            "id" to "fusion",
            "analysis_models" to fusion.models,
            "model" to (fusion.judge?.takeIf { it.isNotBlank() } ?: model),
        )
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history, systemPrompt),
            "stream" to false,
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true),
            "tools" to listOf(mapOf("type" to "openrouter:fusion", "parameters" to fusionParams)),
            "tool_choice" to toolChoice,
            // Avoid parallel multi-tool fan-out; fusion is the only tool we attach.
            "parallel_tool_calls" to false,
            "plugins" to listOf(fusionPlugin),
        )
        addPdfPluginIfNeeded(requestMap, historyHasPdfAttachment(history))
        applyInferenceParams(requestMap, params)
        return requestMap
    }

    /**
     * Follow-up completion with **no** fusion tool: write one final answer from the panel digest.
     * Prevents a second fusion invocation after `fusion_invocation_capped`.
     */
    private fun buildFusionAnswerRequest(
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        analysis: FusionAnalysis,
        params: InferenceParams?,
    ): MutableMap<String, Any> {
        val digest = formatFusionDigestForJudge(analysis)
        val synthSystem = buildString {
            append(systemPrompt)
            append("\n\n## Panel result (already ran — do not call tools)\n")
            append("The fusion panel \"${fusion.panelName}\" has finished. Write ONE final user-facing answer from this digest. ")
            append("Do not call tools. Do not reprint the digest or per-model transcripts.\n\n")
            append(digest)
        }
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history, synthSystem),
            "stream" to false,
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true),
            "tool_choice" to "none",
        )
        addPdfPluginIfNeeded(requestMap, historyHasPdfAttachment(history))
        applyInferenceParams(requestMap, params)
        return requestMap
    }

    private fun formatFusionDigestForJudge(analysis: FusionAnalysis): String = buildString {
        if (analysis.consensus.isNotEmpty()) {
            append("Consensus:\n")
            analysis.consensus.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (analysis.contradictions.isNotEmpty()) {
            append("Disagreements:\n")
            analysis.contradictions.forEach { c ->
                append("- ").append(c.topic)
                if (c.stances.isNotEmpty()) append(": ").append(c.stances.joinToString(" | "))
                append('\n')
            }
            append('\n')
        }
        if (analysis.uniqueInsights.isNotEmpty()) {
            append("Unique insights:\n")
            analysis.uniqueInsights.forEach { i ->
                append("- ")
                if (i.model.isNotBlank()) append(i.model).append(": ")
                append(i.insight).append('\n')
            }
            append('\n')
        }
        if (analysis.blindSpots.isNotEmpty()) {
            append("Blind spots:\n")
            analysis.blindSpots.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (analysis.responses.isNotEmpty()) {
            append("Per-model answers (for your synthesis only — do not paste back):\n")
            analysis.responses.forEach { r ->
                append("### ").append(r.model.ifBlank { "model" }).append('\n')
                append(r.content.take(6000)).append("\n\n")
            }
        }
    }.ifBlank { "(No structured digest fields; use any usable panel signal from context.)" }

    private fun applyInferenceParams(requestMap: MutableMap<String, Any>, params: InferenceParams?) {
        if (params == null) return
        requestMap["temperature"] = params.temperature
        requestMap["top_p"] = params.topP
        if (params.topK > 0) requestMap["top_k"] = params.topK
        if (params.maxTokens > 0) requestMap["max_tokens"] = params.maxTokens
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitAnnotationsAsSearch(annotations: List<*>?) {
        if (annotations == null) return
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.emitPacedContent(answer: String) {
        val step = 18
        var idx = 0
        while (idx < answer.length) {
            val end = (idx + step).coerceAtMost(answer.length)
            emit(StreamChunk.Content(answer.substring(idx, end)))
            idx = end
            delay(14)
        }
    }

    private companion object {
        const val FUSION_FORCE_NUDGE =
            "You must call the fusion tool now before any final answer. " +
                "Do not reply to the user until the fusion tool has returned. " +
                "Call it exactly once."
    }

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
            val cleaned = rawTitle.removeSurrounding("\"").removeSurrounding("'").trim()
            if (cleaned.isNotEmpty()) cleaned else fallbackThreadTitle(firstUserMessage).ifBlank { "New Conversation" }
        } catch (e: Exception) {
            // Fallback: derive a title from the user's message (row clips overflow itself).
            fallbackThreadTitle(firstUserMessage).ifBlank { "New Conversation" }
        }
    }
}
