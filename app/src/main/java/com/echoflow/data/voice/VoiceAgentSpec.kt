package com.echoflow.data.voice

import com.echoflow.data.ChatMessage
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The ElevenLabs agent EchoFlow keeps on the user's account, and what each session sends it.
 *
 * One agent per key carries the settings that change rarely (LLM, voice, TTS model, language,
 * which events the app needs). Each session then overrides only the per-conversation parts: the
 * system prompt with this chat's history, the greeting, and, for safety, the LLM and voice too, so
 * a settings change made a moment ago is honoured even before the agent update lands.
 */
internal object VoiceAgentSpec {
    const val AGENT_NAME = "EchoFlow Voice"

    /** Hard ceiling on one session. Idle sessions end long before this (see VoiceModeController). */
    const val MAX_DURATION_SECONDS = 3600

    /** The input format the app records in. The output format is whatever the agent announces. */
    const val INPUT_AUDIO_FORMAT = "pcm_16000"

    /** Everything the app reads off the socket; anything not listed here is never sent. */
    val CLIENT_EVENTS = listOf(
        "conversation_initiation_metadata",
        "audio",
        "agent_response",
        "agent_response_correction",
        "agent_chat_response_part",
        "user_transcript",
        "tentative_user_transcript",
        "interruption",
        "ping",
        "vad_score",
    )

    /** Asked for only when the agent carries the card tool, since that's the event that runs it. */
    const val TOOL_EVENT = "client_tool_call"

    const val GREETING = "Hey! What's on your mind?"

    /**
     * The full agent definition, for create and update alike. [withTools] adds the card tool; an
     * account that rejects it still gets a working agent without it.
     */
    fun agentBody(
        config: VoiceModeConfig,
        ttsModelId: String = config.effectiveStyle.modelId,
        withTools: Boolean = true,
    ): Map<String, Any?> {
        val style = config.effectiveStyle
        val prompt = linkedMapOf<String, Any?>(
            "prompt" to basePrompt(),
            "llm" to config.llm,
        )
        if (withTools) prompt["tools"] = listOf(VoicePlanTool.definition())
        return linkedMapOf(
            "name" to AGENT_NAME,
            "tags" to listOf("echoflow"),
            "conversation_config" to linkedMapOf(
                "agent" to linkedMapOf(
                    "first_message" to "",
                    "language" to config.language,
                    "prompt" to prompt,
                ),
                "tts" to linkedMapOf(
                    "model_id" to ttsModelId,
                    "voice_id" to config.voiceId,
                    "expressive_mode" to style.expressiveMode,
                ),
                "asr" to linkedMapOf("user_input_audio_format" to INPUT_AUDIO_FORMAT),
                "conversation" to linkedMapOf(
                    "max_duration_seconds" to MAX_DURATION_SECONDS,
                    "client_events" to if (withTools) CLIENT_EVENTS + TOOL_EVENT else CLIENT_EVENTS,
                ),
            ),
            "platform_settings" to linkedMapOf(
                // Overrides are off until the agent allows each one; these are the only ones sent.
                "overrides" to linkedMapOf(
                    "conversation_config_override" to linkedMapOf(
                        "agent" to linkedMapOf(
                            "first_message" to true,
                            "language" to true,
                            "prompt" to linkedMapOf("prompt" to true, "llm" to true),
                        ),
                        "tts" to linkedMapOf("voice_id" to true),
                    ),
                ),
            ),
        )
    }

    /** Fingerprint of [agentBody]: an unchanged hash means the agent on ElevenLabs is current. */
    fun configHash(config: VoiceModeConfig, ttsModelId: String = config.effectiveStyle.modelId): String =
        MessageDigest.getInstance("SHA-256")
            .digest(VoiceApiJson.write(agentBody(config, ttsModelId)).toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    /** The first message on the socket: this conversation's prompt, greeting and settings. */
    fun initiation(
        config: VoiceModeConfig,
        history: List<ChatMessage>,
        projectContext: String = "",
        now: Date = Date(),
        /** False when the user is already talking: a hello would talk over them. */
        greet: Boolean = true,
    ): Map<String, Any?> = linkedMapOf(
        "type" to "conversation_initiation_client_data",
        "conversation_config_override" to linkedMapOf(
            "agent" to linkedMapOf(
                "prompt" to linkedMapOf(
                    "prompt" to sessionPrompt(history, now, projectContext),
                    "llm" to config.llm,
                ),
                // A new chat opens with a short hello; a continued one waits for the user.
                "first_message" to if (greet && spokenHistory(history).isEmpty()) GREETING else null,
                "language" to config.language,
            ),
            "tts" to config.voiceId?.let { linkedMapOf("voice_id" to it) },
        ),
        // No source_info: ElevenLabs only accepts its own SDK names there and rejects the whole
        // message for any other value.
    )

    /** How the agent talks. Everything it writes is spoken aloud, so the rules are about speech. */
    fun basePrompt(): String =
        "You are Echo, the voice of the EchoFlow app, talking with the user out loud in real time.\n\n" +
            "How to speak:\n" +
            "- Talk like a thoughtful friend on a call: warm, direct and natural. Use short sentences.\n" +
            "- Keep answers brief, usually one to three sentences. Go longer only when the user asks for detail, " +
            "and then give it in a few spoken steps rather than one long block.\n" +
            "- Everything you write is read aloud. Never use markdown, headings, bullet symbols, tables, code " +
            "blocks, links, URLs or emoji.\n" +
            "- Say numbers, dates, times, money and symbols the way a person would say them.\n" +
            "- If you need something clarified, ask one short question.\n" +
            "- The user may type some messages instead of speaking. Answer typed and spoken messages the same way.\n" +
            "- Reply in the language the user speaks. If they mix languages, such as Hindi and English, " +
            "reply in the same mix.\n" +
            "- If you are interrupted, stop and listen; don't repeat what you already said unless asked.\n\n" +
            "The user's screen:\n" +
            "- When an answer has a shape worth seeing, such as a plan, steps, a short list of options or a " +
            "schedule, also call the show_card tool with a short title and one line per item, then keep talking " +
            "as usual. Don't read the card out word for word and never mention the tool.\n" +
            "- When the user changes something on the card, call show_card again with the whole updated card.\n" +
            "- Small talk and one-line answers need no card."

    /**
     * [basePrompt] plus today's date, the project's instructions and documents when the chat is
     * in a project (the same context a typed turn gets), and the chat so far.
     */
    fun sessionPrompt(history: List<ChatMessage>, now: Date = Date(), projectContext: String = ""): String {
        val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now)
        val transcript = historyTranscript(history)
        return buildString {
            append(basePrompt())
            append("\n\nToday is ").append(date).append('.')
            if (projectContext.isNotBlank()) {
                append("\n\n").append(projectContext.trim())
            }
            if (transcript.isNotEmpty()) {
                append(
                    "\n\nThis call continues an existing EchoFlow chat. The conversation so far, oldest first, " +
                        "is below. Carry on from it naturally; don't summarise or repeat it unless asked.\n\n",
                )
                append(transcript)
            }
        }
    }

    /** User and assistant turns that have text: tool rows, schedule events and blanks are skipped. */
    fun spokenHistory(history: List<ChatMessage>): List<ChatMessage> =
        history.filter { (it.role == "user" || it.role == "assistant") && it.content.isNotBlank() && it.scheduleEvent == null }

    /**
     * The newest turns that fit in [maxChars], each clipped to [maxTurnChars]. Older turns drop
     * first, so the agent always sees how the chat ended.
     */
    fun historyTranscript(
        history: List<ChatMessage>,
        maxChars: Int = MAX_HISTORY_CHARS,
        maxTurnChars: Int = MAX_TURN_CHARS,
    ): String {
        val lines = ArrayList<String>()
        var used = 0
        for (message in spokenHistory(history).asReversed()) {
            val text = message.content.trim().replace(Regex("\\s+"), " ")
            val clipped = if (text.length > maxTurnChars) text.take(maxTurnChars).trimEnd() + "…" else text
            val line = (if (message.role == "user") "User: " else "Assistant: ") + clipped
            if (used + line.length > maxChars && lines.isNotEmpty()) break
            lines.add(line)
            used += line.length + 1
        }
        return lines.asReversed().joinToString("\n")
    }

    private const val MAX_HISTORY_CHARS = 12_000
    private const val MAX_TURN_CHARS = 1_500
}
