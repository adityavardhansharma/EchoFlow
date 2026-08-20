package com.echoflow.data

/**
 * Speech-to-text for the chat composer (dictation — talk, it lands as text you can edit and
 * send; not a live voice conversation).
 *
 * STT is deliberately independent of whichever chat model is selected: it *always* runs on
 * OpenRouter using the same key entered under Cloud models, whether the chat is on OpenAI, a
 * local model, Ollama or anything else. There is no second key.
 */
enum class SttMode(val storageKey: String) {
    Cloud("cloud"),
    OnDevice("on_device");

    companion object {
        fun fromStorage(raw: String?): SttMode = entries.firstOrNull { it.storageKey == raw } ?: Cloud
    }
}

/**
 * OpenRouter per-minute price mapped onto the $ / $$ / $$$ tags on the STT picker.
 *
 * Cheap is one red dollar; moderate is two green dollars; expensive is three green dollars.
 * Cutoffs sit in the gaps of the current catalog (Grok ~$0.0017, GPT Transcribe $0.0045,
 * Chirp $0.016) so a listing can move without retuning the UI by hand.
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
 * fetched live — the settings page shows exactly what the user will be billed per the OpenRouter
 * listing.
 *
 * [usdPerMinute] is the same OpenRouter rate, normalized to dollars per minute of audio so the
 * dollar-tag cutoffs can compare models billed per second, minute, or hour.
 *
 * [isBest] is true for exactly one model: the lowest Artificial Analysis word-error rate
 * (AA-WER, non-streaming) among catalog entries that have a published score. As of 2026-08:
 * GPT Transcribe 3.3%, Grok STT 4.0%. Chirp 3 is charted on the streaming leaderboard only;
 * Nemotron 3.5 ASR has no AA-WER yet.
 */
data class SttModel(
    val id: String,
    val name: String,
    val provider: String,
    val pricing: String,
    val blurb: String,
    val usdPerMinute: Double,
    val isBest: Boolean = false,
) {
    val costTier: SttCostTier get() = SttCostTier.fromUsdPerMinute(usdPerMinute)
}

object SttCatalog {
    /** Cloud options offered on the STT settings page, in display order. */
    val CLOUD_MODELS = listOf(
        SttModel(
            id = "openai/gpt-transcribe",
            name = "GPT Transcribe",
            provider = "OpenAI",
            // OpenRouter lists $0.0045/min.
            pricing = "~\$0.0045 / min",
            blurb = "High-accuracy dictation, strong on mixed or quiet speech.",
            usdPerMinute = 0.0045,
            isBest = true,
        ),
        SttModel(
            id = "x-ai/grok-stt-1.0",
            name = "Grok STT 1.0",
            provider = "xAI",
            // OpenRouter lists $0.10/hour → ≈ $0.0017/min.
            pricing = "~\$0.0017 / min",
            blurb = "Strong on noisy, conversational speech.",
            usdPerMinute = 0.10 / 60.0,
        ),
        SttModel(
            id = "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b",
            name = "Nemotron 3.5 ASR",
            provider = "NVIDIA",
            // OpenRouter lists $0.000003/second → $0.00018/min.
            pricing = "~\$0.00018 / min",
            blurb = "Low-latency multilingual dictation across 40+ languages.",
            usdPerMinute = 0.000003 * 60.0,
        ),
        SttModel(
            id = "google/chirp-3",
            name = "Chirp 3",
            provider = "Google",
            // OpenRouter lists $0.016/min.
            pricing = "~\$0.016 / min",
            blurb = "Broad language coverage, robust punctuation.",
            usdPerMinute = 0.016,
        ),
    )

    const val DEFAULT_MODEL_ID = "openai/gpt-transcribe"

    fun byId(id: String): SttModel? = CLOUD_MODELS.firstOrNull { it.id == id }

    /** The stored id, falling back to the default when blank or pointing at a removed model. */
    fun resolve(id: String): SttModel = byId(id) ?: CLOUD_MODELS.first()
}
