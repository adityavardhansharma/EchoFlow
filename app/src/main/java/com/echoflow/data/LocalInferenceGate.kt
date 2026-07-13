package com.echoflow.data

import kotlinx.coroutines.sync.Mutex

/**
 * Coordinates on-device inference: exactly one local LLM generation OR one local image
 * generation may run at a time (both compete for the same RAM and accelerators), while
 * cloud requests remain unlimited. A single gate object replaces scattered "is something
 * local running?" flag checks.
 *
 * Non-queuing by design: a second local request fails fast with [LocalInferenceBusy]
 * (matching the app's existing "the on-device model is still responding" behaviour)
 * instead of silently waiting behind a minutes-long generation.
 */
class LocalInferenceGate {
    private val mutex = Mutex()

    @Volatile
    var currentHolder: String? = null
        private set

    /**
     * Runs [block] holding the gate. Throws [LocalInferenceBusy] immediately when another
     * local task holds it. The gate is released on success, failure and cancellation alike.
     */
    suspend fun <T> withExclusive(label: String, block: suspend () -> T): T {
        // No owner token: Mutex.tryLock(owner) THROWS on a repeat acquisition by the same
        // owner instead of returning false, which would turn "busy" into a crash.
        if (!mutex.tryLock()) {
            throw LocalInferenceBusy(currentHolder ?: "another on-device task")
        }
        currentHolder = label
        try {
            return block()
        } finally {
            currentHolder = null
            mutex.unlock()
        }
    }

    val isBusy: Boolean get() = mutex.isLocked
}

class LocalInferenceBusy(holder: String) :
    Exception("On-device compute is busy with $holder. Wait for it to finish and try again.")
