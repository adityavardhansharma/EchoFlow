package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WList
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

    @Test fun `removeWhere shares one step budget across every item`() {
        val items = (1..400).joinToString(", ")
        // Truthy once it finishes, so a full run clears the list and a budget stop leaves a tail.
        val s = W.session("\$xs = [$items]\nroot = Button(\"Go\", onTap: !removeWhere(\$xs, i => len(range(1000)) > 0))")
        val start = System.nanoTime()
        s.fire(s.root()!!.args["onTap"]!!)
        val left = (s.state["xs"] as com.echoflow.weave.core.eval.WList).items.size
        // The shared budget stops the removal well before every item is tested.
        assertTrue("removed down to $left", left in 1..399)
        assertTrue("E303" in s.diagnostics.map { it.code })
        assertTrue(System.nanoTime() - start < 2_000_000_000)
    }

    @Test fun `a size limit during removal is E304, and a waiting read discards the tap`() {
        val over = W.session("\$xs = [1, 2, 3]\nroot = Button(\"Go\", onTap: !removeWhere(\$xs, i => len(range(30000)) > 0))")
        over.fire(over.root()!!.args["onTap"]!!)
        assertTrue("E304" in over.diagnostics.map { it.code })
        assertTrue("E303" !in over.diagnostics.map { it.code })
        assertEquals(3, (over.state["xs"] as com.echoflow.weave.core.eval.WList).items.size)

        val parser = com.echoflow.weave.core.parse.IncrementalParser()
        val head = "\$out = 0\n\$rate = base(10)\n\$xs = [1, 2]\nroot = Button(\"Go\", onTap: [!inc(\$out), !removeWhere(\$xs, i => (\$rate ?? len(range(30000))) > 0)])\n"
        val s = com.echoflow.weave.core.eval.Session(parser.update(head, false), W.ctx())
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(WNum(0.0), s.state["out"])
    }

    @Test fun `list rows take their key prop and scope their children`() {
        val s = W.session("btn = Button(\"Go\", key: \"go\")\nrows = [{k: \"a\"}, {k: \"b\"}]\nroot = List(rows, render: item => Column(key: item.k) [btn])")
        val keys = s.nodes().filter { it.type == "Button" }.map { it.key }
        assertEquals(listOf("root/s:a/s:go", "root/s:b/s:go"), keys)
    }

    @Test fun `a node used as a key is not expanded`() {
        val defs = (1..20).joinToString("\n") { "a$it = [a${it - 1}, a${it - 1}]" }
        val start = System.nanoTime()
        val s = W.session("a0 = [{x: 1}]\n$defs\nroot = Column [Text(\"ok\", key: a20)]")
        assertEquals("ok", s.nodes().first { it.type == "Text" }.let { s.plainText(it) })
        assertTrue(System.nanoTime() - start < 2_000_000_000)
    }

    @Test fun `ranges that overflow a long are rejected before any list is built`() {
        val start = System.nanoTime()
        assertEquals(WNull, W.eval("-1e308 .. 1e308"))
        assertTrue(System.nanoTime() - start < 1_000_000_000)
        // Half away from zero: -1.5 rounds to -2, -0.5 rounds to 0.
        assertEquals(com.echoflow.weave.core.eval.WList(listOf(WNum(-2.0), WNum(-1.0), WNum(0.0))), W.eval("-2 .. 0"))
        assertEquals(com.echoflow.weave.core.eval.WList(listOf(WNum(-1.0), WNum(0.0))), W.eval("-1.5 .. -0.5"))
    }

    @Test fun `string concatenation stops before formatting a huge list`() {
        val start = System.nanoTime()
        assertEquals(WNull, W.eval("repeat(\"y\", 100000) + repeat([1], 5000)"))
        assertTrue(System.nanoTime() - start < 2_000_000_000)
        assertEquals("ab", (W.eval("\"a\" + \"b\"") as com.echoflow.weave.core.eval.WStr).v)
    }

    @Test fun `irr of all zeroes is null, and a short loan still amortizes`() {
        assertEquals(WNull, W.eval("irr([0, 0, 0])"))
        val rows = W.eval("amortize(1000, 0.1, 0.01)") as com.echoflow.weave.core.eval.WList
        assertEquals(1, rows.items.size)
    }

    @Test fun `randInt rejects bounds that are not whole numbers`() {
        assertEquals(WNull, W.eval("randInt(\"x\", 0, 1.5, 3.5)"))
        assertEquals(1.0, (W.eval("randInt(\"x\", 0, 1, 1)") as WNum).v, 0.0)
    }

    @Test fun `a place reads pluck and length, and rounds half away from zero`() {
        val s = W.session("\$xs = [{name: \"a\"}, {name: \"b\"}]\nn = \$xs.name\nk = \$xs.len\nroot = Text(\"ok\")")
        assertEquals(2.0, (s.binding("k") as WNum).v, 0.0)
        assertEquals("a", ((s.binding("n") as com.echoflow.weave.core.eval.WList).items[0] as com.echoflow.weave.core.eval.WStr).v)
        assertEquals(3, com.echoflow.weave.core.eval.Places.listIndex(5, -1.5))
    }

    @Test fun `a value where a place is required is reported`() {
        assertTrue("E205" in W.codes("root = Chart([{a: 1, b: 2}], select: 1)"))
    }

    @Test fun `a mismatched bracket is unbalanced, not a syntax error`() {
        assertTrue("E103" in W.codes("a = (1]\nroot = Text(\"x\")"))
        assertTrue("E101" !in W.codes("a = (1]\nroot = Text(\"x\")"))
        // Only the bad statement is dropped: the root still renders (02 §3.2).
        assertEquals("ok", W.session("a = (1]\nroot = Text(\"ok\")").let { it.plainText(it.root()!!) })
    }

    @Test fun `a hex colour after a colon is not a comment`() {
        val p = W.prog("root = Text(\"a\tb\", color: #FF0000)")
        val shown = W.session("root = Text(\"a\tb\", color: #FF0000)").let { it.plainText(it.root()!!) }
        assertTrue(shown, shown.contains("\t"))
    }

    @Test fun `tone slots do not rewrite a hex colour`() {
        assertTrue("E205" in W.codes("root = Text(\"x\", tone: #f00)"))
    }

    @Test fun `a root that is not a node is not renderable`() {
        val p = W.prog("root = Nope()")
        assertEquals("root", p.root)
        assertTrue(!p.renderable())
    }

    @Test fun `plucking a shared nested list stops at the step budget`() {
        val defs = (1..30).joinToString("\n") { "a$it = [a${it - 1}, a${it - 1}]" }
        val s = W.session("a0 = [{x: 1}]\n$defs\nn = a30.x\nroot = Text(\"ok\")")
        assertEquals(WNull, s.binding("n"))
        assertTrue("E303" in s.diagnostics.map { it.code })
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
        assertTrue(p.renderable())
    }
}
