package com.echoflow.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalInferenceGateTest {
    @Test fun `a second local task fails fast while the first holds the gate`() = runTest {
        val gate = LocalInferenceGate()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch {
            gate.withExclusive("a chat reply") {
                holding.complete(Unit)
                release.await()
            }
        }
        holding.await()
        assertTrue(gate.isBusy)
        assertEquals("a chat reply", gate.currentHolder)
        try {
            gate.withExclusive("image generation") { fail("must not run") }
            fail("expected LocalInferenceBusy")
        } catch (e: LocalInferenceBusy) {
            assertTrue(e.message!!.contains("a chat reply"))
        }
        release.complete(Unit)
        first.join()
        assertFalse(gate.isBusy)
    }

    @Test fun `the gate releases after success, failure and cancellation`() = runTest {
        val gate = LocalInferenceGate()
        assertEquals(1, gate.withExclusive("ok") { 1 })
        assertFalse(gate.isBusy)

        try {
            gate.withExclusive("boom") { throw IllegalStateException("boom") }
            fail("expected failure")
        } catch (_: IllegalStateException) { }
        assertFalse(gate.isBusy)

        val holding = CompletableDeferred<Unit>()
        val job = async {
            gate.withExclusive("cancelled") {
                holding.complete(Unit)
                CompletableDeferred<Unit>().await() // suspend forever
            }
        }
        holding.await()
        assertTrue(gate.isBusy)
        job.cancelAndJoin()
        assertFalse(gate.isBusy)

        // Fully usable again after every release path.
        assertEquals(2, gate.withExclusive("again") { 2 })
    }
    @Test fun `scheduled inference waits for chat and runs only once`() = runTest {
        val gate = LocalInferenceGate()
        val holding = CompletableDeferred<Unit>()
        val chat = launch {
            gate.withExclusive("chat") {
                holding.complete(Unit)
                delay(1_000)
            }
        }
        holding.await()
        var requests = 0
        gate.withExclusive("schedule", waitTimeoutMillis = 5_000) {
            requests++
            // The acquisition timeout must not cancel a longer generation.
            delay(10_000)
        }
        chat.join()
        assertEquals(1, requests)
        assertFalse(gate.isBusy)
    }

    @Test fun `scheduled wait is bounded without releasing the other holder`() = runTest {
        val gate = LocalInferenceGate()
        val holding = CompletableDeferred<Unit>()
        val chat = launch {
            gate.withExclusive("chat") {
                holding.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        holding.await()
        val start = testScheduler.currentTime
        try {
            gate.withExclusive("schedule", waitTimeoutMillis = 1_000) { fail("must not infer") }
            fail("expected busy timeout")
        } catch (_: LocalInferenceBusy) { }
        assertEquals(1_000, testScheduler.currentTime - start)
        assertEquals("chat", gate.currentHolder)
        chat.cancelAndJoin()
        assertFalse(gate.isBusy)
    }

    @Test fun `cancelling a waiting schedule never starts inference`() = runTest {
        val gate = LocalInferenceGate()
        val holding = CompletableDeferred<Unit>()
        val chat = launch {
            gate.withExclusive("chat") {
                holding.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        holding.await()
        val waiting = launch {
            gate.withExclusive("schedule", waitTimeoutMillis = 5_000) { fail("cancelled waiter ran") }
        }
        delay(500)
        waiting.cancelAndJoin()
        assertEquals("chat", gate.currentHolder)
        chat.cancelAndJoin()
        assertEquals(42, gate.withExclusive("next") { 42 })
    }

}
