package com.echoflow.ui.chat

import com.echoflow.data.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModeMemoryTest {
    private val memory = ChatModeMemory()

    @Test fun `a mode stays with its chat and does not follow into another`() {
        // Deep Research on in chat A, then open chat B: B starts normal.
        assertEquals(ChatMode.Normal, memory.switch("a", ChatMode.DeepResearch, "b", carry = false))
        // Back to A: its mode returns.
        assertEquals(ChatMode.DeepResearch, memory.switch("b", ChatMode.Normal, "a", carry = false))
    }

    @Test fun `a new chat starts normal, but one created from the composer keeps the picked mode`() {
        assertEquals(ChatMode.Normal, memory.switch("a", ChatMode.Artifact, null, carry = false))
        assertEquals(ChatMode.Artifact, memory.switch(null, ChatMode.Artifact, "new", carry = true))
    }

    @Test fun `reopening the same chat keeps the current mode`() {
        assertEquals(ChatMode.EchoAdviser, memory.switch("a", ChatMode.EchoAdviser, "a", carry = false))
    }

    @Test fun `imagine media modes are never parked, and forget clears a chat`() {
        memory.switch("a", ChatMode.ImageGen, "b", carry = false)
        assertEquals(ChatMode.Normal, memory.switch("b", ChatMode.Normal, "a", carry = false))

        memory.switch("a", ChatMode.WebSearch, "b", carry = false)
        memory.forget("a")
        assertEquals(ChatMode.Normal, memory.switch("b", ChatMode.Normal, "a", carry = false))
    }
}
