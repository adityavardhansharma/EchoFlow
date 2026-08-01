package com.echoflow.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.echoflow.data.ChatThread
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
class DrawerThreadListTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val threads = listOf(
        ChatThread(id = "a", title = "Alpha chat", createdAt = 100, updatedAt = 300),
        ChatThread(id = "b", title = "Beta chat", createdAt = 100, updatedAt = 200, pinnedAt = 500),
    )

    @Test
    fun longPressShowsPinRenameDeleteOnUnpinnedChat() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = null,
                    renderingChatIds = emptySet(),
                    onThreadSelected = {},
                    onPin = {},
                    onUnpin = {},
                    onRename = {},
                    onDelete = {},
                    grouped = false,
                )
            }
        }

        composeRule.onNodeWithText("Alpha chat").performTouchInput { longClick() }
        composeRule.onNodeWithText("Pin").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Unpin").assertDoesNotExist()
    }

    @Test
    fun longPressShowsUnpinOnPinnedChat() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = null,
                    renderingChatIds = emptySet(),
                    onThreadSelected = {},
                    onPin = {},
                    onUnpin = {},
                    onRename = {},
                    onDelete = {},
                    grouped = false,
                )
            }
        }

        composeRule.onNodeWithText("Beta chat").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unpin").assertIsDisplayed()
        composeRule.onNodeWithText("Pin").assertDoesNotExist()
    }

    @Test
    fun pinnedChatsAppearInPinnedSectionFirst() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = null,
                    renderingChatIds = emptySet(),
                    onThreadSelected = {},
                    onPin = {},
                    onUnpin = {},
                    onRename = {},
                    onDelete = {},
                    grouped = true,
                )
            }
        }

        composeRule.onNodeWithText("Pinned").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pinned").assertIsDisplayed()
    }

    @Test
    fun pinActionFromMenuCallsHandler() {
        var pinnedId: String? = null
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = listOf(threads[0]),
                    currentThreadId = null,
                    renderingChatIds = emptySet(),
                    onThreadSelected = {},
                    onPin = { pinnedId = it.id },
                    onUnpin = {},
                    onRename = {},
                    onDelete = {},
                    grouped = false,
                )
            }
        }

        composeRule.onNodeWithText("Alpha chat").performTouchInput { longClick() }
        composeRule.onNodeWithText("Pin").performClick()
        assertEquals("a", pinnedId)
    }

    @Test
    fun selectedChatDoesNotShowInlineEditDeleteButtons() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = listOf(threads[0]),
                    currentThreadId = "a",
                    renderingChatIds = emptySet(),
                    onThreadSelected = {},
                    onPin = {},
                    onUnpin = {},
                    onRename = {},
                    onDelete = {},
                    grouped = false,
                )
            }
        }

        // Inline rename/delete icon buttons were removed; actions live in the long-press menu.
        composeRule.onNodeWithContentDescription("Rename").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }
}
