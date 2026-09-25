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
    /** True when the model accepts a custom vocabulary list (see [DictationVocabulary]). */
    val supportsCustomVocabulary: Boolean = false,
    /** Picker badge for [supportsCustomVocabulary] models, named after what the provider calls it. */
    val vocabularyLabel: String = "Custom vocabulary",
    /** Optional display classification; the numeric price remains the billing reference. */
    private val costTierOverride: SttCostTier? = null,
) {
    val costTier: SttCostTier get() = costTierOverride ?: SttCostTier.fromUsdPerMinute(usdPerMinute)
}

object SttCatalog {
    const val MAI_MODEL_ID = "microsoft/mai-transcribe-2"
    const val MUSE_MODEL_ID = "meta/muse-voice-transcribe-1.0"
    const val GROK_MODEL_ID = "x-ai/grok-stt-1.0"
    const val GEMINI_TRANSCRIBE_MODEL_ID = "google/gemini-3.5-transcribe"
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

    /** Deepgram's prerecorded model id; `language=multi` makes it Nova-3 Multilingual. */
    const val DEEPGRAM_MODEL_ID = "nova-3"
    val DEEPGRAM_MODEL = SttModel(
        id = DEEPGRAM_MODEL_ID,
        name = "Nova-3 Multilingual",
        provider = "Deepgram",
        // Deepgram pay-as-you-go pre-recorded (2026-09): Nova-3 Multilingual $0.0052/min
        // → $0.312/hour. Keyterm Prompting adds $0.0013/min (~$0.078/hour) only when keyterms
        // are sent; Smart Formatting is included.
        pricing = "~\$0.312 / hr",
        blurb = "Fast, accurate multilingual dictation with smart formatting.",
        usdPerMinute = 0.0052,
        supportsCustomVocabulary = true,
        vocabularyLabel = "Keyterms",
    )

    fun sarvamAvailable(config: CustomProviderConfig): Boolean =
        config.sarvamAvailable

    fun deepgramAvailable(config: CustomProviderConfig): Boolean =
        config.deepgramAvailable

    /** OpenRouter models plus direct-provider models whose key is saved under Custom. */
    fun availableModels(config: CustomProviderConfig): List<SttModel> = buildList {
        addAll(CLOUD_MODELS)
        if (sarvamAvailable(config)) add(SARVAM_MODEL)
        if (deepgramAvailable(config)) add(DEEPGRAM_MODEL)
    }

    /** True for models billed to a direct provider key rather than OpenRouter. */
    fun usesDirectKey(modelId: String): Boolean =
        modelId == SARVAM_MODEL_ID || modelId == DEEPGRAM_MODEL_ID

    fun apiKey(modelId: String, openRouterKey: String, config: CustomProviderConfig): String = when (modelId) {
        SARVAM_MODEL_ID -> if (sarvamAvailable(config)) config.sarvamApiKey else ""
        DEEPGRAM_MODEL_ID -> if (deepgramAvailable(config)) config.deepgramApiKey else ""
        else -> openRouterKey
    }

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
            id = GEMINI_TRANSCRIBE_MODEL_ID,
            name = "Gemini 3.5 Transcribe",
            provider = "Google",
            // OpenRouter bills 25 audio tokens per second at $2/M and reports no output tokens,
            // so cost tracks recording length only: $0.003/min → $0.18/hour (measured on
            // 14 s and 78 s clips, with and without custom vocabulary).
            pricing = "~\$0.18 / hr",
            blurb = "Learns your names and terms through a custom vocabulary.",
            usdPerMinute = 0.18 / 60.0,
            supportsCustomVocabulary = true,
            // $0.003/min sits exactly on the Cheap/Moderate cutoff; pin it so rounding can't flip the tag.
            costTierOverride = SttCostTier.Moderate,
        ),
        SttModel(
            id = MUSE_MODEL_ID,
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
            id = GROK_MODEL_ID,
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

    /** One-shot recovery route for OpenRouter request-validation failures. */
    fun fallbackForBadRequest(modelId: String): String =
        if (modelId == MUSE_MODEL_ID) GROK_MODEL_ID else MUSE_MODEL_ID

    fun supportsCustomVocabulary(id: String): Boolean = byId(id)?.supportsCustomVocabulary == true

    fun byId(id: String): SttModel? = (CLOUD_MODELS + SARVAM_MODEL + DEEPGRAM_MODEL).firstOrNull { it.id == id }

    fun resolveAvailable(id: String, config: CustomProviderConfig): SttModel =
        availableModels(config).firstOrNull { it.id == id } ?: CLOUD_MODELS.first()

    /** The stored id, falling back to the default when blank or pointing at a removed model. */
    fun resolve(id: String): SttModel = byId(id) ?: CLOUD_MODELS.first()
}
