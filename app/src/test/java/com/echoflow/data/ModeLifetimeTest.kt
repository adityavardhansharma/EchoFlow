package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeLifetimeTest {
    @Test fun `media modes last one send`() {
        assertEquals(ModeLifetime.OneSend, ChatMode.ImageGen.lifetime)
        assertEquals(ModeLifetime.OneSend, ChatMode.VideoGen.lifetime)
    }

    @Test fun `paid runs end with their run`() {
        assertEquals(ModeLifetime.UntilRunEnds, ChatMode.DeepResearch.lifetime)
        assertEquals(ModeLifetime.UntilRunEnds, ChatMode.DataAgent.lifetime)
    }

    @Test fun `everything else is sticky`() {
        listOf(ChatMode.Normal, ChatMode.WebSearch, ChatMode.EchoAdviser, ChatMode.EchoFusion,
            ChatMode.EchoAgent, ChatMode.BrowserFlow, ChatMode.Artifact)
            .forEach { assertEquals(it.toString(), ModeLifetime.Sticky, it.lifetime) }
    }
}
