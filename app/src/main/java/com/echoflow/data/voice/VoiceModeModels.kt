package com.echoflow.data.voice

import java.util.Locale

/**
 * How voice mode sounds. Both run inside ElevenLabs Agents, which bills per minute whichever one
 * is picked, so expressive costs no more than fast.
 *
 * - [Expressive] is Eleven v4 Turbo with expressive mode on: it reads tone and emotion and speaks
 *   with them, in 90+ languages, at about 100 ms median latency.
 * - [Fast] is Flash v2.5: the lowest latency, and the one that keeps a Professional Voice Clone
 *   sounding exactly like its owner (expressive mode does not preserve PVC character).
 */
enum class VoiceStyle(val label: String, val modelId: String, val expressiveMode: Boolean, val blurb: String) {
    Expressive(
        label = "Expressive",
        modelId = "eleven_v4_turbo",
        expressiveMode = true,
        blurb = "Eleven v4 Turbo. Hears how you sound and answers with matching tone and emotion. Same price as Fast.",
    ),
    Fast(
        label = "Fast",
        modelId = "eleven_flash_v2_5",
        expressiveMode = false,
        blurb = "Flash v2.5. The quickest replies, and cloned voices stay exactly like the original.",
    );

    companion object {
        val DEFAULT = Expressive

        /**
         * Expressive mode's previous model. Used only when an account rejects v4 Turbo, so
         * expressive still works there rather than voice mode failing to start.
         */
        const val EXPRESSIVE_FALLBACK_MODEL_ID = "eleven_v3_conversational"

        fun fromStorage(value: String?): VoiceStyle = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** One voice the user can talk to: from their own voices, or from the public Voice Library. */
data class VoiceOption(
    val voiceId: String,
    val name: String,
    /** premade, cloned, generated, professional, famous or high_quality. */
    val category: String? = null,
    val description: String? = null,
    val accent: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val useCase: String? = null,
    val language: String? = null,
    val previewUrl: String? = null,
    /** Set for a Voice Library voice: it must be added to My Voices before an agent can use it. */
    val library: LibraryVoiceRef? = null,
) {
    val isProfessionalClone: Boolean get() = category == "professional"

    /** "British · Female · Young · Narration", skipping whatever ElevenLabs left blank. */
    val traits: String
        get() = listOfNotNull(accent, gender, age, useCase)
            .map { it.replace('_', ' ').trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}

data class LibraryVoiceRef(
    val publicOwnerId: String,
    /** False when the owner keeps the voice off the free plan. */
    val freeUsersAllowed: Boolean,
)

/** One page of a voice search. [next] is opaque: a page token for My Voices, a page number for the library. */
data class VoicePage(val voices: List<VoiceOption>, val next: String?)

/** The ElevenLabs plan behind the key, from `GET /v1/user/subscription`. */
data class VoiceAccount(
    val tier: String,
    val status: String?,
    val creditsUsed: Long,
    val creditLimit: Long,
    val resetsAtUnix: Long?,
) {
    val isFree: Boolean get() = tier.equals("free", ignoreCase = true)
    val creditsLeft: Long get() = (creditLimit - creditsUsed).coerceAtLeast(0)

    /** "Free", "Creator", "Pro"…: the tier as ElevenLabs names its plans. */
    val planLabel: String
        get() = tier.split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .ifBlank { "ElevenLabs" }
}

/** An LLM ElevenLabs Agents can answer with, from `GET /v1/convai/llm/list`. */
data class VoiceLlm(val id: String, val deprecated: Boolean = false) {
    val label: String get() = VoiceLlms.label(id)
}

/** A finished voice conversation as ElevenLabs stored it. */
data class VoiceConversationRecord(
    val conversationId: String,
    /** initiated, in-progress, processing, done or failed. */
    val status: String,
    val turns: List<VoiceTranscriptTurn>,
    val durationSeconds: Double?,
    /** What the call cost in ElevenLabs credits. */
    val credits: Double?,
    /** What the call cost in the account's currency, when ElevenLabs reports it. */
    val costFiat: Double?,
    val llmChargeUsd: Double?,
) {
    val isFinal: Boolean get() = status == "done" || status == "failed"
}

data class VoiceTranscriptTurn(val fromUser: Boolean, val text: String, val timeInCallSeconds: Double)

/** Everything voice mode is set to, as one snapshot. */
data class VoiceModeConfig(
    val llm: String = VoiceLlms.DEFAULT,
    val style: VoiceStyle = VoiceStyle.DEFAULT,
    val voiceId: String? = null,
    val voiceName: String? = null,
    val voiceCategory: String? = null,
    val language: String = VoiceLanguages.DEFAULT,
) {
    /**
     * The TTS model the agent actually runs. A Professional Voice Clone loses its character in
     * expressive mode, so that one voice quietly uses [VoiceStyle.Fast] even when Expressive is
     * picked.
     */
    val effectiveStyle: VoiceStyle
        get() = if (style == VoiceStyle.Expressive && voiceCategory == "professional") VoiceStyle.Fast else style
}

/** The LLMs voice mode offers when ElevenLabs' own list can't be fetched, and how to name them. */
object VoiceLlms {
    /**
     * Picked for live talk: a fast first token matters more than depth in a back-and-forth.
     * The first of these the account offers becomes the default.
     */
    val PREFERRED = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.5-flash",
        "claude-haiku-5-5",
        "gpt-5.4-mini",
        "gemini-2.5-flash",
    )
    const val DEFAULT = "gemini-3.8-flash"

    /** A short, current list from ElevenLabs' published `Llm` enum, used offline. */
    val FALLBACK = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-pro-preview",
        "claude-haiku-5-5",
        "claude-sonnet-5-5",
        "claude-opus-5-5",
        "gpt-5.4-mini",
        "gpt-5.5",
        "gpt-6.1-sol",
        "qwen35-397b-a17b",
        "gpt-oss-120b",
    ).map { VoiceLlm(it) }

    /**
     * What to offer: live models only, no dated snapshots (`gpt-4o-2024-11-20`,
     * `claude-sonnet-4@20250514`) and no `custom-llm`, which needs a server of its own.
     */
    fun selectable(all: List<VoiceLlm>): List<VoiceLlm> = all
        .filter { !it.deprecated && it.id != "custom-llm" && !isSnapshot(it.id) }
        .distinctBy { it.id }
        .sortedWith(compareBy<VoiceLlm>({ familyRank(it.id) }, { -versionWeight(it.id) }, { it.id }))

    /** The model to use when the saved one isn't offered any more. */
    fun defaultFrom(available: List<VoiceLlm>): String {
        val ids = available.map { it.id }.toSet()
        return PREFERRED.firstOrNull { it in ids } ?: available.firstOrNull()?.id ?: DEFAULT
    }

    fun isSnapshot(id: String): Boolean =
        '@' in id || Regex("-20\\d{2}-\\d{2}-\\d{2}$").containsMatchIn(id) ||
            Regex("-\\d{3}$").containsMatchIn(id) || Regex("-preview-\\d{2}-?\\d{2,4}").containsMatchIn(id)

    private fun familyRank(id: String): Int = when {
        id.startsWith("gemini") -> 0
        id.startsWith("claude") -> 1
        id.startsWith("gpt-oss") -> 3
        id.startsWith("gpt") -> 2
        else -> 4
    }

    /** Newer versions first inside a family: the numbers in the id, read as one decimal. */
    private fun versionWeight(id: String): Double {
        val numbers = Regex("\\d+(\\.\\d+)?").findAll(id).map { it.value }.toList()
        val head = numbers.firstOrNull()?.toDoubleOrNull() ?: return 0.0
        val minor = numbers.getOrNull(1)?.toDoubleOrNull()?.takeIf { it < 100 } ?: 0.0
        return head + minor / 100.0
    }

    /**
     * "gemini-3.8-flash" → "Gemini 3.8 Flash", "claude-haiku-5-5" → "Claude Haiku 5.5",
     * "gpt-5.4-mini" → "GPT-5.4 Mini", "qwen35-397b-a17b" → "Qwen35 397B A17B".
     */
    fun label(id: String): String {
        val parts = id.split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return id
        val out = ArrayList<String>()
        var i = 0
        while (i < parts.size) {
            val part = parts[i]
            // Claude writes versions as "5-5": join a run of bare integers with dots.
            if (part.all(Char::isDigit) && i + 1 < parts.size && parts[i + 1].all(Char::isDigit) && id.startsWith("claude")) {
                out.add("$part.${parts[i + 1]}")
                i += 2
                continue
            }
            out.add(
                when (part.lowercase(Locale.ROOT)) {
                    "gpt" -> "GPT"
                    "oss" -> "OSS"
                    "glm" -> "GLM"
                    "deepseek" -> "DeepSeek"
                    // Sizes and versions ("397b", "a17b", "4o") read as written in caps.
                    else -> if (Regex("^[a-z]?\\d+(\\.\\d+)?[a-z]?$").matches(part)) part.uppercase(Locale.ROOT)
                    else part.replaceFirstChar { it.titlecase(Locale.ROOT) }
                },
            )
            i++
        }
        // "GPT 5.4 Mini" reads better as "GPT-5.4 Mini", the way OpenAI writes it.
        if (out.size >= 2 && out[0] == "GPT" && out[1].firstOrNull()?.isDigit() == true) {
            out[1] = "GPT-" + out[1]
            out.removeAt(0)
        }
        return out.joinToString(" ")
    }
}

/** Languages an ElevenLabs agent accepts for `agent.language`, named in English. */
object VoiceLanguages {
    const val DEFAULT = "en"

    val CODES = listOf(
        "en", "hi", "es", "fr", "de", "it", "pt", "pt-br", "ja", "ko", "zh", "ar", "ru", "nl", "tr",
        "pl", "sv", "id", "vi", "th", "uk", "cs", "el", "fi", "da", "no", "hu", "ro", "bg", "hr",
        "sk", "ms", "ta", "te", "bn", "mr", "gu", "kn", "ml", "pa", "ur", "fa", "he", "tl", "sw",
    )

    fun label(code: String): String = when (code) {
        "pt-br" -> "Portuguese (Brazil)"
        "zh" -> "Chinese"
        "tl" -> "Filipino"
        else -> Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
    }

    /** Sorted by name, with English first: most people pick from the top. */
    val options: List<Pair<String, String>>
        get() = CODES.map { it to label(it) }
            .sortedWith(compareBy({ it.first != DEFAULT }, { it.second }))
}
