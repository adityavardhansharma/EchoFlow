package com.echoflow.ui

import com.echoflow.data.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditTurnSupportTest {
    private val baseUser = ChatMessage(
        id = "u1",
        chatId = "c1",
        role = "user",
        content = "old prompt",
        createdAt = 100L,
        localAttachmentUri = "content://old",
        localAttachmentMimeType = "image/png",
        localAttachmentName = "old.png",
    )

    @Test
    fun `edited user message uses composer attachment state`() {
        val edited = editedUserMessageForEditTurn(
            lastUser = baseUser,
            newContent = "new prompt",
            attachmentUri = "content://new",
            attachmentMime = "image/jpeg",
            attachmentName = "new.jpg",
        )
        assertEquals("new prompt", edited.content)
        assertEquals("content://new", edited.localAttachmentUri)
        assertEquals("image/jpeg", edited.localAttachmentMimeType)
        assertEquals("new.jpg", edited.localAttachmentName)
    }

    @Test
    fun `edited user message clears attachment when composer has none`() {
        val edited = editedUserMessageForEditTurn(
            lastUser = baseUser,
            newContent = "new prompt",
            attachmentUri = null,
            attachmentMime = null,
            attachmentName = null,
        )
        assertNull(edited.localAttachmentUri)
        assertNull(edited.localAttachmentMimeType)
        assertNull(edited.localAttachmentName)
    }
}
