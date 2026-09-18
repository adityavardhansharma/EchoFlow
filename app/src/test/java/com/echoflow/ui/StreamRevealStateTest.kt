package com.echoflow.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRevealStateTest {
    @Test fun `waits for an answer that has not been composed yet`() = runTest {
        val state = StreamRevealState()
        state.attachViewport(Any())
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
        StreamRevealState().awaitRevealed(listOf(StreamSegment.Text("answer")))
    }

    @Test fun `a mounted viewport waits for a reader acknowledgement`() = runTest {
        val state = StreamRevealState()
        state.attachViewport(Any())
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
}
