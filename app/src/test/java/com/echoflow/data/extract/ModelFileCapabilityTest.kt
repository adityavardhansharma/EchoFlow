package com.echoflow.data.extract

import com.echoflow.data.ChatMessage
import com.echoflow.data.DefaultChatModels
import com.echoflow.data.LocalLlmPrompting
import com.echoflow.data.MessageAttachment
import com.echoflow.data.ToolEventJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileCapabilityTest {
    @Test fun `openrouter models can take provider file uploads`() {
        assertTrue(ModelFileCapability.readsFiles(DefaultChatModels.DEFAULT_MODEL_ID))
        assertTrue(ModelFileCapability.readsFiles("google/gemini-2.0-flash"))
        assertTrue(ModelFileCapability.readsFiles("anthropic/claude-sonnet-4.6"))
    }

    @Test fun `echo lumen uses anydoc instead of provider file uploads`() {
        assertFalse(ModelFileCapability.readsFiles(DefaultChatModels.ECHO_LUMEN_MODEL_ID))
        assertTrue(ModelFileCapability.extractsDocsLocally(DefaultChatModels.ECHO_LUMEN_MODEL_ID))
    }

    @Test fun `local models use anydoc and never provider uploads`() {
        assertFalse(ModelFileCapability.readsFiles("local/gemma-3-1b"))
        assertTrue(ModelFileCapability.extractsDocsLocally("local/gemma-3-1b"))
    }

    @Test fun `custom models cannot take files`() {
        assertFalse(ModelFileCapability.readsFiles("custom/openai/gpt-4o"))
        assertFalse(ModelFileCapability.readsFiles("custom/ollama/llama3"))
        assertFalse(ModelFileCapability.extractsDocsLocally("custom/openai/gpt-4o"))
        assertFalse(ModelFileCapability.readsFiles(""))
    }
}

class LocalLlmPromptingAttachmentHistoryTest {
    @Test fun `historyWithInjectedDocs folds markdown and strips upload URIs`() {
        val attachments = listOf(
            MessageAttachment(
                uri = "content://doc",
                mimeType = "application/pdf",
                name = "notes.pdf",
                extractedText = "Quarterly revenue rose.",
            ),
        )
        val history = listOf(
            ChatMessage(
                id = "u1",
                chatId = "c1",
                role = "user",
                content = "Summarize this",
                createdAt = 1L,
                attachmentsJson = ToolEventJson.attachmentsToJson(attachments),
            ),
        )

        val prepared = LocalLlmPrompting.historyWithInjectedDocs(history).single()

        assertTrue(prepared.content.contains("Quarterly revenue rose."))
        assertTrue(prepared.content.contains("notes.pdf"))
        assertTrue(prepared.localAttachmentUri == null)
        assertTrue(prepared.attachmentsJson == null)
    }
}
