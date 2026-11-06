package com.echoflow.ui.chat

/** Key a send from the blank composer claims, before it has a thread of its own. */
const val BLANK_SEND_KEY = "\u0000blank"

/**
 * One send's hold on the conversations it is about to write into, taken before the send's first
 * suspend so a second send to the same chat is turned away at once instead of after validation.
 *
 * [pending] is shared by every send; it must be thread-safe, because [release] can run from a
 * coroutine completion handler.
 */
class SendClaim(private val firstKey: String) {
    private val keys = mutableListOf<String>()

    /** Claims [firstKey]. False if another send already holds it. */
    fun tryClaim(pending: MutableSet<String>): Boolean {
        if (!pending.add(firstKey)) return false
        synchronized(keys) { keys += firstKey }
        return true
    }

    /** Also holds [key], once the blank composer's send has created its thread. */
    fun extend(pending: MutableSet<String>, key: String) {
        synchronized(keys) {
            if (keys.isEmpty()) return
            if (pending.add(key)) keys += key
        }
    }

    /** Lets go of everything this claim holds. Safe to call more than once. */
    fun release(pending: MutableSet<String>) {
        synchronized(keys) {
            keys.forEach(pending::remove)
            keys.clear()
        }
    }
}
