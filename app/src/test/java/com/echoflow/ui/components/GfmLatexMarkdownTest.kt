package com.echoflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GfmLatexMarkdownTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun gfmEmphasisAndLatexRenderInTheSameDocument() {
        compose.setContent {
            EchoFlowTheme {
                MarkdownText(
                    "1. _Blade Runner 2049_ — **Problem:** evaluate ${'$'}\\frac{8}{2}${'$'}.\n\n" +
                        "Tonight: _Dune: Part Two_.",
                    Modifier.fillMaxWidth(),
                )
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Blade Runner 2049", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Blade Runner 2049", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Problem:", substring = true).fetchSemanticsNode()
        compose.onNodeWithText("Dune: Part Two", substring = true).fetchSemanticsNode()
        compose.onAllNodesWithText("_Blade", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("_Dune", substring = true).assertCountEquals(0)
    }

    @Test
    fun safeGfmHtmlExtensionsRenderWithoutLiteralTags() {
        compose.setContent {
            EchoFlowTheme {
                MarkdownText("<u>Underlined</u> <mark>Marked</mark> H<sub>2</sub>O x<sup>2</sup>")
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Underlined", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val underlined = compose.onNodeWithText("Underlined", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single()
        val combined = compose.onNodeWithText("H2O", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single()
        compose.onAllNodesWithText("<u>", substring = true).assertCountEquals(0)
        assertTrue(underlined.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
        assertTrue(combined.spanStyles.any { it.item.baselineShift == BaselineShift.Subscript })
    }
}
