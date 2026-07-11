package com.echoflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChatResponsePolicyTest {
    @Test fun `keeps segments unchanged when visible text exists`() {
        val segments = listOf(StreamSegment.Reasoning("thought"), StreamSegment.Text("answer"))
        assertSame(segments, ChatResponsePolicy.normalizeForPersistence(segments))
    }

    @Test fun `promotes reasoning-only provider output into visible text`() {
        val normalized = ChatResponsePolicy.normalizeForPersistence(listOf(StreamSegment.Reasoning("analysis\nFinal answer: forty two")))
        assertEquals(listOf(StreamSegment.Reasoning("analysis"), StreamSegment.Text("forty two")), normalized)
    }

    @Test fun `splits think tags and preserves the trace`() {
        assertEquals("<think>work</think>" to "result", ChatResponsePolicy.splitReasoningOnlyAnswer("<think>work</think> result"))
    }

    @Test fun `parses complete incomplete and plain search commands`() {
        assertEquals("weather london", ChatResponsePolicy.extractSearchQuery("<search>weather london</search>"))
        assertEquals("weather london", ChatResponsePolicy.extractSearchQuery("<search> weather london"))
        assertEquals("weather london", ChatResponsePolicy.extractSearchQuery(" SEARCH: weather london "))
        assertNull(ChatResponsePolicy.extractSearchQuery("ordinary answer"))
    }
}
