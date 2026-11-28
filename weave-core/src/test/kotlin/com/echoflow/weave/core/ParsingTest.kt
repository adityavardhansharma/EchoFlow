package com.echoflow.weave.core

import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.Index
import com.echoflow.weave.core.parse.Lambda
import com.echoflow.weave.core.parse.BinaryOp
import com.echoflow.weave.core.parse.Ternary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §2: parsing and auto-fix. */
class ParsingTest {
    private fun expr(src: String, name: String) = (W.prog(src + "\nroot = Text(\"x\")").bindings[name] as Binding).expr

    @Test fun `T-030 lambdas vs parentheses`() {
        assertEquals(1, (expr("f = x => x * 2", "f") as Lambda).params.size)
        assertEquals(2, (expr("g = (a, b) => a + b", "g") as Lambda).params.size)
        assertTrue(expr("a = 1\nb = 2\nh = (a + b) * 2", "h") is BinaryOp)
    }

    @Test fun `T-031 ternary inside a named argument`() {
        val p = W.prog("\$bad = false\nroot = Text(\"x\", tone: \$bad ? error : primary)")
        val c = p.bindings["root"]!!.expr as Call
        assertTrue(c.named.first { it.first == "tone" }.second is Ternary)
        assertEquals(emptyList<String>(), W.codes(p))
    }

    @Test fun `T-032 = in an expression is ==`() {
        val p = W.prog("\$a = 3\nok = \$a = 3\nroot = Text(ok)")
        assertEquals("ok = \$a == 3", W.prog("\$a = 3\nok = \$a = 3\nroot = Text(ok)").print().lines().first { it.startsWith("ok") })
        assertEquals(listOf("W403"), W.codes(p))
    }

    @Test fun `T-040 capitalised binding names`() {
        val p = W.prog("Header = Title(\"x\")\nroot = Column [Header]")
        assertTrue("header" in p.bindings)
        assertEquals("root = Column [header]", p.print().lines().first { it.startsWith("root") })
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("header = Text(\"a\")\nHeader = Title(\"x\")\nroot = Column [header]")
        assertEquals(listOf("E107"), W.codes(q))
    }

    @Test fun `T-041 duplicate names keep the later one`() {
        val p = W.prog("a = 1\na = 2\nroot = Text(a)")
        assertEquals("a = 2", p.print().lines().first { it.startsWith("a") })
        assertEquals(listOf("W404"), W.codes(p))
    }

    @Test fun `T-042 same-trigger effects merge`() {
        val p = W.prog("\$x = 0\n\$a = 0\n\$b = 0\nroot = Text(\$x)\non change(\$x) -> !inc(\$a)\non change(\$x) -> !inc(\$b)")
        assertEquals(1, p.effects.size)
        assertTrue(p.print().contains("on change(\$x) -> [!inc(\$a), !inc(\$b)]"))
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("\$x = 0\nroot = Text(\$x)\non change(\$x) when \$x > 10 -> !toast(\"high\")\non change(\$x) when \$x < 0 -> !toast(\"low\")")
        assertTrue(q.print(), q.print().contains("on change(\$x) -> [!if(\$x > 10, !toast(\"high\")), !if(\$x < 0, !toast(\"low\"))]"))
        val r = W.prog("\$x = 0\nroot = Text(\$x)\non change(\$x, debounce: 300ms) -> !toast(\"a\")\non change(\$x) -> !toast(\"b\")")
        assertEquals(2, r.effects.size)
    }

    @Test fun `T-043 root fallback`() {
        val p = W.prog("card = Card [Text(\"x\")]")
        assertEquals("card", p.root); assertEquals(listOf("W405"), W.codes(p))
        val q = W.prog("x = 1")
        assertNull(q.root); assertFalse(q.renderable()); assertEquals(listOf("W405"), W.codes(q))
    }

    @Test fun `T-044 exports`() {
        val p = W.prog("emi = 1\nroot = Text(emi)\nexport emi, \$nope")
        assertEquals(setOf("emi"), p.exports); assertEquals(listOf("W407"), W.codes(p))
    }

    @Test fun `T-050 extra positionals use the positional order`() {
        val p = W.prog("\$r = 1\nroot = Slider(\$r, 0, 10, 0.5)")
        assertEquals("root = Slider(\$r, min: 0, max: 10, step: 0.5)", p.print().lines().first { it.startsWith("root") })
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("\$r = 1\nroot = Slider(\$r, 0, 10, 0.5, \"x\", 9)")
        assertEquals("root = Slider(\$r, min: 0, max: 10, step: 0.5)", q.print().lines().first { it.startsWith("root") })
        assertEquals(listOf("W403", "E109", "E109"), W.codes(q))
    }

    @Test fun `T-051 positional after named`() = assertEquals(listOf("E108", "E204"), W.codes("root = Text(style: body, \"hi\")"))

    @Test fun `T-052 aliases and unknown args`() {
        val p = W.prog("root = Button(\"Go\", onClick: !toast(\"x\"))")
        assertEquals("root = Button(\"Go\", onTap: !toast(\"x\"))", p.print())
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("root = Button(\"Go\", onTap: !toast(\"x\"), sparkle: true)")
        assertEquals("root = Button(\"Go\", onTap: !toast(\"x\"))", q.print())
        assertEquals(listOf("W401"), W.codes(q))
    }

    @Test fun `T-053 index vs children`() {
        assertTrue(expr("list = [1]\nx = list[0]", "x") is Index)
        assertEquals(1, ((W.prog("a = Text(\"x\")\nroot = Card [a]").bindings["root"]!!.expr) as Call).children!!.size)
        assertTrue(expr("s = \"a,b\"\ny = split(s, \",\")[0]", "y") is Index)
    }

    @Test fun `T-054 component without parentheses`() {
        val p = W.prog("root = Column [Divider, Text(\"x\")]", tier = 2)
        // Divider is not in the 1a registry: E209, the node is removed; Text stays.
        assertEquals("root = Column [null, Text(\"x\")]", p.print())
        val c = (p.bindings["root"]!!.expr as Call)
        assertEquals(2, c.children!!.size)
    }

    @Test fun `T-055 pipes`() =
        assertEquals("ys = take(filter(xs, x => x > 1), 2)", W.prog("xs = [1, 2, 3]\nys = xs |> filter(x => x > 1) |> take(2)\nroot = Text(ys)").print().lines().first { it.startsWith("ys") })

    @Test fun `T-056 action without parentheses`() =
        assertEquals("root = Button(\"Done\", onTap: !confetti())", W.print("root = Button(\"Done\", onTap: !confetti)"))

    @Test fun `T-060 header`() {
        val p = W.prog("@weave 2\nroot = Text(\"x\")")
        assertTrue(p.needsNewerApp); assertFalse(p.renderable())
        val q = W.prog("@weave 1 id: Loan-1\nroot = Text(\"x\")")
        assertNull(q.header.id); assertEquals(listOf("W403"), W.codes(q))
    }
}
