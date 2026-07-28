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

    /**
     * The ratios EchoFlow shows when it has nothing better to go on, widest-to-tallest. Also
     * the fallback preference order.
     *
     * Not a whitelist. Providers publish their own vocabularies and add to them, so anything
     * that treats this as the set of *permitted* values will silently discard a choice the
     * user could see and press — see [resolutionOffering].
     */
    val ASPECT_RATIOS = listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9")

    /** The resolutions EchoFlow shows when it has nothing better to go on, cheapest first. */
    val RESOLUTIONS = listOf("480p", "720p", "1080p")

    const val DEFAULT_ASPECT_RATIO = "16:9"
    const val DEFAULT_RESOLUTION = "720p"

    /**
     * Everything to put in front of the user: the house list, plus whatever this model declares
     * that the house list has never heard of.
     *
     * The house list is always years behind the providers — it predates 4K entirely — so a
     * screen built from it alone can never offer a model's best output, and a store that
     * validates against it throws away the choice if some other screen manages to. Ordered by
     * real pixel height so the row reads cheapest to dearest whatever gets added.
     */
    fun resolutionOffering(supported: List<String>?): List<String> =
        (RESOLUTIONS + supported.orEmpty()).distinct()
            .sortedBy { heightOf(it) ?: Int.MAX_VALUE }

    /** The same union for framing, for the same reason. */
    fun aspectRatioOffering(supported: List<String>?): List<String> =
        (ASPECT_RATIOS + supported.orEmpty()).distinct()

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

    /**
     * Pixel height for a resolution label, so "nearest offer" is a real comparison.
     *
     * K notation has to be understood, not just tolerated: with "4k" unrankable, a 4K
     * preference on a 1080p model fell through to "whatever is listed first" rather than to
     * the closest thing available, which is the entire point of the fallback.
     */
    private fun heightOf(resolution: String): Int? {
        val value = resolution.trim().lowercase()
        return when {
            value.endsWith("k") -> value.dropLast(1).toFloatOrNull()?.let { (it * 540).toInt() }
            else -> value.removeSuffix("p").toIntOrNull()
        }
    }

    private const val DEFAULT_RATIO_VALUE = 16f / 9f
}
