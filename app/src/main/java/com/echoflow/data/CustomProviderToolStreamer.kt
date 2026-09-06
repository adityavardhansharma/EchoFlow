package com.echoflow.data

import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Owns provider-specific native web-search tool loops. */
internal class CustomProviderToolStreamer(
    private val client: OkHttpClient,
    private val dynamicAdapter: JsonAdapter<Any>,
    private val openAiMessages: (List<ChatMessage>, String) -> List<Map<String, Any>>,
    private val openAiResponsesInput: (List<ChatMessage>, String) -> List<Map<String, Any>>,
    private val simpleMessages: (List<ChatMessage>, String) -> List<Map<String, String>>,
    private val urlJoiner: (String, String) -> String,
    private val baseUrlValidator: (String) -> ProviderValidationResult,
    private val errorDecoder: (String, Int, String) -> String,
) {
    private fun buildOpenAiMessages(history: List<ChatMessage>, prompt: String) = openAiMessages(history, prompt)
    private fun buildOpenAiResponsesInput(history: List<ChatMessage>, prompt: String) = openAiResponsesInput(history, prompt)
    private fun buildSimpleMessages(history: List<ChatMessage>, prompt: String) = simpleMessages(history, prompt)
    private fun joinUrl(base: String, path: String) = urlJoiner(base, path)
    private fun validateBaseUrl(base: String) = baseUrlValidator(base)
    private fun customError(label: String, code: Int, body: String) = errorDecoder(label, code, body)

    private val maxToolSearches = 4

    private val webSearchToolOpenAi: Map<String, Any> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to "web_search",
            "description" to "Search the web for current, recent, niche, or factual information. Returns a numbered list of results.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "A focused, self-contained search query."),
                ),
                "required" to listOf("query"),
            ),
        ),
    )

    private val webSearchToolClaude: Map<String, Any> = mapOf(
        "name" to "web_search",
        "description" to "Search the web for current, recent, niche, or factual information. Returns a numbered list of results.",
        "input_schema" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "A focused, self-contained search query."),
            ),
            "required" to listOf("query"),
        ),
    )

    private val webSearchFnGemini: Map<String, Any> = mapOf(
        "name" to "web_search",
        "description" to "Search the web for current, recent, niche, or factual information. Returns a numbered list of results.",
        "parameters" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "query" to mapOf("type" to "string", "description" to "A focused, self-contained search query."),
            ),
            "required" to listOf("query"),
        ),
    )

    private class OpenAiPendingCall {
        var id = ""
        var name = ""
        val args = StringBuilder()
    }

    private class ClaudePendingBlock {
        var type = ""
        var id = ""
        var name = ""
        val text = StringBuilder()
        val json = StringBuilder()
    }

    private fun extractQuery(argsJson: String): String {
        val q = runCatching { (dynamicAdapter.fromJson(argsJson) as? Map<*, *>)?.get("query") as? String }.getOrNull()
        return q?.trim().orEmpty().ifBlank { argsJson.trim() }
    }

    /** OpenAI-style tool loop — used by OpenAI, Cerebras, and any OpenAI-compatible endpoint. */
    fun streamOpenAiTools(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = flow {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        if (model.isBlank()) throw Exception("Enter a model name.")
        val messages = ArrayList<Map<String, Any?>>(buildOpenAiMessages(history, systemPrompt))
        var round = 0
        var searchCount = 0
        while (true) {
            val payload = mutableMapOf<String, Any?>(
                "model" to model.trim(),
                "messages" to messages,
                "stream" to true,
                "temperature" to params.temperature,
                "top_p" to params.topP,
            )
            if (searchCount < maxToolSearches) payload["tools"] = listOf(webSearchToolOpenAi)
            if (params.maxTokens > 0) payload["max_tokens"] = params.maxTokens
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "chat/completions"))
                .addHeader("Content-Type", "application/json")
                .apply { apiKey.trim().takeIf { it.isNotEmpty() }?.let { addHeader("Authorization", "Bearer $it") } }
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()

            val pending = sortedMapOf<Int, OpenAiPendingCall>()
            val assistantContent = StringBuilder()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception(customError("provider", response.code, response.body?.string().orEmpty()))
                val body = response.body ?: throw Exception("Empty stream body received.")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank() || data == "[DONE]") continue
                        val map = runCatching { dynamicAdapter.fromJson(data) as? Map<*, *> }.getOrNull() ?: continue
                        val choice = (map["choices"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: continue
                        val delta = choice["delta"] as? Map<*, *>
                        (delta?.get("content") as? String)?.takeIf { it.isNotEmpty() }?.let { assistantContent.append(it); emit(StreamChunk.Content(it)) }
                        (delta?.get("reasoning") as? String)?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Reasoning(it)) }
                        (delta?.get("reasoning_content") as? String)?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Reasoning(it)) }
                        (delta?.get("tool_calls") as? List<*>)?.forEach { tcAny ->
                            val tc = tcAny as? Map<*, *> ?: return@forEach
                            val idx = (tc["index"] as? Number)?.toInt() ?: 0
                            val slot = pending.getOrPut(idx) { OpenAiPendingCall() }
                            (tc["id"] as? String)?.takeIf { it.isNotEmpty() }?.let { slot.id = it }
                            val fn = tc["function"] as? Map<*, *>
                            (fn?.get("name") as? String)?.takeIf { it.isNotEmpty() }?.let { slot.name = it }
                            (fn?.get("arguments") as? String)?.let { slot.args.append(it) }
                        }
                    }
                }
            }

            if (pending.values.none { it.name == "web_search" }) break

            messages.add(
                mapOf(
                    "role" to "assistant",
                    "content" to assistantContent.toString().takeIf { it.isNotBlank() },
                    "tool_calls" to pending.values.map { call ->
                        val callId = call.id.ifBlank { "call_${call.name}" }
                        mapOf(
                            "id" to callId,
                            "type" to "function",
                            "function" to mapOf("name" to call.name, "arguments" to call.args.toString().ifBlank { "{}" }),
                        )
                    },
                )
            )
            for (call in pending.values) {
                val callId = call.id.ifBlank { "call_${call.name}" }
                if (call.name != "web_search") {
                    messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to "Unknown tool."))
                    continue
                }
                val query = extractQuery(call.args.toString())
                if (searchCount >= maxToolSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached ($maxToolSearches per answer)"))
                    messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to "Search limit reached. Answer now using the results already available."))
                    continue
                }
                searchCount++
                emit(StreamChunk.SearchStarted(query))
                val searchResult = runCatching { search(query) }
                val toolContent = searchResult.fold(
                    onSuccess = { sources ->
                        emit(StreamChunk.SearchSources(query, sources))
                        formatSearchResultsForModel(sources)
                    },
                    onFailure = {
                        emit(StreamChunk.SearchSources(query, emptyList()))
                        emit(StreamChunk.StatusNote("Search failed: ${it.message}"))
                        "Search failed: ${it.message ?: "Unknown error"}"
                    },
                )
                messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to toolContent))
            }
            round++
        }
    }.flowOn(Dispatchers.IO)

    /** Official OpenAI `/v1/responses` tool loop — flat function tools + `function_call_output`. */
    fun streamOpenAiResponsesTools(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("OpenAI API key is missing.")
        if (model.isBlank()) throw Exception("Enter a model name.")
        var previousResponseId: String? = null
        var pendingOutputs: List<Map<String, Any>> = buildOpenAiResponsesInput(history, systemPrompt)
        var searchCount = 0
        var round = 0
        while (true) {
            val payload = OpenAiResponses.request(
                model = model,
                input = pendingOutputs,
                instructions = systemPrompt,
                params = params,
                tools = if (searchCount < maxToolSearches) listOf(OpenAiResponses.webSearchTool) else null,
                previousResponseId = previousResponseId,
            )
            val request = Request.Builder()
                .url(joinUrl(OpenAiResponses.DEFAULT_BASE_URL, OpenAiResponses.PATH))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()

            val pending = linkedMapOf<String, OpenAiPendingCall>()
            fun slot(itemId: String): OpenAiPendingCall {
                val key = itemId.ifBlank { "call_$round" }
                return pending.getOrPut(key) { OpenAiPendingCall() }
            }
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception(customError("OpenAI", response.code, response.body?.string().orEmpty()))
                val body = response.body ?: throw Exception("Empty stream body received.")
                body.source().use { source ->
                    OpenAiResponses.consumeStream(source) { event ->
                        when (event) {
                            is OpenAiResponses.Event.Content -> emit(StreamChunk.Content(event.text))
                            is OpenAiResponses.Event.Reasoning -> emit(StreamChunk.Reasoning(event.text))
                            is OpenAiResponses.Event.ResponseId -> previousResponseId = event.id
                            is OpenAiResponses.Event.FunctionCallMeta -> {
                                val call = slot(event.itemId)
                                if (event.callId.isNotEmpty()) call.id = event.callId
                                if (event.name.isNotEmpty()) call.name = event.name
                            }
                            is OpenAiResponses.Event.FunctionCallArgsDelta -> slot(event.itemId).args.append(event.delta)
                            is OpenAiResponses.Event.FunctionCallArgsDone -> {
                                val call = slot(event.itemId)
                                if (event.callId.isNotEmpty()) call.id = event.callId
                                if (event.name.isNotEmpty()) call.name = event.name
                                if (event.arguments.isNotEmpty()) {
                                    call.args.clear()
                                    call.args.append(event.arguments)
                                }
                            }
                            is OpenAiResponses.Event.Failed -> throw Exception(event.message)
                        }
                    }
                }
            }

            if (pending.values.none { it.name == "web_search" }) break

            val outputs = mutableListOf<Map<String, Any>>()
            for (call in pending.values) {
                val callId = call.id.ifBlank { "call_${call.name}" }
                if (call.name != "web_search") {
                    outputs.add(OpenAiResponses.functionCallOutput(callId, "Unknown tool."))
                    continue
                }
                val query = extractQuery(call.args.toString())
                if (searchCount >= maxToolSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached ($maxToolSearches per answer)"))
                    outputs.add(OpenAiResponses.functionCallOutput(callId, "Search limit reached. Answer now using the results already available."))
                    continue
                }
                searchCount++
                emit(StreamChunk.SearchStarted(query))
                val searchResult = runCatching { search(query) }
                val toolContent = searchResult.fold(
                    onSuccess = { sources ->
                        emit(StreamChunk.SearchSources(query, sources))
                        formatSearchResultsForModel(sources)
                    },
                    onFailure = {
                        emit(StreamChunk.SearchSources(query, emptyList()))
                        emit(StreamChunk.StatusNote("Search failed: ${it.message}"))
                        "Search failed: ${it.message ?: "Unknown error"}"
                    },
                )
                outputs.add(OpenAiResponses.functionCallOutput(callId, toolContent))
            }
            pendingOutputs = outputs
            round++
        }
    }.flowOn(Dispatchers.IO)

    /** Ollama `/api/chat` tool loop. Ollama returns whole tool calls (not deltas) on the stream. */
    fun streamOllamaTools(
        baseUrl: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = flow {
        validateBaseUrl(baseUrl).takeUnless { it.ok }?.let { throw Exception(it.message) }
        if (model.isBlank()) throw Exception("Enter an Ollama model name.")
        val messages = ArrayList<Map<String, Any?>>(buildSimpleMessages(history, systemPrompt))
        var round = 0
        var searchCount = 0
        while (true) {
            val payload = mutableMapOf<String, Any?>(
                "model" to model.trim(),
                "messages" to messages,
                "stream" to true,
                "options" to mapOf(
                    "temperature" to params.temperature,
                    "top_p" to params.topP,
                    "top_k" to params.topK,
                    "num_predict" to params.maxTokens,
                ),
            )
            if (searchCount < maxToolSearches) payload["tools"] = listOf(webSearchToolOpenAi)
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "api/chat"))
                .addHeader("Content-Type", "application/json")
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()

            val collected = StringBuilder()
            val toolCalls = mutableListOf<Pair<String, String>>() // name, argsJson
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception(customError("Ollama", response.code, response.body?.string().orEmpty()))
                val body = response.body ?: throw Exception("Empty stream body received.")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        val map = runCatching { dynamicAdapter.fromJson(line) as? Map<*, *> }.getOrNull() ?: continue
                        (map["error"] as? String)?.takeIf { it.isNotBlank() }?.let { throw Exception(it) }
                        val msg = map["message"] as? Map<*, *>
                        (msg?.get("content") as? String)?.takeIf { it.isNotEmpty() }?.let { collected.append(it); emit(StreamChunk.Content(it)) }
                        (msg?.get("tool_calls") as? List<*>)?.forEach { tcAny ->
                            val fn = (tcAny as? Map<*, *>)?.get("function") as? Map<*, *> ?: return@forEach
                            val name = fn["name"] as? String ?: return@forEach
                            val argsJson = when (val a = fn["arguments"]) {
                                is String -> a
                                is Map<*, *> -> dynamicAdapter.toJson(a)
                                else -> "{}"
                            }
                            toolCalls.add(name to argsJson)
                        }
                    }
                }
            }

            if (toolCalls.none { it.first == "web_search" }) break

            messages.add(
                mapOf(
                    "role" to "assistant",
                    "content" to collected.toString(),
                    "tool_calls" to toolCalls.mapIndexed { index, (name, args) ->
                        val callId = "call_${round}_${index}_${name}"
                        mapOf(
                            "id" to callId,
                            "type" to "function",
                            "function" to mapOf("name" to name, "arguments" to (runCatching { dynamicAdapter.fromJson(args) }.getOrNull() ?: args)),
                        )
                    },
                )
            )
            for ((index, pair) in toolCalls.withIndex()) {
                val (name, args) = pair
                val callId = "call_${round}_${index}_${name}"
                if (name != "web_search") {
                    messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to "Unknown tool."))
                    continue
                }
                val query = extractQuery(args)
                if (searchCount >= maxToolSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached ($maxToolSearches per answer)"))
                    messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to "Search limit reached. Answer now using the results already available."))
                    continue
                }
                searchCount++
                emit(StreamChunk.SearchStarted(query))
                val searchResult = runCatching { search(query) }
                val toolContent = searchResult.fold(
                    onSuccess = { sources ->
                        emit(StreamChunk.SearchSources(query, sources))
                        formatSearchResultsForModel(sources)
                    },
                    onFailure = {
                        emit(StreamChunk.SearchSources(query, emptyList()))
                        emit(StreamChunk.StatusNote("Search failed: ${it.message}"))
                        "Search failed: ${it.message ?: "Unknown error"}"
                    },
                )
                messages.add(mapOf("role" to "tool", "tool_call_id" to callId, "content" to toolContent))
            }
            round++
        }
    }.flowOn(Dispatchers.IO)

    /** Anthropic Messages tool loop — accumulates `tool_use` blocks from the SSE stream. */
    fun streamClaudeTools(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("Claude API key is missing.")
        if (model.isBlank()) throw Exception("Enter a Claude model name.")
        val messages = ArrayList<Map<String, Any?>>()
        history.filter { it.role != "system" }.forEach {
            messages.add(mapOf("role" to if (it.role == "assistant") "assistant" else "user", "content" to it.content))
        }
        var round = 0
        var searchCount = 0
        while (true) {
            val payload = mutableMapOf<String, Any?>(
                "model" to model.trim(),
                "messages" to messages,
                "stream" to true,
                "max_tokens" to params.maxTokens.coerceAtLeast(1024),
            )
            CustomProviderCapabilities.putClaudeSampling(payload, model, params)
            if (searchCount < maxToolSearches) payload["tools"] = listOf(webSearchToolClaude)
            if (systemPrompt.isNotBlank()) payload["system"] = systemPrompt
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey.trim())
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()

            val blocks = sortedMapOf<Int, ClaudePendingBlock>()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception(customError("Claude", response.code, response.body?.string().orEmpty()))
                val body = response.body ?: throw Exception("Empty stream body received.")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank()) continue
                        val ev = runCatching { dynamicAdapter.fromJson(data) as? Map<*, *> }.getOrNull() ?: continue
                        when (ev["type"] as? String) {
                            "content_block_start" -> {
                                val idx = (ev["index"] as? Number)?.toInt() ?: 0
                                val cb = ev["content_block"] as? Map<*, *>
                                val slot = blocks.getOrPut(idx) { ClaudePendingBlock() }
                                slot.type = cb?.get("type") as? String ?: ""
                                if (slot.type == "tool_use") {
                                    slot.id = cb?.get("id") as? String ?: ""
                                    slot.name = cb?.get("name") as? String ?: ""
                                }
                            }
                            "content_block_delta" -> {
                                val idx = (ev["index"] as? Number)?.toInt() ?: 0
                                val d = ev["delta"] as? Map<*, *>
                                when (d?.get("type") as? String) {
                                    "text_delta" -> (d["text"] as? String)?.takeIf { it.isNotEmpty() }?.let {
                                        blocks.getOrPut(idx) { ClaudePendingBlock() }.text.append(it); emit(StreamChunk.Content(it))
                                    }
                                    "input_json_delta" -> (d["partial_json"] as? String)?.let {
                                        blocks.getOrPut(idx) { ClaudePendingBlock() }.json.append(it)
                                    }
                                    "thinking_delta" -> (d["thinking"] as? String)?.takeIf { it.isNotEmpty() }?.let { emit(StreamChunk.Reasoning(it)) }
                                }
                            }
                        }
                    }
                }
            }

            val toolUses = blocks.values.filter { it.type == "tool_use" }
            if (toolUses.none { it.name == "web_search" }) break

            val assistantBlocks = mutableListOf<Map<String, Any?>>()
            blocks.values.forEach { b ->
                when (b.type) {
                    "text" -> b.text.toString().takeIf { it.isNotBlank() }?.let { assistantBlocks.add(mapOf("type" to "text", "text" to it)) }
                    "tool_use" -> assistantBlocks.add(
                        mapOf(
                            "type" to "tool_use",
                            "id" to b.id,
                            "name" to b.name,
                            "input" to (runCatching { dynamicAdapter.fromJson(b.json.toString().ifBlank { "{}" }) }.getOrNull() ?: emptyMap<String, Any>()),
                        )
                    )
                }
            }
            messages.add(mapOf("role" to "assistant", "content" to assistantBlocks))

            val resultBlocks = mutableListOf<Map<String, Any?>>()
            for (b in toolUses) {
                if (b.name != "web_search") {
                    resultBlocks.add(mapOf("type" to "tool_result", "tool_use_id" to b.id, "content" to "Unknown tool."))
                    continue
                }
                val query = extractQuery(b.json.toString())
                if (searchCount >= maxToolSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached ($maxToolSearches per answer)"))
                    resultBlocks.add(mapOf("type" to "tool_result", "tool_use_id" to b.id, "content" to "Search limit reached. Answer now using the results already available."))
                    continue
                }
                searchCount++
                emit(StreamChunk.SearchStarted(query))
                val searchResult = runCatching { search(query) }
                val toolContent = searchResult.fold(
                    onSuccess = { sources ->
                        emit(StreamChunk.SearchSources(query, sources))
                        formatSearchResultsForModel(sources)
                    },
                    onFailure = {
                        emit(StreamChunk.SearchSources(query, emptyList()))
                        emit(StreamChunk.StatusNote("Search failed: ${it.message}"))
                        "Search failed: ${it.message ?: "Unknown error"}"
                    },
                )
                resultBlocks.add(mapOf("type" to "tool_result", "tool_use_id" to b.id, "content" to toolContent))
            }
            messages.add(mapOf("role" to "user", "content" to resultBlocks))
            round++
        }
    }.flowOn(Dispatchers.IO)

    /** Gemini generateContent tool loop — collects `functionCall` parts, replies with `functionResponse`. */
    fun streamGeminiTools(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) throw Exception("Gemini API key is missing.")
        if (model.isBlank()) throw Exception("Enter a Gemini model name.")
        val contents = ArrayList<Map<String, Any?>>()
        history.filter { it.role != "system" }.forEach {
            contents.add(mapOf("role" to if (it.role == "assistant") "model" else "user", "parts" to listOf(mapOf("text" to it.content))))
        }
        val cleanModel = model.removePrefix("models/")
        var round = 0
        var searchCount = 0
        while (true) {
            val payload = mutableMapOf<String, Any?>(
                "contents" to contents,
                "generationConfig" to mapOf(
                    "temperature" to params.temperature,
                    "topP" to params.topP,
                    "maxOutputTokens" to params.maxTokens.coerceAtLeast(256),
                ),
            )
            if (searchCount < maxToolSearches) payload["tools"] = listOf(mapOf("functionDeclarations" to listOf(webSearchFnGemini)))
            if (systemPrompt.isNotBlank()) payload["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to systemPrompt)))
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?key=${apiKey.trim()}&alt=sse")
                .addHeader("Content-Type", "application/json")
                .post(dynamicAdapter.toJson(RequestContextBudget.checkedPayload(payload)).toRequestBody("application/json".toMediaType()))
                .build()

            val functionCalls = mutableListOf<Pair<String, Map<*, *>>>() // name, args
            val modelParts = mutableListOf<Map<String, Any?>>()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception(customError("Gemini", response.code, response.body?.string().orEmpty()))
                val body = response.body ?: throw Exception("Empty stream body received.")
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank()) continue
                        val ev = runCatching { dynamicAdapter.fromJson(data) as? Map<*, *> }.getOrNull() ?: continue
                        val cand = (ev["candidates"] as? List<*>)?.firstOrNull() as? Map<*, *>
                        val parts = (cand?.get("content") as? Map<*, *>)?.get("parts") as? List<*>
                        parts?.forEach { pAny ->
                            val p = pAny as? Map<*, *> ?: return@forEach
                            (p["text"] as? String)?.takeIf { it.isNotEmpty() }?.let { modelParts.add(mapOf("text" to it)); emit(StreamChunk.Content(it)) }
                            (p["functionCall"] as? Map<*, *>)?.let { fc ->
                                val name = fc["name"] as? String ?: return@let
                                val args = fc["args"] as? Map<*, *> ?: emptyMap<String, Any>()
                                functionCalls.add(name to args)
                                modelParts.add(mapOf("functionCall" to mapOf("name" to name, "args" to args)))
                            }
                        }
                    }
                }
            }

            if (functionCalls.none { it.first == "web_search" }) break

            contents.add(mapOf("role" to "model", "parts" to modelParts))
            val responseParts = mutableListOf<Map<String, Any?>>()
            for ((name, args) in functionCalls) {
                if (name != "web_search") {
                    responseParts.add(mapOf("functionResponse" to mapOf("name" to name, "response" to mapOf("content" to "Unknown tool."))))
                    continue
                }
                val query = (args["query"] as? String)?.trim().takeIf { !it.isNullOrBlank() }
                    ?: args.toString().trim()
                if (searchCount >= maxToolSearches) {
                    emit(StreamChunk.StatusNote("Search limit reached ($maxToolSearches per answer)"))
                    responseParts.add(mapOf("functionResponse" to mapOf("name" to "web_search", "response" to mapOf("content" to "Search limit reached. Answer now using the results already available."))))
                    continue
                }
                searchCount++
                emit(StreamChunk.SearchStarted(query))
                val searchResult = runCatching { search(query) }
                val toolContent = searchResult.fold(
                    onSuccess = { sources ->
                        emit(StreamChunk.SearchSources(query, sources))
                        formatSearchResultsForModel(sources)
                    },
                    onFailure = {
                        emit(StreamChunk.SearchSources(query, emptyList()))
                        emit(StreamChunk.StatusNote("Search failed: ${it.message}"))
                        "Search failed: ${it.message ?: "Unknown error"}"
                    },
                )
                responseParts.add(mapOf("functionResponse" to mapOf("name" to "web_search", "response" to mapOf("results" to toolContent))))
            }
            contents.add(mapOf("role" to "user", "parts" to responseParts))
            round++
        }
    }.flowOn(Dispatchers.IO)

}
