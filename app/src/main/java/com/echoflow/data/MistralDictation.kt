package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Voxtral Small Transcribe 3 called directly on Mistral's `POST /v1/audio/transcriptions` with the
 * user's own key. The recorder's 16 kHz mono WAV (≤ 2 minutes) is uploaded as one multipart
 * `file`, with no chunking and no `language`, so Voxtral detects it (and keeps code-switching).
 *
 * Each vocabulary term becomes its own repeated `context_bias` part. Context bias steers
 * recognition toward those spellings; Mistral says it is never treated as an instruction.
 */
internal object MistralDictation {
    const val BASE_URL = "https://api.mistral.ai"

    /**
     * Mistral's docs call the model `voxtral-small-transcribe-3`; its model card and OpenRouter use
     * the versioned `voxtral-small-transcribe-3-0`. The first is tried, and a "model not found"
     * answer retries once with the second.
     */
    val MODELS = listOf("voxtral-small-transcribe-3", "voxtral-small-transcribe-3-0")

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun request(
        apiKey: String,
        wav: ByteArray,
        contextBias: List<String> = emptyList(),
        model: String = MODELS.first(),
        baseUrl: String = BASE_URL,
    ): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .apply { contextBias.forEach { addFormDataPart("context_bias", it) } }
            .addFormDataPart("file", "dictation.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body)
            .build()
    }

    /** The user's vocabulary, deduplicated and capped; Mistral sets no per-term limit of its own. */
    fun contextBias(vocabulary: List<String>): List<String> = DictationVocabulary.normalize(vocabulary)

    /** True when Mistral rejected the model id itself, so the other spelling is worth one try. */
    fun isUnknownModel(code: Int, body: String): Boolean =
        code == 404 || (code in setOf(400, 422) && body.contains("model", ignoreCase = true) &&
            listOf("not found", "invalid model", "does not exist", "unknown").any { body.contains(it, ignoreCase = true) })

    /** The top-level `text`; null when Voxtral heard nothing. */
    fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        return (map["text"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
}
