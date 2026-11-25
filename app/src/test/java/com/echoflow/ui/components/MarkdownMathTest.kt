package com.echoflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownMathTest {

    @Test
    fun currencyStaysText() {
        assertEquals("Plans cost \\\$5 and \\\$10.", normalizeMath("Plans cost \$5 and \$10."))
        assertEquals("Between \\\$5-\\\$10 a month", normalizeMath("Between \$5-\$10 a month"))
        assertEquals("I paid \\\$1,000 or \\\$2k", normalizeMath("I paid \$1,000 or \$2k"))
        assertEquals("Costs \\\$5, \\\$6, or \\\$7", normalizeMath("Costs \$5, \$6, or \$7"))
        assertEquals("A \\\$ sign", normalizeMath("A \$ sign"))
    }

    @Test
    fun keepsRealDollarMath() {
        assertEquals("Let \$x^2 + y^2 = r^2\$ hold", normalizeMath("Let \$x^2 + y^2 = r^2\$ hold"))
        assertEquals("where \$n\$ is the count", normalizeMath("where \$n\$ is the count"))
        assertEquals("a \$p\$-value", normalizeMath("a \$p\$-value"))
        assertEquals("\$\\alpha\$ and \$\\beta\$", normalizeMath("\$\\alpha\$ and \$\\beta\$"))
    }

    @Test
    fun rewritesParenAndBracketDelimiters() {
        assertEquals("is \$P(1+r)^t\$, so", normalizeMath("is \\(P(1+r)^t\\), so"))
        assertEquals("is \$\$x = \\frac{1}{2}\$\$ here", normalizeMath("is \\[x = \\frac{1}{2}\\] here"))
        assertEquals("\$\$\nE = mc^2\n\$\$", normalizeMath("\\[\n  E = mc^2\n\\]"))
    }

    @Test
    fun displayBlocksGetTheirOwnParagraph() {
        assertEquals(
            "Before\n\n\$\$\na = b\nc = d\n\$\$\n\nAfter",
            normalizeMath("Before\n\$\$\na = b\nc = d\n\$\$\nAfter"),
        )
        assertEquals("\$\$\nx\n\$\$", normalizeMath("\$\$ x \$\$"))
    }

    @Test
    fun leavesCodeAndLinksAlone() {
        val source = "Use `\$x\$` and [a](https://x.dev/?q=\$5) then\n```\nprice = \"\$5\"\n```"
        assertEquals(source, normalizeMath(source))
        assertEquals("An escaped \\\$5", normalizeMath("An escaped \\\$5"))
    }

    @Test
    fun unclosedMathOutsideStreamingIsText() {
        assertEquals("\\\$\\\$\nx", normalizeMath("\$\$\nx"))
        assertEquals("open \\\$x", normalizeMath("open \$x"))
    }

    @Test
    fun streamingClosesInlineMath() {
        assertEquals("Area is \$\\pi r\$", normalizeMath("Area is \$\\pi r^", streaming = true))
        assertEquals("so \$P(1+r)\$", normalizeMath("so \\(P(1+r)^", streaming = true))
        assertEquals("Total: ", normalizeMath("Total: \$", streaming = true))
        assertEquals("costs \\\$5", normalizeMath("costs \$5", streaming = true))
        assertEquals("Earlier \$x\$ then \$y_{1}\$", normalizeMath("Earlier \$x\$ then \$y_{1", streaming = true))
    }

    @Test
    fun streamingClosesDisplayMath() {
        assertEquals("\$\$\n\\frac{a}{}\n\$\$", normalizeMath("\$\$\n\\frac{a}{", streaming = true))
        assertEquals("Intro", normalizeMath("Intro\n\$\$", streaming = true).trimEnd())
        assertEquals("\$\$\na = \\sqrt{b}\n\$\$", normalizeMath("\\[\na = \\sqrt{b", streaming = true))
    }

    @Test
    fun balancesPartialLatex() {
        assertEquals("\\frac{a}{b}", balancePartialLatex("\\frac{a}{b"))
        assertEquals("\\frac{a}{}", balancePartialLatex("\\frac{a}"))
        assertEquals("x", balancePartialLatex("x^"))
        assertEquals("x +", balancePartialLatex("x + \\alp"))
        assertEquals("\\left( a\\right.", balancePartialLatex("\\left( a"))
        assertEquals("\\begin{pmatrix} a & b \\\\\\end{pmatrix}", balancePartialLatex("\\begin{pmatrix} a & b \\\\"))
        assertEquals("a", balancePartialLatex("a \\begin{pmat"))
        assertEquals("", balancePartialLatex("\\"))
    }
}
