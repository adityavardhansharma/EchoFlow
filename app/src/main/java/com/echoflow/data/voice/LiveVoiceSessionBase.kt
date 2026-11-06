package com.echoflow.data.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What every [LiveVoiceSession] keeps the same way: the flows the call screen reads, the
 * early-speech flag, and the call's audio routing (call mode, and the earpiece when held to the
 * ear). Subclasses own their socket, mic and player.
 */
internal abstract class LiveVoiceSessionBase(context: Context) : LiveVoiceSession {
    protected val _activity = MutableStateFlow(VoiceAgentActivity.Connecting)
    final override val activity: StateFlow<VoiceAgentActivity> = _activity.asStateFlow()

    protected val _inputLevel = MutableStateFlow(0f)
    final override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    protected val _outputLevel = MutableStateFlow(0f)
    final override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    /** What the user is saying right now, before the provider finalizes it. */
    protected val _userCaption = MutableStateFlow("")
    final override val userCaption: StateFlow<String> = _userCaption.asStateFlow()

    /** The reply as it streams in, then as spoken. Cleared when the user starts a new turn. */
    protected val _agentCaption = MutableStateFlow("")
    final override val agentCaption: StateFlow<String> = _agentCaption.asStateFlow()

    /** The user's last finished turn, spoken or typed, kept on screen under the line. */
    protected val _lastUserTurn = MutableStateFlow("")
    final override val lastUserTurn: StateFlow<String> = _lastUserTurn.asStateFlow()

    /** Counts the user's finished turns, so the screen can pulse the moment one lands. */
    protected val _userTurns = MutableStateFlow(0)
    final override val userTurns: StateFlow<Int> = _userTurns.asStateFlow()

    /** True once the user spoke before the call connected, so the greeting is skipped. */
    @Volatile protected var heardEarly = false
    final override val spokeEarly: Boolean get() = heardEarly

    private val routing = VoiceAudioRouting(context)
    /** Held to the ear: the earpiece plays, as on a phone call (the screen goes dark separately). */
    private val proximity = VoiceProximity(context) { atEar -> routing.holdToEar(atEar) }

    /** Puts the phone in call mode and starts following the proximity sensor. */
    protected fun acquireCallAudio() {
        routing.acquire()
        proximity.start()
    }

    /** Undoes [acquireCallAudio] when [acquired], and drops both level meters to zero. */
    protected fun releaseCallAudio(acquired: Boolean) {
        proximity.stop()
        if (acquired) routing.release()
        _inputLevel.value = 0f
        _outputLevel.value = 0f
    }
}
