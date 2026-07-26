package com.echoflow.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One video model from OpenRouter's directory, with the capability sets the submit call must
 * be validated against. Unlike chat models these differ wildly per model — an out-of-set
 * aspect ratio or resolution is a hard 400, so the picker and the request policy both read
 * from here rather than assuming a house default works everywhere.
 */
data class OpenRouterVideoModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val resolutions: List<String> = emptyList(),
    val aspectRatios: List<String> = emptyList(),
    /** Durations the model offers. Informational only — EchoFlow never sends a duration. */
    val durations: List<Int> = emptyList(),
    val frameImageTypes: List<String> = emptyList(),
    val supportsAudio: Boolean = false,
    /** Every priced variant, normalized to USD per output second. Empty when unpriced. */
    val prices: List<VideoPrice> = emptyList(),
) {
    /** True when the model can animate a starting image, not just a text prompt. */
    val supportsFirstFrame: Boolean get() = frameImageTypes.any { it.equals("first_frame", ignoreCase = true) }

    /**
     * "~$0.10/sec at 720p" — the one number that decides whether a user picks this model.
     * Matches the user's actual settings where the provider prices them separately, and falls
     * back to the cheapest variant so the quote is never an unpleasant surprise.
     */
    fun priceHint(resolution: String?, audio: Boolean? = null): String? {
        val price = bestPrice(resolution, audio) ?: return null
        val qualifier = listOfNotNull(
            price.resolution?.let { "at $it" },
            when (price.audio) {
                true -> "with audio"
                false -> "without audio"
                null -> null
            },
        ).joinToString(", ")
        // Locale-fixed: the figure is quoted in USD alongside a "$", so a locale that swaps
        // in a decimal comma would read as a different currency convention entirely.
        return "~$" + String.format(java.util.Locale.US, "%.2f", price.usdPerSecond) + "/sec" +
            if (qualifier.isEmpty()) "" else " $qualifier"
    }

    /** The variant closest to what the user has configured, cheapest-first among equals. */
    fun bestPrice(resolution: String?, audio: Boolean? = null): VideoPrice? {
        if (prices.isEmpty()) return null
        fun pick(predicate: (VideoPrice) -> Boolean) = prices.filter(predicate).minByOrNull { it.usdPerSecond }
        return pick { it.resolution == resolution && it.audio == audio }
            ?: pick { it.resolution == resolution && it.audio == null }
            ?: pick { it.resolution == null && it.audio == audio }
            ?: pick { it.resolution == resolution }
            ?: prices.minByOrNull { it.usdPerSecond }
    }
}

/**
 * One priced variant of a video model. Providers bill per output second but slice that price
 * by resolution, by audio, or by both — null on either axis means "this price applies
 * regardless of that setting".
 */
data class VideoPrice(
    val resolution: String?,
    val audio: Boolean?,
    val usdPerSecond: Double,
)

/**
 * Fetches OpenRouter's public video-model directory
 * (https://openrouter.ai/api/v1/videos/models — no auth needed) once per process and serves
 * it from memory, so the add-model search filters instantly as the user types.
 */
class OpenRouterVideoModelDirectory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(VideoDirectoryResponse::class.java)

    @Volatile
    private var cache: List<OpenRouterVideoModelInfo>? = null

    suspend fun allModels(): List<OpenRouterVideoModelInfo> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }

        val request = Request.Builder().url("https://openrouter.ai/api/v1/videos/models").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Could not load the video model directory (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            val models = adapter.fromJson(body)?.data.orEmpty()
                .mapNotNull { raw -> raw.id?.let { toInfo(it, raw) } }
                .sortedBy { it.name.lowercase() }
            cache = models
            models
        }
    }

    /** The capability record for one model, or null if it is not (or no longer) offered. */
    suspend fun capabilities(modelId: String): OpenRouterVideoModelInfo? =
        allModels().firstOrNull { it.id == modelId }

    private fun toInfo(id: String, raw: RawVideoModel) = OpenRouterVideoModelInfo(
        id = id,
        name = raw.name ?: id,
        description = raw.description,
        resolutions = raw.supportedResolutions.orEmpty(),
        aspectRatios = raw.supportedAspectRatios.orEmpty(),
        durations = raw.supportedDurations.orEmpty(),
        frameImageTypes = raw.supportedFrameImages.orEmpty(),
        supportsAudio = raw.generateAudio == true,
        prices = parsePricing(raw.pricingSkus.orEmpty()),
    )

    companion object {
        private const val CENTS_PREFIX = "cents_per_video_output_second_"
        private const val DOLLARS_PREFIX = "duration_seconds_"
        private val RESOLUTION_TOKEN = Regex("^\\d+p$|^\\d+k$", RegexOption.IGNORE_CASE)

        /**
         * OpenRouter prices video per output second under several SKU spellings in the very
         * same response: `cents_per_video_output_second_720p` (cents, resolution only) and
         * `duration_seconds_…` (dollars) which may carry a resolution, an audio variant
         * (`with_audio`, `without_audio_4k`), or neither. Everything normalizes to USD per
         * second here; anything unrecognized is dropped rather than shown as free.
         */
        fun parsePricing(skus: Map<String, String>): List<VideoPrice> = skus.mapNotNull { (key, value) ->
            val amount = value.toDoubleOrNull() ?: return@mapNotNull null
            when {
                key.startsWith(CENTS_PREFIX) ->
                    VideoPrice(normalizeResolution(key.removePrefix(CENTS_PREFIX)), null, amount / 100.0)
                key.startsWith(DOLLARS_PREFIX) -> {
                    var rest = key.removePrefix(DOLLARS_PREFIX)
                    val audio = when {
                        rest.startsWith("with_audio") -> true.also { rest = rest.removePrefix("with_audio") }
                        rest.startsWith("without_audio") -> false.also { rest = rest.removePrefix("without_audio") }
                        else -> null
                    }
                    VideoPrice(normalizeResolution(rest.trim('_')), audio, amount)
                }
                else -> null
            }
        }.distinct()

        /** "720p"/"4k" stay; anything that is not a resolution token becomes null. */
        private fun normalizeResolution(token: String): String? = token
            .takeIf { it.isNotBlank() && RESOLUTION_TOKEN.matches(it) }
            ?.let { if (it.endsWith("k", ignoreCase = true)) it.uppercase() else it.lowercase() }
    }
}

private data class VideoDirectoryResponse(val data: List<RawVideoModel>? = null)

private data class RawVideoModel(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @Json(name = "supported_resolutions") val supportedResolutions: List<String>? = null,
    @Json(name = "supported_aspect_ratios") val supportedAspectRatios: List<String>? = null,
    @Json(name = "supported_durations") val supportedDurations: List<Int>? = null,
    @Json(name = "supported_frame_images") val supportedFrameImages: List<String>? = null,
    @Json(name = "generate_audio") val generateAudio: Boolean? = null,
    @Json(name = "pricing_skus") val pricingSkus: Map<String, String>? = null,
)
