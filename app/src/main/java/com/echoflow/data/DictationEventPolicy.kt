package com.echoflow.data

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Filters metadata only. External focus/window transitions must never trust a cached package. */
internal fun needsDictationReconcile(
    type: Int,
    eventPackage: String?,
    ownPackage: String,
    windowId: Int,
    ownWindowId: Int?,
    contentChanges: Int,
    windowChanges: Int,
): Boolean = when (type) {
    AccessibilityEvent.TYPE_VIEW_FOCUSED,
    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
        eventPackage != ownPackage || windowId != ownWindowId
    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
        eventPackage != ownPackage &&
            (contentChanges == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED ||
                contentChanges and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0)
    AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
        val handoff = AccessibilityEvent.WINDOWS_CHANGE_ADDED or
            AccessibilityEvent.WINDOWS_CHANGE_REMOVED or
            AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or AccessibilityEvent.WINDOWS_CHANGE_ACTIVE
        windowChanges == 0 || windowChanges and handoff != 0 ||
            (windowChanges and AccessibilityEvent.WINDOWS_CHANGE_BOUNDS != 0 && windowId != ownWindowId)
    }
    else -> false
}

/** One worker and one pending slot: bursts are bounded without starving work during continuous events. */
internal class DictationWorkQueue(scope: CoroutineScope, private val work: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val job = scope.launch {
        for (request in requests) {
            delay(60)
            // Events during the settling interval are covered by the same fresh focus inspection.
            requests.tryReceive()
            work()
        }
    }
    fun request() { requests.trySend(Unit) }
    fun close() { requests.close(); job.cancel() }
}
