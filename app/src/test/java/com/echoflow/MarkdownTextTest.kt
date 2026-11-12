package com.echoflow

import com.echoflow.ui.components.MarkdownBlock
import com.echoflow.ui.components.markdownToPlainText
import com.echoflow.ui.components.neutralizeUnsupportedVideo
import com.echoflow.ui.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun parsesDollarDisplayMathBlock() {
        val blocks = parseMarkdownBlocks(
            """
            Before

            $$
            f'(x) = \lim_{h \to 0} \frac{f(x+h)-f(x)}{h}
            $$

            After
            """.trimIndent()
        )

        assertEquals("Before", (blocks[0] as MarkdownBlock.Paragraph).text)
        val math = blocks[1] as MarkdownBlock.MathBlock
        assertTrue(math.complete)
        assertEquals("f'(x) = \\lim_{h \\to 0} \\frac{f(x+h)-f(x)}{h}", math.latex)
        assertEquals("After", (blocks[2] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun parsesBracketDisplayMathBlock() {
        val blocks = parseMarkdownBlocks(
            """
            \[
            E = mc^2
            \]
            """.trimIndent()
        )

        val math = blocks.single() as MarkdownBlock.MathBlock
        assertTrue(math.complete)
        assertEquals("E = mc^2", math.latex)
    }

    @Test
    fun keepsUnclosedDisplayMathVisibleWhileStreaming() {
        val blocks = parseMarkdownBlocks(
            """
            $$
            \frac{x}{y}
            """.trimIndent()
        )

        val math = blocks.single() as MarkdownBlock.MathBlock
        assertFalse(math.complete)
        assertEquals("$$\n\\frac{x}{y}", math.raw)
    }

    @Test
    fun markdownToPlainTextStripsSyntaxAndKeepsVisibleText() {
        val plain = markdownToPlainText(
            """
            ## Heading

            A **bold** word, *italic*, `code`, and a [label](https://example.com).

            - first
            - second

            ```kotlin
            val x = 1
            ```
            """.trimIndent()
        )
        assertEquals(
            """
            Heading

            A bold word, italic, code, and a label.

            • first
            • second

            val x = 1
            """.trimIndent(),
            plain,
        )
    }

    @Test
    fun markdownToPlainTextHandlesGfmUnderscoreEmphasisWithoutTouchingIdentifiers() {
        assertEquals(
            "Dune: Part Two and Blade Runner 2049; keep file_name intact.",
            markdownToPlainText("_Dune: Part Two_ and __Blade Runner 2049__; keep file_name intact."),
        )
    }

    @Test
    fun markdownToPlainTextHandlesNestedAndEscapedUnderscores() {
        assertEquals(
            "important; outer inner text; outer inner text",
            markdownToPlainText("___important___; _outer __inner__ text_; __outer _inner_ text__"),
        )
        assertEquals("_literal_ and _closing_", markdownToPlainText("\\_literal_ and _closing\\_"))
        assertEquals("\\_code", markdownToPlainText("`\\_code`"))
    }

    @Test
    fun markdownToPlainTextKeepsExclamationBeforeALink() {
        assertEquals(
            "Look!docs",
            markdownToPlainText("Look![docs](https://example.com)"),
        )
        assertEquals(
            "Look! docs",
            markdownToPlainText("Look! [docs](https://example.com)"),
        )
    }

    @Test
    fun markdownToPlainTextStripsImageBangButKeepsAltText() {
        assertEquals("diagram", markdownToPlainText("![diagram](https://example.com/a.png)"))
        assertEquals("See diagram", markdownToPlainText("See ![diagram](https://example.com/a.png)"))
        assertEquals("See:diagram", markdownToPlainText("See:![diagram](https://example.com/a.png)"))
        assertEquals("diagram", markdownToPlainText("**![diagram](https://example.com/a.png)**"))
    }

    @Test
    fun markdownToPlainTextKeepsBangAfterClosingPunctuation() {
        assertEquals(
            "Wow!!docs",
            markdownToPlainText("Wow!![docs](https://example.com)"),
        )
        assertEquals(
            "(see here)!docs",
            markdownToPlainText("(see here)![docs](https://example.com)"),
        )
    }

    @Test
    fun markdownToPlainTextHandlesNestedParensInLinkUrls() {
        assertEquals(
            "docs",
            markdownToPlainText("[docs](https://example.com/Foo_(bar))"),
        )
    }

    @Test
    fun markdownToPlainTextOmitsCitationHyperlinks() {
        assertEquals(
            "The sky is blue.",
            markdownToPlainText("The sky is blue [1](https://example.com)."),
        )
        assertEquals(
            "Revenue grew last quarter.",
            markdownToPlainText("Revenue grew last quarter. ([Reuters](https://reuters.com))"),
        )
        assertEquals(
            "Two sources agree.",
            markdownToPlainText("Two sources agree [1](https://a.example) [2](https://b.example)."),
        )
        assertEquals(
            "See the docs for more.",
            markdownToPlainText("See [the docs](https://example.com/guide) for more."),
        )
    }

    @Test
    fun videoBlockKeepsTheSurroundingReply() {
        val source = """
            Before

            <video src="https://example.com/clip.mp4"></video>

            After **bold**
        """.trimIndent()

        val rendered = neutralizeUnsupportedVideo(source)
        assertTrue(rendered.contains("Before"))
        assertTrue(rendered.contains("After **bold**"))
        assertTrue(rendered.contains("Video: https://example.com/clip.mp4"))
        assertFalse(rendered.contains("<video"))
    }

    @Test
    fun videoInsideCodeIsLeftAlone() {
        val fenced = "See\n\n```html\n<video src=\"https://example.com/a.mp4\"></video>\n```\n\nDone"
        assertEquals(fenced, neutralizeUnsupportedVideo(fenced))
        val inline = "Use `<video src=\"https://example.com/a.mp4\">` here."
        assertEquals(inline, neutralizeUnsupportedVideo(inline))
    }

    @Test
    fun multilineVideoTagDoesNotSwallowTheNextParagraph() {
        val source = """
            Intro
            <video
              src='https://example.com/a.mp4'>
            </video>
            Still here
        """.trimIndent()

        val rendered = neutralizeUnsupportedVideo(source)
        assertTrue(rendered.contains("Intro"))
        assertTrue(rendered.contains("Video: https://example.com/a.mp4"))
        assertTrue(rendered.contains("Still here"))
        assertFalse(rendered.contains("<video"))
        assertFalse(rendered.contains("</video"))
    }
}
