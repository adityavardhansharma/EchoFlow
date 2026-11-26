package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.program.StateCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 05 §8.6 encoding, storage round trips and the 06 §2.4 state line. */
class StateCodecTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test fun `values encode as the spec table says`() {
        assertEquals("\"300ms\"", StateCodec.encode(WDur(300)))
        assertEquals("\"2026-12-01\"", StateCodec.encode(W.eval("date(\"2026-12-01\")")))
        assertEquals("{\"a\":[1,2.5,\"x\"]}", StateCodec.encode(WRec.of("a" to WList(listOf(WNum(1.0), WNum(2.5), WStr("x")))), ist))
        assertEquals("\"[node]\"", StateCodec.encode(W.session("root = Text(\"x\")").root()!!))
    }

    @Test fun `stored state round-trips through declared types and skips transient states`() {
        val src = "\$n: num = 1\n\$tags = [\"a\"]\n\$t = 5 transient\n\$d: dur = 2s\nroot = Text(\"{\$n}\")"
        val s = W.session(src)
        s.state["n"] = WNum(7.0); s.state["t"] = WNum(9.0)
        val json = StateCodec.encodeState(s.program, s.state, ist)
        assertTrue("\"t\"" !in json)
        val back = StateCodec.decodeState(s.program, json, W.fmt())
        assertEquals(WNum(7.0), back["n"])
        assertEquals(WList(listOf(WStr("a"))), back["tags"])
        assertEquals(WDur(2000), back["d"])
        assertNull(back["t"])
        // a value that no longer fits its type is left out, so the state re-initialises
        assertNull(StateCodec.decodeState(s.program, "{\"n\":\"many\"}", W.fmt())["n"])
        assertEquals(emptyMap<String, Any>(), StateCodec.decodeState(s.program, "{broken", W.fmt()))
    }

    @Test fun `state line lists changed states and truncates`() {
        val s = W.session("\$a = 1\n\$b = \"x\"\nroot = Text(\"{\$a}\")")
        val initial = HashMap(s.state)
        assertNull(StateCodec.stateLine(s.program, s.state, initial))
        s.state["a"] = WNum(5.0)
        assertEquals("{\"a\":5}", StateCodec.stateLine(s.program, s.state, initial))
        s.state["b"] = WStr("y".repeat(500))
        assertEquals("{\"a\":5,\"b\":\"${"y".repeat(200)}…\"}", StateCodec.stateLine(s.program, s.state, initial))
        val tiny = StateCodec.stateLine(s.program, s.state, initial, max = 30)!!
        assertTrue(tiny, tiny.length <= 30)
    }
}
