package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterPayloadsTest {
    @Test fun `adds system message and leaves text messages scalar`() {
        val history = listOf(message(role = "user", content = "hello"))
        val result = OpenRouterPayloads.messages(history, "be useful") { null }
        assertEquals(mapOf("role" to "system", "content" to "be useful"), result[0])
        assertEquals(mapOf("role" to "user", "content" to "hello"), result[1])
    }

    @Test fun `builds PDF file part and enables parser plugin`() {
        val history = listOf(message(role = "user", content = "read", uri = "content://pdf", mime = "APPLICATION/PDF", name = "x.pdf"))
        assertTrue(OpenRouterPayloads.historyHasPdf(history))
        val messages = OpenRouterPayloads.messages(history) { "YWJj" }
        val content = messages.single()["content"] as List<*>
        assertEquals("file", (content[1] as Map<*, *>)["type"])
        val request = mutableMapOf<String, Any>()
        OpenRouterPayloads.enablePdfPlugin(request, true)
        assertTrue(request.containsKey("plugins"))
    }

    @Test fun `assistant attachment is not uploaded`() {
        val history = listOf(message(role = "assistant", content = "answer", uri = "content://image"))
        assertEquals("answer", OpenRouterPayloads.messages(history) { "bytes" }.single()["content"])
        assertFalse(OpenRouterPayloads.historyHasPdf(history))
    }

    @Test fun `attaches edit image to the newest user message preserving existing parts`() {
        val history = listOf(
            message(role = "user", content = "draw a cat"),
            message(role = "assistant", content = "here it is"),
            message(role = "user", content = "make it night", uri = "content://photo"),
        )
        val messages = OpenRouterPayloads.messages(history) { if (it == null) null else "cGhvdG8=" }
        OpenRouterPayloads.attachImageToLastUserMessage(messages, "data:image/png;base64,QUJD")

        val parts = messages.last()["content"] as List<*>
        assertEquals(3, parts.size) // text + user photo + previous generated version
        val attached = parts.last() as Map<*, *>
        assertEquals("image_url", attached["type"])
        assertEquals("data:image/png;base64,QUJD", (attached["image_url"] as Map<*, *>)["url"])
        // The assistant message stays scalar text.
        assertEquals("here it is", messages[1]["content"])
    }

    @Test fun `attach image rewrites scalar user content into parts`() {
        val messages = OpenRouterPayloads.messages(listOf(message(role = "user", content = "redo it"))) { null }
        OpenRouterPayloads.attachImageToLastUserMessage(messages, "data:image/png;base64,QUJD")
        val parts = messages.single()["content"] as List<*>
        assertEquals("text", (parts[0] as Map<*, *>)["type"])
        assertEquals("image_url", (parts[1] as Map<*, *>)["type"])
    }

    @Test fun `attaches extra project files to the last user message`() {
        val history = listOf(message(role = "user", content = "use the brief"))
        history.single().extraAttachments = listOf(
            LocalFileAttachment("file:///tmp/scan.pdf", "application/pdf", "scan.pdf"),
            LocalFileAttachment("file:///tmp/photo.png", "image/png", "photo.png"),
        )
        val messages = OpenRouterPayloads.messages(history) { uri ->
            when {
                uri == null -> null
                uri.endsWith(".pdf") -> "cGRm"
                else -> "cG5n"
            }
        }
        val parts = messages.single()["content"] as List<*>
        assertEquals(3, parts.size)
        assertEquals("file", (parts[1] as Map<*, *>)["type"])
        assertEquals("image_url", (parts[2] as Map<*, *>)["type"])
        assertTrue(OpenRouterPayloads.historyHasPdf(history))
    }

    @Test fun `extracts only structured OpenRouter error message`() {
        assertEquals("quota", OpenRouterPayloads.errorMessage("""{"error":{"message":"quota"}}"""))
        assertNull(OpenRouterPayloads.errorMessage("broken"))
    }

    private fun message(
        role: String,
        content: String,
        uri: String? = null,
        mime: String? = null,
        name: String? = null,
    ) = ChatMessage(
        id = "message-$role",
        chatId = "chat",
        role = role,
        content = content,
        createdAt = 1L,
        localAttachmentUri = uri,
        localAttachmentMimeType = mime,
        localAttachmentName = name,
    )
}
