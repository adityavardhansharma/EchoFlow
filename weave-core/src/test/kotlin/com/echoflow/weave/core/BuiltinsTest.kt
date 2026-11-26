package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 07 §3.3: built-ins. */
class BuiltinsTest {
    private fun d(v: WValue) = (v as WNum).v

    @Test fun `T-284 to T-288 finance`() {
        assertEquals(43391.16, d(W.eval("emi(5000000, 0.085, 20)")), 0.01)
        assertEquals(100.0, d(W.eval("pmt(0, 12, 1200)")), 1e-12)
        assertEquals(0.1, d(W.eval("irr([-100, 110])")), 1e-7)
        assertEquals(0.0, d(W.eval("npv(0.1, [-100, 110])")), 1e-9)
        assertEquals(1210.0, d(W.eval("compound(1000, 0.1, 2)")), 1e-9)
        assertEquals(
            WList(listOf(WRec.of("year" to WNum(1.0), "principal" to WNum(120000.0), "interest" to WNum(0.0), "balance" to WNum(0.0)))),
            W.eval("amortize(120000, 0, 1)"),
        )
        assertEquals(0.0, d(W.eval("last(amortize(5000000, 0.085, 20)).balance")), 0.0)
        assertEquals(5000000.0, d(W.eval("sum(amortize(5000000, 0.085, 20), \"principal\")")), 0.01)
    }

    @Test fun `T-289 rounding`() {
        assertEquals(3.0, d(W.eval("round(2.5)")), 0.0)
        assertEquals(-3.0, d(W.eval("round(-2.5)")), 0.0)
        assertEquals(1200.0, d(W.eval("round(1234, -2)")), 0.0)
    }

    @Test fun `T-290 deterministic random`() {
        assertEquals(W.eval("rand(\"x\", 0)"), W.eval("rand(\"x\", 0)"))
        assertNotEquals(W.eval("rand(\"x\", 0)"), W.eval("rand(\"x\", 1)"))
        val r = d(W.eval("rand(1, 99)"))
        assertTrue(r >= 0 && r < 1)
    }

    @Test fun `T-291 to T-292 dates`() {
        assertEquals(WDate(LocalDate.of(2026, 2, 28)), W.eval("addMonths(date(\"2026-01-31\"), 1)"))
        assertEquals(WNum(2.0), W.eval("weekday(date(\"2026-12-01\"))"))
        assertEquals(WDate(LocalDate.of(2026, 11, 30)), W.eval("startOfWeek(date(\"2026-12-03\"))"))
    }

    @Test fun `T-293 conversions`() {
        assertEquals(1.609344, d(W.eval("convert(1, \"mi\", \"km\")")), 1e-12)
        assertEquals(212.0, d(W.eval("convert(100, \"C\", \"F\")")), 1e-9)
        assertEquals(WNull, W.eval("convert(1, \"kg\", \"m\")"))
    }

    @Test fun `T-294 groupBy keeps first-seen order`() {
        val v = W.eval("groupBy([{c: \"a\"}, {c: \"b\"}, {c: \"a\"}], \"c\")") as WRec
        assertEquals(listOf("a", "b"), v.fields.keys.toList())
        assertEquals(2, (v["a"] as WList).items.size)
    }

    @Test fun `T-295 take and slice`() {
        assertEquals(WList(listOf(WNum(3.0), WNum(4.0))), W.eval("take([1,2,3,4], -2)"))
        assertEquals(WStr("él"), W.eval("slice(\"héllo\", 1, 3)"))
    }

    @Test fun `T-296 validators`() {
        assertEquals(WBool.TRUE, W.eval("isEmail(\"a@b.co\")"))
        assertEquals(WBool.FALSE, W.eval("isEmail(\"a@b\")"))
        assertEquals(WBool.TRUE, W.eval("isPhone(\"+91 98765-43210\")"))
    }
}
