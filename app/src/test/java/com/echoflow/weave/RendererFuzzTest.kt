package com.echoflow.weave

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.ui.components.MarkdownExtensionBlock
import com.echoflow.weave.compose.WeaveBlock
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.check.TypeSpec
import com.echoflow.weave.runtime.WeaveRuntime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * 07 §6: every registered component renders with random arguments without crashing, and the
 * prompt examples render end to end. (Markdown and Code use the ARM-only native parser.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RendererFuzzTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val runtime = WeaveRuntime(db, Registry.names)

    @After fun close() { runtime.close(); db.close() }

    private val values = listOf(
        "0", "-3", "1.5", "1e9", "\"\"", "\"text\"", "\"**bold** and [link](https://example.com)\"", "true", "false", "null",
        "[]", "[1, 2, 3]", "[\"a\", \"b\"]", "[{x: \"a\", y: 3}, {x: \"b\", y: -1}]", "{a: 1}", "m", "xl", "primary", "\$s", "\$n", "\$l",
        "!inc(\$n)", "!toggle(\$b)", "x => x", "Text(\"child\")",
    )

    private var current by androidx.compose.runtime.mutableStateOf<Pair<Int, String>?>(null)
    private var started = false

    private fun render(src: String, key: Int) {
        if (!started) {
            started = true
            compose.setContent {
                current?.let { (k, body) ->
                    androidx.compose.runtime.key(k) { WeaveBlock(runtime.controller(null, null, k), MarkdownExtensionBlock(k, "weave", "weave", body, true)) }
                }
            }
        }
        compose.runOnIdle { current = key to src }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test fun `T-401 every registered component survives random arguments`() {
        val r = Random(7)
        val skip = setOf("Markdown", "Code")
        val parts = ArrayList<String>()
        for (name in Registry.names.sorted()) {
            if (name in skip) continue
            val spec = BuiltinCatalog.component(name) ?: continue
            // 3 sets per component on every build, 200 on a release run (07 preamble)
            repeat(System.getProperty("weave.fuzzSets")?.toIntOrNull() ?: 3) {
                val args = spec.allParams.filter { r.nextBoolean() || it.required }.joinToString(", ") { p ->
                    "${p.name}: ${if (p.type == TypeSpec.STATE) listOf("\$s", "\$n", "\$l", "\$b").random(r) else values.random(r)}"
                }
                parts += "$name($args)"
            }
        }
        val header = "\$s = \"a\"\n\$n = 1\n\$l = [1, 2]\n\$b = false\n"
        parts.chunked(12).forEachIndexed { i, chunk -> render(header + "root = Column [\n  " + chunk.joinToString(",\n  ") + "\n]", 100 + i) }
        assertTrue(parts.isNotEmpty())
    }

    @Test fun `a tip splitter renders and responds`() {
        val src = """
            @weave 1 id: tip title: "Tip splitter" size: card
            ${'$'}bill = 1200
            ${'$'}people = 3
            ${'$'}tip = 10
            each = ${'$'}bill * (1 + ${'$'}tip / 100) / ${'$'}people
            root = Column(gap: m) [
              Slider(${'$'}bill, 0, 5000, 50, label: "Bill", format: "inr"),
              Segmented(${'$'}tip, [5, 10, 15, 20], suffix: "%", label: "Tip"),
              Stepper(${'$'}people, min: 1, max: 20, label: "People"),
              Metric("Each pays", each, format: "inr"),
              BarChart([{who: "Bill", v: ${'$'}bill}, {who: "Tip", v: ${'$'}bill * ${'$'}tip / 100}], x: "who", y: "v"),
              Table([{n: "A", v: 1}, {n: "B", v: 2}], totals: ["v"]),
              Tabs(${'$'}people, ["One", "Two"]) [Text("one"), Text("two")]
            ]
        """.trimIndent()
        render(src, 1)
        compose.onNodeWithTextSafe("Each pays")
    }

    /** Parsing runs off the main thread, so wait (bounded) for the block to appear. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTextSafe(t: String) {
        val present = { onAllNodes(androidx.compose.ui.test.hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        runCatching { waitUntil(15_000) { mainClock.advanceTimeBy(100); present() } }
        assertTrue("'$t' not on screen", present())
    }
}
