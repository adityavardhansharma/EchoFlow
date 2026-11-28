package com.echoflow.weave.core

import com.echoflow.weave.core.eval.Coercion
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.parse.IncrementalParser
import com.echoflow.weave.core.program.Edits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fifth review's findings (PR #268). */
class ReviewFiveTest {
    // resource cases run under a JUnit timeout, so a regression that loops fails instead of hanging
    @Test(timeout = 5_000) fun `replace and join are bounded by the string limit`() {
        val setup = "s = repeat(repeat(\"x\", 1000), 100)"
        assertEquals(WNull, W.eval("replace(s, \"x\", s)", setup))
        assertEquals(WStr("ayc"), W.eval("replace(\"abc\", \"b\", \"y\")"))
        assertEquals(WNull, W.eval("join([s, s, s, s, s, s, s, s, s, s, s, s], \"\")", setup))
        assertEquals(WStr("a-b"), W.eval("join([\"a\", \"b\"], \"-\")"))
    }

    @Test(timeout = 5_000) fun `round clamps its scale`() {
        assertEquals(WNum(1.0), W.eval("round(1, 1000000000)"))
        assertEquals(WNum(1200.0), W.eval("round(1234, -2)"))
    }

    @Test(timeout = 5_000) fun `formatting a shared nested value stops at the cap`() {
        val defs = (1..30).joinToString("\n") { "a$it = [a${it - 1}, a${it - 1}]" }
        val s = W.session("a0 = [1]\n$defs\nroot = Text(a30)")
        val text = s.plainText(s.root()!!)
        assertTrue(text.length <= com.echoflow.weave.core.Limits.STRING_CHARS + 1)
    }

    @Test(timeout = 5_000) fun `a long digit string is rejected as a duration quickly`() {
        assertNull(Coercion.parseDuration("1".repeat(100_000)))
        assertEquals(5_400_000L, Coercion.parseDuration("1h 30m"))
        assertEquals(1_500L, Coercion.parseDuration("1.5s"))
        assertNull(Coercion.parseDuration("1h x"))
    }

    @Test fun `a streaming state waits until its declaration is frozen`() {
        val p = IncrementalParser()
        val s = Session(p.update("\$n = 1 ", false), W.ctx())
        assertTrue("n" !in s.state)
        s.sync(p.update("\$n = 1 + 2\nroot = Text(\$n)", false))
        s.sync(p.update("\$n = 1 + 2\nroot = Text(\$n)", true))
        assertEquals(WNum(3.0), s.state["n"])
    }

    @Test fun `a partial and on the next line keeps the statement open`() {
        val p = IncrementalParser()
        p.update("a = true\na", false)
        val prog = p.update("a = true\nand false\nroot = Text(a)", true)
        val s = Session(prog, W.ctx())
        assertEquals(com.echoflow.weave.core.eval.WBool.FALSE, s.binding("a"))
    }

    @Test fun `a discarded tap moves no history and sends no effects`() {
        val p = IncrementalParser()
        val src = "\$x = 0\n\$rate = base(10)\nroot = Column [Button(\"U\", onTap: [!undo, !copy(\$rate)]), Button(\"S\", onTap: [!scrollTo(root), !copy(\$rate)]), Button(\"I\", onTap: !inc(\$x))]\n"
        val s = Session(p.update(src, false), W.ctx())
        val buttons = s.root()!!.children
        s.fire(buttons[2].args["onTap"]!!)
        assertEquals(1, s.historySize)
        s.fire(buttons[0].args["onTap"]!!)
        assertEquals(1, s.historySize)
        assertEquals(WNum(1.0), s.state["x"])
        s.fire(buttons[1].args["onTap"]!!)
        assertTrue(s.outbox.none { it.kind == "scrollTo" })
    }

    @Test(timeout = 5_000) fun `a zero-delay effect loop doesn't hang the clock`() {
        val s = W.session("\$n = 0\nroot = Button(\"Go\", onTap: !inc(\$n))\non change(\$n) -> !after(0ms, !inc(\$n))")
        s.appear()
        s.fire(s.root()!!.args["onTap"]!!)
        s.advance(10)
        val d = W.session("\$n = 0\nroot = Button(\"Go\", onTap: !inc(\$n))\non change(\$n, debounce: 0ms) -> !inc(\$n)")
        d.appear()
        d.fire(d.root()!!.args["onTap"]!!)
        d.advance(10)
    }

    @Test fun `an edit clears undo history`() {
        val s = W.session("\$x = 0\nroot = Button(\"Go\", onTap: !inc(\$x))")
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(1, s.historySize)
        val r = Edits.apply(s.program, "\$y: str = \"a\"")
        assertTrue(r.accepted)
        s.applyEdit(r)
        assertEquals(0, s.historySize)
    }

    @Test fun `statements past the source limit are dropped`() {
        val big = "x".repeat(com.echoflow.weave.core.Limits.SOURCE_BYTES)
        val p = W.prog("root = Text(\"ok\")\nhuge = \"$big\"\nlater = 1")
        assertTrue("E304" in W.codes(p) || p.diagnostics.any { it.message.contains("source", ignoreCase = true) })
        assertTrue("huge" !in p.bindings && "later" !in p.bindings)
        assertTrue("root" in p.bindings)
    }

    @Test fun `an oversized edit is rejected whole`() {
        val s = W.session("a = 1\nb = 2\non change(\$x) -> !set(\$x, 0)\n\$x = 0\nroot = Text(b)")
        val big = "x".repeat(com.echoflow.weave.core.Limits.SOURCE_BYTES)
        val r = Edits.apply(s.program, "a = \"$big\"")
        assertTrue(!r.accepted)
        assertTrue("b" in r.program.bindings)
    }

    @Test fun `a renamed capitalised binding counts as frozen`() {
        val p = IncrementalParser()
        val prog = p.update("Header = 10\n\$n = Header\nroot = Text(\$n)\nx", false)
        assertTrue(prog.frozenNames!!.contains("header"))
        val s = Session(prog, W.ctx())
        assertEquals(WNum(10.0), s.state["n"])
    }

    @Test(timeout = 5_000) fun `plain text of a shared node tree is bounded`() {
        val defs = (1..30).joinToString("\n") { "n$it = Column [n${it - 1}, n${it - 1}]" }
        val s = W.session("n0 = Text(\"hi\")\n$defs\nroot = Column [Text(nodeText(n30))]")
        assertTrue(s.plainText(s.root()!!).length <= com.echoflow.weave.core.Limits.STRING_CHARS + 1)
    }

    @Test fun `a long multi-part duration is accepted`() {
        assertEquals(17L, Coercion.parseDuration(List(17) { "1ms" }.joinToString(" ")))
    }

    @Test fun `an oversized redeclaration leaves the earlier one active`() {
        val big = "x".repeat(com.echoflow.weave.core.Limits.SOURCE_BYTES)
        val p = W.prog("a = 1\nroot = Text(a)\na = \"$big\"")
        assertEquals(WNum(1.0), Session(p, W.ctx()).binding("a"))
    }

    @Test fun `a later speculative redeclaration is not frozen`() {
        val p = IncrementalParser()
        val prog = p.update("\$n = 1\n\$n = 2 +", false)
        assertTrue("\$n" !in prog.frozenNames!!)
    }
}
