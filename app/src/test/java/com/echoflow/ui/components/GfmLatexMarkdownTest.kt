package com.echoflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
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

    @Test
    fun mixedGfmAndLatexDarkScreenshot() {
        compose.setContent {
            EchoFlowTheme(darkTheme = true, themeName = "lavender") {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    MarkdownText(
                        text = """
                            # Sci-fi movies as calculus problems

                            1. _Blade Runner 2049_ — **IMDb: 8.0/10**

                            Problem: evaluate ${'$'}\lim_{x \to 0}\frac{\sin(8x)}{x}${'$'}.

                            ${'$'}${'$'}
                            \int_0^{7.9} 1\,dx
                            ${'$'}${'$'}

                            - GitHub-style lists and **bold text**
                            - <u>Underline</u>, <mark>highlight</mark>, H<sub>2</sub>O
                        """.trimIndent(),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Blade Runner 2049", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/gfm_latex_dark.png")
    }
}
