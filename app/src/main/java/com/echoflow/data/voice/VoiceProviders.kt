package com.echoflow.data.voice

import com.echoflow.data.CustomProviderConfig
import java.util.Locale

/**
 * Who runs a voice call. The call screen is the same for all three; only the connection differs.
 *
 * - [ElevenLabs]: ElevenLabs Agents. Any LLM from ElevenLabs' list, any voice from its library.
 * - [Gemini]: the Gemini Live API. One model hears and speaks, with Google's built-in voices.
 * - [OpenAi]: the OpenAI Realtime API. One model hears and speaks, with OpenAI's built-in voices.
 */
enum class VoiceProvider(val label: String, val blurb: String) {
    ElevenLabs(
        label = "ElevenLabs",
        blurb = "Any voice from ElevenLabs' library, answered by the LLM you pick. Billed per minute by ElevenLabs.",
    ),
    Gemini(
        label = "Gemini",
        blurb = "Gemini Live hears and speaks in one model, with Google's built-in voices. Uses your Gemini key.",
    ),
    OpenAi(
        label = "OpenAI",
        blurb = "GPT Realtime hears and speaks in one model, with OpenAI's built-in voices. Uses your OpenAI key.",
    );

    /** Gemini and OpenAI run a speech-to-speech model over their realtime socket. */
    val isRealtime: Boolean get() = this != ElevenLabs

    companion object {
        val DEFAULT = ElevenLabs

        fun fromStorage(value: String?): VoiceProvider = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Which providers can run a call right now. ElevenLabs needs its key to have authenticated (the
 * agent setup depends on it). Gemini and OpenAI need only their key switched on: a bad key shows
 * up as a clear error when the call starts.
 */
data class VoiceProviderKeys(
    val elevenLabs: String = "",
    val elevenLabsVerified: Boolean = false,
    val gemini: String = "",
    val openAi: String = "",
) {
    fun ready(provider: VoiceProvider): Boolean = when (provider) {
        VoiceProvider.ElevenLabs -> elevenLabs.isNotEmpty() && elevenLabsVerified
        VoiceProvider.Gemini -> gemini.isNotEmpty()
        VoiceProvider.OpenAi -> openAi.isNotEmpty()
    }

    val readyProviders: List<VoiceProvider> get() = VoiceProvider.entries.filter(::ready)
    val anyReady: Boolean get() = readyProviders.isNotEmpty()

    fun key(provider: VoiceProvider): String = when (provider) {
        VoiceProvider.ElevenLabs -> elevenLabs
        VoiceProvider.Gemini -> gemini
        VoiceProvider.OpenAi -> openAi
    }

    /** The picked provider when it can run; otherwise the first one that can (null: none). */
    fun resolve(picked: VoiceProvider): VoiceProvider? = picked.takeIf(::ready) ?: readyProviders.firstOrNull()

    companion object {
        fun from(config: CustomProviderConfig, elevenLabsVerifiedHash: String?, hash: (String) -> String): VoiceProviderKeys {
            val eleven = if (config.elevenLabsAvailable) config.elevenLabsApiKey.trim() else ""
            return VoiceProviderKeys(
                elevenLabs = eleven,
                elevenLabsVerified = eleven.isNotEmpty() && elevenLabsVerifiedHash == hash(eleven),
                gemini = if (config.geminiAvailable) config.geminiApiKey.trim() else "",
                openAi = if (config.openAiAvailable) config.openAiApiKey.trim() else "",
            )
        }
    }
}

/** A built-in voice of a realtime provider. */
data class RealtimeVoice(val id: String, val traits: String) {
    val name: String get() = id.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/** A speech-to-speech model a realtime provider offers for voice mode. */
data class RealtimeModel(val id: String, val label: String, val blurb: String)

/** Voices and models for Gemini Live and OpenAI Realtime. Both are fixed lists in their docs. */
object RealtimeCatalog {

    /** Gemini Live's 30 HD voices, with Google's one-word descriptions. Puck is Google's default. */
    val GEMINI_VOICES = listOf(
        RealtimeVoice("Puck", "Upbeat"),
        RealtimeVoice("Kore", "Firm"),
        RealtimeVoice("Charon", "Informative"),
        RealtimeVoice("Fenrir", "Excitable"),
        RealtimeVoice("Aoede", "Breezy"),
        RealtimeVoice("Leda", "Youthful"),
        RealtimeVoice("Orus", "Firm"),
        RealtimeVoice("Zephyr", "Bright"),
        RealtimeVoice("Achernar", "Soft"),
        RealtimeVoice("Achird", "Friendly"),
        RealtimeVoice("Algenib", "Gravelly"),
        RealtimeVoice("Algieba", "Smooth"),
        RealtimeVoice("Alnilam", "Firm"),
        RealtimeVoice("Autonoe", "Bright"),
        RealtimeVoice("Callirrhoe", "Easy-going"),
        RealtimeVoice("Despina", "Smooth"),
        RealtimeVoice("Enceladus", "Breathy"),
        RealtimeVoice("Erinome", "Clear"),
        RealtimeVoice("Gacrux", "Mature"),
        RealtimeVoice("Iapetus", "Clear"),
        RealtimeVoice("Laomedeia", "Upbeat"),
        RealtimeVoice("Pulcherrima", "Forward"),
        RealtimeVoice("Rasalgethi", "Informative"),
        RealtimeVoice("Sadachbia", "Lively"),
        RealtimeVoice("Sadaltager", "Knowledgeable"),
        RealtimeVoice("Schedar", "Even"),
        RealtimeVoice("Sulafat", "Warm"),
        RealtimeVoice("Umbriel", "Easy-going"),
        RealtimeVoice("Vindemiatrix", "Gentle"),
        RealtimeVoice("Zubenelgenubi", "Casual"),
    )
    const val GEMINI_DEFAULT_VOICE = "Puck"

    /** OpenAI's Realtime voices. Marin and Cedar are the newest and sound the most natural. */
    val OPENAI_VOICES = listOf(
        RealtimeVoice("marin", "Warm and natural · newest"),
        RealtimeVoice("cedar", "Calm and natural · newest"),
        RealtimeVoice("alloy", "Neutral and balanced"),
        RealtimeVoice("ash", "Clear and confident"),
        RealtimeVoice("ballad", "Soft and expressive"),
        RealtimeVoice("coral", "Bright and friendly"),
        RealtimeVoice("echo", "Steady and warm"),
        RealtimeVoice("sage", "Gentle and thoughtful"),
        RealtimeVoice("shimmer", "Light and clear"),
        RealtimeVoice("verse", "Lively and versatile"),
    )
    const val OPENAI_DEFAULT_VOICE = "marin"

    /** Gemini's current Live model (the 3.1 and 2.5 Live previews shut down 17 Nov 2026). */
    val GEMINI_MODELS = listOf(
        RealtimeModel("gemini-3.8-live", "Gemini 3.8 Live", "Google's current Live model: native audio in and out."),
    )
    const val GEMINI_DEFAULT_MODEL = "gemini-3.8-live"

    val OPENAI_MODELS = listOf(
        RealtimeModel("gpt-realtime-2.1", "GPT Realtime 2.1", "OpenAI's best voice model."),
        RealtimeModel("gpt-realtime-2.1-mini", "GPT Realtime 2.1 Mini", "Lighter and about a third of the price."),
    )
    const val OPENAI_DEFAULT_MODEL = "gpt-realtime-2.1"

    fun voices(provider: VoiceProvider): List<RealtimeVoice> = when (provider) {
        VoiceProvider.Gemini -> GEMINI_VOICES
        VoiceProvider.OpenAi -> OPENAI_VOICES
        VoiceProvider.ElevenLabs -> emptyList()
    }

    fun models(provider: VoiceProvider): List<RealtimeModel> = when (provider) {
        VoiceProvider.Gemini -> GEMINI_MODELS
        VoiceProvider.OpenAi -> OPENAI_MODELS
        VoiceProvider.ElevenLabs -> emptyList()
    }

    /** [id] when the provider still offers it, otherwise the provider's default voice. */
    fun voiceOrDefault(provider: VoiceProvider, id: String?): String {
        val list = voices(provider)
        return list.firstOrNull { it.id.equals(id, ignoreCase = true) }?.id
            ?: if (provider == VoiceProvider.Gemini) GEMINI_DEFAULT_VOICE else OPENAI_DEFAULT_VOICE
    }

    fun modelOrDefault(provider: VoiceProvider, id: String?): String {
        val list = models(provider)
        return list.firstOrNull { it.id == id }?.id
            ?: if (provider == VoiceProvider.Gemini) GEMINI_DEFAULT_MODEL else OPENAI_DEFAULT_MODEL
    }

    fun modelLabel(id: String): String =
        (GEMINI_MODELS + OPENAI_MODELS).firstOrNull { it.id == id }?.label ?: VoiceLlms.label(id)
}
