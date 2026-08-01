package com.echoflow.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.echoflow.data.ChatThread
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DrawerThreadListScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val threads = listOf(
        ChatThread(id = "today", title = "Trip ideas", createdAt = 100, updatedAt = 400),
        ChatThread(id = "pinned", title = "Weekly standup", createdAt = 100, updatedAt = 300, pinnedAt = 900),
        ChatThread(id = "older", title = "Recipe notes", createdAt = 100, updatedAt = 200),
    )

    @Test
    fun drawer_with_pinned_section() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = "today",
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

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_pinned_section.png",
        )
    }

    @Test
    fun drawer_long_press_menu() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = "today",
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

        composeRule.onNodeWithText("Trip ideas").performTouchInput { longClick() }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_long_press_menu.png",
        )
    }

    @Test
    fun drawer_pinned_long_press_unpin_menu() {
        composeRule.setContent {
            EchoFlowTheme {
                DrawerThreadList(
                    threads = threads,
                    currentThreadId = "today",
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

        composeRule.onNodeWithText("Weekly standup").performTouchInput { longClick() }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_pinned_unpin_menu.png",
        )
    }
}
