package com.echoflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessagePersistenceTest {
    @Test fun `drops an empty unfinished response`() {
        assertNull(AssistantMessagePersistence.draft(emptyList(), null, stopped = false))
    }

    @Test fun `keeps stopped empty response with terminal marker`() {
        val draft = AssistantMessagePersistence.draft(emptyList(), null, stopped = true)!!
        assertEquals("stopped", draft.segments.single().type)
    }

    @Test fun `normalizes reasoning only answer and appends interruption`() {
        val draft = AssistantMessagePersistence.draft(
            listOf(StreamSegment.Reasoning("work\nAnswer: result")), "timeout", stopped = false,
        )!!
        assertTrue(draft.content.startsWith("result"))
        assertTrue(draft.content.contains("Connection lost: timeout"))
        assertEquals("work", draft.reasoning)
        assertEquals(listOf("reasoning", "text", "text"), draft.segments.map { it.type })
    }
}
