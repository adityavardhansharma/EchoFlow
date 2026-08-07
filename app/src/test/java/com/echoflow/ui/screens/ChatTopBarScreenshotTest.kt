package com.echoflow.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.echoflow.data.AppMode
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
class ChatTopBarScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun top_bar_chat_mode_menu_and_new_chat() {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(Modifier.fillMaxWidth()) {
                    ChatTopBar(
                        mode = AppMode.Chat,
                        onSelectMode = {},
                        renderingModes = emptySet(),
                        onMenu = {},
                        onNewChat = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/top_bar_chat.png",
        )
    }

    @Test
    fun top_bar_imagine_mode_menu_and_new_creation() {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(Modifier.fillMaxWidth()) {
                    ChatTopBar(
                        mode = AppMode.Imagine,
                        onSelectMode = {},
                        renderingModes = emptySet(),
                        onMenu = {},
                        onNewChat = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/top_bar_imagine.png",
        )
    }
}
