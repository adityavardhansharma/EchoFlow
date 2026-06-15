package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
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

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dynamicAdapter = moshi.adapter(Any::class.java)

    /**
     * Converts an image URI to standard raw Base64 string.
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
                    mapOf(
                        "role" to msg.role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to msg.content),
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf("url" to "data:$mime;base64,$base64")
                            )
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
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "stream" to false
        )
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

        fun looksLikeWebSearch(): Boolean =
            (type?.contains("web_search") == true) || (name?.contains("web_search") == true)
    }

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
        if (tools != null) {
            requestMap["tools"] = tools
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

        client.newCall(request).execute().use { response ->
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
                    val map = dynamicAdapter.fromJson(dataPart) as? Map<*, *>
                    val choices = map?.get("choices") as? List<*>
                    val choice = choices?.firstOrNull() as? Map<*, *>
                    val delta = choice?.get("delta") as? Map<*, *>

                    // Reasoning tokens (different providers name the field differently).
                    val reasoning = (delta?.get("reasoning") as? String)
                        ?: (delta?.get("reasoning_content") as? String)
                    if (!reasoning.isNullOrEmpty()) {
                        reasoningBuf.append(reasoning)
                        onChunk(StreamChunk.Reasoning(reasoning))
                    }

                    val content = delta?.get("content") as? String
                    if (!content.isNullOrEmpty()) {
                        contentBuf.append(content)
                        onChunk(StreamChunk.Content(content))
                    }

                    // Tool call deltas: fragments accumulate per index. Used both by the
                    // OpenRouter server tool (search runs server-side mid-stream) and by
                    // client function calling (we run the search between requests).
                    val toolCallDeltas = delta?.get("tool_calls") as? List<*>
                    toolCallDeltas?.forEach { rawCall ->
                        val call = rawCall as? Map<*, *> ?: return@forEach
                        val index = (call["index"] as? Double)?.toInt() ?: 0
                        val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
                        (call["id"] as? String)?.let { builder.id = it }
                        (call["type"] as? String)?.let { builder.type = it }
                        val function = call["function"] as? Map<*, *>
                        (function?.get("name") as? String)?.let { builder.name = it }
                        (function?.get("arguments") as? String)?.let { builder.args.append(it) }

                        if (!builder.announced && builder.looksLikeWebSearch()) {
                            val query = parseQueryArgument(builder.args.toString())
                            if (query != null) {
                                builder.announced = true
                                lastAnnouncedQuery = query
                                onChunk(StreamChunk.SearchStarted(query))
                            }
                        }
                    }

                    // url_citation annotations may arrive on the delta or on a message object.
                    val annotations = (delta?.get("annotations") as? List<*>)
                        ?: ((choice?.get("message") as? Map<*, *>)?.get("annotations") as? List<*>)
                    if (annotations != null) {
                        val fresh = mutableListOf<SearchSource>()
                        annotations.forEach { rawAnn ->
                            val ann = rawAnn as? Map<*, *> ?: return@forEach
                            if ((ann["type"] as? String) != "url_citation") return@forEach
                            val cite = ann["url_citation"] as? Map<*, *> ?: return@forEach
                            val url = cite["url"] as? String ?: return@forEach
                            if (!seenSourceUrls.add(url)) return@forEach
                            val source = SearchSource(
                                title = (cite["title"] as? String).orEmpty().ifBlank { url },
                                url = url,
                                snippet = cite["content"] as? String
                            )
                            collectedSources.add(source)
                            fresh.add(source)
                        }
                        if (fresh.isNotEmpty()) {
                            onChunk(StreamChunk.SearchSources(lastAnnouncedQuery.orEmpty(), fresh))
                        }
                    }

                    (choice?.get("finish_reason") as? String)?.let { finishReason = it }
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

        val tools = if (serverWebSearch) {
            listOf(
                mapOf(
                    "type" to "openrouter:web_search",
                    // max_total_results backs up the prompt's "at most 3 searches" rule:
                    // 3 searches × 5 results caps what OpenRouter will return overall.
                    "parameters" to mapOf(
                        "max_results" to 5,
                        "max_total_results" to 15
                    )
                )
            )
        } else null

        streamCompletion(apiKey, model, buildMessagesPayload(history, systemPrompt), tools, params) { emit(it) }
    }.flowOn(Dispatchers.IO)

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
            val result = streamCompletion(apiKey, model, messages, tools, params) { emit(it) }

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
