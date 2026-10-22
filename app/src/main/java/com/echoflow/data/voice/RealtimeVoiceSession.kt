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

/**
 * One live call on a speech-to-speech socket: Gemini Live or OpenAI Realtime, picked by
 * [protocol]. It does what [VoiceSession] does for ElevenLabs, with the same flows, so the call
 * screen and the controller can't tell them apart:
 *
 * - the mic opens at the tap and what the user says early is held until the socket is ready;
 * - the reply plays as it streams, with live captions for both sides;
 * - talking or typing over a reply stops it, and tapping the screen cuts in;
 * - finished turns go to the listener in order, user first, so the chat reads right.
 *
 * Gemini closes a connection after about 10 minutes. The session follows the server's resume
 * handle onto a new connection without the user noticing; audio said in between is held.
 */
internal class RealtimeVoiceSession(
    private val context: Context,
    private val listener: VoiceSessionListener,
    private val protocol: RealtimeVoiceProtocol,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) : LiveVoiceSession {
    private val _activity = MutableStateFlow(VoiceAgentActivity.Connecting)
    override val activity: StateFlow<VoiceAgentActivity> = _activity.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    private val _userCaption = MutableStateFlow("")
    override val userCaption: StateFlow<String> = _userCaption.asStateFlow()

    private val _agentCaption = MutableStateFlow("")
    override val agentCaption: StateFlow<String> = _agentCaption.asStateFlow()

    private val _lastUserTurn = MutableStateFlow("")
    override val lastUserTurn: StateFlow<String> = _lastUserTurn.asStateFlow()

    private val _userTurns = MutableStateFlow(0)
    override val userTurns: StateFlow<Int> = _userTurns.asStateFlow()

    @Volatile private var heardEarly = false
    override val spokeEarly: Boolean get() = heardEarly

    /** Tokens used so far, summed from the server's reports. */
    @Volatile var usage = RealtimeUsage()
        private set

    private val routing = VoiceAudioRouting(context)
    private val proximity = VoiceProximity(context) { atEar -> routing.holdToEar(atEar) }
    private val conversationId = "local-" + java.util.UUID.randomUUID().toString()
    private var socket: WebSocket? = null
    private var microphone: VoiceMicrophone? = null
    private var player: VoicePlayer? = null
    private var apiKey = ""
    private var spec: RealtimeSessionSpec? = null
    @Volatile private var resumeHandle: String? = null
    @Volatile private var reconnects = 0
    @Volatile private var everReady = false
    @Volatile private var endedByUser = false
    @Volatile private var finished = false
    @Volatile private var audioOpen = false
    @Volatile private var connected = false
    @Volatile private var micAllowed = true
    /** A reply is being generated or played (between the first output and the turn's end). */
    @Volatile private var replying = false
    /** Gemini's reply was cut off: the rest of its audio is dropped until its turn ends. */
    @Volatile private var droppingReply = false
    /** OpenAI replies that were cut off, by item: anything still arriving for them is dropped. */
    private val cancelledItems = HashSet<String>()
    /** Guards the turn state below: socket events and the user's taps arrive on different threads. */
    private val turnLock = Any()
    @Volatile private var replyItemId: String? = null
    @Volatile private var awaitingReply = false
    private val early = ArrayDeque<ByteArray>()

    /** What the user has said this turn (Gemini builds the turn from these pieces). */
    private val userText = StringBuilder()
    private val agentText = StringBuilder()
    /** OpenAI user turns committed but not transcribed yet; replies wait so the chat stays in order. */
    private val pendingUserItems = LinkedHashSet<String?>()
    private val heldAgentTurns = ArrayList<String>()
    @Volatile private var heldSince = 0L

    override var micMuted: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            microphone?.muted = value
            if (value) {
                _inputLevel.value = 0f
                // Gemini holds trailing audio until told the stream paused.
                if (changed && connected) protocol.audioPaused()?.let { socket?.send(it) }
            }
        }

    override var speakerMuted: Boolean = false
        set(value) {
            field = value
            player?.muted = value
        }

    override fun openAudio(micAllowed: Boolean) {
        this.micAllowed = micAllowed
        var micFailed = false
        synchronized(this) {
            if (finished || audioOpen) return
            audioOpen = true
            routing.acquire()
            proximity.start()
            val newPlayer = VoicePlayer(protocol.outputSampleRate) { level -> _outputLevel.value = level }
            newPlayer.muted = speakerMuted
            newPlayer.start()
            player = newPlayer
            if (micAllowed && !openMic()) micFailed = true
        }
        if (micFailed) listener.onMicUnavailable()
    }

    /** Caller holds the lock. False when the mic couldn't be opened. */
    private fun openMic(): Boolean {
        val mic = VoiceMicrophone(
            sampleRate = protocol.inputSampleRate,
            onChunk = ::onMicChunk,
            onLevel = { level ->
                _inputLevel.value = level
                if (!everReady && level > EARLY_SPEECH_LEVEL) heardEarly = true
            },
        )
        mic.muted = micMuted
        if (!mic.start()) {
            Log.w(TAG, "Microphone unavailable; typing only")
            return false
        }
        microphone = mic
        return true
    }

    private fun onMicChunk(pcm: ByteArray) {
        synchronized(early) {
            if (!connected) {
                early.addLast(pcm)
                while (early.size > MAX_EARLY_CHUNKS) early.removeFirst()
            } else {
                socket?.send(protocol.audio(Base64.encodeToString(pcm, Base64.NO_WRAP)))
            }
        }
    }

    /** Opens the socket with [apiKey] and sets the session up with [spec]. */
    fun start(apiKey: String, spec: RealtimeSessionSpec, micAllowed: Boolean) {
        openAudio(micAllowed)
        if (finished) return
        this.apiKey = apiKey
        this.spec = spec
        connect()
    }

    private fun connect() {
        val spec = spec ?: return
        val request = Request.Builder().url(protocol.url(apiKey, spec.model)).apply {
            protocol.headers(apiKey).forEach { (name, value) -> header(name, value) }
        }.build()
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    protocol.setup(spec, resumeHandle).forEach(webSocket::send)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (webSocket !== socket) return
                    val events = runCatching { protocol.parse(text) { Base64.decode(it, Base64.DEFAULT) } }
                        .onFailure { Log.w(TAG, "Couldn't read a server message", it) }
                        .getOrDefault(emptyList())
                    synchronized(turnLock) {
                        events.forEach { handle(webSocket, it) }
                        afterEvents()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    closed(webSocket, code, reason, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed(webSocket, code, reason, null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "${protocol.provider.label} socket failed", t)
                    closed(webSocket, response?.code ?: -1, response?.message.orEmpty(), t)
                }
            },
        )
    }

    /** A connection ended. A resumable one that the user didn't end is picked up on a new socket. */
    private fun closed(webSocket: WebSocket, code: Int, reason: String, error: Throwable?) {
        if (webSocket !== socket || finished) return
        synchronized(early) { connected = false }
        if (!endedByUser && protocol.resumable && everReady && resumeHandle != null && reconnects < MAX_RECONNECTS) {
            reconnects++
            Log.i(TAG, "Resuming the ${protocol.provider.label} session on a new connection")
            connect()
            return
        }
        val name = protocol.provider.label
        finish(
            when {
                endedByUser -> null
                code == 401 || code == 403 -> "$name refused the voice session. Check your $name key."
                code == 404 -> "$name doesn't offer this voice model to your key."
                code == 429 -> "$name says you're over your rate limit or quota."
                error != null -> "Lost the connection to $name."
                code == 1000 -> reason.takeIf { it.isNotBlank() }
                else -> reason.ifBlank { "The $name voice session closed." }
            },
        )
    }

    private fun handle(webSocket: WebSocket, event: RealtimeEvent) {
        if (finished) return
        when (event) {
            RealtimeEvent.Ready -> ready(webSocket)
            is RealtimeEvent.Audio -> {
                if (dropped(event.itemId)) return
                if (!replying || (event.itemId != null && event.itemId != replyItemId)) startReply(event.itemId)
                player?.enqueue(event.pcm)
                awaitingReply = false
                _activity.value = VoiceAgentActivity.Speaking
                listener.onActivity()
            }
            is RealtimeEvent.UserTranscriptDelta -> {
                // Gemini's pieces carry their own spacing; OpenAI's are word pieces of one turn.
                userText.append(event.text)
                _userCaption.value = userText.toString().trim()
                listener.onActivity()
            }
            RealtimeEvent.UserSpeechStarted -> {
                // The user talked over the reply: the server has stopped it, so stop playing and
                // tell it how much was heard.
                if (replying) stopReply(serverAlreadyStopped = true)
                userText.clear()
                _activity.value = VoiceAgentActivity.Listening
                listener.onActivity()
            }
            is RealtimeEvent.UserCommitted -> {
                pendingUserItems += event.itemId
                awaitingReply = true
                _activity.value = VoiceAgentActivity.Thinking
            }
            is RealtimeEvent.UserTranscript -> {
                pendingUserItems.remove(event.itemId)
                userText.clear()
                _userCaption.value = ""
                if (event.text.isNotBlank()) userTurn(event.text)
                releaseHeldTurns()
            }
            is RealtimeEvent.UserTranscriptFailed -> {
                pendingUserItems.remove(event.itemId)
                userText.clear()
                _userCaption.value = ""
                releaseHeldTurns()
            }
            is RealtimeEvent.AgentTranscriptDelta -> {
                if (dropped(event.itemId)) return
                if (!replying || (event.itemId != null && event.itemId != replyItemId)) startReply(event.itemId)
                agentText.append(event.text)
                _agentCaption.value = agentText.toString().trim()
            }
            is RealtimeEvent.AgentTranscriptDone -> {
                // A reply that was cut off was already saved with what had been said.
                if (dropped(event.itemId)) return
                val text = event.text.ifBlank { agentText.toString().trim() }
                agentText.clear()
                if (text.isNotBlank()) agentTurn(text)
            }
            RealtimeEvent.TurnComplete -> {
                // Gemini's reply text ends here; OpenAI's already ended with its transcript.
                val text = agentText.toString().trim()
                agentText.clear()
                if (text.isNotBlank()) agentTurn(text)
                if (!protocol.finalUserTranscripts) flushUserTurn()
                replying = false
                droppingReply = false
                awaitingReply = false
            }
            RealtimeEvent.Interrupted -> {
                // Gemini heard the user over its reply and stopped it. What it said so far stands.
                player?.interrupt()
                val text = agentText.toString().trim()
                agentText.clear()
                if (text.isNotBlank()) agentTurn(text)
                replying = false
                droppingReply = false
                _activity.value = VoiceAgentActivity.Listening
            }
            is RealtimeEvent.Usage -> {
                usage += event.usage
                listener.onUsage(usage)
            }
            is RealtimeEvent.ResumeHandle -> resumeHandle = event.handle
            RealtimeEvent.GoAway -> if (protocol.resumable && resumeHandle != null) {
                // Hand over before the server cuts us off: the next connection resumes the call.
                Log.i(TAG, "Server is closing this connection soon; resuming on a new one")
                webSocket.close(1000, "Resuming")
            }
            is RealtimeEvent.Error -> Log.w(TAG, "${protocol.provider.label} error: ${event.message}").also {
                if (!everReady) {
                    // Rejected during setup (a bad model or field): end with the server's words.
                    finish("${protocol.provider.label}: ${event.message}")
                }
            }
        }
    }

    private fun ready(webSocket: WebSocket) {
        val firstTime = !everReady
        everReady = true
        // Send what the user already said, then let the mic stream straight through.
        synchronized(early) {
            early.forEach { pcm -> webSocket.send(protocol.audio(Base64.encodeToString(pcm, Base64.NO_WRAP))) }
            early.clear()
            connected = true
        }
        if (micMuted) protocol.audioPaused()?.let(webSocket::send)
        if (!firstTime) return
        _activity.value = VoiceAgentActivity.Listening
        listener.onConnected(conversationId)
        val greet = spec?.greet == true && !spokeEarly
        if (greet) {
            awaitingReply = true
            protocol.greeting().forEach(webSocket::send)
        }
    }

    private fun startReply(itemId: String?) {
        // The user's words come before the reply in the chat (Gemini: whatever was heard so far).
        if (!protocol.finalUserTranscripts) flushUserTurn()
        val sameReply = itemId != null && itemId == replyItemId
        replying = true
        replyItemId = itemId
        // OpenAI can end an older response after this one began; its words so far stay.
        if (sameReply) return
        agentText.clear()
        _agentCaption.value = ""
        player?.markReplyStart()
    }

    /** Gemini: the user's turn is everything heard before the reply started. */
    private fun flushUserTurn() {
        val text = userText.toString().trim()
        userText.clear()
        _userCaption.value = ""
        if (text.isNotBlank()) userTurn(text)
    }

    private fun userTurn(text: String) {
        _lastUserTurn.value = text
        _userTurns.value += 1
        listener.onUserTurn(text)
        listener.onActivity()
    }

    private fun agentTurn(text: String) {
        _agentCaption.value = text
        if (protocol.finalUserTranscripts && pendingUserItems.isNotEmpty()) {
            // The user's transcript is still coming: hold the reply so it lands after it.
            if (heldAgentTurns.isEmpty()) heldSince = System.currentTimeMillis()
            heldAgentTurns += text
        } else {
            listener.onAgentTurn(text)
        }
        listener.onActivity()
    }

    private fun releaseHeldTurns() {
        if (pendingUserItems.isNotEmpty() && System.currentTimeMillis() - heldSince < HOLD_MAX_MS) return
        pendingUserItems.clear()
        heldAgentTurns.forEach(listener::onAgentTurn)
        heldAgentTurns.clear()
    }

    private fun dropped(itemId: String?): Boolean =
        if (itemId != null) itemId in cancelledItems else droppingReply

    /**
     * Cuts the reply off where the user stopped hearing it: playback stops, the words shown so far
     * are saved as the reply, and the rest of it is dropped as it arrives.
     */
    private fun cancelCurrentReply(): RealtimeVoiceProtocol.Cancel {
        val played = player?.playedMillis ?: 0L
        player?.interrupt()
        val itemId = replyItemId
        if (itemId != null) cancelledItems += itemId else droppingReply = true
        val said = agentText.toString().trim()
        agentText.clear()
        if (said.isNotBlank()) agentTurn(said)
        replying = false
        return RealtimeVoiceProtocol.Cancel(itemId, played)
    }

    /** Stops the reply that's playing; [serverAlreadyStopped] when the server cut it off itself. */
    private fun stopReply(serverAlreadyStopped: Boolean) {
        protocol.cancelReply(cancelCurrentReply(), serverAlreadyStopped).forEach { socket?.send(it) }
    }

    private fun afterEvents() {
        if (_activity.value == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = if (awaitingReply) VoiceAgentActivity.Thinking else VoiceAgentActivity.Listening
        }
    }

    override fun refreshActivity() = synchronized(turnLock) {
        if (_activity.value == VoiceAgentActivity.Speaking && player?.isSpeaking == false) {
            _activity.value = VoiceAgentActivity.Listening
        }
        // A user transcript that never came shouldn't hold the reply back forever.
        if (heldAgentTurns.isNotEmpty() && System.currentTimeMillis() - heldSince >= HOLD_MAX_MS) releaseHeldTurns()
    }

    override fun sendText(text: String): Boolean = synchronized(turnLock) {
        val clean = text.trim()
        if (clean.isEmpty() || finished || !connected) return false
        val ws = socket ?: return false
        // Typing over the reply cuts it off, just like talking over it would.
        val cancel = if (replying) cancelCurrentReply() else null
        // Anything the user said before typing is its own turn, first.
        if (!protocol.finalUserTranscripts) flushUserTurn()
        _agentCaption.value = ""
        _lastUserTurn.value = clean
        _userTurns.value += 1
        awaitingReply = true
        _activity.value = VoiceAgentActivity.Thinking
        var sent = true
        protocol.userText(clean, cancel).forEach { sent = ws.send(it) && sent }
        return sent
    }

    override fun cutIn(): Boolean = synchronized(turnLock) {
        if (finished || _activity.value != VoiceAgentActivity.Speaking) return false
        stopReply(serverAlreadyStopped = false)
        _activity.value = VoiceAgentActivity.Listening
        listener.onActivity()
        return true
    }

    /** Neither API has a "user is typing" signal; typing still counts as the call being alive. */
    override fun typing() {
        listener.onActivity()
    }

    override fun end() {
        endedByUser = true
        socket?.close(1000, "User ended the call")
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
        synchronized(early) {
            early.clear()
            connected = false
        }
        if (!endedByUser) socket?.close(1000, null)
        proximity.stop()
        if (audioOpen) routing.release()
        _inputLevel.value = 0f
        _outputLevel.value = 0f
        socket = null
        // Whatever was said last but never closed out still belongs in the chat.
        synchronized(turnLock) {
            if (!protocol.finalUserTranscripts) flushUserTurn()
            agentText.toString().trim().takeIf { it.isNotBlank() }?.let(listener::onAgentTurn)
            agentText.clear()
            heldAgentTurns.forEach(listener::onAgentTurn)
            heldAgentTurns.clear()
        }
        listener.onEnded(error)
    }

    private companion object {
        const val TAG = "RealtimeVoiceSession"
        /** About 20 s of 100 ms chunks: enough for a slow connection or a resume. */
        const val MAX_EARLY_CHUNKS = 200
        const val EARLY_SPEECH_LEVEL = 0.45f
        const val MAX_RECONNECTS = 12
        const val HOLD_MAX_MS = 4_000L
    }
}
