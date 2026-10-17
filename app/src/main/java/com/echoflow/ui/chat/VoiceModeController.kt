package com.echoflow.ui.chat

import android.app.Application
import com.echoflow.data.ChatMessage
import com.echoflow.data.SettingsRepository
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.usage.UsageKind
import com.echoflow.data.usage.UsageLedger
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageRecord
import com.echoflow.data.voice.ElevenLabsApiException
import com.echoflow.data.voice.ElevenLabsVoiceApi
import com.echoflow.data.voice.VoiceAgentActivity
import com.echoflow.data.voice.VoiceAgentProvisioner
import com.echoflow.data.voice.VoiceAgentSpec
import com.echoflow.data.voice.VoiceApiJson
import com.echoflow.data.voice.VoiceConversationRecord
import com.echoflow.data.voice.VoiceLlms
import com.echoflow.data.voice.VoiceModeAuth
import com.echoflow.data.voice.VoiceModeService
import com.echoflow.data.voice.VoiceModeSettings
import com.echoflow.data.voice.VoiceSession
import com.echoflow.data.voice.VoiceSessionListener
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class VoiceModePhase { Off, Starting, Live, Saving }

/** What the voice-mode screen shows. Live levels and captions are separate flows (they change fast). */
data class VoiceModeUiState(
    val phase: VoiceModePhase = VoiceModePhase.Off,
    /** "Setting up your voice…", "Connecting…", "Saving your conversation…". */
    val step: String? = null,
    val micMuted: Boolean = false,
    /** False when the mic permission was refused: the session is typing-only. */
    val micAvailable: Boolean = true,
    val speakerMuted: Boolean = false,
    val llmLabel: String = "",
    val voiceName: String? = null,
    /** Counts down the last [VoiceModeController.IDLE_WARNING_SECONDS] before an idle session ends. */
    val idleSecondsLeft: Int? = null,
) {
    val active: Boolean get() = phase != VoiceModePhase.Off
}

/** What a call needs from the chat it starts in, captured once when the call starts. */
data class VoiceChatContext(
    val history: List<ChatMessage> = emptyList(),
    /** The owning project's instructions and reference documents, or "" outside a project. */
    val projectContext: String = "",
    val projectId: String? = null,
    /** True when the call started in a temporary chat: its chat must not outlive it. */
    val temporary: Boolean = false,
)

/** Where a voice conversation's turns are written: the open chat, or a new one. */
interface VoiceTranscriptSink {
    suspend fun prepare(chatId: String?): VoiceChatContext

    /** The chat the session writes into: [startChatId] when it had one, otherwise a new chat. */
    suspend fun chatFor(startChatId: String?, firstUserText: String, context: VoiceChatContext): String
    suspend fun put(message: ChatMessage)
    suspend fun remove(messageId: String)

    /** Swaps [removeIds] for [inserts] in one transaction: a failure keeps the original turns. */
    suspend fun replace(chatId: String, removeIds: List<String>, inserts: List<ChatMessage>)

    /** The call is over and every write for it has run. */
    suspend fun sessionEnded(chatId: String?, context: VoiceChatContext)
}

/**
 * Voice mode: a live ElevenLabs Agents conversation that continues the open chat.
 *
 * - Starting makes sure the user's EchoFlow agent matches their Voice mode settings, then opens a
 *   session that carries the chat so far, so voice picks up where typing left off.
 * - Every finished turn, spoken or typed, is saved into the chat as it happens, so nothing is lost
 *   if the app dies mid-call.
 * - Ending fetches ElevenLabs' own transcript and, if it differs (an interrupted reply, a
 *   corrected word), rewrites this session's turns to match. The chat then shows the conversation
 *   exactly as it went, and later typed messages continue from it with the chat's own model.
 * - A session with nothing said or played for [IDLE_TIMEOUT_SECONDS] ends itself, since
 *   ElevenLabs bills every open minute.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModeController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val sink: VoiceTranscriptSink,
    private val voiceSettings: VoiceModeSettings = VoiceModeSettings.get(app),
    private val api: ElevenLabsVoiceApi = ElevenLabsVoiceApi(),
) {
    private val auth = VoiceModeAuth(voiceSettings, api)
    private val provisioner = VoiceAgentProvisioner(voiceSettings, api)

    private val _state = MutableStateFlow(VoiceModeUiState())
    val state: StateFlow<VoiceModeUiState> = _state.asStateFlow()

    /** One-off messages for a toast: why a session couldn't start, or why it ended. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val session = MutableStateFlow<VoiceSession?>(null)
    val activity: StateFlow<VoiceAgentActivity> = session
        .flatMapLatest { it?.activity ?: flowOf(VoiceAgentActivity.Connecting) }
        .stateIn(scope, SharingStarted.Eagerly, VoiceAgentActivity.Connecting)
    val inputLevel: StateFlow<Float> = session.flatMapLatest { it?.inputLevel ?: flowOf(0f) }
        .stateIn(scope, SharingStarted.Eagerly, 0f)
    val outputLevel: StateFlow<Float> = session.flatMapLatest { it?.outputLevel ?: flowOf(0f) }
        .stateIn(scope, SharingStarted.Eagerly, 0f)
    val userCaption: StateFlow<String> = session.flatMapLatest { it?.userCaption ?: flowOf("") }
        .stateIn(scope, SharingStarted.Eagerly, "")
    val agentCaption: StateFlow<String> = session.flatMapLatest { it?.agentCaption ?: flowOf("") }
        .stateIn(scope, SharingStarted.Eagerly, "")

    /** Voice mode is offered only once the saved ElevenLabs key has authenticated. */
    val available: StateFlow<Boolean> = combine(
        settingsRepository.customProviderConfig,
        voiceSettings.verifiedKeyHash,
    ) { config, verified ->
        config.elevenLabsAvailable && verified != null && verified == UsageKeys.hash(config.elevenLabsApiKey)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Turn writes run one at a time, in arrival order, on this queue. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var log: SessionLog? = null
    private var startJob: Job? = null
    private var idleJob: Job? = null
    @Volatile private var lastActivityAt = 0L

    init {
        scope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Couldn't save a voice turn", e)
                }
            }
        }
        // A newly saved key is checked once, so the waveform button appears without a Settings visit.
        scope.launch {
            settingsRepository.customProviderConfig
                .map { if (it.elevenLabsAvailable) it.elevenLabsApiKey.trim() else "" }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key.isNotEmpty() && !voiceSettings.isVerified(key)) auth.verify(key) { currentKey() == key }
                }
        }
    }

    private fun currentKey(): String {
        val providers = settingsRepository.customProviderConfig.value
        return if (providers.elevenLabsAvailable) providers.elevenLabsApiKey.trim() else ""
    }

    /**
     * Starts voice mode on [chatId] (null: a new chat, made when the first turn is saved).
     * [micAllowed] false runs a typing-only session: the agent still answers out loud.
     */
    fun start(chatId: String?, micAllowed: Boolean) {
        if (_state.value.active) return
        val providers = settingsRepository.customProviderConfig.value
        val key = if (providers.elevenLabsAvailable) providers.elevenLabsApiKey.trim() else ""
        if (key.isEmpty()) {
            _notice.value = "Add an ElevenLabs key under Settings → Custom to use voice mode."
            return
        }
        val config = voiceSettings.config.value
        _state.value = VoiceModeUiState(
            phase = VoiceModePhase.Starting,
            step = "Setting up your voice…",
            micMuted = !micAllowed,
            micAvailable = micAllowed,
            llmLabel = VoiceLlms.label(config.llm),
            voiceName = config.voiceName,
        )
        val sessionLog = SessionLog(startChatId = chatId, apiKey = key, llm = config.llm)
        log = sessionLog
        startJob = scope.launch {
            try {
                val agentId = provisioner.ensureAgent(key, config).getOrThrow()
                _state.update { it.copy(step = "Connecting…") }
                val url = api.signedUrl(key, agentId).recoverCatching { error ->
                    // The saved agent was deleted on ElevenLabs: make a new one and try once more.
                    if (error !is ElevenLabsApiException || error.code != 404) throw error
                    voiceSettings.forgetAgent()
                    api.signedUrl(key, provisioner.ensureAgent(key, config).getOrThrow()).getOrThrow()
                }.getOrThrow()
                val context = sink.prepare(chatId)
                sessionLog.context = context
                val initiation = VoiceApiJson.write(
                    VoiceAgentSpec.initiation(config, context.history, context.projectContext),
                )
                if (!isActive || log !== sessionLog) return@launch
                val live = VoiceSession(app, listenerFor(sessionLog))
                // Whatever the user toggled while the call was connecting carries into it.
                live.micMuted = _state.value.micMuted
                live.speakerMuted = _state.value.speakerMuted
                session.value = live
                VoiceModeService.endHandler = { scope.launch { end() } }
                VoiceModeService.start(app)
                sessionLog.startedAt = System.currentTimeMillis()
                lastActivityAt = sessionLog.startedAt
                live.start(url, initiation, micAllowed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Voice mode couldn't start", e)
                if (e is ElevenLabsApiException && e.unauthorized) voiceSettings.markUnverified()
                _notice.value = "Voice mode couldn't start: ${e.message ?: "unknown error"}"
                reset()
            }
        }
    }

    /** Ends the session and saves it into the chat. Safe to call in any phase. */
    fun end(note: String? = null) {
        when (_state.value.phase) {
            VoiceModePhase.Off, VoiceModePhase.Saving -> return
            VoiceModePhase.Starting -> if (session.value == null) {
                startJob?.cancel()
                reset()
                return
            }
            VoiceModePhase.Live -> Unit
        }
        if (note != null) _notice.value = note
        _state.update { it.copy(phase = VoiceModePhase.Saving, step = "Saving your conversation…", idleSecondsLeft = null) }
        idleJob?.cancel()
        // The session reports back through onEnded, which saves and closes the screen.
        session.value?.end()
    }

    /** Sends a typed message into the call. False when it wasn't sent, so the draft is kept. */
    fun sendText(text: String): Boolean {
        val clean = text.trim()
        val live = session.value ?: return false
        if (clean.isEmpty() || _state.value.phase != VoiceModePhase.Live) return false
        if (!live.sendText(clean)) {
            _notice.value = "Couldn't send that. The voice session isn't connected."
            return false
        }
        lastActivityAt = System.currentTimeMillis()
        log?.let { sessionLog -> writes.trySend { saveUserTurn(sessionLog, clean) } }
        return true
    }

    fun typing() {
        if (_state.value.phase == VoiceModePhase.Live) session.value?.typing()
    }

    fun toggleMic() {
        val current = _state.value
        if (!current.micAvailable) return
        val muted = !current.micMuted
        session.value?.micMuted = muted
        _state.update { it.copy(micMuted = muted) }
    }

    fun toggleSpeaker() {
        val muted = !_state.value.speakerMuted
        session.value?.speakerMuted = muted
        _state.update { it.copy(speakerMuted = muted) }
    }

    fun consumeNotice() { _notice.value = null }

    /** The chat screen is going away for good: hang up without waiting to reconcile. */
    fun release() {
        startJob?.cancel()
        idleJob?.cancel()
        session.value?.end()
        session.value = null
        VoiceModeService.endHandler = null
        VoiceModeService.stop(app)
    }

    private fun listenerFor(sessionLog: SessionLog) = object : VoiceSessionListener {
        override fun onConnected(conversationId: String) {
            scope.launch {
                sessionLog.conversationId = conversationId
                if (_state.value.phase == VoiceModePhase.Starting) {
                    _state.update { it.copy(phase = VoiceModePhase.Live, step = null) }
                }
                startIdleWatch()
            }
        }

        override fun onUserTurn(text: String) {
            writes.trySend { saveUserTurn(sessionLog, text) }
        }

        override fun onAgentTurn(text: String) {
            writes.trySend { saveAgentTurn(sessionLog, text) }
        }

        override fun onAgentCorrection(original: String, corrected: String) {
            writes.trySend { correctAgentTurn(sessionLog, original, corrected) }
        }

        override fun onActivity() {
            lastActivityAt = System.currentTimeMillis()
        }

        override fun onMicUnavailable() {
            scope.launch {
                _state.update { it.copy(micAvailable = false, micMuted = true) }
                _notice.value = "The microphone isn't available, so this call is typing only."
            }
        }

        override fun onEnded(error: String?) {
            scope.launch { finish(sessionLog, error) }
        }
    }

    private fun startIdleWatch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive && _state.value.phase == VoiceModePhase.Live) {
                delay(1_000)
                val live = session.value ?: break
                live.refreshActivity()
                // Only audio actually playing keeps the call alive. A reply that never arrives
                // ("Thinking" forever) must still reach the idle countdown.
                if (activity.value == VoiceAgentActivity.Speaking) {
                    lastActivityAt = System.currentTimeMillis()
                }
                val idle = (System.currentTimeMillis() - lastActivityAt) / 1_000
                val left = (IDLE_TIMEOUT_SECONDS - idle).toInt()
                val warning = left.takeIf { it in 1..IDLE_WARNING_SECONDS }
                if (_state.value.idleSecondsLeft != warning) _state.update { it.copy(idleSecondsLeft = warning) }
                if (left <= 0) {
                    end("Voice mode ended after 5 minutes of quiet.")
                    break
                }
            }
        }
    }

    private suspend fun finish(sessionLog: SessionLog, error: String?) {
        if (sessionLog.finished) return
        sessionLog.finished = true
        idleJob?.cancel()
        VoiceModeService.endHandler = null
        VoiceModeService.stop(app)
        if (error != null) _notice.value = error
        _state.update { it.copy(phase = VoiceModePhase.Saving, step = "Saving your conversation…", idleSecondsLeft = null) }
        // Runs after every turn already queued, so the reconcile sees the whole live record.
        val done = CompletableDeferred<Unit>()
        writes.trySend {
            try {
                reconcile(sessionLog)
            } finally {
                try {
                    sink.sessionEnded(sessionLog.chatId, sessionLog.context)
                } finally {
                    done.complete(Unit)
                }
            }
        }
        withTimeoutOrNull(RECONCILE_TIMEOUT_MS + 2_000) { done.await() }
        if (log === sessionLog) reset()
    }

    private fun reset() {
        session.value = null
        log = null
        startJob = null
        idleJob?.cancel()
        idleJob = null
        _state.value = VoiceModeUiState()
    }

    // ── Turn persistence (runs on the write queue) ────────────────────────────────────────

    private suspend fun saveUserTurn(sessionLog: SessionLog, text: String) {
        val chatId = sessionLog.chatId
            ?: sink.chatFor(sessionLog.startChatId, text, sessionLog.context).also { sessionLog.chatId = it }
        // A greeting spoken before the first user turn had nowhere to go yet.
        sessionLog.pendingAgent.forEach { insert(sessionLog, chatId, fromUser = false, text = it) }
        sessionLog.pendingAgent.clear()
        insert(sessionLog, chatId, fromUser = true, text = text)
    }

    private suspend fun saveAgentTurn(sessionLog: SessionLog, text: String) {
        val chatId = sessionLog.chatId ?: sessionLog.startChatId?.also { sessionLog.chatId = it }
        if (chatId == null) {
            sessionLog.pendingAgent.add(text)
            return
        }
        insert(sessionLog, chatId, fromUser = false, text = text)
    }

    private suspend fun correctAgentTurn(sessionLog: SessionLog, original: String, corrected: String) {
        val saved = sessionLog.saved.lastOrNull { !it.fromUser && it.message.content == original }
            ?: sessionLog.saved.lastOrNull { !it.fromUser }
            ?: return
        if (corrected.isBlank()) {
            // Cut off before saying anything: the reply never happened.
            sink.remove(saved.message.id)
            sessionLog.saved.remove(saved)
        } else {
            val updated = saved.message.copy(content = corrected)
            sink.put(updated)
            sessionLog.saved[sessionLog.saved.indexOf(saved)] = saved.copy(message = updated)
        }
    }

    private suspend fun insert(sessionLog: SessionLog, chatId: String, fromUser: Boolean, text: String) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = if (fromUser) "user" else "assistant",
            content = text,
            createdAt = sessionLog.nextTimestamp(),
        )
        sink.put(message)
        sessionLog.saved.add(SavedTurn(fromUser, message))
    }

    /**
     * Makes the chat match ElevenLabs' transcript of the call and logs the call's cost. Gives up
     * quietly after [RECONCILE_TIMEOUT_MS]: the turns saved live are already in the chat.
     */
    private suspend fun reconcile(sessionLog: SessionLog) {
        val conversationId = sessionLog.conversationId ?: return
        var latest: VoiceConversationRecord? = null
        val record = withTimeoutOrNull(RECONCILE_TIMEOUT_MS) {
            repeat(RECONCILE_ATTEMPTS) { attempt ->
                api.conversation(sessionLog.apiKey, conversationId).getOrNull()?.let { latest = it }
                if (latest?.isFinal == true) return@withTimeoutOrNull latest
                if (attempt < RECONCILE_ATTEMPTS - 1) delay(RECONCILE_POLL_MS)
            }
            null
        }
        // Not final in time (still processing, or unreachable): keep the turns saved live and log
        // an estimate, so the call still shows in Spend.
        if (record == null) {
            recordSpend(sessionLog, conversationId, latest?.durationSeconds, final = null)
            return
        }
        recordSpend(sessionLog, conversationId, record.durationSeconds, final = record)
        val server = record.turns
        if (server.isEmpty() || server.none { it.fromUser }) return
        val chatId = sessionLog.chatId
            ?: sink.chatFor(sessionLog.startChatId, server.first { it.fromUser }.text, sessionLog.context)
                .also { sessionLog.chatId = it }
        val local = sessionLog.saved
        val sameShape = local.size == server.size && local.zip(server).all { (l, s) -> l.fromUser == s.fromUser }
        if (sameShape) {
            local.zip(server).forEach { (l, s) ->
                if (l.message.content != s.text) sink.put(l.message.copy(content = s.text))
            }
        } else {
            var previous = sessionLog.startedAt - 1
            val inserts = server.map { turn ->
                val at = maxOf(previous + 1, sessionLog.startedAt + (turn.timeInCallSeconds * 1_000).toLong())
                previous = at
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = if (turn.fromUser) "user" else "assistant",
                    content = turn.text,
                    createdAt = at,
                )
            }
            sink.replace(chatId, local.map { it.message.id }, inserts)
        }
    }

    /**
     * One Spend row per call. ElevenLabs reports the call's credits and, on most plans, its price;
     * otherwise the price is estimated from the call's length at the Agents list rate.
     */
    private fun recordSpend(
        sessionLog: SessionLog,
        conversationId: String,
        durationSeconds: Double?,
        final: VoiceConversationRecord?,
    ) {
        val seconds = durationSeconds ?: ((System.currentTimeMillis() - sessionLog.startedAt) / 1_000.0)
        val charged = final?.costFiat
        val usd = charged ?: (seconds / 60.0 * AGENT_USD_PER_MINUTE + (final?.llmChargeUsd ?: 0.0))
        UsageLedger.record(
            UsageRecord(
                id = "${UsageProvider.ElevenLabs.name}:voice:$conversationId",
                provider = UsageProvider.ElevenLabs.name,
                keyHash = UsageKeys.hash(sessionLog.apiKey),
                createdAt = sessionLog.startedAt,
                kind = UsageKind.Voice.name,
                model = sessionLog.llm,
                externalId = conversationId,
                costUsd = usd,
                credits = final?.credits,
                audioSeconds = seconds,
                detail = if (charged != null) "Voice mode" else "Voice mode · estimated from call length",
            ),
        )
    }

    private data class SavedTurn(val fromUser: Boolean, val message: ChatMessage)

    private class SessionLog(val startChatId: String?, val apiKey: String, val llm: String) {
        var chatId: String? = null
        var conversationId: String? = null
        var startedAt: Long = System.currentTimeMillis()
        var finished = false
        var context = VoiceChatContext()
        val saved = mutableListOf<SavedTurn>()
        val pendingAgent = mutableListOf<String>()
        private var lastTimestamp = 0L

        /** Strictly increasing, so the chat (ordered by createdAt) keeps the turns in order. */
        fun nextTimestamp(): Long {
            val now = maxOf(System.currentTimeMillis(), lastTimestamp + 1)
            lastTimestamp = now
            return now
        }
    }

    companion object {
        private const val TAG = "VoiceMode"
        const val IDLE_TIMEOUT_SECONDS = 5 * 60
        const val IDLE_WARNING_SECONDS = 30
        private const val RECONCILE_TIMEOUT_MS = 12_000L
        private const val RECONCILE_ATTEMPTS = 8
        private const val RECONCILE_POLL_MS = 1_500L
        /** ElevenLabs Agents list price per minute, used only when a call reports no price. */
        private const val AGENT_USD_PER_MINUTE = 0.10
    }
}
