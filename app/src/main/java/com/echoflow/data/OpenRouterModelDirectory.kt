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
    val acceptsImages: Boolean? = null,
    val outputsImage: Boolean = false, // architecture.output_modalities contains "image"
    val outputsText: Boolean = true, // architecture.output_modalities contains "text"
    /** True when OpenRouter bills image output separately from prompt/completion tokens. */
    val hasImageOutputPrice: Boolean = false,
) {
    val isFree: Boolean
        get() = (promptPricePerM ?: 0.0) == 0.0 &&
            (completionPricePerM ?: 0.0) == 0.0 &&
            !hasImageOutputPrice

    /**
     * Dedicated Image API models (Muse, Flux, Seedream, gpt-image-*) output image only.
     * Gemini/GPT-5 image models still speak chat completions with `modalities: ["image","text"]`.
     */
    val usesDedicatedImageApi: Boolean get() = outputsImage && !outputsText
}

/**
 * Fetches OpenRouter's public model directory once per process and serves it from memory,
 * so the add-model search can filter the full list instantly as the user types.
 *
 * Chat and Imagine use different listings: unfiltered `/api/v1/models` is the chat catalog
 * and omits dedicated image-only models. Imagine loads
 * `/api/v1/models?output_modalities=image`, which is where Meta Muse Image and other
 * Image-API models appear.
 */
class OpenRouterModelDirectory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var chatCache: List<OpenRouterModelInfo>? = null

    @Volatile
    private var imageCache: List<OpenRouterModelInfo>? = null

    suspend fun allModels(): List<OpenRouterModelInfo> = fetchInto(chatCache) { chatCache = it }

    /**
     * Every model OpenRouter will generate an image with, including dedicated Image API
     * models that are missing from the chat catalog.
     */
    suspend fun imageModels(): List<OpenRouterModelInfo> = fetchInto(
        imageCache,
        url = IMAGE_DIRECTORY_URL,
        keep = { it.outputsImage && !isRouterAuto(it.id) },
    ) { imageCache = it }

    private suspend fun fetchInto(
        cache: List<OpenRouterModelInfo>?,
        url: String = CHAT_DIRECTORY_URL,
        keep: (OpenRouterModelInfo) -> Boolean = { true },
        store: (List<OpenRouterModelInfo>) -> Unit,
    ): List<OpenRouterModelInfo> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Could not load the model directory (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val models = parseDirectory(body).filter(keep)
            models.forEach { model -> model.contextLength?.takeIf { it > 0 }?.let { contextLengths[model.id] = it } }
            models.forEach { model -> model.acceptsImages?.let { imageInputs[model.id] = it } }
            store(models)
            models
        }
    }

    companion object {
        private val imageInputs = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        fun imageInputSupport(model: String): Boolean? = imageInputs[model]
        private val contextLengths = java.util.concurrent.ConcurrentHashMap<String, Int>()
        fun contextTokens(model: String): Int = contextLengths[model] ?: 8192

        const val CHAT_DIRECTORY_URL = "https://openrouter.ai/api/v1/models"
        const val IMAGE_DIRECTORY_URL = "https://openrouter.ai/api/v1/models?output_modalities=image"

        fun parseDirectory(body: String): List<OpenRouterModelInfo> {
            val parsed = directoryAdapter().fromJson(body)?.data.orEmpty()
            return parsed.mapNotNull { raw ->
                val id = raw.id ?: return@mapNotNull null
                val outputs = raw.architecture?.outputModalities.orEmpty()
                OpenRouterModelInfo(
                    id = id,
                    name = raw.name ?: id,
                    contextLength = raw.contextLength,
                    promptPricePerM = raw.pricing?.prompt?.toDoubleOrNull()?.times(1_000_000),
                    completionPricePerM = raw.pricing?.completion?.toDoubleOrNull()?.times(1_000_000),
                    acceptsImages = raw.architecture?.inputModalities?.contains("image"),
                    outputsImage = outputs.any { it.equals("image", ignoreCase = true) },
                    outputsText = outputs.any { it.equals("text", ignoreCase = true) } || outputs.isEmpty(),
                    hasImageOutputPrice = raw.pricing?.imageOutput
                        ?.toDoubleOrNull()
                        ?.let { it > 0.0 } == true,
                )
            }.sortedBy { it.name.lowercase() }
        }

        fun isRouterAuto(id: String): Boolean = id.startsWith("openrouter/auto")

        /**
         * When the live directory is unavailable, send Gemini/GPT-5 image models through
         * chat completions and everything else (Muse, Flux, gpt-image-1) through `/images`.
         */
        fun fallbackUsesDedicatedImageApi(modelId: String): Boolean {
            val id = modelId.lowercase()
            if (isRouterAuto(id)) return false
            if (id.contains("gpt-image")) return true
            if ((id.contains("gemini") || id.contains("gpt-5")) && id.contains("image")) return false
            return true
        }

        private fun directoryAdapter() = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(DirectoryResponse::class.java)
    }
}

internal data class DirectoryResponse(val data: List<RawModel>? = null)

internal data class RawModel(
    val id: String? = null,
    val name: String? = null,
    @Json(name = "context_length") val contextLength: Int? = null,
    val pricing: RawPricing? = null,
    val architecture: RawArchitecture? = null,
)

internal data class RawArchitecture(
    @Json(name = "input_modalities") val inputModalities: List<String>? = null,
    @Json(name = "output_modalities") val outputModalities: List<String>? = null,
)

internal data class RawPricing(
    val prompt: String? = null,
    val completion: String? = null,
    @Json(name = "image_output") val imageOutput: String? = null,
)
