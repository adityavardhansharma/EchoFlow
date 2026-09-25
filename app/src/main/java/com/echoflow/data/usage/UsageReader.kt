package com.echoflow.data.usage

import com.squareup.moshi.JsonReader
import okio.BufferedSource

/** The usage a provider reported for one request, merged across its stream events. */
data class UsageReading(
    val externalId: String? = null,
    val model: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val costUsd: Double? = null,
    val credits: Double? = null,
    val audioSeconds: Double? = null,
    val detail: String? = null,
) {
    /** True when the provider reported anything worth a row. Ids and models alone are not. */
    val hasUsage: Boolean
        get() = inputTokens != null || outputTokens != null || costUsd != null ||
            credits != null || audioSeconds != null || detail != null
}

/**
 * Folds a provider's response documents (one JSON body, or each SSE event) into a [UsageReading].
 *
 * Streams report usage in different places: OpenRouter, xAI and Cerebras in a final chunk,
 * Anthropic split across `message_start` and cumulative `message_delta`, Gemini on every chunk
 * cumulatively, OpenAI Responses on `response.completed`. Token counts are merged with max so a
 * cumulative field never goes backwards; a price is only ever the provider's own number.
 */
class UsageAccumulator(private val provider: UsageProvider) {
    private var reading = UsageReading()

    fun accept(doc: Map<*, *>) {
        // Nested envelopes carry the same shape: Anthropic `message_start.message` and OpenAI
        // Responses `response.completed.response`.
        (doc["message"] as? Map<*, *>)?.let(::accept)
        (doc["response"] as? Map<*, *>)?.let(::accept)

        val id = doc.string("id") ?: doc.string("requestId") ?: doc.string("responseId") ?:
            doc.string("search_id") ?: doc.string("run_id")
        val model = doc.string("model") ?: doc.string("modelVersion")
        reading = reading.copy(
            externalId = reading.externalId ?: id,
            model = reading.model ?: model,
        )

        when (val usage = doc["usage"]) {
            is Map<*, *> -> readUsage(usage)
            is List<*> -> readSkus(usage)
        }
        (doc["usageMetadata"] as? Map<*, *>)?.let(::readGemini)
        (doc["costDollars"] as? Map<*, *>)?.number("total")?.let { cost(it) }
        readCredits(doc)
        (doc["metadata"] as? Map<*, *>)?.let(::readDeepgram)
    }

    fun reading(): UsageReading = reading

    private fun readUsage(usage: Map<*, *>) {
        val cacheWrite = usage.long("cache_creation_input_tokens")
        val cacheRead = usage.long("cache_read_input_tokens")
        val baseInput = usage.long("prompt_tokens") ?: usage.long("input_tokens")
        // Anthropic reports cached input separately from `input_tokens`; everyone else includes it.
        val input = if (provider == UsageProvider.Claude && baseInput != null) {
            baseInput + (cacheWrite ?: 0) + (cacheRead ?: 0)
        } else baseInput
        val output = usage.long("completion_tokens") ?: usage.long("output_tokens")
        val cached = (usage["prompt_tokens_details"] as? Map<*, *>)?.long("cached_tokens")
            ?: (usage["input_tokens_details"] as? Map<*, *>)?.long("cached_tokens")
            ?: cacheRead
        val reasoning = (usage["completion_tokens_details"] as? Map<*, *>)?.long("reasoning_tokens")
            ?: (usage["output_tokens_details"] as? Map<*, *>)?.long("reasoning_tokens")
        tokens(input, output, cached, reasoning)
        // OpenRouter's `cost` is in dollars; xAI bills in ticks of 1e-10 USD.
        usage.number("cost")?.let { cost(it) }
        usage.number("cost_in_usd_ticks")?.let { cost(it / 1e10) }
    }

    private fun readGemini(meta: Map<*, *>) {
        val thoughts = meta.long("thoughtsTokenCount")
        val candidates = meta.long("candidatesTokenCount")
        val output = if (candidates == null && thoughts == null) null else (candidates ?: 0) + (thoughts ?: 0)
        tokens(meta.long("promptTokenCount"), output, meta.long("cachedContentTokenCount"), thoughts)
    }

    /** Parallel reports billed SKUs (`[{name: "sku_search", count: 1}]`) rather than a price. */
    private fun readSkus(skus: List<*>) {
        val parts = skus.mapNotNull { raw ->
            val sku = raw as? Map<*, *> ?: return@mapNotNull null
            val name = sku.string("name")?.removePrefix("sku_")?.replace('_', ' ') ?: return@mapNotNull null
            val count = sku.long("count") ?: 1
            "$name × $count"
        }
        if (parts.isNotEmpty()) reading = reading.copy(detail = parts.joinToString(", "))
    }

    private fun readCredits(doc: Map<*, *>) {
        val data = doc["data"] as? Map<*, *>
        val credits = doc.number("creditsUsed")
            ?: data?.number("creditsUsed")
            ?: (data?.get("metadata") as? Map<*, *>)?.number("creditsUsed")
            ?: return
        // A polled job reports its running total; keep the latest (largest) figure.
        reading = reading.copy(credits = maxOf(reading.credits ?: 0.0, credits))
    }

    private fun readDeepgram(meta: Map<*, *>) {
        if (provider != UsageProvider.Deepgram) return
        reading = reading.copy(
            externalId = reading.externalId ?: meta.string("request_id"),
            audioSeconds = meta.number("duration") ?: reading.audioSeconds,
        )
    }

    private fun tokens(input: Long?, output: Long?, cached: Long?, reasoning: Long?) {
        reading = reading.copy(
            inputTokens = maxOrNull(reading.inputTokens, input),
            outputTokens = maxOrNull(reading.outputTokens, output),
            cachedTokens = maxOrNull(reading.cachedTokens, cached),
            reasoningTokens = maxOrNull(reading.reasoningTokens, reasoning),
        )
    }

    private fun cost(usd: Double) {
        if (usd.isNaN() || usd < 0) return
        reading = reading.copy(costUsd = maxOf(reading.costUsd ?: 0.0, usd))
    }

    private fun maxOrNull(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else maxOf(a, b)
}

/**
 * Reads only the parts of a response the ledger needs. Image and scrape responses can be
 * megabytes of base64 or markdown, so everything else is skipped without being materialised.
 */
internal object UsageJson {
    private val topLevel = setOf(
        "id", "requestId", "responseId", "search_id", "run_id", "model", "modelVersion", "type",
        "usage", "usageMetadata", "costDollars", "creditsUsed", "metadata",
    )
    private val envelopes = mapOf(
        "message" to setOf("id", "model", "usage"),
        "response" to setOf("id", "model", "usage"),
        "data" to setOf("creditsUsed", "metadata"),
    )

    fun read(source: BufferedSource): Map<String, Any?>? = runCatching {
        val reader = JsonReader.of(source)
        reader.isLenient = true
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
        readObject(reader) { name ->
            when {
                name in topLevel -> Field.Full
                name in envelopes -> Field.Only(envelopes.getValue(name))
                else -> Field.Skip
            }
        }
    }.getOrNull()

    private sealed interface Field {
        data object Full : Field
        data object Skip : Field
        data class Only(val keys: Set<String>) : Field
    }

    private fun readObject(reader: JsonReader, fieldFor: (String) -> Field): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (val field = fieldFor(name)) {
                Field.Full -> out[name] = reader.readJsonValue()
                Field.Skip -> reader.skipValue()
                is Field.Only -> if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                    out[name] = readObject(reader) { if (it in field.keys) Field.Full else Field.Skip }
                } else reader.skipValue()
            }
        }
        reader.endObject()
        return out
    }
}

private fun Map<*, *>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }
private fun Map<*, *>.number(key: String): Double? = when (val v = this[key]) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}
private fun Map<*, *>.long(key: String): Long? = number(key)?.toLong()
