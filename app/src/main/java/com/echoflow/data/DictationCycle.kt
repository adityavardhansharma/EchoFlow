package com.echoflow.data

/**
 * System dictation's state machine: Idle → (starting) → Recording → Transcribing → Idle, with
 * abort from anywhere. [generation] moves on with every new capture and every abort, so work
 * that suspended (a mic start, a transcription) can tell whether it still belongs to the
 * current dictation. Pure, so the rules are tested without an accessibility service.
 */
internal class DictationCycle {
    var phase: DictationPhase = DictationPhase.Idle
        private set

    /** A tap is between the press and the mic actually recording. */
    var starting: Boolean = false
        private set

    var generation: Long = 0L
        private set

    enum class Tap { Ignore, Stop, Start }

    /** What a tap on the bubble does now. [Tap.Start] also marks the start in progress. */
    fun tap(): Tap = when {
        phase == DictationPhase.Transcribing || starting -> Tap.Ignore
        phase == DictationPhase.Recording -> Tap.Stop
        else -> {
            starting = true
            Tap.Start
        }
    }

    /** The start attempt ended, whether or not it reached [recording]. */
    fun startSettled() {
        starting = false
    }

    /** A new capture begins; returns its generation. */
    fun newCapture(): Long = ++generation

    fun isCurrent(generation: Long): Boolean = generation == this.generation

    fun recording() {
        phase = DictationPhase.Recording
    }

    /** Recording → Transcribing. False when it wasn't recording. */
    fun stopRecording(): Boolean {
        if (phase != DictationPhase.Recording) return false
        phase = DictationPhase.Transcribing
        return true
    }

    /** The transcription for [generation] finished. False (and no change) if a newer one owns the cycle. */
    fun finished(generation: Long): Boolean {
        if (!isCurrent(generation)) return false
        phase = DictationPhase.Idle
        return true
    }

    /** Drops whatever is in flight; anything still running for the old generation is now stale. */
    fun abort() {
        generation++
        phase = DictationPhase.Idle
    }
}
