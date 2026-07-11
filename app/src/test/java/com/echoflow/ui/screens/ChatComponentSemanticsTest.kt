package com.echoflow.ui.screens

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatComponentSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingSendButtonExposesStopActionAndCallback() {
        var stops = 0
        composeRule.setContent {
            EchoFlowTheme {
                SendButton(
                    enabled = true,
                    isStreaming = true,
                    onStop = { stops++ },
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Stop generating")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, stops)
    }

    @Test
    fun normalButtonExposesSendAction() {
        composeRule.setContent {
            EchoFlowTheme {
                SendButton(enabled = true, isStreaming = false, research = false, onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    @Test
    fun researchButtonExposesStartAction() {
        composeRule.setContent {
            EchoFlowTheme {
                SendButton(enabled = true, isStreaming = false, research = true, onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Start research").assertIsEnabled()
    }
}
