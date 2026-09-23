package com.echoflow.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * Coordinates on-device inference: exactly one local LLM generation may run at a time (they
 * compete for the same RAM and accelerators), while cloud requests remain unlimited. A single
 * gate object replaces scattered "is something local running?" flag checks.
 *
 * Interactive requests fail fast with [LocalInferenceBusy]. Background schedules may
 * opt into a bounded, cancellable wait before starting any inference.
 */
class LocalInferenceGate {
    private val mutex = Mutex()

    @Volatile
    var currentHolder: String? = null
        private set

    /**
     * Runs [block] holding the gate. Only acquisition is bounded by [waitTimeoutMillis];
     * inference itself is not restarted or timed out. The gate is released on cancellation too.
     */
    suspend fun <T> withExclusive(
        label: String,
        waitTimeoutMillis: Long = 0,
        block: suspend () -> T,
    ): T {
        require(waitTimeoutMillis >= 0)
        var remaining = waitTimeoutMillis
        // No owner token: Mutex.tryLock(owner) THROWS on a repeat acquisition by the same
        // owner instead of returning false, which would turn "busy" into a crash.
        while (!mutex.tryLock()) {
            if (remaining <= 0) throw LocalInferenceBusy(currentHolder ?: "another on-device task")
            val pause = minOf(250L, remaining)
            delay(pause)
            remaining -= pause
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
