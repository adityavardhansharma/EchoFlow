package com.echoflow

import com.echoflow.ui.components.MarkdownBlock
import com.echoflow.ui.components.markdownToPlainText
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
}
