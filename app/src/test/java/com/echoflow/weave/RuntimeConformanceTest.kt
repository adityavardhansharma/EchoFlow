package com.echoflow.weave

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.runtime.HiddenModel
import com.echoflow.weave.runtime.ModelLoop
import com.echoflow.weave.runtime.WeaveBlockController
import com.echoflow.weave.runtime.WeaveRuntime
import com.echoflow.weave.runtime.tiers.EchoUiModelTiers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 07 §6 runtime behaviour that runs without a screen: timers, frames, the model loop, plain text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimeConformanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val runtime = WeaveRuntime(db, Registry.names)

    @After fun close() { runtime.close(); db.close() }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }

    private fun block(src: String, key: Int = src.hashCode()): WeaveBlockController =
        runtime.controller(null, null, key).also { runBlocking { it.update(src, true, EvalContext()) }; idle() }

    private fun num(c: WeaveBlockController, state: String) = (c.session!!.state[state] as WNum).v

    private val SIM = "\$t = 0\nroot = Text(\$t)\non frame -> !set(\$t, \$t + dt)"

    @Test fun `T-451 an off-screen block's every effect doesn't fire, and resumes on screen`() {
        val c = block("\$n = 0\nroot = Text(\$n)\non every(1s) -> !inc(\$n)")
        c.appear()
        c.onScreen = false
        c.tick(5_000)
        assertEquals(0.0, num(c, "n"), 0.0)
        c.onScreen = true
        c.tick(1_000)
        assertEquals(1.0, num(c, "n"), 0.0)
    }

    @Test fun `T-452 only the most visible block runs frame effects`() {
        val a = block(SIM, 1)
        val b = block(SIM, 2)
        listOf(a, b).forEach { it.onScreen = true }
        a.visibleFraction = 0.4f
        b.visibleFraction = 0.9f
        assertSame(b, runtime.frameOwner())
        a.frame(16); b.frame(16)
        assertEquals(0.0, num(a, "t"), 0.0)
        assertTrue(num(b, "t") > 0.0)
        // scrolled: now `a` is the most visible
        b.visibleFraction = 0.1f
        assertSame(a, runtime.frameOwner())
        b.onScreen = false
        a.visibleFraction = 0f
        assertEquals(null, runtime.frameOwner())
    }

    @Test fun `T-453 three frames over 2 ms pause the simulation until tapped`() {
        val c = block(SIM)
        c.onScreen = true
        c.visibleFraction = 1f
        var clock = 0L
        c.nanoTime = { clock += 3_000_000; clock } // every frame-effect run appears to take 3 ms
        repeat(2) { c.frame(16) }
        assertFalse(c.framePaused)
        c.frame(16)
        assertTrue(c.framePaused)
        val t = num(c, "t")
        c.frame(16)
        assertEquals("paused frames don't run", t, num(c, "t"), 0.0)
        c.resumeFrames()
        c.nanoTime = { clock += 100_000; clock }
        c.frame(16)
        assertTrue(num(c, "t") > t)
        assertFalse(c.framePaused)
    }

    @Test fun `T-454 ask is latest-wins`() {
        val gates = ArrayList<CompletableDeferred<String>>()
        val model = object : HiddenModel {
            override suspend fun complete(systemPrompt: String, userText: String, timeoutMs: Long, onPartial: (String) -> Unit): String {
                val d = CompletableDeferred<String>(); gates += d; return d.await()
            }
            override val available = true
        }
        runtime.modelLoop = ModelLoop(runtime, model, emptyMap(), { false }, { 3 })
        val c = block("note = Text(\"start\")\nroot = Column [note, Button(\"Go\", onTap: !ask(\"rewrite\", [note]))]")
        val tap = c.root()!!.children[1].args["onTap"]!!
        c.fire(tap); idle()
        Thread.sleep(100)
        c.fire(tap); idle()
        assertEquals(2, gates.size)
        gates[1].complete("```weave edit b1\nnote = Text(\"second\")\n```"); idle()
        gates[0].complete("```weave edit b1\nnote = Text(\"first\")\n```"); idle()
        assertEquals("second", ((c.binding("note") as WNode).args["content"] as WStr).v)
    }

    @Test fun `T-462 blocks keep rendering and working after the level goes Off`() {
        val c = block("\$n = 0\nroot = Button(\"Add\", onTap: !inc(\$n))")
        // Off only changes what the next prompt asks for (05 §11.4); the runtime never reads it
        assertEquals(0, EchoUiModelTiers.plan(IntelligentUiLevel.OFF, 3, "hello").tier)
        assertNotNull(c.root())
        c.fire(c.root()!!.args["onTap"]!!)
        assertEquals(1.0, num(c, "n"), 0.0)
    }

    @Test fun `T-465 reading a binding off the main thread throws in debug builds`() {
        val c = block("x = 1\nroot = Text(x)")
        assertNotNull(c.binding("x"))
        val error = runBlocking { withContext(Dispatchers.Default) { runCatching { c.binding("x") }.exceptionOrNull() } }
        assertTrue("got $error", error is IllegalStateException)
    }

    @Test fun `T-466 show as plain text and back keeps state`() {
        val c = block("\$n = 2\nroot = Column [Text(\"Count {\$n}\"), Button(\"Add\", onTap: !inc(\$n))]")
        c.fire(c.root()!!.children[1].args["onTap"]!!)
        c.showPlain(true)
        assertTrue(c.plain)
        assertTrue(c.plainText(), c.plainText().contains("Count 3"))
        c.showPlain(false)
        assertFalse(c.plain)
        assertEquals(3.0, num(c, "n"), 0.0)
    }
}
