package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.program.Edits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundary values and runtime rules found in review: nothing throws, nothing leaks. */
class RobustnessTest {

    @Test fun `huge padding widths are null, not an allocation`() {
        assertEquals(WNull, W.eval("padStart(\"\", 2147483647)"))
        assertEquals(WNull, W.eval("padEnd(\"x\", 50000, \"ab\")"))
        assertEquals("ababx", (W.eval("padStart(\"x\", 5, \"ab\")") as com.echoflow.weave.core.eval.WStr).v)
    }

    @Test fun `extreme counts and dates evaluate to values or null`() {
        assertEquals(WList(listOf(WNum(1.0))), W.eval("take([1], -2147483648)"))
        assertEquals(WList(emptyList()), W.eval("drop([1, 2], 2147483647)"))
        assertEquals(WNull, W.eval("addYears(today(), 1000000000)"))
        assertNotNull(W.session("root = Text(addYears(today(), 1000000000))").root())
        // state initialisation survives too
        val s = W.session("\$d = addYears(today(), 1000000000)\nroot = Text(\"ok\")")
        assertEquals(WNull, s.state["d"])
    }

    @Test fun `a timer an effect starts can't send an outbound action`() {
        val s = W.session("root = Text(\"hi\")\non appear -> !after(1ms, !say(\"hello\"))")
        s.appear(); s.advance(10)
        assertTrue(s.outbox.none { it.kind == "say" })
        val tapped = W.session("root = Button(\"Go\", onTap: !after(1ms, !say(\"hello\")))")
        tapped.fire(tapped.root()!!.args["onTap"]!!); tapped.advance(10)
        assertEquals(listOf("say"), tapped.outbox.map { it.kind })
    }

    @Test fun `an edit that deletes an every effect stops its timer`() {
        val s = W.session("\$n = 0\nroot = Text(\"{\$n}\")\non every(1s) -> !inc(\$n)")
        s.appear(); s.advance(2000)
        assertEquals(WNum(2.0), s.state["n"])
        val r = Edits.apply(s.program, "-on every(1s)")
        assertTrue(r.accepted)
        s.applyEdit(r); s.advance(5000)
        assertEquals(WNum(2.0), s.state["n"])
    }

    @Test fun `an edit whose root can't render is rejected`() {
        val s = W.session("root = Text(\"ok\")")
        assertFalse(Edits.apply(s.program, "root = 123").accepted)
        assertFalse(Edits.apply(s.program, "a = b\nb = a\nroot = a").accepted)
        assertTrue(Edits.apply(s.program, "root = Text(\"new\")").accepted)
    }
}
