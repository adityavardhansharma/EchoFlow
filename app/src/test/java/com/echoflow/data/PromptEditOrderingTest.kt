package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptEditOrderingTest {
    @Test
    fun `failed regeneration restores assistant after edited prompt`() {
        val originalUser = ChatMessage(
            id = "user",
            chatId = "chat",
            role = "user",
            content = "original prompt",
            createdAt = 100L,
        )
        val originalAssistant = ChatMessage(
            id = "assistant",
            chatId = "chat",
            role = "assistant",
            content = "original answer",
            createdAt = 200L,
        )

        val editedUser = originalUser.withEditedPrompt(
            content = "edited prompt",
            attachmentUri = null,
            attachmentMimeType = null,
            attachmentName = null,
        )
        val restoredTranscript = listOf(editedUser, originalAssistant).sortedBy { it.createdAt }

        assertEquals(originalUser.createdAt, editedUser.createdAt)
        assertEquals(listOf("user", "assistant"), restoredTranscript.map { it.role })
    }
}
