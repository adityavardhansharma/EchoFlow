package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyVersionsTest {
    @Test
    fun `count is archived size plus current`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "newest",
            createdAt = 1L,
            replyVersionsJson = ToolEventJson.replyVersionsToJson(
                listOf(
                    ReplyVersion(content = "first"),
                    ReplyVersion(content = "second"),
                ),
            ),
        )
        assertEquals(3, ReplyVersions.count(message))
    }

    @Test
    fun `display returns archived snapshot then latest`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "newest",
            createdAt = 1L,
            segmentsJson = """[{"type":"text","text":"newest"}]""",
            replyVersionsJson = ToolEventJson.replyVersionsToJson(
                listOf(
                    ReplyVersion(
                        content = "older",
                        segmentsJson = """[{"type":"text","text":"older"}]""",
                    ),
                ),
            ),
        )
        assertEquals("older", ReplyVersions.display(message, 0).content)
        assertEquals("newest", ReplyVersions.display(message, 1).content)
    }

    @Test
    fun `copyText prefers segment timeline body`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "",
            createdAt = 1L,
            segmentsJson = """[{"type":"text","text":"Visible answer body"}]""",
        )
        assertEquals("Visible answer body", ReplyVersions.copyText(message, 0))
    }

    @Test
    fun `snapshot captures the live assistant fields`() {
        val message = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "live",
            createdAt = 1L,
            reasoning = "think",
            segmentsJson = """[{"type":"text","text":"live"}]""",
        )
        val snap = ReplyVersions.snapshot(message)
        assertEquals("live", snap.content)
        assertEquals("think", snap.reasoning)
        assertEquals(message.segmentsJson, snap.segmentsJson)
    }

    @Test
    fun `retrying after a failed edit archives the restored answer only once`() {
        val restored = ChatMessage(
            id = "a1",
            chatId = "c1",
            role = "assistant",
            content = "original answer",
            createdAt = 1L,
            replyVersionsJson = ToolEventJson.replyVersionsToJson(
                listOf(ReplyVersion(content = "older answer")),
            ),
        )

        val firstAttempt = ReplyVersions.archiveCurrent(restored)
        val retryAttempt = ReplyVersions.archiveCurrent(restored)

        assertEquals(firstAttempt, retryAttempt)
        assertEquals(2, ToolEventJson.replyVersionsFromJson(retryAttempt).size)
    }
}
