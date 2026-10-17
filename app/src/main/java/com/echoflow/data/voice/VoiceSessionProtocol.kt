package com.echoflow.data.voice

/** One message from the ElevenLabs Agents WebSocket, reduced to what voice mode acts on. */
sealed interface VoiceServerEvent {
    data class Metadata(val conversationId: String, val inputSampleRate: Int, val outputSampleRate: Int) : VoiceServerEvent
    class Audio(val pcm: ByteArray, val eventId: Int) : VoiceServerEvent
    data class UserTranscript(val text: String) : VoiceServerEvent
    data class TentativeUserTranscript(val text: String) : VoiceServerEvent
    data class AgentResponse(val text: String) : VoiceServerEvent
    data class AgentResponseCorrection(val original: String, val corrected: String) : VoiceServerEvent
    /** A streamed piece of the reply's text: [kind] is start, delta or stop. */
    data class AgentTextPart(val kind: String, val text: String) : VoiceServerEvent
    data class Interruption(val eventId: Int) : VoiceServerEvent
    data class Ping(val eventId: Int) : VoiceServerEvent
    data class Vad(val score: Float) : VoiceServerEvent
    data class Error(val message: String) : VoiceServerEvent
    data object Ignored : VoiceServerEvent
}

/**
 * The Agents WebSocket protocol: parses what the server sends and builds what the client sends.
 * Pure (the base64 decoder is injected) so it is tested without a device.
 */
internal object VoiceSessionProtocol {

    fun parse(text: String, decodeBase64: (String) -> ByteArray): VoiceServerEvent {
        val map = VoiceApiJson.read(text) ?: return VoiceServerEvent.Ignored
        fun obj(key: String) = map[key] as? Map<*, *>
        fun int(m: Map<*, *>?, key: String) = (m?.get(key) as? Number)?.toInt() ?: 0
        return when (map["type"]) {
            "conversation_initiation_metadata" -> {
                val event = obj("conversation_initiation_metadata_event") ?: return VoiceServerEvent.Ignored
                VoiceServerEvent.Metadata(
                    conversationId = event["conversation_id"] as? String ?: return VoiceServerEvent.Ignored,
                    inputSampleRate = sampleRate(event["user_input_audio_format"] as? String) ?: 16_000,
                    outputSampleRate = sampleRate(event["agent_output_audio_format"] as? String) ?: 16_000,
                )
            }
            "audio" -> {
                val event = obj("audio_event") ?: return VoiceServerEvent.Ignored
                val data = event["audio_base_64"] as? String
                if (data.isNullOrEmpty()) VoiceServerEvent.Ignored
                else VoiceServerEvent.Audio(decodeBase64(data), int(event, "event_id"))
            }
            "user_transcript" -> (obj("user_transcription_event")?.get("user_transcript") as? String)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { VoiceServerEvent.UserTranscript(it) } ?: VoiceServerEvent.Ignored
            "tentative_user_transcript" -> (obj("tentative_user_transcription_event")?.get("user_transcript") as? String)
                ?.let { VoiceServerEvent.TentativeUserTranscript(it.trim()) } ?: VoiceServerEvent.Ignored
            "agent_response" -> (obj("agent_response_event")?.get("agent_response") as? String)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { VoiceServerEvent.AgentResponse(it) } ?: VoiceServerEvent.Ignored
            "agent_response_correction" -> {
                val event = obj("agent_response_correction_event") ?: return VoiceServerEvent.Ignored
                VoiceServerEvent.AgentResponseCorrection(
                    original = (event["original_agent_response"] as? String).orEmpty().trim(),
                    corrected = (event["corrected_agent_response"] as? String).orEmpty().trim(),
                )
            }
            "agent_chat_response_part" -> {
                val part = obj("text_response_part") ?: return VoiceServerEvent.Ignored
                VoiceServerEvent.AgentTextPart(part["type"] as? String ?: "delta", part["text"] as? String ?: "")
            }
            "interruption" -> VoiceServerEvent.Interruption(int(obj("interruption_event"), "event_id"))
            "ping" -> VoiceServerEvent.Ping(int(obj("ping_event"), "event_id"))
            "vad_score" -> VoiceServerEvent.Vad(((obj("vad_score_event")?.get("vad_score") as? Number)?.toFloat()) ?: 0f)
            "error" -> {
                val event = obj("error_event")
                val message = (event?.get("message") as? String)?.takeIf { it.isNotBlank() }
                    ?: (event?.get("reason") as? String)?.takeIf { it.isNotBlank() }
                    ?: "ElevenLabs reported an error."
                VoiceServerEvent.Error(message)
            }
            else -> VoiceServerEvent.Ignored
        }
    }

    /** "pcm_16000" → 16000. μ-law and unknown formats return null: the app plays PCM only. */
    fun sampleRate(format: String?): Int? =
        format?.takeIf { it.startsWith("pcm_") }?.removePrefix("pcm_")?.toIntOrNull()?.takeIf { it in 8_000..48_000 }

    fun userAudio(base64: String): String = VoiceApiJson.write(mapOf("user_audio_chunk" to base64))
    fun pong(eventId: Int): String = VoiceApiJson.write(mapOf("type" to "pong", "event_id" to eventId))
    fun userMessage(text: String): String = VoiceApiJson.write(mapOf("type" to "user_message", "text" to text))
    /** Tells the agent the user is busy (typing): it holds off speaking for a moment. */
    fun userActivity(): String = VoiceApiJson.write(mapOf("type" to "user_activity"))
}
