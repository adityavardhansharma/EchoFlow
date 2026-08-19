package com.echoflow.data

import com.echoflow.data.LocalLlmPrompting.MAX_ATTACHMENT_CONTEXT_CHARS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LocalLlmPrompting.contentWithAttachments] — how parsed docs reach an on-device model. */
class LocalLlmPromptingAttachmentsTest {
    private fun userMessage(content: String, attachmentsJson: String?): ChatMessage = ChatMessage(
        id = "m1",
        chatId = "c1",
        role = "user",
        content = content,
        createdAt = 0L,
        attachmentsJson = attachmentsJson,
    )

    @Test fun `a message with no attachments is returned unchanged`() {
        val message = userMessage("what is 2+2", null)
        assertEquals("what is 2+2", LocalLlmPrompting.contentWithAttachments(message))
    }

    @Test fun `a parsed doc is appended below the typed text`() {
        val json = ToolEventJson.attachmentsToJson(
            listOf(MessageAttachment(uri = "u1", mimeType = "application/pdf", name = "report.pdf", extractedText = "# Q3\n\nRevenue up"))
        )
        val message = userMessage("summarize this", json)
        val out = LocalLlmPrompting.contentWithAttachments(message)
        assertTrue(out.startsWith("summarize this"))
        assertTrue(out.contains("Attached file: report.pdf"))
        assertTrue(out.contains("Revenue up"))
    }

    @Test fun `several docs are all injected`() {
        val json = ToolEventJson.attachmentsToJson(
            listOf(
                MessageAttachment(uri = "u1", mimeType = "application/pdf", name = "a.pdf", extractedText = "alpha body"),
                MessageAttachment(uri = "u2", mimeType = "text/csv", name = "b.csv", extractedText = "beta body"),
            )
        )
        val out = LocalLlmPrompting.contentWithAttachments(userMessage("compare", json))
        assertTrue(out.contains("a.pdf"))
        assertTrue(out.contains("alpha body"))
        assertTrue(out.contains("b.csv"))
        assertTrue(out.contains("beta body"))
    }

    @Test fun `images and text-less attachments contribute nothing`() {
        val json = ToolEventJson.attachmentsToJson(
            listOf(MessageAttachment(uri = "u1", mimeType = "image/png", name = "photo.png", extractedText = null))
        )
        val out = LocalLlmPrompting.contentWithAttachments(userMessage("look", json))
        assertEquals("look", out)
        assertFalse(out.contains("Attached file"))
    }

    @Test fun `an empty prompt still delivers the doc text`() {
        val json = ToolEventJson.attachmentsToJson(
            listOf(MessageAttachment(uri = "u1", mimeType = "application/pdf", name = "only.pdf", extractedText = "the whole thing"))
        )
        val out = LocalLlmPrompting.contentWithAttachments(userMessage("", json))
        assertTrue(out.contains("only.pdf"))
        assertTrue(out.contains("the whole thing"))
    }

    @Test fun `a long doc is truncated to the context budget`() {
        val body = "x".repeat(MAX_ATTACHMENT_CONTEXT_CHARS + 80)
        val json = ToolEventJson.attachmentsToJson(
            listOf(MessageAttachment(uri = "u1", mimeType = "application/pdf", name = "big.pdf", extractedText = body))
        )
        val out = LocalLlmPrompting.contentWithAttachments(userMessage("summarize", json))
        assertTrue(out.startsWith("summarize"))
        assertTrue(out.contains("[…document truncated…]"))
        assertFalse(out.contains("x".repeat(MAX_ATTACHMENT_CONTEXT_CHARS + 1)))
    }

    @Test fun `a second doc is omitted once the budget is spent`() {
        val huge = "a".repeat(MAX_ATTACHMENT_CONTEXT_CHARS)
        val json = ToolEventJson.attachmentsToJson(
            listOf(
                MessageAttachment(uri = "u1", mimeType = "application/pdf", name = "a.pdf", extractedText = huge),
                MessageAttachment(uri = "u2", mimeType = "text/csv", name = "b.csv", extractedText = "beta body"),
            )
        )
        val out = LocalLlmPrompting.contentWithAttachments(userMessage("compare", json))
        assertTrue(out.contains("a.pdf"))
        assertTrue(out.contains("omitted"))
        assertFalse(out.contains("beta body"))
    }
}
