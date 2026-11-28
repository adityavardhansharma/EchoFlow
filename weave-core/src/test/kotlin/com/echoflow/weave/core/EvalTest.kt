package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.truthy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §3 and §3.1: values, evaluation and operators. */
class EvalTest {
    private fun n(d: Double) = WNum(d)
    private fun list(vararg d: Double) = WList(d.map { WNum(it) })

    @Test fun `T-200 non-finite results are null`() {
        assertEquals(WNull, W.eval("1 / 0"))
        assertEquals(WNull, W.eval("sqrt(-1)"))
        assertEquals(WNull, W.eval("pow(10, 400)"))
    }

    @Test fun `T-201 symbols`() {
        assertEquals(emptyList<String>(), W.codes("root = Text(\"x\", tone: primary)"))
        val (v, codes) = W.evalWithDiags("primray")
        assertEquals(WStr("primray"), WStr((v as com.echoflow.weave.core.eval.WSym).name))
        assertTrue(codes.contains("W407"))
    }

    @Test fun `T-210 T-211 string parsing`() {
        val s = W.session("\$n: num = 0\nroot = TextField(\$n, label: \"Amount\", kind: number)")
        val place = s.nodes().first { it.type == "TextField" }.places.getValue("value")
        s.input(place, WStr("1,234.5")); assertEquals(n(1234.5), s.state["n"])
        s.input(place, WStr("12%")); assertEquals(n(0.12), s.state["n"])
        s.input(place, WStr("₹500")); assertEquals(n(0.12), s.state["n"])
        assertEquals(WDur(5_400_000), com.echoflow.weave.core.eval.Coercion(W.fmt()).dur(WStr("1h 30m")).value)
    }

    @Test fun `T-220 truthiness`() {
        val vals = listOf<WValue>(n(0.0), WStr(""), WList.EMPTY, WRec(LinkedHashMap()), WStr("0"), WDur(0), WStr("false"))
        assertEquals(listOf(false, false, false, false, true, false, true), vals.map { it.truthy() })
    }

    @Test fun `T-221 sorting with nulls`() {
        assertEquals(WList(listOf(n(1.0), n(2.0), n(3.0), WNull)), W.eval("sortBy([3, null, 1, 2])"))
        assertEquals(WList(listOf(n(3.0), n(1.0), WNull)), W.eval("sortBy([3, null, 1], desc: true)"))
    }

    @Test fun `T-230 qualified reads`() {
        val loan = W.prog("@weave 1 id: loan\nemi = 100\nroot = Text(emi)\nexport emi")
        val exports = { block: String, name: String -> if (block == "loan" && name in loan.exports) Session(loan, W.ctx()).binding(name) else null }
        val s = Session(W.prog("x = loan::emi\nroot = Text(x)"), EvalContext(W.fmt(), exports = exports))
        assertEquals(n(100.0), s.binding("x"))
        val gone = Session(W.prog("x = loan::emi\nroot = Text(x)"), W.ctx())
        assertEquals(WNull, gone.binding("x"))
    }

    @Test fun `T-240 cycles`() {
        val s = W.session("a = b + 1\nb = a + 1\nroot = Text(a)")
        assertEquals(WNull, s.binding("a"))
        assertEquals(WNull, s.binding("b"))
        val cyc = s.diagnostics.filter { it.code == "E302" }
        assertEquals(1, cyc.size)
        assertTrue(cyc[0].message, cyc[0].message.startsWith("a, b"))
    }

    @Test fun `T-241 budget and limits`() {
        val (a, ca) = W.evalWithDiags("f(0)", "fn f(n) = f(n + 1)")
        assertEquals(WNull, a); assertTrue(ca.toString(), "E303" in ca)
        val s = W.session("result = range(30000)\nroot = Text(\"x\")")
        assertEquals(WNull, s.binding("result")); assertTrue("E304" in s.diagnostics.map { it.code })
        val t = W.session("result = sum(map(range(10000), i => map(range(10), j => j)))\nroot = Text(\"x\")")
        assertEquals(WNull, t.binding("result")); assertTrue("E303" in t.diagnostics.map { it.code })
    }

    @Test fun `T-250 to T-262 operators`() {
        assertEquals(WBool.FALSE, W.eval("0.1 + 0.2 == 0.3"))
        assertEquals(WBool.TRUE, W.eval("round(0.1 + 0.2, 2) == 0.3"))
        assertEquals(WBool.FALSE, W.eval("1 == \"1\""))
        assertEquals(WStr("a1"), W.eval("\"a\" + 1"))
        assertEquals(list(1.0, 2.0), W.eval("[1] + [2]"))
        assertEquals(WRec.of("a" to n(2.0), "b" to n(3.0)), W.eval("{a: 1} + {a: 2, b: 3}"))
        assertEquals(n(2.0), W.eval("-1 % 3"))
        assertEquals(WNull, W.eval("10 % 0"))
        assertEquals(n(1.0), W.eval("null ?? 1"))
        assertEquals(WBool.FALSE, W.eval("false ?? 1"))
        assertEquals(n(5.0), W.eval("0 or 5"))
        assertEquals(n(4.0), W.eval("3 and 4"))
        assertEquals(n(0.0), W.eval("0 and 4"))
        assertEquals(WDur(259_200_000), W.eval("date(\"2026-12-03\") - date(\"2026-11-30\")"))
        assertEquals(WBool.FALSE, W.eval("\"b\" < \"a\""))
        assertEquals(WBool.TRUE, W.eval("\"a\" < \"ab\""))
        assertEquals(WBool.FALSE, W.eval("null < 1"))
        assertEquals(WBool.TRUE, W.eval("3 in [1, 2, 3]"))
        assertEquals(WBool.TRUE, W.eval("\"k\" in {k: 1}"))
        assertEquals(WBool.TRUE, W.eval("\"ell\" in \"hello\""))
        assertEquals(list(5.0, 4.0, 3.0, 2.0), W.eval("5..2"))
    }
}
