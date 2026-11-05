package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotedPassagesTest {
    @Test fun `each quote reaches the model as a blockquote ahead of the typed text`() {
        val quotes = listOf(
            QuotedPassage("Room uses TRUNCATE by default.", "a1"),
            QuotedPassage("WAL lets readers run\n\nwhile writing.", "a1"),
        )
        assertEquals(
            "> Room uses TRUNCATE by default.\n\n> WAL lets readers run\n>\n> while writing.\n\nWhy is that faster?",
            QuotedPassages.forModel(quotes, "Why is that faster?"),
        )
    }

    @Test fun `a message without quotes is untouched`() {
        assertEquals("Hello", QuotedPassages.forModel(emptyList(), "Hello"))
        val row = ChatMessage(id = "u1", chatId = "c", role = "user", content = "Hello", createdAt = 0)
        assertSame(row, row.withQuotesForModel())
    }

    @Test fun `stored content stays what was typed while the request copy carries the quote`() {
        val row = ChatMessage(
            id = "u1", chatId = "c", role = "user", content = "Explain", createdAt = 0,
            quotesJson = QuotedPassages.toJson(listOf(QuotedPassage("the rollback file", "a1"))),
        )
        assertEquals("Explain", row.content)
        assertEquals("> the rollback file\n\nExplain", row.withQuotesForModel().content)
        assertEquals("u1", row.withQuotesForModel().id)
    }

    @Test fun `quotes round-trip through JSON and bad JSON reads as none`() {
        val quotes = listOf(QuotedPassage("a", "m1"), QuotedPassage("b", "m2"))
        assertEquals(quotes, QuotedPassages.fromJson(QuotedPassages.toJson(quotes)))
        assertNull(QuotedPassages.toJson(emptyList()))
        assertTrue(QuotedPassages.fromJson("not json").isEmpty())
    }

    @Test fun `selected text is tidied and long selections are capped`() {
        assertEquals("one\n\ntwo", QuotedPassages.clean("  one  \r\n\r\n\r\n\r\ntwo  "))
        assertNull(QuotedPassages.clean(" \n "))
        val long = QuotedPassages.clean("x".repeat(5_000))!!
        assertTrue(long.length <= 1_501 && long.endsWith("…"))
    }

    @Test fun `the cap never splits an emoji`() {
        val text = "x".repeat(1_499) + "😀" + "tail"
        val cut = QuotedPassages.clean(text)!!
        assertTrue(cut.dropLast(1).none { Character.isSurrogate(it) })
    }

    @Test fun `a quoted follow-up searches for its subject`() {
        val quotes = listOf(QuotedPassage("Room uses TRUNCATE journal mode", "a1"))
        assertEquals("Room uses TRUNCATE journal mode — Is this still current?", QuotedPassages.searchQuery(quotes, "Is this still current?"))
        assertEquals("Plain", QuotedPassages.searchQuery(emptyList(), "Plain"))
    }
}
