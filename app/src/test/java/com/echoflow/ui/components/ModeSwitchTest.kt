package com.echoflow.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.echoflow.data.AppMode
import com.echoflow.data.ImagineMedia
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModeSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothSurfacesAreAlwaysReachableAndLabelled() {
        // Imagine is a brand-new concept, so it must never hide behind an unfamiliar glyph:
        // both segments carry a label at all times, whichever one is active.
        var picked: AppMode? = null
        composeRule.setContent {
            EchoFlowTheme {
                ModeSwitch(
                    selected = AppMode.Chat,
                    onSelect = { picked = it },
                    renderingModes = emptySet(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Chat mode").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Imagine mode").assertHasClickAction().performClick()
        assertEquals(AppMode.Imagine, picked)
    }

    @Test
    fun tappingTheActiveSurfaceDoesNothing() {
        // One control, one meaning: re-selecting where you already are must not fire, or every
        // stray tap on the bar would re-park and restore the current conversation.
        var picked: AppMode? = null
        composeRule.setContent {
            EchoFlowTheme {
                ModeSwitch(
                    selected = AppMode.Imagine,
                    onSelect = { picked = it },
                    renderingModes = emptySet(),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Imagine mode").performClick()
        assertNull(picked)
    }

    @Test
    fun aRenderInTheOtherSurfaceIsAnnounced() {
        // The split's one real risk is hiding work. A clip rendering in Imagine has to be
        // visible — and legible to TalkBack — while the user is over in Chat.
        composeRule.setContent {
            EchoFlowTheme {
                ModeSwitch(
                    selected = AppMode.Chat,
                    onSelect = {},
                    renderingModes = setOf(AppMode.Imagine),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Imagine mode, video rendering").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Chat mode").assertHasClickAction()
    }

    @Test
    fun theMediaToggleSelectsWhatImagineIsMaking() {
        var picked: ImagineMedia? = null
        composeRule.setContent {
            EchoFlowTheme {
                MediaToggle(selected = ImagineMedia.Image, onSelect = { picked = it })
            }
        }

        composeRule.onNodeWithContentDescription("Video mode").performClick()
        assertEquals(ImagineMedia.Video, picked)

        // Same rule as the mode switch: selecting the current option is a no-op.
        picked = null
        composeRule.onNodeWithContentDescription("Image mode").performClick()
        assertNull(picked)
    }
}
