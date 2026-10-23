package com.echoflow.data.voice

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** What the agent is doing right now, for the orb and the status line. */
enum class VoiceAgentActivity { Connecting, Listening, Thinking, Speaking }

/** What a live session reports upward. Called on background threads. */
internal interface VoiceSessionListener {
    fun onConnected(conversationId: String)
    fun onUserTurn(text: String)
    fun onAgentTurn(text: String)
    /** The user cut the agent off: [corrected] is what was actually said before they did. */
    fun onAgentCorrection(original: String, corrected: String)
    /** Anything that counts as the conversation being alive (speech, typing, a reply). */
    fun onActivity()
    /** The mic couldn't be opened (held by another app, or failed): the call is typing-only. */
    fun onMicUnavailable()
    /** The socket closed. [error] is null when the user ended it. */
    fun onEnded(error: String?)
    /** Tokens used so far (Gemini and OpenAI report them; ElevenLabs bills per minute). */
    fun onUsage(total: RealtimeUsage) = Unit
    /** Something went wrong mid-call that the user should hear about, though the call goes on. */
    fun onNotice(message: String) = Unit
}

/**
 * A live voice call as the controller and the call screen see it, whoever runs it:
 * [VoiceSession] for ElevenLabs, [RealtimeVoiceSession] for Gemini Live and OpenAI Realtime.
 */
internal interface LiveVoiceSession {
    val activity: StateFlow<VoiceAgentActivity>
    val inputLevel: StateFlow<Float>
    val outputLevel: StateFlow<Float>
    /** What the user is saying right now, before it's final. */
    val userCaption: StateFlow<String>
    /** The reply as it streams in, then as spoken. */
    val agentCaption: StateFlow<String>
    /** The user's last finished turn, spoken or typed. */
    val lastUserTurn: StateFlow<String>
    /** Counts the user's finished turns, so the screen can pulse the moment one lands. */
    val userTurns: StateFlow<Int>
    /** True once the user spoke before the call connected, so the greeting is skipped. */
    val spokeEarly: Boolean
    var micMuted: Boolean
    var speakerMuted: Boolean

    /** Opens the call's audio at the tap; [micAllowed] false runs a typing-only call. */
    fun openAudio(micAllowed: Boolean)
    /** Re-checks whether the reply is still playing; called on a short tick. */
    fun refreshActivity()
    /** Posts a typed message. False when the call isn't live. */
    fun sendText(text: String): Boolean
    /** The user tapped to cut in. False when nothing was playing. */
    fun cutIn(): Boolean
    /** The user is typing. */
    fun typing()
    fun end()
}

/**
 * One live ElevenLabs Agents conversation over WebSocket: streams the mic up, plays the agent's
 * audio down, answers pings, and turns the event stream into turns and live captions.
 *
 * Text is a first-class input: [sendText] posts a typed message into the same conversation, and
 * [typing] tells the agent to hold off speaking while the user writes.
 */
internal class VoiceSession(
    private val context: Context,
    private val listener: VoiceSessionListener,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) : LiveVoiceSession {
    private val _activity = MutableStateFlow(VoiceAgentActivity.Connecting)
    override val activity: StateFlow<VoiceAgentActivity> = _activity.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    /** What the user is saying right now, before ElevenLabs finalizes it. */
    private val _userCaption = MutableStateFlow("")
    override val userCaption: StateFlow<String> = _userCaption.asStateFlow()

    /** The reply as it streams in, then as spoken. Cleared when the user starts a new turn. */
    private val _agentCaption = MutableStateFlow("")
    override val agentCaption: StateFlow<String> = _agentCaption.asStateFlow()

    /** The user's last finished turn, spoken or typed, kept on screen under the line. */
    private val _lastUserTurn = MutableStateFlow("")
    override val lastUserTurn: StateFlow<String> = _lastUserTurn.asStateFlow()

    /** Counts the user's finished turns, so the screen can pulse the moment one lands. */
    private val _userTurns = MutableStateFlow(0)
    override val userTurns: StateFlow<Int> = _userTurns.asStateFlow()

    /** True once the user spoke before the call connected, so the greeting is skipped. */
    @Volatile private var heardEarly = false
    override val spokeEarly: Boolean get() = heardEarly

    private val routing = VoiceAudioRouting(context)
    /** Held to the ear: the earpiece plays, as on a phone call (the screen goes dark separately). */
    private val proximity = VoiceProximity(context) { atEar -> routing.holdToEar(atEar) }
    private var socket: WebSocket? = null
    private var microphone: VoiceMicrophone? = null
    private var player: VoicePlayer? = null
    @Volatile private var endedByUser = false
    @Volatile private var finished = false
    @Volatile private var awaitingReply = false
    @Volatile private var lastTypedText: String? = null
    @Volatile private var lastTypedAt = 0L
    @Volatile private var lastActivitySentAt = 0L
    @Volatile private var micAllowed = true
    @Volatile private var audioOpen = false
    @Volatile private var connected = false
    @Volatile private var micRate = 0
    /** The user tapped to cut the agent off: the rest of that reply's audio is dropped. */
    @Volatile private var droppingReply = false
    /** Mic audio recorded before the socket was ready, sent the moment it is. */
    private val early = ArrayDeque<ByteArray>()
    private var streamedText = StringBuilder()

    override var micMuted: Boolean = false
        set(value) {
            field = value
            microphone?.muted = value
            if (value) _inputLevel.value = 0f
        }

    override var speakerMuted: Boolean = false
        set(value) {
            field = value
            player?.muted = value
        }

    /**
     * Opens the call's audio the moment the user taps: call mode, and the mic when [micAllowed].
     * What the user says before the socket is ready is held and sent as soon as it is, so they
     * can start talking straight away. [micAllowed] false (no permission) runs a typing-only call.
     */
    override fun openAudio(micAllowed: Boolean) {
        this.micAllowed = micAllowed
        var micFailed = false
        synchronized(this) {
            if (finished || audioOpen) return
            audioOpen = true
            routing.acquire()
            proximity.start()
            if (micAllowed && !openMic(EARLY_SAMPLE_RATE)) micFailed = true
        }
        if (micFailed) listener.onMicUnavailable()
    }

    /** Caller holds the lock. False when the mic couldn't be opened. */
    private fun openMic(sampleRate: Int): Boolean {
        val mic = VoiceMicrophone(
            sampleRate = sampleRate,
            onChunk = ::onMicChunk,
            onLevel = { level ->
                _inputLevel.value = level
                if (!connected && level > EARLY_SPEECH_LEVEL) heardEarly = true
            },
        )
        mic.muted = micMuted
        if (!mic.start()) {
            Log.w(TAG, "Microphone unavailable; typing only")
            return false
        }
        microphone = mic
        micRate = sampleRate
        return true
    }

    /** Holds audio until the socket is ready, then sends it; one lock keeps the order exact. */
    private fun onMicChunk(pcm: ByteArray) {
        synchronized(early) {
            if (!connected) {
                early.addLast(pcm)
                while (early.size > MAX_EARLY_CHUNKS) early.removeFirst()
            } else {
                socket?.send(VoiceSessionProtocol.userAudio(Base64.encodeToString(pcm, Base64.NO_WRAP)))
            }
        }
    }

    /**
     * Opens the socket on [signedUrl] and sends [initiation] first, as the protocol requires.
     * Audio is opened here too if [openAudio] wasn't called first.
     */
    fun start(signedUrl: String, initiation: String, micAllowed: Boolean) {
        openAudio(micAllowed)
        if (finished) return
        socket = client.newWebSocket(
            Request.Builder().url(signedUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(initiation)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handle(webSocket, VoiceSessionProtocol.parse(text) { Base64.decode(it, Base64.DEFAULT) })
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    finish(if (endedByUser || code == 1000) closeNote(reason) else reason.ifBlank { "The voice session closed." })
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(if (endedByUser || code == 1000) closeNote(reason) else reason.ifBlank { "The voice session closed." })
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Voice socket failed", t)
                    finish(
                        if (endedByUser) null
                        else if (response?.code == 401 || response?.code == 403) "ElevenLabs refused the session. Check your key."
                        else "Lost the connection to ElevenLabs.",
                    )
                }
            },
        )
    }

    /** A clean close from the server (time limit, agent hung up) is worth a word; a user end is not. */
    private fun closeNote(reason: String): String? =
        if (endedByUser) null else reason.takeIf { it.isNotBlank() && !it.equals("User ended conversation", ignoreCase = true) }

    private fun handle(webSocket: WebSocket, event: VoiceServerEvent) {
        if (finished) return
        when (event) {
            is VoiceServerEvent.Metadata -> {
                var micFailed = false
                // Audio is opened under the same lock finish() closes it under, so an End that
                // lands mid-setup can never leave the mic recording after the call is over.
                synchronized(this) {
                    if (finished) return
                    val newPlayer = VoicePlayer(event.outputSampleRate) { level -> _outputLevel.value = level }
                    newPlayer.muted = speakerMuted
                    player = newPlayer
                    newPlayer.start()
                    // The mic opened at the tap in the agent's usual format; a different one
                    // means reopening it, and the early audio (in the wrong format) is dropped.
                    if (micAllowed && microphone != null && micRate != event.inputSampleRate) {
                        microphone?.stop()
                        microphone = null
                        synchronized(early) { early.clear() }
                        if (!openMic(event.inputSampleRate)) micFailed = true
                    }
                }
                // Send what the user already said, then let the mic stream straight through. Under
                // the same lock as the mic's sends, so no new chunk can slip in between.
                synchronized(early) {
                    early.forEach { pcm -> webSocket.send(VoiceSessionProtocol.userAudio(Base64.encodeToString(pcm, Base64.NO_WRAP))) }
                    early.clear()
                    connected = true
                }
                if (micFailed) listener.onMicUnavailable()
                _activity.value = VoiceAgentActivity.Listening
                listener.onConnected(event.conversationId)
            }
            is VoiceServerEvent.Audio -> {
                // After a cut-in the rest of that reply is dropped until a new turn starts.
                if (!droppingReply) {
                    player?.enqueue(event.pcm)
                    awaitingReply = false
                    _activity.value = VoiceAgentActivity.Speaking
                    listener.onActivity()
                }
            }
            is VoiceServerEvent.TentativeUserTranscript -> {
                if (event.text.isNotEmpty()) {
                    _userCaption.value = event.text
                    listener.onActivity()
                }
            }
            is VoiceServerEvent.UserTranscript -> {
                _userCaption.value = ""
                // A typed message can come back as a transcript too; it was already recorded.
                val echo = lastTypedText?.equals(event.text, ignoreCase = true) == true &&
                    System.currentTimeMillis() - lastTypedAt < TYPED_ECHO_WINDOW_MS
                if (!echo) {
                    _agentCaption.value = ""
                    streamedText = StringBuilder()
                    droppingReply = false
                    awaitingReply = true
                    _activity.value = VoiceAgentActivity.Thinking
                    _lastUserTurn.value = event.text
                    _userTurns.value += 1
                    listener.onUserTurn(event.text)
                }
                listener.onActivity()
            }
            is VoiceServerEvent.AgentTextPart -> {
                when (event.kind) {
                    "start" -> {
                        streamedText = StringBuilder(event.text)
                        droppingReply = false
                    }
                    else -> streamedText.append(event.text)
                }
                if (streamedText.isNotBlank()) _agentCaption.value = streamedText.toString().trim()
            }
            is VoiceServerEvent.AgentResponse -> {
                _agentCaption.value = event.text
                listener.onAgentTurn(event.text)
                listener.onActivity()
            }
            is VoiceServerEvent.AgentResponseCorrection -> {
                if (event.corrected.isNotEmpty()) _agentCaption.value = event.corrected
                listener.onAgentCorrection(event.original, event.corrected)
            }
            is VoiceServerEvent.Interruption -> {
                // The server cut the reply off too: whatever plays next is a new reply.
                droppingReply = false
                player?.interrupt()
                _activity.value = VoiceAgentActivity.Listening
            }
            is VoiceServerEvent.Ping -> webSocket.send(VoiceSessionProtocol.pong(event.eventId))
            is VoiceServerEvent.Vad -> if (event.score > SPEECH_VAD) listener.onActivity()
            is VoiceServerEvent.Error -> Log.w(TAG, "Agent error: ${event.message}")
            // The app offers no client tools. An agent that still asks for one gets a plain no.
            is VoiceServerEvent.ClientToolCall -> webSocket.send(
                VoiceSessionProtocol.clientToolResult(event.callId, "This app has no tool called ${event.name}.", isError = true),
            )
            VoiceServerEvent.Ignored -> Unit
        }
        // Speaking ends when the last audio has played out, not when the reply text arrives.
        if (_activity.value == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = if (awaitingReply) VoiceAgentActivity.Thinking else VoiceAgentActivity.Listening
        }
    }

    /** Re-checks whether the agent is still talking; called on a short tick by the controller. */
    override fun refreshActivity() {
        val current = _activity.value
        if (current == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = VoiceAgentActivity.Listening
        }
    }

    /** Posts a typed message into the conversation. Returns false when the session isn't live. */
    override fun sendText(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || finished) return false
        val ws = socket ?: return false
        lastTypedText = clean
        lastTypedAt = System.currentTimeMillis()
        // Typing over the agent cuts it off, just like speaking over it would.
        player?.interrupt()
        _agentCaption.value = ""
        streamedText = StringBuilder()
        droppingReply = false
        _lastUserTurn.value = clean
        _userTurns.value += 1
        awaitingReply = true
        _activity.value = VoiceAgentActivity.Thinking
        return ws.send(VoiceSessionProtocol.userMessage(clean))
    }

    /**
     * The user tapped to cut in: the agent stops at once and the rest of its reply is dropped,
     * and it is asked to hold off while the user talks. False when the agent wasn't speaking.
     */
    override fun cutIn(): Boolean {
        if (finished || _activity.value != VoiceAgentActivity.Speaking) return false
        droppingReply = true
        player?.interrupt()
        _activity.value = VoiceAgentActivity.Listening
        socket?.send(VoiceSessionProtocol.userActivity())
        listener.onActivity()
        return true
    }

    /** The user is typing: ask the agent to hold off, at most about once a second. */
    override fun typing() {
        val now = System.currentTimeMillis()
        if (now - lastActivitySentAt < 1_000) return
        lastActivitySentAt = now
        socket?.send(VoiceSessionProtocol.userActivity())
        listener.onActivity()
    }

    override fun end() {
        endedByUser = true
        socket?.close(1000, "User ended conversation")
        finish(null)
    }

    private fun finish(error: String?) {
        synchronized(this) {
            if (finished) return
            finished = true
            microphone?.stop()
            microphone = null
            player?.stop()
            player = null
        }
        synchronized(early) { early.clear() }
        proximity.stop()
        if (audioOpen) routing.release()
        _inputLevel.value = 0f
        _outputLevel.value = 0f
        socket = null
        listener.onEnded(error)
    }

    private companion object {
        const val TAG = "VoiceSession"
        const val TYPED_ECHO_WINDOW_MS = 4_000L
        const val SPEECH_VAD = 0.6f
        /** What the agent records in (VoiceAgentSpec.INPUT_AUDIO_FORMAT), so the mic can open before it says so. */
        const val EARLY_SAMPLE_RATE = 16_000
        /** About 20 s of 100 ms chunks: enough for a slow connection, bounded if it never comes. */
        const val MAX_EARLY_CHUNKS = 200
        const val EARLY_SPEECH_LEVEL = 0.45f
    }
}
