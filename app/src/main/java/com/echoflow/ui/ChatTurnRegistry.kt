package com.echoflow.ui

import com.echoflow.ui.chat.SendClaim
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Every chat's turn in flight, in one place. A turn moves through three stages:
 *
 * 1. claimed: a send holds the chat (or the blank composer) before its first suspend,
 * 2. registered: its coroutine is the chat's job, so later sends see it as running,
 * 3. visible: it has an [ActiveStreamState] the UI draws.
 *
 * It ends when its own job clears it ([clearIfOwned]) or the chat's work is cancelled. Jobs are
 * only touched from the main thread; claims can be released from any thread.
 */
internal class ChatTurnRegistry {
    private val jobs = mutableMapOf<String, Job>()
    private val pending: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val _streams = MutableStateFlow<Map<String, ActiveStreamState>>(emptyMap())

    /** The visible turn of each chat that has one. */
    val streams: StateFlow<Map<String, ActiveStreamState>> = _streams.asStateFlow()

    /** True while [chatId] has a registered turn that hasn't finished. */
    fun isRunning(chatId: String): Boolean = jobs[chatId]?.isActive == true

    /** True if any visible turn runs on device. */
    fun anyLocal(): Boolean = _streams.value.values.any { it.isLocal }

    /** Claims [key] for a new send, or null if another send already holds it. */
    fun claim(key: String): SendClaim? = SendClaim(key).takeIf { it.tryClaim(pending) }

    /** Also holds [key] under [claim], once a blank-composer send has created its thread. */
    fun extend(claim: SendClaim, key: String) = claim.extend(pending, key)

    /** Lets go of [claim]. Safe from any thread and safe to repeat. */
    fun release(claim: SendClaim) = claim.release(pending)

    /** Makes [job] the chat's turn and lets go of [claim], which the job now replaces. */
    fun register(chatId: String, job: Job?, claim: SendClaim) {
        job?.let { jobs[chatId] = it }
        release(claim)
    }

    fun setState(chatId: String, state: ActiveStreamState?) {
        _streams.value = _streams.value.toMutableMap().apply {
            if (state == null) remove(chatId) else put(chatId, state)
        }
    }

    /** Clears the chat's turn only while [job] still owns it. */
    fun clearIfOwned(chatId: String, job: Job?): Boolean {
        if (job == null || jobs[chatId] !== job) return false
        jobs.remove(chatId)
        setState(chatId, null)
        return true
    }

    /** Asks the chat's turn to stop, without waiting. */
    fun cancel(chatId: String) {
        jobs[chatId]?.cancel()
    }

    /** Stops the chat's turn and waits until it has unwound. */
    suspend fun cancelAndJoin(chatId: String) {
        jobs.remove(chatId)?.cancelAndJoin()
    }
}
