package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §3.2: actions and effects. */
class ActionsTest {
    private fun tap(s: Session, label: String) {
        val b = s.nodes().first { it.type == "Button" && (it.args["label"] as? WStr)?.v == label }
        s.fire(b.args.getValue("onTap"))
    }

    private fun block(setup: String, onTap: String) = W.session("$setup\nroot = Button(\"go\", onTap: $onTap)")

    @Test fun `T-270 actions in a list see earlier writes`() {
        val s = block("\$n = 0", "[!set(\$n, \$n + 1), !set(\$n, \$n + 1)]")
        tap(s, "go")
        assertEquals(WNum(2.0), s.state["n"]); assertEquals(1, s.historySize)
    }

    @Test fun `T-271 to T-274 places`() {
        val s = block("\$todos = [{t: \"a\", done: false}]", "!set(\$todos[0].done, true)")
        tap(s, "go")
        assertEquals(WList(listOf(WRec.of("t" to WStr("a"), "done" to WBool.TRUE))), s.state["todos"])
        val o = block("\$l = [1, 2]", "!set(\$l[5], 9)")
        tap(o, "go")
        assertEquals(WList(listOf(WNum(1.0), WNum(2.0))), o.state["l"]); assertTrue("E205" in o.diagnostics.map { it.code })
        val a = block("\$l = [1, 2]", "!set(\$l[2], 3)")
        tap(a, "go")
        assertEquals(WList(listOf(WNum(1.0), WNum(2.0), WNum(3.0))), a.state["l"])
        val t = block("\$x: num = 0", "!set(\$x, \"abc\")")
        tap(t, "go")
        assertEquals(WNum(0.0), t.state["x"]); assertTrue("E205" in t.diagnostics.map { it.code })
    }

    @Test fun `T-275 and T-460 effects cannot run outbound actions`() {
        val s = W.session("root = Text(\"x\")\non appear -> [!url(\"https://example.com\"), !toast(\"hi\")]")
        s.appear()
        assertEquals(listOf("toast"), s.outbox.map { it.kind })
    }

    @Test fun `T-276 undo inside the same transaction has nothing to undo`() {
        val s = block("\$a = 1", "[!inc(\$a), !undo]")
        tap(s, "go")
        assertEquals(WNum(2.0), s.state["a"]); assertEquals(1, s.historySize)
    }

    @Test fun `T-042b and T-042d merged effects`() {
        val s = W.session("\$x = 0\nroot = Text(\$x)\non change(\$x) when \$x > 10 -> !toast(\"high\")\non change(\$x) when \$x < 0 -> !toast(\"low\")")
        val place = com.echoflow.weave.core.eval.Place("x", emptyList())
        s.input(place, WNum(11.0)); assertEquals(listOf("high"), s.outbox.map { (it.args[0] as WStr).v })
        s.outbox.clear(); s.input(place, WNum(-1.0)); assertEquals(listOf("low"), s.outbox.map { (it.args[0] as WStr).v })
        val d = W.session("\$x = 0\n\$n = 0\nroot = Text(\$x)\non change(\$x) when \$x > 10 -> !set(\$x, 0)\non change(\$x) when \$x < 5 -> !inc(\$n)")
        d.input(place, WNum(11.0))
        assertEquals(WNum(0.0), d.state["x"])
        // first firing: branch 1 resets $x, branch 2 sees 0 → $n = 1; the reset's change fires again → $n = 2
        assertEquals(WNum(2.0), d.state["n"])
    }

    @Test fun `T-278 state waits for its function`() {
        val s = W.session("\$rate = base(10)\nroot = Text(\$rate)\nfn base(n) = n * 2")
        assertEquals(WNum(20.0), s.state["rate"])
    }

    @Test fun `T-279 a missing function initialises to null`() {
        val p = W.prog("\$rate = nope(10)\nroot = Text(\$rate)")
        val s = Session(p, W.ctx())
        assertEquals(com.echoflow.weave.core.eval.WNull, s.state["rate"]); assertTrue("E202" in W.codes(p))
    }

    @Test fun `T-280 effect loops stop after 8 firings`() {
        val s = block("\$a = 0\n\$b = 0\non change(\$a) -> !inc(\$b)\non change(\$b) -> !inc(\$a)", "!inc(\$a)")
        tap(s, "go")
        assertEquals(WNum(5.0), s.state["a"]); assertEquals(WNum(4.0), s.state["b"])
        assertEquals(1, s.diagnostics.count { it.code == "E306" })
    }

    @Test fun `T-281 debounce`() {
        val s = W.session("\$q = 0\n\$n = 0\nroot = Text(\$q)\non change(\$q, debounce: 400ms) -> !inc(\$n)")
        val p = com.echoflow.weave.core.eval.Place("q", emptyList())
        repeat(5) { s.input(p, WNum(it + 1.0)); s.advance(100) }
        assertEquals(WNum(0.0), s.state["n"])
        s.advance(300)
        assertEquals(WNum(1.0), s.state["n"])
    }

    @Test fun `T-282 every is raised to 250ms`() {
        val p = W.prog("\$n = 0\nroot = Text(\$n)\non every(100ms) -> !inc(\$n)")
        assertTrue("W402" in W.codes(p))
        val s = Session(p, W.ctx())
        s.appear(); s.advance(1000)
        assertTrue(s.state["n"] in listOf(WNum(3.0), WNum(4.0)))
    }

    @Test fun `T-283 appear fires once`() {
        val s = W.session("\$n = 0\nroot = Text(\$n)\non appear -> !inc(\$n)")
        s.appear(); s.appear()
        assertEquals(WNum(1.0), s.state["n"])
    }
}
