package com.echoflow.weave

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.runtime.HiddenModel
import com.echoflow.weave.runtime.ModelLoop
import com.echoflow.weave.runtime.WeaveRuntime
import com.echoflow.weave.runtime.WeaveTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 05 §8 end to end with a fake model: !ask, lazy, query + consent, and the hourly budget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelLoopTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val runtime = WeaveRuntime(db, Registry.names)
    private val requests = ArrayList<String>()
    private var reply: (String) -> String = { "" }

    private val model = object : HiddenModel {
        override suspend fun complete(systemPrompt: String, userText: String, timeoutMs: Long, onPartial: (String) -> Unit): String {
            requests += userText; return reply(userText)
        }
        override val available = true
    }
    private val tool = object : WeaveTool {
        override val name = "web.search"
        override fun consentText(args: WValue) = "search"
        override suspend fun run(args: WValue): WValue = WStr("result")
    }
    init { runtime.modelLoop = ModelLoop(runtime, model, mapOf("web.search" to tool), { false }, { 3 }) }

    @After fun close() { runtime.close(); db.close() }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }

    private fun block(src: String) = runtime.controller(null, null, src.hashCode()).also { runBlocking { it.update(src, true, EvalContext()) }; idle() }

    @Test fun `T-455 ask rewrites only the named parts`() {
        reply = { "```weave edit b1\nnote = Text(\"shorter\")\ntip = 99\n```" }
        val c = block("tip = 10\nnote = Text(\"long text\")\nroot = Column [note, Button(\"Shorten\", onTap: !ask(\"shorter\", [note]))]")
        val button = c.root()!!.children[1]
        c.fire(button.args["onTap"]!!)
        idle()
        assertEquals(1, requests.size)
        assertTrue(requests[0].contains("new definitions for only these parts: note"))
        assertEquals("shorter", ((c.binding("note") as WNode).args["content"] as WStr).v)
        assertEquals(10.0, (c.binding("tip") as com.echoflow.weave.core.eval.WNum).v, 0.0)
        assertEquals(2, c.blockVersion)
        assertEquals(listOf("shorter" to listOf("note")), c.askLog)
    }

    @Test fun `T-457 lazy parts generate once and render`() {
        reply = { "```weave\nroot = Text(\"Day two: the fort\")\n```" }
        val c = block("root = Column [Text(\"Trip\"), day2]\nday2 = lazy(\"plan day two\", city: \"Jaipur\")")
        val lazyNode = c.root()!!.children[1]
        assertEquals("Lazy", lazyNode.type)
        c.lazyNode("day2", lazyNode.args["prompt"]!!, lazyNode.args["context"] as WRec)
        idle()
        val shown = c.lazyNode("day2", lazyNode.args["prompt"]!!, lazyNode.args["context"] as WRec)
        assertNotNull(shown)
        assertEquals("Day two: the fort", (shown!!.args["content"] as WStr).v)
        assertEquals(1, requests.size)
    }

    @Test fun `T-458 queries wait for consent, then run`() {
        val c = block("q = query(\"web.search\", {query: \"rate\"})\nroot = Text(\"{q.value}\")")
        c.root()
        idle()
        val req = c.consentRequest
        assertNotNull(req)
        req!!.answer(true, false)
        idle(); Thread.sleep(100); idle()
        assertEquals("result", ((c.binding("q") as WRec)["value"] as WStr).v)
    }

    @Test fun `T-456 21st hidden request in an hour shows the budget banner`() {
        reply = { "```weave edit b1\nnote = Text(\"x\")\n```" }
        val c = block("note = Text(\"a\")\nroot = Column [note, Button(\"Go\", onTap: !ask(\"again\", [note]))]")
        repeat(21) { c.fire(c.root()!!.children[1].args["onTap"]!!); idle() }
        assertEquals(20, requests.size)
        assertTrue(c.budgetBanner)
    }

}
