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

    @Test fun `image generation seeds a pending placeholder and resolves on the saved file`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.ImageGenStarted("rain", editing = true, previousImagePath = "/old.png"))
        val pending = segments.single() as StreamSegment.Image
        assertEquals("rain", pending.pattern)
        assertEquals("/old.png", pending.previousImagePath)
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("Here you go"))
        ChatSegmentReducer.reduce(segments, StreamChunk.ImageGenerated(dataUrl = "", filePath = "/new.png", imageId = "img-1"))
        val resolved = segments.filterIsInstance<StreamSegment.Image>().single()
        assertFalse(resolved.generating)
        assertEquals("/new.png", resolved.filePath)
        assertEquals("img-1", resolved.imageId)
    }

    @Test fun `an unsaved base64 image chunk never reaches the timeline`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.ImageGenStarted("ripple", editing = false))
        ChatSegmentReducer.reduce(segments, StreamChunk.ImageGenerated(dataUrl = "data:image/png;base64,AAAA"))
        val image = segments.single() as StreamSegment.Image
        assertNull(image.filePath)
        assertEquals(true, image.generating)
    }

    @Test fun `video generation tracks its job through progress to the finished clip`() {
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.VideoGenStarted("vid-1", "rain", "9:16"))
        val pending = segments.single() as StreamSegment.Video
        assertEquals("rain", pending.pattern)
        assertEquals("9:16", pending.aspectRatio)
        assertEquals(true, pending.generating)

        ChatSegmentReducer.reduce(segments, StreamChunk.VideoGenProgress("vid-1", "in_progress"))
        assertEquals("in_progress", (segments.single() as StreamSegment.Video).status)

        ChatSegmentReducer.reduce(segments, StreamChunk.VideoGenerated("vid-1", "/clip.mp4"))
        val resolved = segments.single() as StreamSegment.Video
        assertFalse(resolved.generating)
        assertEquals("/clip.mp4", resolved.filePath)
        assertEquals("completed", resolved.status)
    }

    @Test fun `a video event for another job never touches this one's card`() {
        // Two clips can be in flight across chats; matching on id rather than "the last
        // pending card" is what keeps one job's completion from resolving the other's.
        val segments = mutableListOf<StreamSegment>()
        ChatSegmentReducer.reduce(segments, StreamChunk.VideoGenStarted("vid-1", "ripple", "16:9"))
        ChatSegmentReducer.reduce(segments, StreamChunk.VideoGenerated("vid-other", "/wrong.mp4"))
        val untouched = segments.single() as StreamSegment.Video
        assertNull(untouched.filePath)
        assertEquals(true, untouched.generating)
    }

    @Test fun `real output removes transient agent banner`() {
        val segments = mutableListOf<StreamSegment>()
        assertNull(ChatSegmentReducer.reduce(segments, StreamChunk.AgentRunStarted))
        ChatSegmentReducer.reduce(segments, StreamChunk.Content("done"))
        assertEquals(listOf(StreamSegment.Text("done")), segments)
    }
}
