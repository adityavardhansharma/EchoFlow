package com.echoflow.weave

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.ui.components.LocalMarkdownExtensionOrdinal
import com.echoflow.ui.components.MarkdownExtensionBlock
import com.echoflow.ui.theme.Spacing
import com.echoflow.weave.compose.WeaveBlock
import com.echoflow.weave.compose.WeaveBlockExtension
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.runtime.WeaveRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 07 §5: rendering checks that need Compose (popups and the theme matrix are elsewhere, T-408/T-410). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ComposeConformanceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val runtime = WeaveRuntime(db, Registry.names)

    @After fun close() { runtime.close(); db.close() }

    private fun show(src: String, key: Int = 1, width: Dp = 360.dp) {
        compose.setContent {
            Box(Modifier.width(width)) { WeaveBlock(runtime.controller(null, null, key), MarkdownExtensionBlock(key, "weave", "weave", src, true)) }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun state(key: Int, name: String) = runtime.controller(null, null, key).session!!.state[name]

    @Test fun `T-402 a tappable card clips its ripple and pads inside its border`() {
        show("root = Card(bg: primary, shape: xl, border: true, pad: l, onTap: !toast(\"x\")) [Text(\"inside\")]")
        // the card's tap target merges its text, so read the text unmerged
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val card = with(compose.density) { clickables.maxBy { it.boundsInRoot.width * it.boundsInRoot.height }.boundsInRoot.let { androidx.compose.ui.unit.DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) } }
        val text = compose.onNodeWithText("inside", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("padding l inside the card", text.left - card.left >= Spacing.base - 0.5.dp)
        assertTrue(text.top - card.top >= Spacing.base - 0.5.dp)
    }

    @Test fun `T-403 a row that doesn't fit becomes a column`() {
        val label = "A twenty-char label!"
        show("root = Row [" + (1..4).joinToString(", ") { "Button(\"$label\", onTap: !toast(\"$it\"))" } + "]")
        val tops = compose.onAllNodes(hasText(label)).fetchSemanticsNodes().map { it.boundsInRoot.top }
        assertEquals(4, tops.size)
        assertTrue("stacked: $tops", tops.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun `T-403b a row with a carousel renders`() {
        show("root = Row [Text(\"a\"), Carousel [Card [Text(\"one\")], Card [Text(\"two\")]]]")
        compose.onNodeWithText("a").assertExists()
        compose.onNodeWithText("one").assertExists()
    }

    @Test fun `T-404 a metric animates through intermediate values and ends exactly`() {
        show("\$v = 100\nroot = Column [Metric(\"Score\", \$v), Button(\"Up\", onTap: !set(\$v, 200))]")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Up").performClick()
        compose.mainClock.advanceTimeBy(48)
        // the drawn value animates; the spoken one is the target (04 §6, T-413)
        assertTrue("not yet 200", compose.onAllNodes(hasText("200"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNode(hasText("200"), useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Score: 200")).assertExists()
    }

    @Test fun `T-405 a 0_05 step slider writes no float noise`() {
        show("\$r = 8.5\nroot = Slider(\$r, min: 6, max: 14, step: 0.05)")
        for (target in listOf(8.549999f, 10.1f, 13.95f)) {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
            compose.waitForIdle()
            val v = (state(1, "r") as WNum).v
            assertTrue("$v has float noise", v.toString().substringAfter('.').length <= 2)
        }
        assertEquals(13.95, (state(1, "r") as WNum).v, 0.0)
    }

    @Test fun `T-406 tabs compose only the selected page`() {
        show("\$tab = 0\nroot = Tabs(\$tab, labels: [\"A\", \"B\"]) [Text(\"page one\"), Text(\"page two\")]")
        compose.onNodeWithText("page one").assertExists()
        compose.onNodeWithText("page two").assertDoesNotExist()
    }

    @Test fun `T-407 placeholders never shrink while a block streams`() {
        val lines = listOf(
            "root = Column [",
            "  title,",
            "  chart,",
            "  note",
            "]",
            "title = Title(\"Spending\")",
            "chart = BarChart([{x: \"a\", y: 1}, {x: \"b\", y: 3}], x: \"x\", y: \"y\")",
            "note = Text(\"Done\")",
        )
        var body by mutableStateOf("")
        compose.setContent {
            Box(Modifier.width(360.dp)) { WeaveBlock(runtime.controller(null, null, 7), MarkdownExtensionBlock(7, "weave", "weave", body, false)) }
        }
        var last = 0f
        for (i in 1..lines.size) {
            body = lines.take(i).joinToString("\n")
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(500)
            val h = compose.onRoot().getBoundsInRoot().let { (it.bottom - it.top).value }
            assertTrue("height went $last → $h at line $i", h >= last - 0.5f)
            last = h
        }
    }

    @Test fun `T-412 tapping a bar selects its record`() {
        show("\$sel = null\nroot = Chart([{x: \"a\", y: 1}, {x: \"b\", y: 2}], select: \$sel) [bar(x: \"x\", y: \"y\")]")
        compose.onNode(hasContentDescription("chart", substring = true, ignoreCase = true)).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.3f))
        }
        compose.waitForIdle()
        val sel = state(1, "sel") as? WRec
        assertEquals(WStr("b"), sel?.get("x"))
    }

    @Test fun `T-413 metrics speak their value and buttons are at least 48 dp`() {
        show("root = Column [Metric(\"Monthly EMI\", 43391, format: inr), Button(\"Go\", onTap: !toast(\"x\"))]")
        compose.onNode(hasContentDescription("Monthly EMI: ₹43,391")).assertExists()
        val touch = compose.onNodeWithText("Go").fetchSemanticsNode().touchBoundsInRoot
        assertTrue("touch height ${touch.height}px", touch.height >= with(compose.density) { 48.dp.toPx() } - 0.5f)
    }

    @Test fun `T-414 tokens resolve through the theme`() {
        val custom = Shapes(medium = RoundedCornerShape(3.dp), large = RoundedCornerShape(5.dp))
        var medium: Shape? = null
        var large: Shape? = null
        compose.setContent {
            MaterialTheme(shapes = custom) {
                medium = TokenResolver.shape(WStr("m"))
                large = TokenResolver.shape(WStr("l"))
            }
        }
        compose.waitForIdle()
        assertSame(custom.medium, medium)
        assertSame(custom.large, large)
        assertEquals(Spacing.base, TokenResolver.space(WStr("l"), 0.dp))
        assertEquals(Spacing.xl, TokenResolver.space(WStr("xl"), 0.dp))
    }

    @Test fun `T-461 a block past the reply's limit doesn't render as a block`() {
        val ext = WeaveBlockExtension(runtime, messageId = null, chatId = null, maxBlocks = 1)
        var ordinal by mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalMarkdownExtensionOrdinal provides ordinal) {
                androidx.compose.runtime.key(ordinal) {
                    ext.Render(MarkdownExtensionBlock(9 + ordinal, "weave", "weave", "root = Text(\"a block\")", true), Modifier)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("a block").assertExists()
        // the second block of a Balanced reply falls back to a code fence (its body, not the UI)
        ordinal = 1
        compose.waitForIdle()
        compose.onNodeWithText("a block").assertDoesNotExist()
    }
}
