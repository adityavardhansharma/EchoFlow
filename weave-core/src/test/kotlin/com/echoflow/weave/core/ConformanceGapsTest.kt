package com.echoflow.weave.core

import com.echoflow.weave.core.eval.Place
import com.echoflow.weave.core.eval.Scope
import com.echoflow.weave.core.eval.WAction
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.parse.Span
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.core.prompt.EchoUiTiers
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 cases that had no test of their own. */
class ConformanceGapsTest {

    private fun undo() = WAction("undo", emptyList(), emptyList(), Scope.EMPTY, Span.NONE)

    @Test fun `T-277 a slider drag over 10 values is one history entry`() {
        val s = W.session("\$v = 0\nroot = Slider(\$v, 0, 10)")
        val place = Place("v", emptyList())
        for (i in 1..10) s.input(place, WNum.of(i.toDouble()), drag = true)
        s.endDrag()
        assertEquals(WNum.of(10.0), s.state["v"])
        assertEquals(1, s.historySize)
        s.fire(undo())
        assertEquals(WNum.of(0.0), s.state["v"])
        // the next drag is a new entry
        s.input(place, WNum.of(3.0), drag = true); s.endDrag()
        s.input(place, WNum.of(4.0), drag = true); s.endDrag()
        assertEquals(2, s.historySize)
    }

    @Test fun `T-464b 250 changed states fit a 2,000-character state line`() {
        val names = (0 until 250).map { "s%03d".format(it) }
        val src = names.joinToString("\n") { "\$$it = 0" } + "\nroot = Text(\"x\")"
        val s = W.session(src)
        val initial = s.state.toMap()
        val changed = initial + names.associateWith { WStr("y".repeat(300)) }
        val line = StateCodec.stateLine(s.program, changed, initial)!!
        assertTrue("${line.length} chars", line.length <= 2000)
        val kept = Regex(""""(s\d{3})":"…"""").findAll(line).map { it.groupValues[1] }.toList()
        assertEquals(names.take(kept.size), kept)
        val more = Regex(""""…":"\+(\d+) more"\}$""").find(line)!!.groupValues[1].toInt()
        assertEquals(250, kept.size + more)
        assertTrue(kept.isNotEmpty())
    }

    @Test fun `T-467 an initial value past STATE_BYTES is null with E304 and later states still initialise`() {
        val s = W.session("\$big = map(range(1000), i => \"x\" * 1000)\n\$n = 1\nroot = Text(\$n)")
        assertEquals(WNull, s.state["big"])
        assertEquals(WNum.of(1.0), s.state["n"])
        assertTrue(s.diagnostics.map { it.code }.toString(), s.diagnostics.any { it.code == "E304" })
        assertTrue(StateCodec.encodeState(s.program, s.state).length < Limits.STATE_BYTES)
    }

    @Test fun `T-467 a write past STATE_BYTES rolls back the whole transaction`() {
        val s = W.session("\$a = 0\n\$big = \"\"\nroot = Button(\"Fill\", onTap: [!set(\$a, 1), !set(\$big, map(range(300), i => \"x\" * 1000))])")
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(WNum.of(0.0), s.state["a"])
        assertEquals("the previous value is kept", WStr(""), s.state["big"])
        assertTrue(s.diagnostics.any { it.code == "E304" })
    }

    @Test fun `T-602 a chat override wins over the global level`() {
        assertEquals(IntelligentUiLevel.IMMERSIVE, EchoUiTiers.chatLevel(IntelligentUiLevel.OFF, "immersive"))
        assertEquals(IntelligentUiLevel.OFF, EchoUiTiers.chatLevel(IntelligentUiLevel.OFF, null))
        assertEquals(IntelligentUiLevel.BALANCED, EchoUiTiers.chatLevel(IntelligentUiLevel.OFF, "loud"))
    }

    @Test fun `T-610 a record averaging 2_3 errors over 10 blocks drops a tier`() {
        var record = emptyList<Int>()
        var downgraded = false
        // 23 errors over 10 blocks; the downgrade lands once the average passes 2 with ≥ 5 blocks
        for (e in listOf(1, 2, 2, 3, 2, 3, 2, 3, 2, 3)) {
            val (next, down) = EchoUiTiers.recordQuality(record, e)
            record = next
            if (down) { downgraded = true; break }
        }
        assertTrue(downgraded)
        assertTrue("a downgrade starts a fresh record", record.isEmpty())
        val (calm, down) = EchoUiTiers.recordQuality(List(9) { 2 }, 2)
        assertFalse("exactly 2 is not more than 2", down)
        assertEquals(10, calm.size)
        assertFalse(EchoUiTiers.recordQuality(listOf(9, 9, 9), 9).second)
    }
}
