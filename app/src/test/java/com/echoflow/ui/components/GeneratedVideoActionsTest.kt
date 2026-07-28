package com.echoflow.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.echoflow.data.GeneratedVideo
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeneratedVideoActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyVideoActionStaysOnTheSameRow() {
        composeRule.setContent {
            EchoFlowTheme {
                GeneratedVideoActions(
                    onCopy = {},
                    onFullscreen = {},
                    onDownload = {},
                    onShare = {},
                )
            }
        }

        val copy = composeRule.onNodeWithContentDescription("Copy").assertHasClickAction()
        val fullscreen = composeRule.onNodeWithContentDescription("Full screen").assertHasClickAction()
        val download = composeRule.onNodeWithContentDescription("Save").assertHasClickAction()
        val share = composeRule.onNodeWithContentDescription("Share").assertHasClickAction()

        val copyTop = copy.fetchSemanticsNode().boundsInRoot.top
        assertEquals(copyTop, fullscreen.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(copyTop, download.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(copyTop, share.fetchSemanticsNode().boundsInRoot.top, 0.5f)
    }

    @Test
    fun everyWaitingStateHasItsOwnWording() {
        // Rendering runs for minutes, so a generic "Working…" for every state would leave the
        // user unable to tell a stuck submit from a download in progress.
        val labels = listOf(
            GeneratedVideo.STATUS_QUEUED,
            GeneratedVideo.STATUS_PENDING,
            GeneratedVideo.STATUS_IN_PROGRESS,
            GeneratedVideo.STATUS_DOWNLOADING,
        ).map(::videoStatusLabel)

        assertEquals(labels.size, labels.distinct().size)
        assertTrue(labels.none { it == videoStatusLabel("something-new") })
    }
}
