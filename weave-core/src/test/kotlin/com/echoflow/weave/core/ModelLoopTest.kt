package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.program.Edits
import com.echoflow.weave.core.program.ModelResponses
import com.echoflow.weave.core.prompt.HiddenPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 05 §8 hidden requests: templates, response parsing, !ask restriction, lazy fragments. */
class ModelLoopTest {

    @Test fun `first fence or the whole text`() {
        assertEquals("a = 1", ModelResponses.firstFence("Sure.\n```weave edit b1\na = 1\n```\nmore"))
        assertEquals("root = Text(\"x\")", ModelResponses.firstFence("root = Text(\"x\")"))
        assertEquals("a = 2", ModelResponses.firstFence("```weave\na = 2"))
    }

    @Test fun `ask may only replace its names or add new ones`() {
        val s = W.session("\$n = 1\ntip = 10\nnote = Text(\"a\")\nroot = Column [note, Text(\"{tip}\")]")
        val (edit, skipped) = ModelResponses.restrictAsk(s.program, "note = Text(\"b\")\ntip = 99\nhelper = 5\n\$n = 5\n\$m = 2\n-tip", setOf("note"))
        assertTrue(edit.contains("note = Text(\"b\")"))
        assertTrue(edit.contains("helper = 5"))
        assertTrue(edit.contains("\$m = 2"))
        assertTrue("tip" in skipped && "\$n" in skipped)
        assertTrue(Edits.apply(s.program, edit).accepted)
    }

    @Test fun `ask sends update names, not their values`() {
        val src = "note = Text(\"a\")\nroot = Button(\"Go\", onTap: !ask(\"shorter\", [note]))"
        val s = com.echoflow.weave.core.eval.Session(com.echoflow.weave.core.program.Compiler.compile(src, com.echoflow.weave.core.catalog.BuiltinCatalog), W.ctx())
        s.fire(s.root()!!.args["onTap"]!!)
        val e = s.outbox.single()
        assertEquals("ask", e.kind)
        assertEquals(WList(listOf(WStr("note"))), e.args.getOrNull(1) ?: e.named["update"])
    }

    @Test fun `lazy fragments become a prefixed binding`() {
        val edit = ModelResponses.lazyEdit("day2", "items = [\"Fort\", \"Lake\"]\nroot = List(items)")!!
        assertTrue(edit, edit.contains("day2__lazy = List(day2__items)"))
        assertTrue(edit.contains("day2__items = [\"Fort\", \"Lake\"]"))
        val s = W.session("root = Text(\"x\")")
        assertTrue(Edits.apply(s.program, edit).accepted)
    }

    @Test fun `templates carry the source, state and task`() {
        val t = HiddenPrompts.ask("b1", "Tip", "root = Text(\"x\")", "{}", "[]", listOf("shorter" to listOf("note")), "make it blue", listOf("note"))
        assertTrue(t.contains("```weave edit b1```"))
        assertTrue(t.contains("Recent updates: \"shorter\" -> note"))
        assertTrue(HiddenPrompts.regen("root = Text(\"x\")", "{}").contains("complete new block"))
    }
}
