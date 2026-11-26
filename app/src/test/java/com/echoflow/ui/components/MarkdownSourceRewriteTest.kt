package com.echoflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The HTML and video rewrites must leave code, escapes, and link text exactly as written. */
class MarkdownSourceRewriteTest {

    @Test
    fun quotedFencesStayLiteral() {
        val html = "> ~~~html\n> <b>x</b>\n> ~~~\n\nAfter <b>y</b>"
        assertEquals("> ~~~html\n> <b>x</b>\n> ~~~\n\nAfter **y**", normalizeInlineHtml(html))
        val video = "> ~~~html\n> <video src=\"clip.mp4\"></video>\n> ~~~"
        assertEquals(video, neutralizeUnsupportedVideo(video))
    }

    @Test
    fun fencesNestedInListsStayLiteral() {
        val source = "- Example\n     ~~~html\n  <video src=\"clip.mp4\"></video>\n     ~~~"
        assertEquals(source, neutralizeUnsupportedVideo(source))
    }

    @Test
    fun overIndentedCloserDoesNotEndTheFence() {
        val source = "~~~html\n    ~~~\n<b>literal</b>\n~~~"
        assertEquals(source, normalizeInlineHtml(source))
    }

    @Test
    fun indentedCodeStaysProtectedPastItsFirstLine() {
        val source = "    first = 1\n    <b>literal</b>\n    price = \"\$5\""
        assertEquals(source, normalizeInlineHtml(source))
        assertEquals(source, normalizeMath(source))
    }

    @Test
    fun escapedTagsAndLinkTitlesStayLiteral() {
        assertEquals("Type \\<b> for bold.", normalizeInlineHtml("Type \\<b> for bold."))
        assertEquals("\\<b>literal\\</b>", normalizeInlineHtml("\\<b>literal\\</b>"))
        val link = "[docs](https://example.test \"<b>\")"
        assertEquals(link, normalizeInlineHtml(link))
        assertEquals("Real **bold**", normalizeInlineHtml("Real <b>bold</b>"))
    }

    @Test
    fun lineBreaksInPipelessTableRowsStayOnTheRow() {
        assertEquals(
            "A | B\n--- | ---\none two | three",
            normalizeInlineHtml("A | B\n--- | ---\none<br>two | three"),
        )
        assertEquals("one  \ntwo", normalizeInlineHtml("one<br>two"))
    }

    @Test
    fun videoFallbackReadsTheRealSrcAttribute() {
        assertEquals(
            "Video: actual.mp4",
            neutralizeUnsupportedVideo("<video data-src=\"preview.mp4\" src=\"actual.mp4\"></video>"),
        )
        assertEquals(
            "Video: real.mp4",
            neutralizeUnsupportedVideo("<video title=\"src=fake.mp4\" src='real.mp4'></video>"),
        )
        assertEquals("Video", neutralizeUnsupportedVideo("<video data-src=\"preview.mp4\"></video>"))
    }
}
