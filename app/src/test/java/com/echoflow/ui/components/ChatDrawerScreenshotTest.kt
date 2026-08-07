package com.echoflow.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.echoflow.data.AppMode
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
class ChatDrawerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val threads = listOf(
        ChatThread(id = "today", title = "Trip ideas", createdAt = 100, updatedAt = 400),
        ChatThread(id = "older", title = "Recipe notes", createdAt = 100, updatedAt = 200),
    )

    @Test
    fun drawer_footer_chat_mode() {
        composeRule.setContent {
            EchoFlowTheme {
                ChatDrawerContent(
                    mode = AppMode.Chat,
                    allThreads = threads,
                    currentThreadId = "today",
                    renderingChatIds = emptySet(),
                    otherModeMatchCount = 0,
                    onThreadSelected = {},
                    onNewChatClicked = {},
                    onDeleteThread = {},
                    onRenameThread = { _, _ -> },
                    onPinThread = {},
                    onUnpinThread = {},
                    onSettingsClicked = {},
                    searchQuery = "",
                    onSearchQueryChange = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_footer_chat.png",
        )
    }

    @Test
    fun drawer_footer_imagine_mode() {
        composeRule.setContent {
            EchoFlowTheme {
                ChatDrawerContent(
                    mode = AppMode.Imagine,
                    allThreads = threads,
                    currentThreadId = "today",
                    renderingChatIds = emptySet(),
                    otherModeMatchCount = 0,
                    onThreadSelected = {},
                    onNewChatClicked = {},
                    onDeleteThread = {},
                    onRenameThread = { _, _ -> },
                    onPinThread = {},
                    onUnpinThread = {},
                    onSettingsClicked = {},
                    searchQuery = "",
                    onSearchQueryChange = {},
                )
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_footer_imagine.png",
        )
    }

    @Test
    fun drawer_footer_large_font_scale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                EchoFlowTheme {
                    ChatDrawerContent(
                        mode = AppMode.Chat,
                        allThreads = threads,
                        currentThreadId = "today",
                        renderingChatIds = emptySet(),
                        otherModeMatchCount = 0,
                        onThreadSelected = {},
                        onNewChatClicked = {},
                        onDeleteThread = {},
                        onRenameThread = { _, _ -> },
                        onPinThread = {},
                        onUnpinThread = {},
                        onSettingsClicked = {},
                        searchQuery = "",
                        onSearchQueryChange = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/drawer_footer_large_font.png",
        )
    }
}
