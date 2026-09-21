package com.echoflow

import com.echoflow.ui.components.MarkdownBlock
import com.echoflow.ui.components.markdownToPlainText
import com.echoflow.ui.components.parseMarkdownBlocks
import com.echoflow.ui.components.prepareGfmLatex
import com.echoflow.ui.components.prepareInlineLatex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun gfmAndLatexRemainInOneMarkdownSegment() {
        val segments = prepareGfmLatex(
            "1. _Blade Runner 2049_ — **Problem:** evaluate ${'$'}\\frac{8}{2}${'$'} ([IMDb](https://imdb.com))."
        )

        val markdown = segments.single() as com.echoflow.ui.components.GfmLatexSegment.Markdown
        assertTrue(markdown.source.contains("_Blade Runner 2049_"))
        assertTrue(markdown.source.contains("**Problem:**"))
        assertTrue(markdown.source.contains("[IMDb](https://imdb.com)"))
        assertEquals("\\frac{8}{2}", markdown.math.single().latex)
        assertFalse(markdown.source.contains("${'$'}\\frac{8}{2}${'$'}"))
    }

    @Test
    fun displayLatexSplitsWithoutChangingSurroundingGfm() {
        val segments = prepareGfmLatex(
            "## Result\n\n_Lead_\n\n${'$'}${'$'}\n\\int_0^1 x dx\n${'$'}${'$'}\n\n- **Done**"
        )

        assertEquals(3, segments.size)
        assertTrue((segments[0] as com.echoflow.ui.components.GfmLatexSegment.Markdown).source.contains("_Lead_"))
        assertEquals("\\int_0^1 x dx", (segments[1] as com.echoflow.ui.components.GfmLatexSegment.DisplayMath).latex)
        assertTrue((segments[2] as com.echoflow.ui.components.GfmLatexSegment.Markdown).source.contains("- **Done**"))
    }

    @Test
    fun latexMarkersInsideCodeAreNotExtracted() {
        val (source, math) = prepareInlineLatex("Use `price = ${'$'}5` and ```sh\necho ${'$'}HOME\n```")
        assertTrue(math.isEmpty())
        assertEquals("Use `price = ${'$'}5` and ```sh\necho ${'$'}HOME\n```", source)
    }

    @Test
    fun mismatchedAndShorterFencesDoNotExposeLatex() {
        val markdown = """
            ````markdown
            ~~~
            ```
            ${'$'}x${'$'}
            ${'$'}${'$'}
            y
            ${'$'}${'$'}
            ````

            ~~~~markdown
            ```
            ${'$'}z${'$'}
            ~~~~
        """.trimIndent()

        val segments = prepareGfmLatex(markdown)
        assertEquals(1, segments.size)
        val segment = segments.single() as com.echoflow.ui.components.GfmLatexSegment.Markdown
        assertEquals(markdown, segment.source)
        assertTrue(segment.math.isEmpty())
    }

    @Test
    fun indentedAndEmptyDisplayMathStayWithGfm() {
        val markdown = "    ${'$'}${'$'}\n    x + y\n    ${'$'}${'$'}\n\n${'$'}${'$'}${'$'}${'$'}\n\n${'$'}${'$'}\n${'$'}${'$'}"
        val segments = prepareGfmLatex(markdown)

        assertEquals(1, segments.size)
        val segment = segments.single() as com.echoflow.ui.components.GfmLatexSegment.Markdown
        assertEquals(markdown, segment.source)
        assertTrue(segment.math.isEmpty())
    }

    @Test
    fun inlineLatexDoesNotRewriteLinkDestinationsOrLiteralTokens() {
        val literal = "ECHOFLOWLATEXPLACEHOLDER0TOKEN"
        val markdown = "$literal [details](https://example.test/${'$'}x${'$'}) then ${'$'}y${'$'}"
        val (prepared, math) = prepareInlineLatex(markdown)

        assertTrue(prepared.startsWith(literal))
        assertTrue(prepared.contains("[details](https://example.test/${'$'}x${'$'})"))
        assertEquals(listOf("y"), math.map { it.latex })
    }

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
}
