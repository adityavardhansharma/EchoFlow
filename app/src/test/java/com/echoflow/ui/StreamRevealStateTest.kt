package com.echoflow.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRevealStateTest {
    @Test fun `waits for an answer that has not been composed yet`() = runTest {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.attachViewport(viewport)
        val reader = Any()
        val completion = launch(start = CoroutineStart.UNDISPATCHED) {
            state.awaitRevealed(listOf(StreamSegment.Text("hello")))
        }
        assertFalse(completion.isCompleted)
        state.report(reader, 0, 2)
        yield()
        assertFalse(completion.isCompleted)
        state.report(reader, 0, 5)
        completion.join()
        assertTrue(completion.isCompleted)
    }

    @Test fun `leaving the viewport releases an unfinished reveal`() = runTest {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.attachViewport(viewport)
        val completion = launch(start = CoroutineStart.UNDISPATCHED) {
            state.awaitRevealed(listOf(StreamSegment.Text("answer")))
        }
        assertFalse(completion.isCompleted)
        state.detachViewport(viewport)
        completion.join()
        assertTrue(completion.isCompleted)
    }

    @Test fun `background replies do not wait for readers`() = runTest {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.abandonViewport(viewport)
        state.awaitRevealed(listOf(StreamSegment.Text("answer")))
    }

    @Test fun `a mounted viewport waits for a reader acknowledgement`() = runTest {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.attachViewport(viewport)
        val completion = launch(start = CoroutineStart.UNDISPATCHED) {
            state.awaitRevealed(listOf(StreamSegment.Reasoning("thought")))
        }
        assertFalse(completion.isCompleted)
        state.reportSkipped(Any(), 0)
        completion.join()
        assertTrue(completion.isCompleted)
    }

    @Test fun `a pending viewport blocks the handoff until it is abandoned`() = runTest {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        val completion = launch(start = CoroutineStart.UNDISPATCHED) {
            state.awaitRevealed(listOf(StreamSegment.Text("answer")))
        }
        assertFalse(completion.isCompleted)
        state.abandonViewport(viewport)
        completion.join()
        assertTrue(completion.isCompleted)
    }

    @Test fun `a reply seen for the first time reveals from the start`() {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.attachViewport(viewport)
        assertEquals(0, state.resumePoint(0, 40))
    }

    @Test fun `coming back to a chat does not replay its answer`() {
        val state = StreamRevealState()
        val first = Any()
        state.beginViewport(first)
        state.attachViewport(first)
        state.report(Any(), 0, 12)
        // The user switches to another chat, then back.
        state.abandonViewport(first)
        val second = Any()
        state.beginViewport(second)
        assertEquals(300, state.resumePoint(0, 300))
        // Text that only starts after the return still reveals at the same place it is.
        assertEquals(5, state.resumePoint(1, 5))
    }

    @Test fun `a row scrolled back into view keeps what it already showed`() {
        val state = StreamRevealState()
        val viewport = Any()
        state.beginViewport(viewport)
        state.attachViewport(viewport)
        val reader = Any()
        state.report(reader, 0, 20)
        state.detach(reader)
        assertEquals(80, state.resumePoint(0, 80))
        assertEquals(0, state.resumePoint(1, 10))
    }

    @Test fun `an activity timer keeps its start across remounts`() {
        val state = StreamRevealState()
        assertEquals(1_000L, state.activityStartedAt(0, 1_000L))
        assertEquals(1_000L, state.activityStartedAt(0, 9_000L))
        assertEquals(9_000L, state.activityStartedAt(3, 9_000L))
    }
}
