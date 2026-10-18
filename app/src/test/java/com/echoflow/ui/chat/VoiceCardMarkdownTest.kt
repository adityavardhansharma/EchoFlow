package com.echoflow.ui.chat

import com.echoflow.data.voice.VoicePlan
import com.echoflow.data.voice.VoicePlanRow
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCardMarkdownTest {

    @Test
    fun `a call's card is kept in the chat as a title and a line per item`() {
        val card = VoicePlan(
            "Lisbon · 3 days",
            listOf(VoicePlanRow("Day 1", "Alfama", edited = true), VoicePlanRow("", "Pack light")),
        )
        assertEquals("**Lisbon · 3 days**\n\n- **Day 1:** Alfama\n- Pack light", cardMarkdown(card))
    }
}
