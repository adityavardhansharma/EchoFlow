package com.echoflow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeneratedImageActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun copyDownloadAndShareStayOnTheSameRow() {
        composeRule.setContent {
            EchoFlowTheme {
                GeneratedImageActions(onCopy = {}, onDownload = {}, onShare = {})
            }
        }

        val copy = composeRule.onNodeWithContentDescription("Copy").assertHasClickAction()
        val download = composeRule.onNodeWithContentDescription("Save").assertHasClickAction()
        val share = composeRule.onNodeWithContentDescription("Share").assertHasClickAction()

        val copyTop = copy.fetchSemanticsNode().boundsInRoot.top
        assertEquals(copyTop, download.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(copyTop, share.fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test
    fun aScreensOwnActionJoinsTheBarRatherThanFloatingBesideIt() {
        // The whole point of the shared bar: an extra action must sit inside the same container
        // and on the same line, not append itself as a detached button.
        composeRule.setContent {
            EchoFlowTheme {
                GeneratedImageActions(
                    onCopy = null,
                    onDownload = {},
                    onShare = {},
                    extra = listOf(MediaAction(Icons.Default.AddPhotoAlternate, "Reference") {}),
                )
            }
        }

        val save = composeRule.onNodeWithContentDescription("Save").assertHasClickAction()
        val reference = composeRule.onNodeWithContentDescription("Reference").assertHasClickAction()

        assertEquals(
            save.fetchSemanticsNode().boundsInRoot.top,
            reference.fetchSemanticsNode().boundsInRoot.top,
            0.5f,
        )
    }
}
