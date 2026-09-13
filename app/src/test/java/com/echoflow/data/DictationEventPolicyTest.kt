package com.echoflow.data

import android.view.accessibility.AccessibilityEvent.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictationEventPolicyTest {
    private fun accepts(type: Int, pkg: String? = "external", id: Int = 2,
                        content: Int = 0, windows: Int = 0) =
        needsDictationReconcile(type, pkg, "echo", id, 1, content, windows)

    @Test fun `own UI churn is ignored but entering a new own window is inspected`() {
        assertFalse(accepts(TYPE_VIEW_FOCUSED, "echo", 1))
        assertFalse(accepts(TYPE_WINDOW_STATE_CHANGED, "echo", 1))
        assertFalse(accepts(TYPE_WINDOW_CONTENT_CHANGED, "echo", 1, CONTENT_CHANGE_TYPE_SUBTREE))
        assertFalse(accepts(TYPE_WINDOWS_CHANGED, null, 1, windows = WINDOWS_CHANGE_BOUNDS))
        assertTrue(accepts(TYPE_WINDOW_STATE_CHANGED, "echo", 3))
    }

    @Test fun `cached own window never suppresses external focus or split screen handoff`() {
        assertTrue(accepts(TYPE_VIEW_FOCUSED))
        assertTrue(accepts(TYPE_WINDOW_STATE_CHANGED))
        for (change in listOf(WINDOWS_CHANGE_ADDED, WINDOWS_CHANGE_REMOVED,
                WINDOWS_CHANGE_FOCUSED, WINDOWS_CHANGE_ACTIVE, 0)) {
            assertTrue(accepts(TYPE_WINDOWS_CHANGED, null, 1, windows = change))
        }
    }

    @Test fun `typing does no inspection but structural removal and keyboard motion do`() {
        assertFalse(accepts(TYPE_VIEW_TEXT_CHANGED))
        assertFalse(accepts(TYPE_WINDOW_CONTENT_CHANGED, content = CONTENT_CHANGE_TYPE_TEXT))
        assertFalse(accepts(TYPE_WINDOW_CONTENT_CHANGED, content = CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION))
        assertTrue(accepts(TYPE_WINDOW_CONTENT_CHANGED, content = CONTENT_CHANGE_TYPE_SUBTREE))
        assertTrue(accepts(TYPE_WINDOW_CONTENT_CHANGED, content = CONTENT_CHANGE_TYPE_UNDEFINED))
        assertTrue(accepts(TYPE_WINDOWS_CHANGED, windows = WINDOWS_CHANGE_BOUNDS))
    }

    @Test fun `a burst makes one scan and continuous traffic cannot starve scans`() = runTest {
        var scans = 0
        val queue = DictationWorkQueue(backgroundScope) { scans++ }
        repeat(1000) { queue.request() }
        runCurrent()
        advanceTimeBy(60); runCurrent()
        assertEquals(1, scans)
        repeat(12) { queue.request(); advanceTimeBy(10); runCurrent() }
        assertEquals(3, scans)
        queue.close()
    }

    @Test fun `events during a suspended scan reserve only one followup`() = runTest {
        var scans = 0
        val gate = CompletableDeferred<Unit>()
        val queue = DictationWorkQueue(backgroundScope) { scans++; gate.await() }
        queue.request(); runCurrent(); advanceTimeBy(60); runCurrent()
        repeat(1000) { queue.request() }
        assertEquals(1, scans)
        gate.complete(Unit); runCurrent(); advanceTimeBy(60); runCurrent()
        assertEquals(2, scans)
        queue.close()
    }

    @Test fun `closing cancels delayed work and rejects future requests`() = runTest {
        var scans = 0
        val queue = DictationWorkQueue(backgroundScope) { scans++ }
        queue.request(); runCurrent()
        queue.close(); queue.request(); advanceTimeBy(1000); runCurrent()
        assertEquals(0, scans)
    }

    @Test fun `layout deduplication includes position size and reattachment`() {
        val updates = DictationBubbleLayoutUpdates()
        assertTrue(updates.changed(10, 20, 48, 48))
        assertFalse(updates.changed(10, 20, 48, 48))
        assertTrue(updates.changed(200, 20, 48, 48))
        assertTrue(updates.changed(200, 10, 48, 48))
        assertTrue(updates.changed(200, 10, 96, 96))
        assertFalse(updates.changed(200, 10, 96, 96))
        updates.reset()
        assertTrue(updates.changed(200, 10, 96, 96))
    }
}
