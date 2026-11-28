package com.echoflow.weave.core

import com.echoflow.weave.core.eval.Place
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.program.Edits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §4: edits and versions. */
class EditsTest {
    private fun rootText(s: Session) = s.plainText(s.root()!!)

    @Test fun `T-100 replace a binding`() {
        val s = W.session("a = 1\nroot = Text(a)")
        val r = Edits.apply(s.program, "a = 2")
        assertTrue(r.accepted); s.applyEdit(r)
        assertEquals("2", rootText(s)); assertEquals(2, s.program.version)
    }

    @Test fun `T-101 add bindings`() {
        val s = W.session("root = Column [a]\na = Text(\"x\")")
        s.applyEdit(Edits.apply(s.program, "b = Text(\"y\")\nroot = Column [a, b]"))
        assertEquals("x\ny", rootText(s))
    }

    @Test fun `T-102 T-103 kept and reset state`() {
        val s = W.session("\$r = 5\nroot = Text(\$r)")
        s.input(Place("r", emptyList()), WNum(9.0))
        s.applyEdit(Edits.apply(s.program, "\$r = 1")); assertEquals(WNum(9.0), s.state["r"])
        s.applyEdit(Edits.apply(s.program, "\$r = 1 reset")); assertEquals(WNum(1.0), s.state["r"])
    }

    @Test fun `T-104 delete effects`() {
        val p = W.prog("\$r = 1\nroot = Text(\$r)\non change(\$r) -> !haptic(tick)")
        assertTrue(Edits.apply(p, "-on change(\$r)").program.effects.isEmpty())
        val q = Edits.apply(p, "on change(\$r) -> !toast(\"a\")\n-on change(\$r)\non change(\$r) -> !toast(\"b\")").program
        assertEquals(1, q.effects.size)
        assertTrue(q.print().contains("on change(\$r) -> !toast(\"b\")"))
    }

    @Test fun `T-105 root cannot be deleted`() {
        val r = Edits.apply(W.prog("root = Text(\"x\")"), "-root")
        assertTrue("E107" in r.diagnostics.map { it.code }); assertTrue("root" in r.program.bindings)
    }

    @Test fun `T-106 deleting a used binding`() {
        val s = W.session("root = Text(a)\na = \"x\"")
        val r = Edits.apply(s.program, "-a")
        assertTrue(r.accepted); s.applyEdit(r)
        assertEquals("—", rootText(s)); assertTrue("E301" in r.program.diagnostics.map { it.code } || "W407" in r.program.diagnostics.map { it.code })
    }

    @Test fun `T-106b an edit with no renderable root is rejected`() {
        val p = W.prog("root = Text(\"x\")")
        val r = Edits.apply(p, "root = Nope()")
        assertFalse(r.accepted); assertEquals(1, r.program.version)
    }

    @Test fun `T-108 limits reject the whole edit`() {
        val src = (1..398).joinToString("\n") { "a$it = $it" } + "\nroot = Text(\"x\")"
        val p = W.prog(src)
        assertEquals(399, p.bindings.size)
        val r = Edits.apply(p, "h1 = 1\nh2 = 2")
        assertFalse(r.accepted)
    }

    @Test fun `T-110 canonical print round-trips`() {
        val p = W.prog("root=Card(gap:m)[Text( \"a\" ),Text(\"b\")]")
        assertEquals("root = Card(gap: m) [Text(\"a\"), Text(\"b\")]", p.print())
        for (src in Corpus.programs()) {
            val once = W.prog(src).print()
            assertEquals(src, once, W.prog(once).print())
        }
    }

    @Test fun `edits keep unknown helpers out of the null root`() {
        val s = W.session("root = Text(\"x\")")
        assertEquals(WStr("x"), s.root()!!.args["content"])
        assertEquals(WNull, s.binding("nope"))
    }
}
