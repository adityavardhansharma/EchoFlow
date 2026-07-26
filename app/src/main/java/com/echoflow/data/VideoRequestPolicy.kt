package com.echoflow.data

/**
 * Pure rules for turning the user's video preferences into a request the chosen model will
 * actually accept. Aspect ratio and resolution are per-model on OpenRouter — an out-of-set
 * value is a hard 400 — so every preference is reconciled against the model's declared
 * capabilities before it is sent, and dropped entirely when the model declares nothing.
 *
 * Duration is deliberately not part of this: the model decides how long the clip runs.
 */
object VideoRequestPolicy {

    /** The ratios EchoFlow offers, widest-to-tallest. Also the fallback preference order. */
    val ASPECT_RATIOS = listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")

    /** The resolutions EchoFlow offers, cheapest first. */
    val RESOLUTIONS = listOf("480p", "720p", "1080p")

    const val DEFAULT_ASPECT_RATIO = "16:9"
    const val DEFAULT_RESOLUTION = "720p"

    /**
     * The aspect ratio to send. Null means "say nothing and let the provider choose" — the
     * correct move when a model publishes no ratio set at all (several image-to-video models
     * derive framing from the input image instead).
     */
    fun resolveAspectRatio(preferred: String, supported: List<String>): String? {
        if (supported.isEmpty()) return null
        if (preferred in supported) return preferred
        // Prefer another ratio with the same orientation before flipping the user's framing.
        val wanted = orientationOf(preferred)
        return supported.firstOrNull { orientationOf(it) == wanted }
            ?: ASPECT_RATIOS.firstOrNull { it in supported }
            ?: supported.first()
    }

    /**
     * The resolution to send, falling back to the closest offer so a 1080p preference on a
     * 720p-only model still runs (at 720p) instead of failing the whole turn.
     */
    fun resolveResolution(preferred: String, supported: List<String>): String? {
        if (supported.isEmpty()) return null
        if (preferred in supported) return preferred
        val wanted = heightOf(preferred) ?: return supported.first()
        return supported.minByOrNull { candidate ->
            val height = heightOf(candidate) ?: Int.MAX_VALUE
            kotlin.math.abs(height - wanted)
        } ?: supported.first()
    }

    /** Width ÷ height for a "W:H" ratio, used to size the in-chat placeholder and player. */
    fun aspectRatioValue(ratio: String?): Float {
        val parts = ratio?.split(":")?.mapNotNull { it.trim().toFloatOrNull() } ?: return DEFAULT_RATIO_VALUE
        if (parts.size != 2 || parts[1] <= 0f || parts[0] <= 0f) return DEFAULT_RATIO_VALUE
        return parts[0] / parts[1]
    }

    /** "landscape" | "portrait" | "square" — what the user actually cares about preserving. */
    fun orientationOf(ratio: String): String {
        val value = aspectRatioValue(ratio)
        return when {
            value > 1.02f -> "landscape"
            value < 0.98f -> "portrait"
            else -> "square"
        }
    }

    private fun heightOf(resolution: String): Int? =
        resolution.trim().removeSuffix("p").removeSuffix("P").toIntOrNull()

    private const val DEFAULT_RATIO_VALUE = 16f / 9f
}
