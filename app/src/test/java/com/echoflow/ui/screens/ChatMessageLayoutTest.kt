package com.echoflow.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echoflow.data.ChatMessage
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatMessageLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun persistedTimelineStacksReasoningAboveAnswer() {
        val message = ChatMessage(
            id = "assistant-1",
            chatId = "chat-1",
            role = "assistant",
            content = "Visible answer",
            createdAt = 1L,
            segmentsJson = ToolEventJson.segmentsToJson(
                listOf(
                    PersistedSegment(type = "reasoning", text = "Hidden reasoning"),
                    PersistedSegment(type = "text", text = "Visible answer"),
                )
            ),
        )

        composeRule.setContent {
            EchoFlowTheme {
                MessageBubble(message = message, onCopy = {})
            }
        }

        val reasoningBounds = composeRule.onNodeWithText("Reasoning")
            .fetchSemanticsNode().boundsInRoot
        val answerBounds = composeRule.onNodeWithText("Visible answer")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Answer must be laid out below the reasoning section",
            answerBounds.top >= reasoningBounds.bottom,
        )
    }
}
