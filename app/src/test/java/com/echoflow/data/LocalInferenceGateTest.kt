package com.echoflow.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

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
}
