package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WSym
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 07 §3.4: formats. */
class FormatsTest {
    private val inF = W.fmt("en-IN")
    private val usF = W.fmt("en-US")
    private fun f(x: Double, name: String, vararg args: Any, us: Boolean = false) =
        (if (us) usF else inF).apply(WNum(x), name, args.map { if (it is Int) WNum(it.toDouble()) else WSym(it.toString()) })

    @Test fun `T-300 int`() { assertEquals("12,34,568", f(1234567.8, "int")); assertEquals("1,234,568", f(1234567.8, "int", us = true)) }
    @Test fun `T-301 num and dec`() { assertEquals("3.00", f(3.0, "num", 2)); assertEquals("3.1", f(3.10, "dec", 2)) }

    @Test fun `T-302 T-303 compact`() {
        assertEquals(listOf("2.5 Cr", "3.4 L", "5.6K", "999"), listOf(25000000.0, 340000.0, 5600.0, 999.0).map { f(it, "compact") })
        assertEquals(listOf("1.2M", "-1.5K"), listOf(1200000.0, -1500.0).map { f(it, "compact", us = true) })
    }

    @Test fun `T-304 percent and pct`() {
        assertEquals("8.5%", f(0.085, "percent")); assertEquals("50%", f(0.5, "percent")); assertEquals("8.5%", f(8.5, "pct"))
    }

    @Test fun `T-305 T-306 currency`() {
        assertEquals("₹43,391", f(43391.2, "inr"))
        assertEquals("$12.50", f(12.5, "usd", us = true))
        assertEquals("₹2.5 Cr", f(25000000.0, "inrCompact", us = true))
    }

    @Test fun `T-307 T-308 signed and ordinal`() {
        assertEquals("+12", f(12.0, "signed")); assertEquals("−3", f(-3.0, "signed")); assertEquals("3rd", f(3.0, "ordinal"))
    }

    @Test fun `T-309 T-310 durations`() {
        assertEquals(listOf("2h 15m", "45s", "350ms", "1d 3h"), listOf(8100000.0, 45000.0, 350.0, 97200000.0).map { f(it, "duration") })
        assertEquals("02:05", f(125000.0, "clock")); assertEquals("1:02:05", f(3725000.0, "clock"))
    }

    @Test fun `T-311 full date`() =
        assertEquals("Tuesday, 1 December 2026", inF.apply(WDate(LocalDate.of(2026, 12, 1)), "date", listOf(WSym("full"))))

    @Test fun `T-312 null is an em dash`() = assertEquals("—", inF.auto(WNull))

    @Test fun `T-313 plural`() {
        assertEquals("items", f(2.0, "plural", "item", "items")); assertEquals("item", f(1.0, "plural", "item", "items"))
    }
}
