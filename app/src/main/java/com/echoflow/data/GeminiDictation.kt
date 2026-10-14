package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini 3.5 Transcribe called directly on the Gemini API with the user's own key, through the
 * `/v1beta/interactions` endpoint. The recorder's 16 kHz mono WAV (≤ 2 minutes, ~5 MB as base64)
 * goes inline, well under the 20 MB request limit.
 *
 * - `custom_vocabulary` biases recognition toward the user's names and terms.
 * - Rambler sets `mode: "smart"`, the API's name for the clean-up Gboard's Rambler does: it drops
 *   filler words and false starts, resolves spoken self-corrections, and formats lists and
 *   numbers. OpenRouter doesn't forward this option, so it exists only on the direct route.
 */
internal object GeminiDictation {
    const val MODEL = "gemini-3.5-transcribe"
    const val BASE_URL = "https://generativelanguage.googleapis.com"

    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun requestBody(wavBase64: String, vocabulary: List<String> = emptyList(), rambler: Boolean = false): Map<String, Any> {
        val transcription = buildMap<String, Any> {
            val terms = DictationVocabulary.normalize(vocabulary)
            if (terms.isNotEmpty()) put("custom_vocabulary", terms)
            if (rambler) put("mode", "smart")
        }
        return buildMap {
            put("model", MODEL)
            put("input", listOf(mapOf("type" to "audio", "data" to wavBase64, "mime_type" to "audio/wav")))
            if (transcription.isNotEmpty()) put("generation_config", mapOf("transcription_config" to transcription))
        }
    }

    fun request(
        apiKey: String,
        wavBase64: String,
        vocabulary: List<String> = emptyList(),
        rambler: Boolean = false,
        baseUrl: String = BASE_URL,
    ): Request = Request.Builder()
        .url("${baseUrl.trimEnd('/')}/v1beta/interactions")
        .header("x-goog-api-key", apiKey.trim())
        .post(json.toJson(requestBody(wavBase64, vocabulary, rambler)).toRequestBody("application/json".toMediaType()))
        .build()

    /** The text parts of every `model_output` step, joined; null when nothing was heard. */
    fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        val steps = (map["steps"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .filter { (it["type"] as? String ?: "model_output") == "model_output" }
        val text = steps.flatMap { (it["content"] as? List<*>).orEmpty() }
            .filterIsInstance<Map<*, *>>()
            .filter { (it["type"] as? String ?: "text") == "text" }
            .mapNotNull { it["text"] as? String }
            .joinToString(" ") { it.trim() }
        return text.trim().takeIf { it.isNotBlank() }
    }
}
