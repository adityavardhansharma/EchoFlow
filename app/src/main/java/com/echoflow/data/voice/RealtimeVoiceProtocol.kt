package com.echoflow.data.voice

import java.net.URLEncoder

/**
 * What a realtime speech-to-speech socket (Gemini Live, OpenAI Realtime) reports, reduced to
 * what voice mode acts on. One server message can carry several of these (Gemini sends audio,
 * a caption and "turn complete" together), so parsing returns a list.
 */
internal sealed interface RealtimeEvent {
    /** The session accepted its setup: audio can flow. */
    data object Ready : RealtimeEvent
    /** A piece of the reply's audio. [itemId] names the reply (OpenAI), for cutting it short. */
    class Audio(val pcm: ByteArray, val itemId: String? = null) : RealtimeEvent
    /** A piece of what the user is saying, to append to the live caption. */
    data class UserTranscriptDelta(val text: String) : RealtimeEvent
    /** The user stopped talking and their audio was committed as a turn ([itemId], OpenAI). */
    data class UserCommitted(val itemId: String?) : RealtimeEvent
    /** The finished transcript of one user turn (OpenAI sends it separately from the reply). */
    data class UserTranscript(val itemId: String?, val text: String) : RealtimeEvent
    /** The transcript of a user turn couldn't be made: stop waiting for it. */
    data class UserTranscriptFailed(val itemId: String?) : RealtimeEvent
    /** The user started talking (OpenAI's server VAD): a reply that's playing must stop. */
    data object UserSpeechStarted : RealtimeEvent
    /** A piece of the reply's spoken text. */
    data class AgentTranscriptDelta(val text: String, val itemId: String? = null) : RealtimeEvent
    /** The full spoken text of one reply (OpenAI). Gemini's replies end with [TurnComplete]. */
    data class AgentTranscriptDone(val text: String, val itemId: String? = null) : RealtimeEvent
    /** The model finished its turn. */
    data object TurnComplete : RealtimeEvent
    /** The server cut the reply off because the user talked over it (Gemini). */
    data object Interrupted : RealtimeEvent
    /** Tokens used, as the server reports them. */
    data class Usage(val usage: RealtimeUsage) : RealtimeEvent
    /** A handle that resumes this session on a new connection (Gemini). */
    data class ResumeHandle(val handle: String) : RealtimeEvent
    /** The server will close this connection soon; resume on a new one (Gemini). */
    data object GoAway : RealtimeEvent
    data class Error(val message: String) : RealtimeEvent
}

/** Token counts from one usage report. Summed over a call for Spend. */
data class RealtimeUsage(
    val textIn: Long = 0,
    val audioIn: Long = 0,
    val cachedIn: Long = 0,
    val textOut: Long = 0,
    val audioOut: Long = 0,
) {
    operator fun plus(other: RealtimeUsage) = RealtimeUsage(
        textIn + other.textIn,
        audioIn + other.audioIn,
        cachedIn + other.cachedIn,
        textOut + other.textOut,
        audioOut + other.audioOut,
    )

    val isEmpty: Boolean get() = textIn + audioIn + cachedIn + textOut + audioOut == 0L
}

/** Everything a realtime session is set up with: the same prompt ElevenLabs gets, and a voice. */
internal data class RealtimeSessionSpec(
    val model: String,
    val voice: String,
    val instructions: String,
    /** Say a short hello first (a new chat, and the user hasn't started talking). */
    val greet: Boolean,
)

/** One provider's realtime socket: where it lives, what to send, and how to read what comes back. */
internal interface RealtimeVoiceProtocol {
    val provider: VoiceProvider
    /** What the mic records in. */
    val inputSampleRate: Int
    /** What the server's audio is in. */
    val outputSampleRate: Int
    /**
     * True when the server finishes user turns itself ([RealtimeEvent.UserTranscript]); false when
     * the user's turn is whatever was heard before the reply started (Gemini).
     */
    val finalUserTranscripts: Boolean
    /** True when the session can carry on over a new connection with a [RealtimeEvent.ResumeHandle]. */
    val resumable: Boolean

    fun url(apiKey: String, model: String): String
    fun headers(apiKey: String): Map<String, String>

    /** Sent the moment the socket opens. [resumeHandle] continues an earlier connection. */
    fun setup(spec: RealtimeSessionSpec, resumeHandle: String?): List<String>
    /** Sent once the server says [RealtimeEvent.Ready] on the first connection, when greeting. */
    fun greeting(): List<String>
    fun parse(text: String, decodeBase64: (String) -> ByteArray): List<RealtimeEvent>
    fun audio(base64: String): String
    /** The mic went quiet on purpose (muted): flush what the server is holding. */
    fun audioPaused(): String?
    /** A typed message. [cancel] is set when a reply is playing and has to stop first. */
    fun userText(text: String, cancel: Cancel?): List<String>
    /** Stop the reply that's playing: [cancel] says which and how much of it was heard. */
    fun cancelReply(cancel: Cancel, serverAlreadyStopped: Boolean): List<String>

    data class Cancel(val itemId: String?, val playedMillis: Long)
}

/**
 * The Gemini Live API over its WebSocket (`BidiGenerateContent`), with the user's Gemini key.
 *
 * - 16 kHz PCM up, 24 kHz PCM down, Google's built-in voice in `speechConfig`.
 * - Both sides are transcribed (`inputAudioTranscription`, `outputAudioTranscription`), which is
 *   what fills the captions and the chat.
 * - Connections last about 10 minutes: `sessionResumption` hands out a handle, and the session
 *   reconnects with it on `goAway`. `contextWindowCompression` lifts the 15 minute audio cap.
 * - Typed messages go in as `realtimeInput.text`; Gemini 3 Live takes `clientContent` only for
 *   seeding history, so the chat so far goes in the system instruction instead.
 */
internal object GeminiLiveProtocol : RealtimeVoiceProtocol {
    const val ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    override val provider = VoiceProvider.Gemini
    override val inputSampleRate = 16_000
    override val outputSampleRate = 24_000
    override val finalUserTranscripts = false
    override val resumable = true

    /** The key goes in the URL, as Google's WebSocket guide does it; the header is sent too. */
    override fun url(apiKey: String, model: String): String =
        "$ENDPOINT?key=" + URLEncoder.encode(apiKey.trim(), "UTF-8")

    override fun headers(apiKey: String): Map<String, String> = mapOf("x-goog-api-key" to apiKey.trim())

    override fun setup(spec: RealtimeSessionSpec, resumeHandle: String?): List<String> {
        val setup = linkedMapOf<String, Any?>(
            "model" to "models/${spec.model}",
            "generationConfig" to linkedMapOf(
                "responseModalities" to listOf("AUDIO"),
                "speechConfig" to linkedMapOf(
                    "voiceConfig" to linkedMapOf(
                        "prebuiltVoiceConfig" to linkedMapOf("voiceName" to spec.voice),
                    ),
                ),
            ),
            "systemInstruction" to linkedMapOf("parts" to listOf(linkedMapOf("text" to spec.instructions))),
            "inputAudioTranscription" to emptyMap<String, Any>(),
            "outputAudioTranscription" to emptyMap<String, Any>(),
            "realtimeInputConfig" to linkedMapOf(
                "activityHandling" to "START_OF_ACTIVITY_INTERRUPTS",
            ),
            // An empty handle starts a resumable session; a handle continues one.
            "sessionResumption" to (resumeHandle?.let { linkedMapOf("handle" to it) } ?: emptyMap<String, Any>()),
            "contextWindowCompression" to linkedMapOf("slidingWindow" to emptyMap<String, Any>()),
        )
        return listOf(VoiceApiJson.write(mapOf("setup" to setup)))
    }

    override fun greeting(): List<String> = listOf(text(GREETING_CUE))

    override fun parse(text: String, decodeBase64: (String) -> ByteArray): List<RealtimeEvent> {
        val map = VoiceApiJson.read(text) ?: return emptyList()
        val events = ArrayList<RealtimeEvent>()
        if (map.containsKey("setupComplete")) events += RealtimeEvent.Ready
        (map["serverContent"] as? Map<*, *>)?.let { content ->
            // Order matters: the user's words land before the reply's, and "done" comes last.
            (content["inputTranscription"] as? Map<*, *>)?.get("text")?.let { it as? String }
                ?.takeIf { it.isNotEmpty() }?.let { events += RealtimeEvent.UserTranscriptDelta(it) }
            if (content["interrupted"] == true) events += RealtimeEvent.Interrupted
            val parts = ((content["modelTurn"] as? Map<*, *>)?.get("parts") as? List<*>).orEmpty()
            parts.filterIsInstance<Map<*, *>>().forEach { part ->
                val inline = part["inlineData"] as? Map<*, *>
                val data = inline?.get("data") as? String
                val mime = inline?.get("mimeType") as? String
                if (!data.isNullOrEmpty() && (mime == null || mime.startsWith("audio/"))) {
                    events += RealtimeEvent.Audio(decodeBase64(data))
                }
            }
            (content["outputTranscription"] as? Map<*, *>)?.get("text")?.let { it as? String }
                ?.takeIf { it.isNotEmpty() }?.let { events += RealtimeEvent.AgentTranscriptDelta(it) }
            if (content["turnComplete"] == true) events += RealtimeEvent.TurnComplete
        }
        (map["sessionResumptionUpdate"] as? Map<*, *>)?.let { update ->
            val handle = update["newHandle"] as? String
            if (update["resumable"] != false && !handle.isNullOrBlank()) events += RealtimeEvent.ResumeHandle(handle)
        }
        if (map.containsKey("goAway")) events += RealtimeEvent.GoAway
        (map["usageMetadata"] as? Map<*, *>)?.let { usage(it) }?.takeIf { !it.isEmpty }
            ?.let { events += RealtimeEvent.Usage(it) }
        (map["error"] as? Map<*, *>)?.let { error ->
            events += RealtimeEvent.Error((error["message"] as? String)?.takeIf { it.isNotBlank() } ?: "Gemini reported an error.")
        }
        return events
    }

    /** Tokens by modality: audio counts as audio, everything else (text, the prompt) as text. */
    fun usage(meta: Map<*, *>): RealtimeUsage {
        fun split(details: Any?, total: Long): Pair<Long, Long> {
            val rows = (details as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            if (rows.isEmpty()) return total to 0L
            val audio = rows.filter { it["modality"] == "AUDIO" }.sumOf { (it["tokenCount"] as? Number)?.toLong() ?: 0L }
            return (total - audio).coerceAtLeast(0) to audio
        }
        val prompt = (meta["promptTokenCount"] as? Number)?.toLong() ?: 0L
        val response = (meta["responseTokenCount"] as? Number)?.toLong()
            ?: (meta["candidatesTokenCount"] as? Number)?.toLong() ?: 0L
        val (textIn, audioIn) = split(meta["promptTokensDetails"], prompt)
        val (textOut, audioOut) = split(meta["responseTokensDetails"] ?: meta["candidatesTokensDetails"], response)
        val cached = (meta["cachedContentTokenCount"] as? Number)?.toLong() ?: 0L
        return RealtimeUsage(
            textIn = (textIn - cached).coerceAtLeast(0),
            audioIn = audioIn,
            cachedIn = cached,
            textOut = textOut,
            audioOut = audioOut,
        )
    }

    override fun audio(base64: String): String = VoiceApiJson.write(
        mapOf("realtimeInput" to mapOf("audio" to linkedMapOf("data" to base64, "mimeType" to "audio/pcm;rate=$inputSampleRate"))),
    )

    override fun audioPaused(): String = VoiceApiJson.write(mapOf("realtimeInput" to mapOf("audioStreamEnd" to true)))

    /** Gemini has no "cancel": a new input interrupts the reply on its own. */
    override fun userText(text: String, cancel: RealtimeVoiceProtocol.Cancel?): List<String> = listOf(text(text))

    /** Nothing to send: the app stops playing and drops the rest of the reply itself. */
    override fun cancelReply(cancel: RealtimeVoiceProtocol.Cancel, serverAlreadyStopped: Boolean): List<String> = emptyList()

    private fun text(text: String) = VoiceApiJson.write(mapOf("realtimeInput" to mapOf("text" to text)))

    const val GREETING_CUE = "(The call just connected. Greet me in one short, friendly sentence and ask what's on my mind.)"
}

/**
 * The OpenAI Realtime API over WebSocket, with the user's OpenAI key.
 *
 * - 24 kHz PCM both ways. Server VAD finds the ends of turns and cuts the reply off when the user
 *   talks over it; the app then tells the server how much was heard (`conversation.item.truncate`)
 *   so the model's memory of the reply matches what the user heard.
 * - The user's speech is transcribed alongside (`audio.input.transcription`), and the reply's
 *   spoken text streams as `response.output_audio_transcript.*`.
 */
internal object OpenAiRealtimeProtocol : RealtimeVoiceProtocol {
    const val ENDPOINT = "wss://api.openai.com/v1/realtime"
    /** Streams the user's words while they talk, which the live caption needs. */
    const val TRANSCRIPTION_MODEL = "gpt-4o-transcribe"

    override val provider = VoiceProvider.OpenAi
    override val inputSampleRate = 24_000
    override val outputSampleRate = 24_000
    override val finalUserTranscripts = true
    override val resumable = false

    override fun url(apiKey: String, model: String): String = "$ENDPOINT?model=" + URLEncoder.encode(model, "UTF-8")

    override fun headers(apiKey: String): Map<String, String> = mapOf("Authorization" to "Bearer ${apiKey.trim()}")

    override fun setup(spec: RealtimeSessionSpec, resumeHandle: String?): List<String> {
        val pcm = linkedMapOf("type" to "audio/pcm", "rate" to inputSampleRate)
        val session = linkedMapOf<String, Any?>(
            "type" to "realtime",
            "instructions" to spec.instructions,
            "output_modalities" to listOf("audio"),
            "audio" to linkedMapOf(
                "input" to linkedMapOf(
                    "format" to pcm,
                    "noise_reduction" to linkedMapOf("type" to "near_field"),
                    "transcription" to linkedMapOf("model" to TRANSCRIPTION_MODEL),
                    "turn_detection" to linkedMapOf(
                        "type" to "semantic_vad",
                        "eagerness" to "auto",
                        "create_response" to true,
                        "interrupt_response" to true,
                    ),
                ),
                "output" to linkedMapOf(
                    "format" to linkedMapOf("type" to "audio/pcm", "rate" to outputSampleRate),
                    "voice" to spec.voice,
                ),
            ),
        )
        return listOf(VoiceApiJson.write(linkedMapOf("type" to "session.update", "session" to session)))
    }

    override fun greeting(): List<String> = listOf(
        VoiceApiJson.write(
            linkedMapOf(
                "type" to "response.create",
                "response" to linkedMapOf(
                    "instructions" to "The call just connected. Greet the user in one short, friendly sentence and ask what's on their mind.",
                ),
            ),
        ),
    )

    override fun parse(text: String, decodeBase64: (String) -> ByteArray): List<RealtimeEvent> {
        val map = VoiceApiJson.read(text) ?: return emptyList()
        fun str(key: String) = map[key] as? String
        val event: RealtimeEvent? = when (map["type"]) {
            "session.updated" -> RealtimeEvent.Ready
            "response.output_audio.delta" -> str("delta")?.takeIf { it.isNotEmpty() }
                ?.let { RealtimeEvent.Audio(decodeBase64(it), str("item_id")) }
            "response.output_audio_transcript.delta" -> str("delta")?.takeIf { it.isNotEmpty() }
                ?.let { RealtimeEvent.AgentTranscriptDelta(it, str("item_id")) }
            "response.output_audio_transcript.done" -> RealtimeEvent.AgentTranscriptDone(str("transcript").orEmpty().trim(), str("item_id"))
            "response.done" -> null
            "input_audio_buffer.speech_started" -> RealtimeEvent.UserSpeechStarted
            "input_audio_buffer.committed" -> RealtimeEvent.UserCommitted(str("item_id"))
            "conversation.item.input_audio_transcription.delta" -> str("delta")?.takeIf { it.isNotEmpty() }
                ?.let { RealtimeEvent.UserTranscriptDelta(it) }
            "conversation.item.input_audio_transcription.completed" ->
                RealtimeEvent.UserTranscript(str("item_id"), str("transcript").orEmpty().trim())
            "conversation.item.input_audio_transcription.failed" -> RealtimeEvent.UserTranscriptFailed(str("item_id"))
            "error" -> {
                val error = map["error"] as? Map<*, *>
                val code = error?.get("code") as? String
                // Cancelling a reply that already finished is harmless; it isn't worth a word.
                if (code == "response_cancel_not_active") null
                else RealtimeEvent.Error((error?.get("message") as? String)?.takeIf { it.isNotBlank() } ?: "OpenAI reported an error.")
            }
            else -> null
        }
        if (map["type"] == "response.done") {
            val response = map["response"] as? Map<*, *>
            val events = mutableListOf<RealtimeEvent>()
            (response?.get("usage") as? Map<*, *>)?.let(::usage)?.takeIf { !it.isEmpty }?.let { events += RealtimeEvent.Usage(it) }
            events += RealtimeEvent.TurnComplete
            return events
        }
        return listOfNotNull(event)
    }

    fun usage(usage: Map<*, *>): RealtimeUsage {
        fun long(m: Map<*, *>?, key: String) = (m?.get(key) as? Number)?.toLong() ?: 0L
        val input = usage["input_token_details"] as? Map<*, *>
        val output = usage["output_token_details"] as? Map<*, *>
        // Cached tokens are a share of the text and audio input, billed at the cached rate.
        val cached = long(input, "cached_tokens")
        val cachedDetails = input?.get("cached_tokens_details") as? Map<*, *>
        val cachedAudio = long(cachedDetails, "audio_tokens")
        val cachedText = if (cachedDetails != null) long(cachedDetails, "text_tokens") else cached
        return RealtimeUsage(
            textIn = (long(input, "text_tokens") - cachedText).coerceAtLeast(0),
            audioIn = (long(input, "audio_tokens") - cachedAudio).coerceAtLeast(0),
            cachedIn = cached,
            textOut = long(output, "text_tokens"),
            audioOut = long(output, "audio_tokens"),
        )
    }

    override fun audio(base64: String): String =
        VoiceApiJson.write(linkedMapOf("type" to "input_audio_buffer.append", "audio" to base64))

    override fun audioPaused(): String? = null

    override fun userText(text: String, cancel: RealtimeVoiceProtocol.Cancel?): List<String> = buildList {
        if (cancel != null) addAll(cancelReply(cancel, serverAlreadyStopped = false))
        add(
            VoiceApiJson.write(
                linkedMapOf(
                    "type" to "conversation.item.create",
                    "item" to linkedMapOf(
                        "type" to "message",
                        "role" to "user",
                        "content" to listOf(linkedMapOf("type" to "input_text", "text" to text)),
                    ),
                ),
            ),
        )
        add(VoiceApiJson.write(mapOf("type" to "response.create")))
    }

    override fun cancelReply(cancel: RealtimeVoiceProtocol.Cancel, serverAlreadyStopped: Boolean): List<String> = buildList {
        // When the user spoke over it, server VAD has already cancelled the response.
        if (!serverAlreadyStopped) add(VoiceApiJson.write(mapOf("type" to "response.cancel")))
        if (cancel.itemId != null) {
            add(
                VoiceApiJson.write(
                    linkedMapOf(
                        "type" to "conversation.item.truncate",
                        "item_id" to cancel.itemId,
                        "content_index" to 0,
                        "audio_end_ms" to cancel.playedMillis.coerceAtLeast(0),
                    ),
                ),
            )
        }
    }
}

/** What a call costs, from the tokens it used. Prices are USD per million tokens. */
internal object RealtimePricing {
    private data class Rates(val textIn: Double, val audioIn: Double, val cachedIn: Double, val textOut: Double, val audioOut: Double)

    private val rates = mapOf(
        "gpt-realtime-2.1" to Rates(4.0, 32.0, 0.40, 24.0, 64.0),
        "gpt-realtime-2.1-mini" to Rates(0.60, 10.0, 0.30, 2.40, 20.0),
        // Google hasn't published 3.8 Live prices yet: Gemini 3.1 Flash Live's rates stand in.
        "gemini-3.8-live" to Rates(0.75, 3.0, 0.0, 4.50, 12.0),
    )

    /** True when the model's own price list is known (not a stand-in). */
    fun exact(model: String): Boolean = model.startsWith("gpt-realtime")

    fun usd(model: String, usage: RealtimeUsage): Double? {
        val r = rates[model] ?: return null
        return (usage.textIn * r.textIn + usage.audioIn * r.audioIn + usage.cachedIn * r.cachedIn +
            usage.textOut * r.textOut + usage.audioOut * r.audioOut) / 1_000_000.0
    }
}
