package com.echoflow.ui.chat

import com.echoflow.ui.chat.TemporaryChat.Armed
import com.echoflow.ui.chat.TemporaryChat.Live
import com.echoflow.ui.chat.TemporaryChat.Off
import com.echoflow.ui.chat.TemporaryChatEffect.Discard
import com.echoflow.ui.chat.TemporaryChatEffect.ExcludeFromMemory
import com.echoflow.ui.chat.TemporaryChatEffect.Remember
import org.junit.Assert.*
import org.junit.Test

class TemporaryChatMachineTest {
    @Test fun `arming from off saves nothing`() {
        val step = TemporaryChatMachine.arm(Off)
        assertEquals(Armed, step.state)
        assertTrue(step.effects.isEmpty())
    }

    @Test fun `first send turns an armed chat live, remembered and out of memory`() {
        val step = TemporaryChatMachine.threadCreated(Armed, "c1")!!
        assertEquals(Live("c1"), step.state)
        assertEquals(listOf(Remember("c1"), ExcludeFromMemory("c1")), step.effects)
    }

    @Test fun `a thread created while not armed is an ordinary chat`() {
        assertNull(TemporaryChatMachine.threadCreated(Off, "c1"))
        assertNull(TemporaryChatMachine.threadCreated(Live("c0"), "c1"))
    }

    @Test fun `leaving a live chat for another discards it`() {
        val step = TemporaryChatMachine.leave(Live("c1"), to = "c2")
        assertEquals(Off, step.state)
        assertEquals(listOf(Discard("c1")), step.effects)
    }

    @Test fun `leaving a live chat for the blank composer discards it`() {
        assertEquals(listOf(Discard("c1")), TemporaryChatMachine.leave(Live("c1"), to = null).effects)
    }

    @Test fun `reopening the live chat itself keeps it`() {
        val step = TemporaryChatMachine.leave(Live("c1"), to = "c1")
        assertEquals(Live("c1"), step.state)
        assertTrue(step.effects.isEmpty())
    }

    @Test fun `leaving an armed blank chat just disarms it`() {
        val step = TemporaryChatMachine.leave(Armed, to = "c2")
        assertEquals(Off, step.state)
        assertTrue(step.effects.isEmpty())
    }

    @Test fun `the open thread is temporary only while it is the live one`() {
        assertTrue(Armed.covers(null))
        assertTrue(Live("c1").covers("c1"))
        assertFalse(Live("c1").covers("c2"))
        assertFalse(Off.covers(null))
    }

    @Test fun `opening a new thread from armed ends live, like the view model does`() {
        // openNewThread: capture, navigate (leave), then apply threadCreated.
        val before = Armed
        val afterLeave = TemporaryChatMachine.leave(before, to = "c1")
        assertEquals(Off, afterLeave.state)
        assertTrue(afterLeave.effects.isEmpty())
        assertEquals(Live("c1"), TemporaryChatMachine.threadCreated(before, "c1")!!.state)
    }
}
