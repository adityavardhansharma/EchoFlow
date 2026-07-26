package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** The provider job handle returned by a submit, and the resume point after a process kill. */
data class VideoJobHandle(
    val id: String,
    val pollingUrl: String,
    val status: String,
)

/** One poll of a running job. [downloadUrls] are the provider's authenticated direct links. */
data class VideoJobState(
    val id: String,
    val status: String,
    val error: String? = null,
    val downloadUrls: List<String> = emptyList(),
) {
    val isTerminal: Boolean get() = status in GeneratedVideo.TERMINAL_STATUSES
    val succeeded: Boolean get() = status == GeneratedVideo.STATUS_COMPLETED
}

/** Typed domain errors so routing/UI can react without string-matching messages. */
sealed class VideoGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey(message: String) : VideoGenerationException(message)
    class SubmitFailed(message: String, cause: Throwable? = null) : VideoGenerationException(message, cause)
    class GenerationFailed(message: String, cause: Throwable? = null) : VideoGenerationException(message, cause)
    class DownloadFailed(message: String, cause: Throwable? = null) : VideoGenerationException(message, cause)
}

/**
 * OpenRouter's asynchronous video API (`/api/v1/videos`). Video generation cannot use the
 * chat-completions path at all: a clip takes 30s–several minutes, so the contract is
 * submit → poll → download, with the job id being the only thing that survives in between.
 *
 * Deliberately absent from every request: `duration`. The model picks the clip's length
 * itself — the app only ever expresses framing (aspect ratio / resolution).
 */
class OpenRouterVideoService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Downloads are tens of megabytes over mobile links; they get their own generous budget.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dynamicAdapter = moshi.adapter(Any::class.java)

    /**
     * Submits one generation. [frameImageDataUrl] animates an existing image (first frame);
     * omitting it makes this a text-to-video request. Only parameters the caller already
     * validated against the model's capabilities should be passed — an unsupported value is
     * a 400, not a silent downgrade.
     */
    suspend fun submit(
        apiKey: String,
        model: String,
        prompt: String,
        aspectRatio: String? = null,
        resolution: String? = null,
        generateAudio: Boolean? = null,
        frameImageDataUrl: String? = null,
    ): VideoJobHandle = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw VideoGenerationException.MissingApiKey(
                "Video generation uses OpenRouter. Add your API key in Settings → Cloud models."
            )
        }
        val payload = buildMap<String, Any> {
            put("model", model)
            put("prompt", prompt)
            aspectRatio?.takeIf { it.isNotBlank() }?.let { put("aspect_ratio", it) }
            resolution?.takeIf { it.isNotBlank() }?.let { put("resolution", it) }
            generateAudio?.let { put("generate_audio", it) }
            frameImageDataUrl?.takeIf { it.isNotBlank() }?.let { url ->
                put(
                    "frame_images",
                    listOf(
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to url),
                            "frame_type" to "first_frame",
                        )
                    )
                )
            }
        }
        val request = Request.Builder()
            .url("$BASE_URL/videos")
            .headers(authHeaders(apiKey))
            .post(dynamicAdapter.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw VideoGenerationException.SubmitFailed(submitErrorMessage(response.code, body, model))
            }
            val map = parseObject(body)
                ?: throw VideoGenerationException.SubmitFailed("OpenRouter returned an unreadable video job.")
            val id = map["id"] as? String
                ?: throw VideoGenerationException.SubmitFailed("OpenRouter did not return a video job id.")
            VideoJobHandle(
                id = id,
                // A job is always pollable at its canonical URL; the returned one is preferred
                // because OpenRouter may route a job to a provider-specific host.
                pollingUrl = (map["polling_url"] as? String)?.takeIf { it.isNotBlank() } ?: "$BASE_URL/videos/$id",
                status = (map["status"] as? String) ?: GeneratedVideo.STATUS_PENDING,
            )
        }
    }

    /** One status check. Non-terminal statuses simply mean "keep waiting". */
    suspend fun poll(apiKey: String, pollingUrl: String): VideoJobState = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(pollingUrl).headers(authHeaders(apiKey)).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw VideoGenerationException.GenerationFailed(
                    ProviderHttpSupport.errorMessage("OpenRouter", response.code, body)
                )
            }
            val map = parseObject(body)
                ?: throw VideoGenerationException.GenerationFailed("OpenRouter returned an unreadable job status.")
            VideoJobState(
                id = (map["id"] as? String).orEmpty(),
                status = (map["status"] as? String) ?: GeneratedVideo.STATUS_IN_PROGRESS,
                error = errorText(map["error"]),
                downloadUrls = (map["unsigned_urls"] as? List<*>).orEmpty().filterIsInstance<String>(),
            )
        }
    }

    /**
     * Streams the finished MP4. The body is handed to [block] and closed afterwards, so the
     * bytes go straight to disk and a whole clip never has to sit in memory.
     */
    suspend fun <T> withVideoContent(
        apiKey: String,
        jobId: String,
        index: Int = 0,
        block: suspend (InputStream) -> T,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/videos/$jobId/content?index=$index")
            .headers(authHeaders(apiKey))
            .get()
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw VideoGenerationException.DownloadFailed(
                    ProviderHttpSupport.errorMessage("OpenRouter", response.code, response.body?.string().orEmpty())
                )
            }
            val stream = response.body?.byteStream()
                ?: throw VideoGenerationException.DownloadFailed("OpenRouter returned an empty video.")
            block(stream)
        }
    }

    private fun authHeaders(apiKey: String) = okhttp3.Headers.Builder()
        .add("Authorization", "Bearer $apiKey")
        .add("HTTP-Referer", "https://localhost")
        .add("X-Title", "EchoFlow")
        .build()

    private fun parseObject(body: String): Map<*, *>? =
        runCatching { dynamicAdapter.fromJson(body) as? Map<*, *> }.getOrNull()

    private fun errorText(raw: Any?): String? = when (raw) {
        is String -> raw.takeIf { it.isNotBlank() }
        is Map<*, *> -> (raw["message"] as? String)?.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun submitErrorMessage(code: Int, body: String, model: String): String = when (code) {
        400 -> ProviderHttpSupport.errorMessage("OpenRouter", code, body)
            .takeIf { !it.endsWith("HTTP $code.") }
            ?: "$model rejected these settings. Try a different aspect ratio or resolution."
        401 -> "OpenRouter rejected your API key. Check it in Settings → Cloud models."
        402, 403 -> "OpenRouter refused the request — your credit may be depleted."
        404 -> "$model is not available for video generation."
        else -> ProviderHttpSupport.errorMessage("OpenRouter", code, body)
    }

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
