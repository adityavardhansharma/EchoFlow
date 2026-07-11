package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSystemPromptsTest {
    @Test fun `finish prompt remains byte stable`() {
        assertEquals(
            "Summarize what was accomplished in this browser session and the final state of the page " +
                "(key items, prices, links or results found). Be concise and useful. Do not take any " +
                "further action — this is the closing summary.",
            SystemPrompts.browserFinishPrompt(),
        )
    }

    @Test fun `confirmed send prompt trims only the supplied draft boundary`() {
        assertEquals(
            "The user has reviewed and approved the following message. Send/submit it exactly as written, " +
                "then confirm it was sent.\n\nApproved message:\nHello",
            SystemPrompts.browserSendConfirmedPrompt("  Hello  "),
        )
    }

    @Test fun `draft interaction keeps compose safety rule and trimmed task`() {
        val prompt = SystemPrompts.browserInteractPrompt("  write hello  ", draftMode = true)
        assertTrue(prompt.contains("COMPOSE it but DO NOT send or submit it"))
        assertTrue(prompt.endsWith("Task: write hello"))
        assertFalse(prompt.endsWith("  "))
    }
}
