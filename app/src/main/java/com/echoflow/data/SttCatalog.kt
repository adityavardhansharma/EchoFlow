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
 * One selectable cloud STT model.
 *
 * [pricing] is a short human-readable line shown on the picker. These three models are curated
 * by us rather than searched, so the price is carried here alongside the id instead of being
 * fetched live — the settings page shows exactly what the user will be billed per the OpenRouter
 * listing.
 */
data class SttModel(
    val id: String,
    val name: String,
    val provider: String,
    val pricing: String,
    val blurb: String,
)

object SttCatalog {
    /** The three cloud options offered on the STT settings page, in display order. */
    val CLOUD_MODELS = listOf(
        SttModel(
            id = "fish-audio/transcribe-1",
            name = "Fish Audio Transcribe 1",
            provider = "Fish Audio",
            pricing = "~\$0.006 / min",
            blurb = "Fast, budget multilingual transcription.",
        ),
        SttModel(
            id = "x-ai/grok-stt-1.0",
            name = "Grok STT 1.0",
            provider = "xAI",
            // OpenRouter lists \$0.10/hour → ≈ \$0.0017/min.
            pricing = "~\$0.0017 / min",
            blurb = "Strong on noisy, conversational speech.",
        ),
        SttModel(
            id = "google/chirp-3",
            name = "Chirp 3",
            provider = "Google",
            // OpenRouter lists \$0.016/min.
            pricing = "~\$0.016 / min",
            blurb = "Broad language coverage, robust punctuation.",
        ),
    )

    const val DEFAULT_MODEL_ID = "fish-audio/transcribe-1"

    fun byId(id: String): SttModel? = CLOUD_MODELS.firstOrNull { it.id == id }

    /** The stored id, falling back to the default when blank or pointing at a removed model. */
    fun resolve(id: String): SttModel = byId(id) ?: CLOUD_MODELS.first()
}
