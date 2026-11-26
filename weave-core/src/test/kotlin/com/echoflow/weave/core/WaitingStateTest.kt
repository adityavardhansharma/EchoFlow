package com.echoflow.weave.core

import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.parse.IncrementalParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 03 §7.1 while a block streams: states wait for what they read; taps on waiting values are discarded. */
class WaitingStateTest {
    private val head = "\$out = 0\n\$rate = base(10)\nroot = Button(\"Copy\", onTap: !set(\$out, \$rate))\n"

    @Test fun `a state waits for a function that hasn't streamed in`() {
        val parser = IncrementalParser()
        val s = Session(parser.update(head, complete = false), W.ctx())
        assertTrue("rate" in s.waitingStates)
        assertEquals(WNum(0.0), s.state["out"])
        s.sync(parser.update(head + "fn base(n) = n * 2\n", complete = false))
        assertFalse("rate" in s.waitingStates)
        assertEquals(WNum(20.0), s.state["rate"])
    }

    @Test fun `T-279b a tap that reads a waiting value is discarded`() {
        val parser = IncrementalParser()
        val s = Session(parser.update(head, complete = false), W.ctx())
        val tap = s.root()!!.args["onTap"]!!
        s.fire(tap)
        assertEquals(WNum(0.0), s.state["out"])
        assertEquals(0, s.historySize)
        assertEquals(1, s.rejectedTaps)
        s.sync(parser.update(head + "fn base(n) = n * 2\n", complete = false))
        s.fire(s.root()!!.args["onTap"]!!)
        assertEquals(WNum(20.0), s.state["out"])
    }

    @Test fun `fence close initialises every state and drops undeclared ones`() {
        val parser = IncrementalParser()
        val s = Session(parser.update("\$a = 1\n\$b = nope(2)\nroot = Text(\$a)\n", complete = false), W.ctx())
        assertTrue("b" in s.waitingStates)
        s.sync(parser.update("\$b = nope(2)\nroot = Text(\$b)\n", complete = true))
        assertTrue(s.waitingStates.isEmpty())
        assertFalse("a" in s.state)
    }
}
