package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyVersioningTest {
    @Test
    fun `totalVersions counts archived plus current`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "latest",
            createdAt = 1L,
            replyVersionsJson = ToolEventJson.replyVersionsToJson(
                listOf(
                    ReplyVersion(content = "first"),
                    ReplyVersion(content = "second"),
                ),
            ),
        )
        assertEquals(3, ReplyVersioning.totalVersions(message))
    }

    @Test
    fun `displayMessage returns archived snapshot for earlier index`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "latest",
            createdAt = 1L,
            segmentsJson = """[{"type":"text","text":"latest"}]""",
            replyVersionsJson = ToolEventJson.replyVersionsToJson(
                listOf(ReplyVersion(content = "older", segmentsJson = """[{"type":"text","text":"older"}]""")),
            ),
        )
        val shown = ReplyVersioning.displayMessage(message, 0)
        assertEquals("older", shown.content)
        assertEquals("latest", ReplyVersioning.displayMessage(message, 1).content)
    }

    @Test
    fun `copyText prefers segment timeline over empty content field`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "",
            createdAt = 1L,
            segmentsJson = """[{"type":"text","text":"Visible answer body"}]""",
        )
        assertEquals("Visible answer body", ReplyVersioning.copyText(message, 0))
    }
}
