package com.echoflow.ui

import com.echoflow.data.StreamChunk
import org.junit.Assert.*
import org.junit.Test

class MemoryTimelineTest {
    @Test fun `memory status resolves in place and persists without a search card`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.MemoryActivity("a", "Recalling…", true))
        ChatSegmentReducer.reduce(segments, StreamChunk.MemoryActivity("a", "Recalled 2 memories", false))
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("Here's your project."))
        assertEquals(2, segments.size)
        val draft = AssistantMessagePersistence.draft(segments, null, false)!!
        assertTrue(draft.toolEvents.isEmpty())
        assertEquals("memory", draft.segments.first().type)
        assertEquals("Recalled 2 memories", draft.segments.first().text)
    }
    @Test fun `stopped memory request never looks permanently active or successful`() {
        val draft = AssistantMessagePersistence.draft(listOf(StreamSegment.Memory("a", "Remembering…", true)), null, true)!!
        assertEquals("Memory request interrupted", draft.segments.first().text)
    }
}
