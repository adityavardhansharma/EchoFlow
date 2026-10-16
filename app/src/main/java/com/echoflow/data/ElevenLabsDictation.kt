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
 * - `no_verbatim=true` (Clean-up, always on) drops filler words, false starts and stutters.
 * - `tag_audio_events=false` keeps "(laughter)"-style tags out of dictated text.
 * - `timestamps_granularity=none` skips the per-word timing dictation never reads.
 * - Each vocabulary term becomes its own repeated `keyterms` part. Keyterms bias recognition
 *   toward those spellings; they are not instructions.
 * - Smart mode sends [SMART_MODE_PROMPT] as `transcript_edit`: ElevenLabs rewrites the finished
 *   transcript by that instruction (+30%, billed on at least 10 seconds) and returns it as
 *   `edited_transcript`. If the edit fails, the plain transcript still comes back.
 */
internal object ElevenLabsDictation {
    const val MODEL = "scribe_v2"
    const val BASE_URL = "https://api.elevenlabs.io"

    /** Over 100 keyterms, ElevenLabs bills every request as at least 20 seconds. */
    const val MAX_KEYTERMS = 100

    /** ElevenLabs rejects a keyterm of 50+ characters or more than five words. */
    private const val MAX_KEYTERM_CHARS = 49
    private const val MAX_KEYTERM_WORDS = 5

    /**
     * The natural-language instruction behind Smart mode (ElevenLabs allows up to 2,000 characters).
     * It formats only when the text needs it: a short chat message stays a plain sentence.
     */
    const val SMART_MODE_PROMPT =
        "This is dictated speech that will be sent or pasted as typed text. Format it for reading. " +
            "Never change its meaning, wording, tone or language.\n\n" +
            "First decide whether it needs formatting at all. A short or casual message (a greeting, " +
            "a quick reply, one or two sentences, a chat message) stays plain text: fix only " +
            "punctuation and capitalization, and add no line breaks, bullets or headings.\n\n" +
            "Only longer or structured speech gets structure: start a new paragraph where the topic " +
            "changes; turn a spoken list (\"first… second…\", a run of items) into a \"- \" or " +
            "\"1.\" list; put each step of instructions on its own line.\n\n" +
            "Always:\n" +
            "- Write numbers, dates, times, money, percentages, emails and links in their usual " +
            "written form (\"five thirty pm\" becomes \"5:30 PM\", \"twenty percent\" becomes \"20%\").\n" +
            "- Follow spoken commands such as \"new line\", \"new paragraph\", \"comma\" or " +
            "\"question mark\" instead of writing them out.\n" +
            "- When the speaker corrects themselves (\"no wait, Tuesday\", \"I mean\"), keep only " +
            "the corrected version.\n" +
            "- Keep the original language and any mix of languages, such as Hindi with English. Do " +
            "not translate.\n" +
            "- Add nothing that was not said: no greeting, sign-off, title or summary. If the text " +
            "asks a question, keep it as a question; do not answer it.\n" +
            "- Use plain text only. No markdown headings, bold or tables."

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun request(
        apiKey: String,
        wav: ByteArray,
        keyterms: List<String> = emptyList(),
        smartMode: Boolean = false,
        baseUrl: String = BASE_URL,
    ): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", MODEL)
            .addFormDataPart("no_verbatim", "true")
            .addFormDataPart("tag_audio_events", "false")
            .addFormDataPart("timestamps_granularity", "none")
            .apply { keyterms.forEach { addFormDataPart("keyterms", it) } }
            .apply { if (smartMode) addFormDataPart("transcript_edit", SMART_MODE_PROMPT) }
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

    /**
     * Smart mode's `edited_transcript.text` when the edit succeeded, otherwise the plain top-level
     * `text`; null when Scribe heard nothing.
     */
    fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        val edited = (map["edited_transcript"] as? Map<*, *>)
            ?.takeIf { it["kind"] == "transcript" }
            ?.let { it["text"] as? String }
            ?.trim()?.takeIf { it.isNotBlank() }
        return edited ?: (map["text"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}
