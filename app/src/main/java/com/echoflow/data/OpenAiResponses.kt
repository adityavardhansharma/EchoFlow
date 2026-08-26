package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.BufferedSource

/**
 * Direct OpenAI [POST /v1/responses] payload + SSE helpers.
 *
 * Official OpenAI chat has moved off Chat Completions for newer models; OpenAI-compatible
 * hosts (Cerebras, xAI, local servers) stay on `/v1/chat/completions`.
 */
internal object OpenAiResponses {
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val PATH = "responses"

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    val webSearchTool: Map<String, Any> = mapOf(
        "type" to "function",
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

    fun request(
        model: String,
        input: List<Any>,
        instructions: String,
        params: InferenceParams,
        tools: List<Map<String, Any>>? = null,
        previousResponseId: String? = null,
    ): MutableMap<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "model" to model.trim(),
            "input" to input,
            "stream" to true,
        )
        if (instructions.isNotBlank()) payload["instructions"] = instructions
        payload["temperature"] = params.temperature
        payload["top_p"] = params.topP
        if (params.maxTokens > 0) payload["max_output_tokens"] = params.maxTokens
        if (!tools.isNullOrEmpty()) payload["tools"] = tools
        previousResponseId?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["previous_response_id"] = it }
        return payload
    }

    fun inputItems(history: List<ChatMessage>, encode: (String?) -> String?): List<Map<String, Any>> {
        return history.map { msg ->
            val extraImages = if (msg.role == "user") {
                msg.extraAttachments.mapNotNull { extra ->
                    if (extra.mimeType.equals("application/pdf", ignoreCase = true)) return@mapNotNull null
                    val encoded = encode(extra.uri) ?: return@mapNotNull null
                    inputImage("data:${extra.mimeType};base64,$encoded")
                }
            } else {
                emptyList()
            }
            val primaryImage = if (msg.role == "user" && !msg.localAttachmentMimeType.equals("application/pdf", ignoreCase = true)) {
                encode(msg.localAttachmentUri)?.let { bytes ->
                    val mime = msg.localAttachmentMimeType ?: "image/jpeg"
                    inputImage("data:$mime;base64,$bytes")
                }
            } else {
                null
            }
            val parts = buildList {
                add(mapOf("type" to "input_text", "text" to msg.content))
                if (primaryImage != null) add(primaryImage)
                addAll(extraImages)
            }
            if (parts.size == 1 && extraImages.isEmpty() && primaryImage == null) {
                mapOf("role" to msg.role, "content" to msg.content)
            } else {
                mapOf("role" to msg.role, "content" to parts)
            }
        }
    }

    fun functionCallOutput(callId: String, output: String): Map<String, Any> = mapOf(
        "type" to "function_call_output",
        "call_id" to callId,
        "output" to output,
    )

    sealed class Event {
        data class Content(val text: String) : Event()
        data class Reasoning(val text: String) : Event()
        data class ResponseId(val id: String) : Event()
        data class FunctionCallMeta(val itemId: String, val callId: String, val name: String) : Event()
        data class FunctionCallArgsDelta(val itemId: String, val delta: String) : Event()
        data class FunctionCallArgsDone(val itemId: String, val callId: String, val name: String, val arguments: String) : Event()
        data class Failed(val message: String) : Event()
    }

    fun parseData(data: String): Event? {
        if (data.isBlank() || data == "[DONE]") return null
        val map = runCatching { json.fromJson(data) as? Map<*, *> }.getOrNull() ?: return null
        val type = map["type"] as? String ?: return null
        return when (type) {
            "response.output_text.delta" -> deltaText(map)?.let { Event.Content(it) }
            "response.reasoning_summary_text.delta",
            "response.reasoning_text.delta",
            -> deltaText(map)?.let { Event.Reasoning(it) }
            "response.created",
            "response.in_progress",
            "response.completed",
            -> responseId(map)?.let { Event.ResponseId(it) }
            "response.output_item.added",
            "response.output_item.done",
            -> functionCallMeta(map)
            "response.function_call_arguments.delta" -> {
                val delta = deltaText(map) ?: return null
                Event.FunctionCallArgsDelta(itemId = stringField(map, "item_id"), delta = delta)
            }
            "response.function_call_arguments.done" -> Event.FunctionCallArgsDone(
                itemId = stringField(map, "item_id"),
                callId = stringField(map, "call_id"),
                name = stringField(map, "name"),
                arguments = (map["arguments"] as? String).orEmpty(),
            )
            "error", "response.failed" -> Event.Failed(errorMessage(map))
            else -> null
        }
    }

    suspend fun consumeStream(source: BufferedSource, onEvent: suspend (Event) -> Unit) {
        val decoder = SseDecoder()
        fun dispatch(frame: SseEvent) {
            when (val event = parseData(frame.data)) {
                null -> Unit
                is Event.Failed -> throw Exception(event.message)
                else -> onEvent(event)
            }
        }
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            decoder.accept(line)?.let(::dispatch)
        }
        decoder.flush()?.let(::dispatch)
    }

    private fun inputImage(dataUrl: String): Map<String, Any> = mapOf(
        "type" to "input_image",
        "image_url" to dataUrl,
    )

    private fun deltaText(map: Map<*, *>): String? {
        val raw = map["delta"] ?: map["text"]
        val text = when (raw) {
            is String -> raw
            is Map<*, *> -> (raw["text"] as? String) ?: (raw["delta"] as? String)
            else -> null
        }
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun responseId(map: Map<*, *>): String? {
        val response = map["response"] as? Map<*, *>
        return (response?.get("id") as? String)?.takeIf { it.isNotEmpty() }
    }

    private fun functionCallMeta(map: Map<*, *>): Event.FunctionCallMeta? {
        val item = map["item"] as? Map<*, *> ?: return null
        if (item["type"] as? String != "function_call") return null
        return Event.FunctionCallMeta(
            itemId = (item["id"] as? String).orEmpty(),
            callId = (item["call_id"] as? String).orEmpty(),
            name = (item["name"] as? String).orEmpty(),
        )
    }

    private fun stringField(map: Map<*, *>, key: String): String = (map[key] as? String).orEmpty()

    private fun errorMessage(map: Map<*, *>): String {
        val nested = when (val error = map["error"]) {
            is String -> error
            is Map<*, *> -> error["message"] as? String
            else -> null
        }
        val fromResponse = ((map["response"] as? Map<*, *>)?.get("error") as? Map<*, *>)?.get("message") as? String
        return nested ?: fromResponse ?: map["message"] as? String ?: "OpenAI request failed."
    }
}
