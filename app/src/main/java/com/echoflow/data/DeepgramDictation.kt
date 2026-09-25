package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.ceil

/**
 * Deepgram Nova-3 Multilingual over the prerecorded `/v1/listen` REST endpoint. The recorder's
 * 16 kHz mono WAV (≤ 2 minutes) is posted as the raw request body in one call — no chunking.
 *
 * - `language=multi` handles code switching such as Hindi–English within one recording.
 * - `smart_format=true` adds punctuation, casing, and readable numbers/dates. It formats; it does
 *   not rewrite false starts or self-corrections (that is Smart Mode's job, not dictation's).
 * - Each vocabulary term becomes its own repeated `keyterm` parameter. Keyterms bias recognition
 *   toward those spellings; they are not instructions.
 */
internal object DeepgramDictation {
    /** Deepgram recommends the most important 20–50 terms. */
    const val MAX_KEYTERMS = 50

    /**
     * Deepgram rejects more than 500 keyterm tokens per request. Its tokenizer is not public, so
     * this stays under a conservative estimate rather than risking a rejected recording.
     */
    const val KEYTERM_TOKEN_BUDGET = 450

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun request(baseUrl: String, apiKey: String, wav: ByteArray, keyterms: List<String> = emptyList()): Request {
        val url = "${baseUrl.trimEnd('/')}/v1/listen".toHttpUrl().newBuilder()
            .addQueryParameter("model", SttCatalog.DEEPGRAM_MODEL_ID)
            .addQueryParameter("language", "multi")
            .addQueryParameter("smart_format", "true")
            // HttpUrl percent-encodes each value, so multi-word terms arrive as one literal phrase.
            .apply { keyterms.forEach { addQueryParameter("keyterm", it) } }
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Token ${apiKey.trim()}")
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
    }

    /**
     * The user's normalized vocabulary trimmed to what Deepgram accepts, keeping their order.
     * Commas, semicolons, and colons are dropped because Deepgram would read `term:0.5` or
     * `a, b` as one literal keyterm rather than weights or a list.
     */
    fun keyterms(vocabulary: List<String>): List<String> {
        var budget = KEYTERM_TOKEN_BUDGET
        val out = ArrayList<String>()
        for (term in DictationVocabulary.normalize(vocabulary)) {
            val clean = term.replace(Regex("[,;:]"), " ").replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty() || out.any { it.equals(clean, ignoreCase = true) }) continue
            val cost = estimatedTokens(clean)
            if (cost > budget) break
            budget -= cost
            out.add(clean)
            if (out.size == MAX_KEYTERMS) break
        }
        return out
    }

    /** ~3 Latin characters per token; other scripts (e.g. Devanagari) count one per character. */
    private fun estimatedTokens(term: String): Int {
        val latin = term.count { it.code < 0x250 }
        return ceil(latin / 3.0).toInt() + (term.length - latin) + 1
    }

    /** `results.channels[0].alternatives[0].transcript`, or null when Deepgram heard nothing. */
    fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        val channel = ((map["results"] as? Map<*, *>)?.get("channels") as? List<*>)?.firstOrNull() as? Map<*, *>
        val alternative = (channel?.get("alternatives") as? List<*>)?.firstOrNull() as? Map<*, *>
        return (alternative?.get("transcript") as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}
