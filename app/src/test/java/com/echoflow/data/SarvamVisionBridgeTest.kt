package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SarvamVisionBridgeTest {
    @Test fun `ocr blocks join the latest user turn and keep stored text intact`() {
        val history = listOf(
            ChatMessage(id = "1", chatId = "c", role = "user", content = "What is this?", createdAt = 1),
            ChatMessage(id = "2", chatId = "c", role = "assistant", content = "Tell me more.", createdAt = 2),
            ChatMessage(id = "3", chatId = "c", role = "user", content = "And this?", createdAt = 3),
        )
        val out = SarvamVisionBridge.historyWithOcr(
            history,
            listOf(SarvamVisionBridge.ocrBlock("bill.png", "Total 120")),
        )
        assertEquals(3, out.size)
        assertEquals("What is this?", out[0].content)
        assertTrue(out[2].content.startsWith("And this?"))
        assertTrue(out[2].content.contains("Total 120"))
        // Stored rows are untouched — the augmentation is request-only.
        assertEquals("And this?", history[2].content)
    }

    @Test fun `no blocks or no user turn returns history unchanged`() {
        val history = listOf(
            ChatMessage(id = "1", chatId = "c", role = "assistant", content = "Hi.", createdAt = 1),
        )
        assertEquals(history, SarvamVisionBridge.historyWithOcr(history, emptyList()))
        assertEquals(history, SarvamVisionBridge.historyWithOcr(history, listOf("  ")))
        assertEquals(history, SarvamVisionBridge.historyWithOcr(history, listOf("Total 120")))
    }

    @Test fun `image-only turn becomes the ocr block`() {
        val history = listOf(
            ChatMessage(id = "1", chatId = "c", role = "user", content = "", createdAt = 1),
        )
        val out = SarvamVisionBridge.historyWithOcr(history, listOf("Total 120"))
        assertTrue(out[0].content.contains("Total 120"))
    }

    @Test fun `ocr block labels the image and caps long text`() {
        val block = SarvamVisionBridge.ocrBlock("bill.png", "Total 120")
        assertTrue(block.contains("bill.png"))
        assertTrue(block.contains("Total 120"))
        val long = SarvamVisionBridge.ocrBlock(null, "x".repeat(10_000))
        assertTrue(long.length < 10_000)
        assertTrue(long.contains("truncated"))
    }
}
