package com.echoflow.ui.chat

import org.junit.Assert.*
import org.junit.Test

class SendClaimTest {
    private val pending = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Test fun `a second send to the same chat is refused until the first releases`() {
        val first = SendClaim("c1")
        assertTrue(first.tryClaim(pending))
        assertFalse(SendClaim("c1").tryClaim(pending))
        first.release(pending)
        assertTrue(SendClaim("c1").tryClaim(pending))
    }

    @Test fun `sends to different chats don't block each other`() {
        assertTrue(SendClaim("c1").tryClaim(pending))
        assertTrue(SendClaim("c2").tryClaim(pending))
    }

    @Test fun `a blank composer send also holds the thread it creates`() {
        val first = SendClaim(BLANK_SEND_KEY)
        assertTrue(first.tryClaim(pending))
        first.extend(pending, "new")
        assertFalse(SendClaim("new").tryClaim(pending))
        assertFalse(SendClaim(BLANK_SEND_KEY).tryClaim(pending))
        first.release(pending)
        assertTrue(pending.isEmpty())
    }

    @Test fun `release is safe to call twice and never frees another send's claim`() {
        val first = SendClaim("c1")
        assertTrue(first.tryClaim(pending))
        first.release(pending)
        val second = SendClaim("c1")
        assertTrue(second.tryClaim(pending))
        first.release(pending)
        assertTrue("c1" in pending)
    }

    @Test fun `a refused claim can't extend or release anything`() {
        assertTrue(SendClaim("c1").tryClaim(pending))
        val refused = SendClaim("c1")
        assertFalse(refused.tryClaim(pending))
        refused.extend(pending, "c2")
        refused.release(pending)
        assertEquals(setOf("c1"), pending.toSet())
    }

    @Test fun `blank Chat and blank Imagine sends don't block each other`() {
        assertTrue(SendClaim(blankSendKey("Chat")).tryClaim(pending))
        assertTrue(SendClaim(blankSendKey("Imagine")).tryClaim(pending))
        assertFalse(SendClaim(blankSendKey("Chat")).tryClaim(pending))
    }
}
