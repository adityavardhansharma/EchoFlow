package com.echoflow.weave

import com.echoflow.data.ChatMessage
import com.echoflow.ui.components.MarkdownExtensionPart
import com.echoflow.ui.components.MarkdownStreamPlanner
import com.echoflow.weave.compose.chart.Charts
import com.echoflow.weave.compose.chart.Lttb
import com.echoflow.weave.runtime.WeaveHistory
import com.echoflow.weave.runtime.WeaveRuntime
import com.echoflow.weave.runtime.storage.WeaveBlockRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §1.1 fences, 07 §6 history, and the pure chart helpers. */
class FenceHistoryConformanceTest {

    private fun parts(text: String) = MarkdownStreamPlanner(setOf("weave", "weave-json")).plan(text).blocks.filterIsInstance<MarkdownExtensionPart>().map { it.block }

    @Test fun `T-001 the info string is case-sensitive`() {
        assertTrue(parts("```Weave\nroot = Text(\"x\")\n```\n").isEmpty())
        assertEquals(1, parts("```weave\nroot = Text(\"x\")\n```\n").size)
    }

    @Test fun `T-001b an edit fence names its target and ignores extra words`() {
        val b = parts("```weave edit loan extra\nx = 1\n```\n").single()
        assertEquals("weave", b.kind)
        val words = b.info.trim().split(Regex("\\s+"))
        assertEquals(listOf("weave", "edit", "loan"), words.take(3))
    }

    @Test fun `T-004 tilde fences are blocks and only a tilde closes them`() {
        val tilde = parts("~~~weave\nroot = Text(\"x\")\n~~~\n").single()
        assertTrue(tilde.complete)
        assertEquals("root = Text(\"x\")", tilde.body)
        val mixed = parts("~~~weave\nroot = Text(\"x\")\n```\n").single()
        assertFalse("~~~ closed by ``` stays open", mixed.complete)
    }

    private fun row(source: String, state: String = "{}", ui: String = "{}", blockId: String = "b1", messageId: String = "m1", key: Int = 0) = WeaveBlockRow(
        id = "$messageId-$key", chatId = "c", messageId = messageId, blockKey = key, blockId = blockId, version = 1, form = "source",
        source = source, stateJson = state, historyJson = "[]", lazyJson = "{}", queryJson = "{}", consentJson = "{}",
        firedJson = "{}", askLogJson = "[]", uiStateJson = ui, createdAt = 0, updatedAt = 0,
    )

    @Test fun `T-464 history sends the current source and a state line inside the fence`() {
        val src = "@weave 1 id: b1\n\$rate = 8.5\nroot = Slider(\$rate, min: 6, max: 14)"
        val body = WeaveHistory.body(row(src, state = "{\"rate\":9.1}"), headerOnly = false)
        assertTrue(body, body.startsWith(src))
        assertTrue(body, body.endsWith("\n# state {\"rate\":9.1}"))
        val msg = ChatMessage(id = "m1", chatId = "c", role = "assistant", content = "Here:\n```weave\n\$rate = 8.5\nroot = Slider(\$rate, min: 6, max: 14)\n```", createdAt = 0)
        val out = WeaveHistory.rewrite(listOf(msg), listOf(row(src, state = "{\"rate\":9.1}"))).single().content
        assertTrue(out, out.contains("# state {\"rate\":9.1}\n```"))
        assertFalse(out.contains("<weave-state>"))
    }

    @Test fun `T-466 a block shown as plain text goes to history as its plain text`() {
        val src = "@weave 1 id: b1\nroot = Text(\"Hello there\")"
        val body = WeaveHistory.body(row(src, ui = WeaveRuntime.addFlag("{}", "plain")), headerOnly = false)
        assertTrue(body, body.startsWith("# shown to the user as plain text:"))
        assertTrue(body.contains("# Hello there"))
    }

    @Test fun `T-107 an edit for a missing block becomes a new block with that id`() {
        assertEquals("@weave 1 id: zz\nroot = Text(\"x\")", WeaveRuntime.newBlockFromEdit("root = Text(\"x\")\n-gone", "zz"))
        assertTrue(WeaveRuntime.validBlockId("zz"))
        assertFalse(WeaveRuntime.validBlockId("Loan-1"))
        // history keeps the edit-born block, so the model sees it
        val msg = ChatMessage(id = "m2", chatId = "c", role = "assistant", content = "```weave edit zz\nroot = Text(\"x\")\n```", createdAt = 0)
        val out = WeaveHistory.rewrite(listOf(msg), listOf(row("@weave 1 id: zz\nroot = Text(\"x\")", blockId = "zz", messageId = "m2"))).single().content
        assertTrue(out, out.contains("@weave 1 id: zz"))
    }

    @Test fun `T-411 nice ticks for 10 to 30 from zero are 0, 10, 20, 30`() {
        val (lo, hi, step) = Charts.nice(0.0, 30.0)
        assertEquals(0.0, lo, 0.0); assertEquals(30.0, hi, 0.0); assertEquals(10.0, step, 0.0)
    }

    @Test fun `T-465 downsampling works on projected points and keeps the ends`() {
        val n = 10_000
        val x = FloatArray(n) { it.toFloat() }
        val y = FloatArray(n) { if (it == 4_321) 500f else (it % 7).toFloat() }
        val keep = Lttb.indices(x, y, 200)
        assertEquals(200, keep.size)
        assertEquals(0, keep.first()); assertEquals(n - 1, keep.last())
        assertTrue("indices are increasing", keep.toList().zipWithNext().all { (a, b) -> a < b })
        assertTrue("the spike survives", 4_321 in keep)
        assertEquals(5, Lttb.indices(x.copyOf(5), y.copyOf(5), 200).size)
    }
}
