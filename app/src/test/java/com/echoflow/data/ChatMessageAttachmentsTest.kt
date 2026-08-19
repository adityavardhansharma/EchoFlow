package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ChatMessage.attachments] coalesces the new JSON list with the legacy single columns. */
class ChatMessageAttachmentsTest {
    private fun message(
        localUri: String? = null,
        localMime: String? = null,
        localName: String? = null,
        attachmentsJson: String? = null,
    ) = ChatMessage(
        id = "m", chatId = "c", role = "user", content = "", createdAt = 0L,
        localAttachmentUri = localUri, localAttachmentMimeType = localMime, localAttachmentName = localName,
        attachmentsJson = attachmentsJson,
    )

    @Test fun `no attachments yields an empty list`() {
        assertTrue(message().attachments.isEmpty())
    }

    @Test fun `a legacy single attachment is wrapped as one entry`() {
        val list = message(localUri = "u", localMime = "application/pdf", localName = "old.pdf").attachments
        assertEquals(1, list.size)
        assertEquals("u", list[0].uri)
        assertEquals("application/pdf", list[0].mimeType)
        assertEquals("old.pdf", list[0].name)
        assertNull(list[0].extractedText)
    }

    @Test fun `the json list wins when present`() {
        val json = ToolEventJson.attachmentsToJson(
            listOf(
                MessageAttachment("u1", "application/pdf", "a.pdf", "alpha"),
                MessageAttachment("u2", "text/csv", "b.csv", "beta"),
            )
        )
        // Even with legacy columns also set, the richer JSON list is what reads back.
        val list = message(localUri = "u1", localMime = "application/pdf", localName = "a.pdf", attachmentsJson = json).attachments
        assertEquals(2, list.size)
        assertEquals("alpha", list[0].extractedText)
        assertEquals("beta", list[1].extractedText)
    }
}
