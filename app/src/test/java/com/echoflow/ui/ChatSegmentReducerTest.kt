package com.echoflow.ui

import com.echoflow.data.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSegmentReducerTest {
    @Test fun `coalesces adjacent content while preserving reasoning boundary`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("one"))
        ChatSegmentReducer.reduce(segments, StreamChunk.Content(" two"))
        ChatSegmentReducer.reduce(segments, StreamChunk.Reasoning("why"))
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("answer"))
        assertEquals(listOf(StreamSegment.Text("one two"), StreamSegment.Reasoning("why"), StreamSegment.Text("answer")), segments)
    }

    @Test fun `resolves the active search and returns status notes separately`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.SearchStarted("query"))
        ChatSegmentReducer.reduce(segments, StreamChunk.SearchSources("query", emptyList()))
        val search = segments.single() as StreamSegment.Search
        assertFalse(search.active)
        assertEquals("working", ChatSegmentReducer.reduce(segments, StreamChunk.StatusNote("working")))
    }

    @Test fun `real output removes transient agent banner`() {
        val segments = mutableListOf<StreamSegment>()
        assertNull(ChatSegmentReducer.reduce(segments, StreamChunk.AgentRunStarted))
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("done"))
        assertEquals(listOf(StreamSegment.Text("done")), segments)
    }
}
