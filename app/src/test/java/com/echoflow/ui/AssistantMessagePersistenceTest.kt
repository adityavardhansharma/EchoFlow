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

    @Test fun `a finished image is durable even without answer text`() {
        val draft = AssistantMessagePersistence.draft(
            listOf(
                StreamSegment.Image(
                    imageId = "img-1", filePath = "/data/img.png", pattern = "ripple",
                    editing = false, previousImagePath = null, generating = false,
                )
            ),
            null, stopped = false,
        )!!
        val segment = draft.segments.single()
        assertEquals("image", segment.type)
        assertEquals("/data/img.png", segment.image?.filePath)
        assertEquals("img-1", segment.image?.imageId)
    }

    @Test fun `a still-generating image placeholder is not persisted`() {
        val draft = AssistantMessagePersistence.draft(
            listOf(
                StreamSegment.Image(
                    imageId = null, filePath = null, pattern = "rain",
                    editing = false, previousImagePath = null, generating = true,
                )
            ),
            null, stopped = false,
        )
        assertNull(draft)
    }

    @Test fun `a finished video persists both its job id and its file`() {
        val draft = AssistantMessagePersistence.draft(
            listOf(
                StreamSegment.Video(
                    videoId = "vid-1", filePath = "/data/clip.mp4", pattern = "ripple",
                    aspectRatio = "16:9", status = "completed", generating = false,
                )
            ),
            null, stopped = false,
        )!!
        val segment = draft.segments.single()
        assertEquals("video", segment.type)
        assertEquals("vid-1", segment.video?.videoId)
        assertEquals("/data/clip.mp4", segment.video?.filePath)
    }

    @Test fun `a still-rendering video is persisted anyway so the card can be recovered`() {
        // The opposite of the image rule on purpose: a clip takes minutes, the job outlives
        // the turn, and without a row in the conversation there would be nothing pointing at
        // the (already paid for) render once it finishes.
        val draft = AssistantMessagePersistence.draft(
            listOf(
                StreamSegment.Video(
                    videoId = "vid-2", filePath = null, pattern = "rain",
                    aspectRatio = "9:16", status = "in_progress", generating = true,
                )
            ),
            null, stopped = false,
        )!!
        val segment = draft.segments.single()
        assertEquals("video", segment.type)
        assertEquals("vid-2", segment.video?.videoId)
        assertNull(segment.video?.filePath)
    }

    @Test fun `keeps interrupted empty response so edit history is not orphaned`() {
        val draft = AssistantMessagePersistence.draft(emptyList(), "network error", stopped = false)!!
        assertTrue(draft.content.contains("network error"))
        assertEquals("text", draft.segments.last().type)
    }

    @Test fun `interruptedOnlyDraft writes a minimal persisted stub`() {
        val draft = AssistantMessagePersistence.interruptedOnlyDraft("Reply failed.")
        assertEquals("Reply failed.", draft.content)
        assertEquals("Reply failed.", draft.segments.single().text)
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
