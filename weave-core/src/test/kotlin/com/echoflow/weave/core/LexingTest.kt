package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.lex.Lexer
import com.echoflow.weave.core.lex.Normalize
import com.echoflow.weave.core.lex.TK
import com.echoflow.weave.core.parse.Splitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §1: source, lexing and splitting. */
class LexingTest {
    private fun statements(body: String) = Splitter.split(Lexer(Normalize.body(body)).lex()).size

    @Test fun `T-002 stray fence line removed`() {
        val p = W.prog("a = 1\n```\nb = 2\nroot = Text(a + b)")
        assertEquals(listOf("a", "b", "root"), p.bindings.keys.toList())
    }

    @Test fun `T-003 CRLF and BOM normalise to the same program`() {
        val a = W.print("a = 1\nroot = Text(a)")
        val b = W.print("﻿a = 1\r\nroot = Text(a)")
        assertEquals(a, b)
        assertEquals(Normalize.body("x\r\ny\rz"), Normalize.body(Normalize.body("x\r\ny\rz")))
    }

    @Test fun `T-010 underscores in numbers`() = assertEquals(WNum(1000000.0), W.eval("1_000_000"))

    @Test fun `T-010b malformed digit runs are one E104 each`() {
        assertEquals(listOf("E104"), W.codes("a = 1__0\nroot = Text(\"x\")"))
        assertEquals(listOf("E104"), W.codes("a = 1_\nroot = Text(\"x\")"))
    }

    @Test fun `T-011 range does not eat the dot`() =
        assertEquals(WList(listOf(WNum(1.0), WNum(2.0), WNum(3.0))), W.eval("1..3"))

    @Test fun `T-012 percent vs modulo`() {
        assertEquals(WNum(0.45), W.eval("45%"))
        assertEquals(WNum(1.0), W.eval("10%3"))
        assertEquals(WNum(1.0), W.eval("10 % 3"))
        assertEquals(WList(listOf(WNum(0.45), WNum(0.05))), W.eval("[45%, 5%]"))
    }

    @Test fun `T-013 durations`() {
        assertEquals(WDur(300), W.eval("300ms"))
        assertEquals(WDur(120_000), W.eval("2m"))
        assertEquals(WDur(1_500), W.eval("1.5s"))
        val (v, codes) = W.evalWithDiags("5min")
        assertEquals(WDur(300_000), v)
        assertTrue(codes.contains("W403"))
    }

    @Test fun `T-014 unit suffixes are removed and snapped`() {
        val p = W.prog("root = Card(gap: 12dp) [Text(\"x\")]")
        assertEquals("root = Card(gap: m) [Text(\"x\")]", p.print())
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("root = Card(gap: 13px) [Text(\"x\")]")
        assertEquals("root = Card(gap: m) [Text(\"x\")]", q.print())
        assertEquals(listOf("W403", "W402"), W.codes(q))
    }

    @Test fun `T-015 strings and interpolation`() {
        assertEquals(WStr("Hi Asha"), W.eval("\"Hi {name}\"", "name = \"Asha\""))
        assertEquals(WStr("3.14"), W.eval("\"{x | num(2)}\"", "x = 3.14159"))
        assertEquals(WStr("{literal}"), W.eval("\"{{literal}}\""))
        val (v, codes) = W.evalWithDiags("\"{x | nope}\"", "x = 2")
        assertEquals(WStr("2"), v)
        assertTrue(codes.contains("E202"))
        assertEquals(WStr("2"), W.eval("\"{a |> take(1)}\"", "a = [2, 3]"))
        val (s, c1) = W.evalWithDiags("'single'")
        assertEquals(WStr("single"), s); assertTrue(c1.contains("W403"))
        val (q, c2) = W.evalWithDiags("“curly”")
        assertEquals(WStr("curly"), q); assertTrue(c2.contains("W403"))
    }

    @Test fun `T-016 raw strings keep the trailing newline`() {
        assertEquals("line1\n  line2\n", Lexer.rawText("\n    line1\n      line2\n    "))
    }

    @Test fun `T-017 hex in colour slot and hex-looking comments`() {
        val p = W.prog("root = Text(\"x\", color: #FF0000)")
        assertEquals("root = Text(\"x\", color: error)", p.print())
        assertEquals(listOf("W403"), W.codes(p))
        val q = W.prog("a = 1 # #FF0000 is red\nroot = Text(a)")
        assertEquals(WNum(1.0), com.echoflow.weave.core.eval.Session(q, W.ctx()).binding("a"))
        assertTrue(Lexer("a = 1 # #FF0000").lex().none { it.kind == TK.HEX })
    }

    @Test fun `T-020 to T-026 logical lines`() {
        assertEquals(2, statements("a = 1\nb = 2"))
        assertEquals(1, statements("root = Card [\n  a,\n  b\n]"))
        assertEquals(1, statements("x = 1 +\n  2"))
        assertEquals(1, statements("xs = items\n  |> filter(i => i > 0)"))
        assertEquals(1, statements("t = \$a\n  ? \"yes\"\n  : \"no\""))
        assertEquals(1, statements("a = (1\n\n# note\n+ 2)"))
        val p = W.prog("a = 1)\nb = 2\nroot = Text(b)")
        assertTrue("a" !in p.bindings); assertTrue("b" in p.bindings)
        assertEquals(listOf("E103"), W.codes(p))
    }
}
