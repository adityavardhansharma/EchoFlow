package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ElevenLabs Scribe v2 called directly on `POST /v1/speech-to-text` with the user's own key. The
 * recorder's 16 kHz mono WAV (≤ 2 minutes) is uploaded as one multipart `file` — no chunking.
 *
 * - `tag_audio_events=false` keeps "(laughter)"-style tags out of dictated text.
 * - `timestamps_granularity=none` skips the per-word timing dictation never reads.
 * - Each vocabulary term becomes its own repeated `keyterms` part. Keyterms bias recognition
 *   toward those spellings; they are not instructions.
 */
internal object ElevenLabsDictation {
    const val MODEL = "scribe_v2"
    const val BASE_URL = "https://api.elevenlabs.io"

    /** Over 100 keyterms, ElevenLabs bills every request as at least 20 seconds. */
    const val MAX_KEYTERMS = 100

    /** ElevenLabs rejects a keyterm of 50+ characters or more than five words. */
    private const val MAX_KEYTERM_CHARS = 49
    private const val MAX_KEYTERM_WORDS = 5

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun request(
        apiKey: String,
        wav: ByteArray,
        keyterms: List<String> = emptyList(),
        baseUrl: String = BASE_URL,
    ): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", MODEL)
            .addFormDataPart("tag_audio_events", "false")
            .addFormDataPart("timestamps_granularity", "none")
            .apply { keyterms.forEach { addFormDataPart("keyterms", it) } }
            .addFormDataPart("file", "dictation.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/speech-to-text")
            .header("xi-api-key", apiKey.trim())
            .post(body)
            .build()
    }

    /**
     * The user's normalized vocabulary trimmed to what Scribe accepts, keeping their order.
     * Characters ElevenLabs forbids (`< > { } [ ] \`) are dropped; a term that is still too long
     * or has more than five words is skipped rather than failing the whole recording.
     */
    fun keyterms(vocabulary: List<String>): List<String> {
        val out = ArrayList<String>()
        for (term in DictationVocabulary.normalize(vocabulary)) {
            val clean = term.replace(Regex("[<>{}\\[\\]\\\\]"), " ").replace(Regex("\\s+"), " ").trim()
            if (clean.isEmpty() || clean.length > MAX_KEYTERM_CHARS) continue
            if (clean.split(' ').size > MAX_KEYTERM_WORDS) continue
            if (out.any { it.equals(clean, ignoreCase = true) }) continue
            out.add(clean)
            if (out.size == MAX_KEYTERMS) break
        }
        return out
    }

    /** The top-level `text`, or null when Scribe heard nothing. */
    fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        return (map["text"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}
