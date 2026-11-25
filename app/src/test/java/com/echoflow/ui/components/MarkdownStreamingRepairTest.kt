package com.echoflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownStreamingRepairTest {

    private fun repair(text: String) = repairStreamingMarkdown(text)

    @Test
    fun closesOpenEmphasis() {
        assertEquals("This is **bol**", repair("This is **bol"))
        assertEquals("x _it_", repair("x _it"))
        assertEquals("**bold *it***", repair("**bold *it"))
        assertEquals("~~gone~~", repair("~~gone"))
        assertEquals("==hi==", repair("==hi"))
        assertEquals("\$x\$ **b**", repair("\$x\$ **b"))
    }

    @Test
    fun holdsBackADelimiterWithNothingAfterIt() {
        assertEquals("This is", repair("This is **"))
        assertEquals("Hello", repair("Hello *"))
        assertEquals("snake_", repair("snake_"))
        assertEquals("- item", repair("- item"))
        assertEquals("* * *", repair("* * *"))
    }

    @Test
    fun closesOpenCodeSpans() {
        assertEquals("a `code`", repair("a `code"))
        assertEquals("a", repair("a `").trimEnd())
        assertEquals("**see `x`**", repair("**see `x"))
    }

    @Test
    fun linksShowTheirTextUntilComplete() {
        assertEquals("See Reuters", repair("See [Reuters](https://reu"))
        assertEquals("See Reu", repair("See [Reu"))
        assertEquals("See Reuters", repair("See [Reuters]"))
        assertEquals("See [Reuters](https://reuters.com)", repair("See [Reuters](https://reuters.com)"))
        assertEquals("Chart:", repair("Chart: ![alt](https://ex"))
        assertEquals("- ", repair("- ["))
        assertEquals("- [x]", repair("- [x]"))
    }

    @Test
    fun holdsBackPartialSyntaxLines() {
        assertEquals("Text", repair("Text\n``"))
        assertEquals("Para", repair("Para\n-"))
        assertEquals("Para\n\n---", repair("Para\n\n---"))
        assertEquals("Hello", repair("Hello <vid").trimEnd())
        assertEquals("Hello", repair("Hello <video src=\"https://x").trimEnd())
        assertEquals("a < b", repair("a < b"))
    }

    @Test
    fun tableHeaderGetsItsSeparatorEarly() {
        assertEquals("| a | b |\n| --- | --- |", repair("| a | b |"))
        assertEquals("| a | b |\n| :-- | --- |", repair("| a | b |\n|:--"))
        assertEquals("| a | b |\n|---|---|", repair("| a | b |\n|---|---|"))
        assertEquals("Intro\n\n| a | b |\n| --- | --- |", repair("Intro\n\n| a | b |"))
    }

    @Test
    fun openFenceIsLeftToTheParser() {
        val code = "```kotlin\nval a = **b\n\nval c = [x"
        assertEquals(code, repair(code))
    }

    @Test
    fun repairIsANoOpOnWellFormedText() {
        val text = "## Title\n\nA **bold** and *it* and `code` and [link](https://x.dev).\n\n- [ ] task\n- [x] done"
        assertEquals(text, repair(text))
    }
}
