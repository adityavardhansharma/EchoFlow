package com.echoflow.data

/**
 * Protects a navigation from being undone by slower work that started before it.
 *
 * Opening a remembered conversation means a database read, and a read can still be in flight
 * when the user switches mode again, taps a conversation, or opens one from a notification. An
 * unguarded coroutine then assigns its stale answer on top of them: the wrong thread on screen,
 * and the next message filed into it.
 *
 * Existing as a named object rather than a loose counter is the point. This has been got wrong
 * twice — once on the mode-switch path and once at startup, where "nothing can have happened
 * yet" is exactly backwards, because a cold database makes that lookup the slowest it ever is.
 * A guard you have to hold is harder to forget than a convention you have to remember.
 *
 * Confined to the main thread, like the state it protects.
 */
class NavigationGuard {
    private var epoch = 0L

    /** Records that the user has moved. Call from wherever the open conversation changes. */
    fun navigated() {
        epoch++
    }

    /** Takes a token before suspending. */
    fun begin(): Long = epoch

    /** Whether a [begin] token is still good — that is, nothing has navigated since. */
    fun stillCurrent(token: Long): Boolean = token == epoch
}
