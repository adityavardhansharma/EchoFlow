package com.echoflow.data

/**
 * Speech-to-text for the chat composer (dictation — talk, it lands as text you can edit and
 * send; not a live voice conversation).
 *
 * Dictation uses the selected provider’s key, independently of the chat model.
 */
enum class SttMode(val storageKey: String) {
    Cloud("cloud"),
    OnDevice("on_device");

    companion object {
        fun fromStorage(raw: String?): SttMode = entries.firstOrNull { it.storageKey == raw } ?: Cloud
    }
}

/**
 * OpenRouter hourly price mapped onto the $ / $$ / $$$ tags on the STT picker.
 *
 * Cheap is one red dollar; moderate is two green dollars; expensive is three green dollars.
 * Cutoffs sit in the gaps of the current catalog (Grok ~$0.10, Muse Transcribe $0.18,
 * GPT Transcribe $0.27, Chirp $0.96 per hour) so a listing can move without retuning the UI by hand.
 */
enum class SttCostTier(val dollars: Int) {
    Cheap(1),
    Moderate(2),
    Expensive(3);

    companion object {
        fun fromUsdPerMinute(usd: Double): SttCostTier = when {
            usd < 0.003 -> Cheap
            usd < 0.01 -> Moderate
            else -> Expensive
        }
    }
}

/**
 * One selectable cloud STT model.
 *
 * [pricing] is a short human-readable line shown on the picker. These models are curated
 * by us rather than searched, so the price is carried here alongside the id instead of being
 * fetched live — the settings page shows a per-hour rate in the same `$ / hr` form for
 * every listing.
 *
 * [usdPerMinute] is that rate, normalized to dollars per minute of audio so the
 * dollar-tag cutoffs can compare models billed per second, minute, or hour.
 *
 * [isBest] is true for exactly one model: the recommended dictation pick. MAI-Transcribe-2
 * is first in the list. The other cloud models remain available below it.
 */
data class SttModel(
    val id: String,
    val name: String,
    val provider: String,
    val pricing: String,
    val blurb: String,
    val usdPerMinute: Double,
    val isBest: Boolean = false,
    val showCostTier: Boolean = true,
    /** Optional display classification; the numeric price remains the billing reference. */
    private val costTierOverride: SttCostTier? = null,
) {
    val costTier: SttCostTier get() = costTierOverride ?: SttCostTier.fromUsdPerMinute(usdPerMinute)
}

object SttCatalog {
    const val MAI_MODEL_ID = "microsoft/mai-transcribe-2"
    const val SARVAM_MODEL_ID = "saaras:v4"
    val SARVAM_MODEL = SttModel(
        id = SARVAM_MODEL_ID,
        name = "Saaras v4",
        provider = "Sarvam",
        // Sarvam lists ₹30/hour ≈ $0.36/hour.
        pricing = "~\$0.36 / hr",
        blurb = "22 Indian languages and English, with automatic language detection.",
        usdPerMinute = 0.006,
    )

    fun sarvamAvailable(config: CustomProviderConfig): Boolean =
        config.sarvamAvailable

    fun availableModels(config: CustomProviderConfig): List<SttModel> =
        if (sarvamAvailable(config)) CLOUD_MODELS + SARVAM_MODEL else CLOUD_MODELS

    fun apiKey(modelId: String, openRouterKey: String, config: CustomProviderConfig): String =
        if (modelId == SARVAM_MODEL_ID) {
            if (sarvamAvailable(config)) config.sarvamApiKey else ""
        } else openRouterKey

    /** Cloud options offered on the STT settings page, in display order. */
    val CLOUD_MODELS = listOf(
        SttModel(
            id = MAI_MODEL_ID,
            name = "MAI Transcribe 2",
            provider = "Microsoft AI",
            // OpenRouter lists $0.10/hour.
            pricing = "~\$0.10 / hr",
            blurb = "Clean multilingual dictation with automatic language detection and code switching.",
            usdPerMinute = 0.10 / 60.0,
            isBest = true,
            costTierOverride = SttCostTier.Moderate,
        ),
        SttModel(
            id = "meta/muse-voice-transcribe-1.0",
            name = "Muse Transcribe",
            provider = "Meta",
            // OpenRouter lists $0.18/hour.
            pricing = "~\$0.18 / hr",
            blurb = "Push-to-talk dictation with speaker awareness and keyword biasing.",
            usdPerMinute = 0.18 / 60.0,
        ),
        SttModel(
            id = "openai/gpt-transcribe",
            name = "GPT Transcribe",
            provider = "OpenAI",
            // OpenRouter lists $0.0045/min → $0.27/hour.
            pricing = "~\$0.27 / hr",
            blurb = "High-accuracy dictation, strong on mixed or quiet speech.",
            usdPerMinute = 0.0045,
        ),
        SttModel(
            id = "x-ai/grok-stt-1.0",
            name = "Grok STT 1.0",
            provider = "xAI",
            // OpenRouter lists $0.10/hour.
            pricing = "~\$0.10 / hr",
            blurb = "Strong on noisy, conversational speech.",
            usdPerMinute = 0.10 / 60.0,
        ),
        SttModel(
            id = "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b",
            name = "Nemotron 3.5 ASR",
            provider = "NVIDIA",
            // OpenRouter lists $0.000003/second → $0.0108/hour.
            pricing = "~\$0.0108 / hr",
            blurb = "Low-latency multilingual dictation across 40+ languages.",
            usdPerMinute = 0.000003 * 60.0,
        ),
        SttModel(
            id = "google/chirp-3",
            name = "Chirp 3",
            provider = "Google",
            // OpenRouter lists $0.016/min → $0.96/hour.
            pricing = "~\$0.96 / hr",
            blurb = "Broad language coverage, robust punctuation.",
            usdPerMinute = 0.016,
        ),
    )

    const val DEFAULT_MODEL_ID = MAI_MODEL_ID

    fun byId(id: String): SttModel? = (CLOUD_MODELS + SARVAM_MODEL).firstOrNull { it.id == id }

    fun resolveAvailable(id: String, config: CustomProviderConfig): SttModel =
        availableModels(config).firstOrNull { it.id == id } ?: CLOUD_MODELS.first()

    /** The stored id, falling back to the default when blank or pointing at a removed model. */
    fun resolve(id: String): SttModel = byId(id) ?: CLOUD_MODELS.first()
}
