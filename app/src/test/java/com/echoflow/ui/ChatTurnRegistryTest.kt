package com.echoflow.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnRegistryTest {
    @Test
    fun secondClaimOnSameChatIsRefusedUntilReleased() {
        val turns = ChatTurnRegistry()
        val claim = turns.claim("x")!!
        assertNull(turns.claim("x"))
        turns.release(claim)
        assertNotNull(turns.claim("x"))
    }

    @Test
    fun registerMakesChatRunningAndReleasesClaim() {
        val turns = ChatTurnRegistry()
        val claim = turns.claim("chat")!!
        val job = Job()
        turns.register("chat", job, claim)
        assertTrue(turns.isRunning("chat"))
        // The claim is gone, so the running job is now the only guard.
        assertNotNull(turns.claim("chat"))
    }

    @Test
    fun clearIfOwnedIgnoresAStaleJob() {
        val turns = ChatTurnRegistry()
        val old = Job()
        val current = Job()
        turns.register("chat", old, turns.claim("chat")!!)
        turns.register("chat", current, turns.claim("chat")!!)
        turns.setState("chat", ActiveStreamState(messageId = "m"))

        assertFalse(turns.clearIfOwned("chat", old))
        assertEquals("m", turns.streams.value["chat"]?.messageId)

        assertTrue(turns.clearIfOwned("chat", current))
        assertNull(turns.streams.value["chat"])
        assertFalse(turns.isRunning("chat"))
    }

    @Test
    fun anyLocalLooksAcrossChats() {
        val turns = ChatTurnRegistry()
        turns.setState("a", ActiveStreamState(messageId = "1"))
        assertFalse(turns.anyLocal())
        turns.setState("b", ActiveStreamState(messageId = "2", isLocal = true))
        assertTrue(turns.anyLocal())
    }

    @Test
    fun cancelAndJoinStopsAndForgetsTheTurn() = runBlocking {
        val turns = ChatTurnRegistry()
        val job = Job()
        turns.register("chat", job, turns.claim("chat")!!)
        turns.cancelAndJoin("chat")
        assertTrue(job.isCancelled)
        assertFalse(turns.isRunning("chat"))
    }

    @Test
    fun extendedClaimHoldsBothKeys() {
        val turns = ChatTurnRegistry()
        val claim = turns.claim("blank")!!
        turns.extend(claim, "new-chat")
        assertNull(turns.claim("new-chat"))
        turns.release(claim)
        assertNotNull(turns.claim("new-chat"))
        assertNotNull(turns.claim("blank"))
    }
}
