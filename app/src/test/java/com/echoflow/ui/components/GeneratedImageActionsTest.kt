package com.echoflow.ui.components

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
                GeneratedImageActions(
                    onCopy = {},
                    onDownload = {},
                    onShare = {},
                )
            }
        }

        val copy = composeRule.onNodeWithContentDescription("Copy").assertHasClickAction()
        val download = composeRule.onNodeWithContentDescription("Save to gallery").assertHasClickAction()
        val share = composeRule.onNodeWithContentDescription("Share").assertHasClickAction()

        val copyTop = copy.fetchSemanticsNode().boundsInRoot.top
        assertEquals(copyTop, download.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(copyTop, share.fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }
}
