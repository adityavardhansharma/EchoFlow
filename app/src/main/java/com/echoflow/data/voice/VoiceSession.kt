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
    /** The socket closed. [error] is null when the user ended it. */
    fun onEnded(error: String?)
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
) {
    private val _activity = MutableStateFlow(VoiceAgentActivity.Connecting)
    val activity: StateFlow<VoiceAgentActivity> = _activity.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    /** What the user is saying right now, before ElevenLabs finalizes it. */
    private val _userCaption = MutableStateFlow("")
    val userCaption: StateFlow<String> = _userCaption.asStateFlow()

    /** The reply as it streams in, then as spoken. Cleared when the user starts a new turn. */
    private val _agentCaption = MutableStateFlow("")
    val agentCaption: StateFlow<String> = _agentCaption.asStateFlow()

    private val routing = VoiceAudioRouting(context)
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
    private var streamedText = StringBuilder()

    var micMuted: Boolean = false
        set(value) {
            field = value
            microphone?.muted = value
            if (value) _inputLevel.value = 0f
        }

    var speakerMuted: Boolean = false
        set(value) {
            field = value
            player?.muted = value
        }

    /**
     * Opens the socket on [signedUrl] and sends [initiation] first, as the protocol requires.
     * [micAllowed] false (no permission) runs a typing-only session.
     */
    fun start(signedUrl: String, initiation: String, micAllowed: Boolean) {
        this.micAllowed = micAllowed
        routing.acquire()
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
        when (event) {
            is VoiceServerEvent.Metadata -> {
                player = VoicePlayer(event.outputSampleRate) { level -> _outputLevel.value = level }.also {
                    it.muted = speakerMuted
                    it.start()
                }
                if (micAllowed) {
                    microphone = VoiceMicrophone(
                        sampleRate = event.inputSampleRate,
                        onChunk = { pcm ->
                            socket?.send(VoiceSessionProtocol.userAudio(Base64.encodeToString(pcm, Base64.NO_WRAP)))
                        },
                        onLevel = { level -> _inputLevel.value = level },
                    ).also {
                        it.muted = micMuted
                        if (!it.start()) {
                            microphone = null
                            Log.w(TAG, "Microphone unavailable; typing only")
                        }
                    }
                }
                _activity.value = VoiceAgentActivity.Listening
                listener.onConnected(event.conversationId)
            }
            is VoiceServerEvent.Audio -> {
                player?.enqueue(event.pcm)
                awaitingReply = false
                _activity.value = VoiceAgentActivity.Speaking
                listener.onActivity()
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
                    awaitingReply = true
                    _activity.value = VoiceAgentActivity.Thinking
                    listener.onUserTurn(event.text)
                }
                listener.onActivity()
            }
            is VoiceServerEvent.AgentTextPart -> {
                when (event.kind) {
                    "start" -> streamedText = StringBuilder(event.text)
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
                player?.interrupt()
                _activity.value = VoiceAgentActivity.Listening
            }
            is VoiceServerEvent.Ping -> webSocket.send(VoiceSessionProtocol.pong(event.eventId))
            is VoiceServerEvent.Vad -> if (event.score > SPEECH_VAD) listener.onActivity()
            is VoiceServerEvent.Error -> Log.w(TAG, "Agent error: ${event.message}")
            VoiceServerEvent.Ignored -> Unit
        }
        // Speaking ends when the last audio has played out, not when the reply text arrives.
        if (_activity.value == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = if (awaitingReply) VoiceAgentActivity.Thinking else VoiceAgentActivity.Listening
        }
    }

    /** Re-checks whether the agent is still talking; called on a short tick by the controller. */
    fun refreshActivity() {
        val current = _activity.value
        if (current == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = VoiceAgentActivity.Listening
        }
    }

    /** Posts a typed message into the conversation. Returns false when the session isn't live. */
    fun sendText(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || finished) return false
        val ws = socket ?: return false
        lastTypedText = clean
        lastTypedAt = System.currentTimeMillis()
        // Typing over the agent cuts it off, just like speaking over it would.
        player?.interrupt()
        _agentCaption.value = ""
        streamedText = StringBuilder()
        awaitingReply = true
        _activity.value = VoiceAgentActivity.Thinking
        return ws.send(VoiceSessionProtocol.userMessage(clean))
    }

    /** The user is typing: ask the agent to hold off, at most about once a second. */
    fun typing() {
        val now = System.currentTimeMillis()
        if (now - lastActivitySentAt < 1_000) return
        lastActivitySentAt = now
        socket?.send(VoiceSessionProtocol.userActivity())
        listener.onActivity()
    }

    fun end() {
        endedByUser = true
        socket?.close(1000, "User ended conversation")
        finish(null)
    }

    private fun finish(error: String?) {
        synchronized(this) {
            if (finished) return
            finished = true
        }
        microphone?.stop()
        microphone = null
        player?.stop()
        player = null
        routing.release()
        _inputLevel.value = 0f
        _outputLevel.value = 0f
        socket = null
        listener.onEnded(error)
    }

    private companion object {
        const val TAG = "VoiceSession"
        const val TYPED_ECHO_WINDOW_MS = 4_000L
        const val SPEECH_VAD = 0.6f
    }
}
