package com.echoflow.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chunk layout around the native view. The native parser ships ARM-only, so chunks are drawn
 * here by a probe instead of Enriched Markdown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StreamingMarkdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Stable
    private class Probe {
        val compositions = mutableMapOf<String, Int>()
        val latest = mutableMapOf<String, MarkdownChunkSpec>()
    }

    @Composable
    private fun ChunkProbe(spec: MarkdownChunkSpec, probe: Probe) {
        val name = spec.source.substringBefore(' ')
        SideEffect {
            probe.compositions[name] = (probe.compositions[name] ?: 0) + 1
            probe.latest[name] = spec
        }
        Text(spec.source, Modifier)
    }

    @Test
    fun frozenChunksAreNotRedrawnWhileTheTailStreams() {
        val first = "first " + List(250) { "word$it" }.joinToString(" ")
        val tail = "\n\nsecond paragraph with **bold**, \$x^2\$, and a [link](https://example.test)."
        val probe = Probe()
        var text by mutableStateOf("")
        composeRule.setContent {
            StreamingMarkdown(text = text, streaming = true) { spec -> ChunkProbe(spec, probe) }
        }
        val reply = first + tail
        for (end in 1..reply.length step 7) {
            text = reply.take(end)
            composeRule.waitForIdle()
        }
        val frozenRedraws = probe.compositions.getValue("first")
        text = reply
        composeRule.waitForIdle()
        text = "$reply More."
        composeRule.waitForIdle()

        assertEquals(frozenRedraws, probe.compositions.getValue("first"))
        assertFalse(probe.latest.getValue("first").streamingTail)
        assertTrue(probe.latest.getValue("second").streamingTail)
        composeRule.onNodeWithText("$tail More.".trim()).assertExists()
    }

    @Test
    fun registeredFencesReachTheirExtensionAsTheyGrow() {
        val seen = mutableListOf<MarkdownExtensionBlock>()
        val chart = object : MarkdownBlockExtension {
            @Composable
            override fun Render(block: MarkdownExtensionBlock, modifier: Modifier) {
                SideEffect { seen += block }
                Text("chart:${block.body}", modifier)
            }
        }
        var text by mutableStateOf("Intro\n\n```chart\n{\"a\":")
        composeRule.setContent {
            CompositionLocalProvider(LocalMarkdownBlockExtensions provides mapOf("chart" to chart)) {
                StreamingMarkdown(text = text, streaming = true) { spec -> Text(spec.source) }
            }
        }
        composeRule.onNodeWithText("chart:{\"a\":").assertExists()
        text = "Intro\n\n```chart\n{\"a\":1}\n```\n\nAfter"
        composeRule.waitForIdle()

        composeRule.onNodeWithText("chart:{\"a\":1}").assertExists()
        composeRule.onNodeWithText("Intro").assertExists()
        composeRule.onNodeWithText("After").assertExists()
        assertFalse(seen.first().complete)
        assertTrue(seen.last().complete)
    }
}
