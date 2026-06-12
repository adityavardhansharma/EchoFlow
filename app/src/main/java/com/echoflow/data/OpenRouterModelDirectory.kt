package com.echoflow.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One model from OpenRouter's public directory, normalized for the picker UI. */
data class OpenRouterModelInfo(
    val id: String, // "anthropic/claude-sonnet-4.6"
    val name: String, // "Anthropic: Claude Sonnet 4.6"
    val contextLength: Int?,
    val promptPricePerM: Double?, // USD per 1M prompt tokens
    val completionPricePerM: Double?, // USD per 1M completion tokens
) {
    val isFree: Boolean get() = (promptPricePerM ?: 0.0) == 0.0 && (completionPricePerM ?: 0.0) == 0.0
}

/**
 * Fetches OpenRouter's public model directory (https://openrouter.ai/api/v1/models — no
 * auth needed) once per process and serves it from memory, so the add-model search can
 * filter the full list instantly as the user types.
 */
class OpenRouterModelDirectory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(DirectoryResponse::class.java)

    @Volatile
    private var cache: List<OpenRouterModelInfo>? = null

    suspend fun allModels(): List<OpenRouterModelInfo> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }

        val request = Request.Builder().url("https://openrouter.ai/api/v1/models").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Could not load the model directory (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val parsed = adapter.fromJson(body)?.data.orEmpty()
            val models = parsed.mapNotNull { raw ->
                val id = raw.id ?: return@mapNotNull null
                OpenRouterModelInfo(
                    id = id,
                    name = raw.name ?: id,
                    contextLength = raw.contextLength,
                    promptPricePerM = raw.pricing?.prompt?.toDoubleOrNull()?.times(1_000_000),
                    completionPricePerM = raw.pricing?.completion?.toDoubleOrNull()?.times(1_000_000),
                )
            }.sortedBy { it.name.lowercase() }
            cache = models
            models
        }
    }
}

private data class DirectoryResponse(val data: List<RawModel>? = null)

private data class RawModel(
    val id: String? = null,
    val name: String? = null,
    @Json(name = "context_length") val contextLength: Int? = null,
    val pricing: RawPricing? = null,
)

private data class RawPricing(
    val prompt: String? = null,
    val completion: String? = null,
)
