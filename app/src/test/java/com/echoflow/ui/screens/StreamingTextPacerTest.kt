package com.echoflow.ui.screens

import com.echoflow.ui.screens.chat.StreamingTextPacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTextPacerTest {
    @Test fun `does not force a character when a frame has no reveal budget`() {
        val pacer = StreamingTextPacer()
        val text = "a".repeat(100)
        pacer.advance(text, 0L)
        var shown = 0
        for (frame in 1..10) shown = pacer.advance(text, frame * 10_000_000L)
        assertTrue(shown > 0)
        assertEquals(shown, pacer.advance(text, 100_000_001L))
    }

    @Test fun `reveal is monotonic and never splits a surrogate pair`() {
        val pacer = StreamingTextPacer()
        val text = "a\uD83D\uDE00b".repeat(40)
        var previous = 0
        for (frame in 0..300) {
            val shown = pacer.advance(text, frame * 16_666_667L)
            assertTrue(shown in previous..text.length)
            if (shown in 1 until text.length) {
                assertFalse(text[shown - 1].isHighSurrogate() && text[shown].isLowSurrogate())
            }
            previous = shown
        }
        assertEquals(text.length, previous)
    }

    @Test fun `reveal never splits a combining grapheme`() {
        val pacer = StreamingTextPacer()
        val text = "Cafe\u0301 noir ".repeat(30)
        for (frame in 0..300) {
            val shown = pacer.advance(text, frame * 16_666_667L)
            if (shown in 1 until text.length) {
                assertFalse("combining mark must stay with its base", text[shown].category == CharCategory.NON_SPACING_MARK)
            }
        }
    }

    @Test fun `resuming does not turn elapsed background time into reveal credit`() {
        val pacer = StreamingTextPacer()
        val text = "a".repeat(1000)
        var shown = 0
        for (frame in 0..20) shown = pacer.advance(text, frame * 16_666_667L)
        pacer.resume()
        assertEquals(shown, pacer.advance(text, 60_000_000_000L))
    }
}
