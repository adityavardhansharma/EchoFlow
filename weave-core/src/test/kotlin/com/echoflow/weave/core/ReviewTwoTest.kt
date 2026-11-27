package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.Coercion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundary cases from the second review of #268. */
class ReviewTwoTest {

    @Test fun `nested shared lists can't expand without bound through member access`() {
        val defs = (1..30).joinToString("\n") { "a$it = [a${it - 1}, a${it - 1}]" }
        val s = W.session("a0 = [{x: 1}]\n$defs\nr = a30.x\nroot = Text(\"ok\")")
        assertEquals(WNull, s.binding("r"))
    }

    @Test fun `repeat, string multiply and concatenation bound their results`() {
        assertEquals(WNull, W.eval("repeat(repeat(repeat(\"x\", 1000), 1000), 1000)"))
        assertEquals(WNull, W.eval("repeat(\"x\", 1000) * 1000"))
        assertEquals("xxx", (W.eval("repeat(\"x\", 3)") as com.echoflow.weave.core.eval.WStr).v)
    }

    @Test fun `huge decimal counts in formats are capped`() {
        val v = W.eval("fmt(1, \"num(1000000000)\")")
        assertTrue((v as com.echoflow.weave.core.eval.WStr).v.length < 40)
    }

    @Test fun `number parsing rejects long non-numbers quickly`() {
        val start = System.nanoTime()
        assertNull(Coercion.parseNumber("1".repeat(300) + "x"))
        assertNull(Coercion.parseNumber("1".repeat(5000)))
        assertEquals(12.5, Coercion.parseNumber("12.5")!!, 0.0)
        assertTrue(System.nanoTime() - start < 500_000_000)
    }

    @Test fun `a runaway handler returns instead of throwing`() {
        val s = W.session("\$n = 0\nfn f(x) = f(x + 1)\nroot = Button(\"Go\", onTap: _ => f(0))")
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(WNum(0.0), s.state["n"])
    }

    @Test fun `a deferred action that reads a waiting value discards the transaction`() {
        val parser = com.echoflow.weave.core.parse.IncrementalParser()
        val head = "\$out = 0\n\$rate = base(10)\nroot = Button(\"Go\", onTap: [!inc(\$out), !copy(\$rate)])\n"
        val s = com.echoflow.weave.core.eval.Session(parser.update(head, false), W.ctx())
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(WNum(0.0), s.state["out"])
        assertTrue(s.outbox.isEmpty())
    }

    @Test fun `inputs keep their onChange handler`() {
        val s = W.session("\$n = 1\n\$out = 0\nroot = Slider(\$n, onChange: v => !set(\$out, v))")
        val slider = s.root()!!
        s.input(slider.places["value"]!!, WNum(7.0), slider.args["onChange"])
        assertEquals(WNum(7.0), s.state["out"])
        assertTrue("E203" !in W.codes("\$n = 1\nroot = Slider(\$n, onChange: v => !set(\$n, v), help: \"h\", rule: true)"))
    }

    @Test fun `very long flat expressions are dropped, not a stack overflow`() {
        val src = "x = " + (1..5000).joinToString("+") { "1" } + "\nroot = Text(\"{x}\")"
        val p = W.prog(src)
        assertTrue("E304" in W.codes(p))
        assertTrue(p.renderable)
    }
}
