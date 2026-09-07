package com.echoflow.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.echoflow.data.OpenRouterConnection
import com.echoflow.ui.OpenRouterAuthState
import com.echoflow.ui.theme.EchoFlowTheme
import com.echoflow.ui.theme.Spacing
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class OpenRouterConnectionCardTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(connection: OpenRouterConnection = OpenRouterConnection(), dark: Boolean = false,
        auth: OpenRouterAuthState = OpenRouterAuthState(), onSignIn: () -> Unit = {}, onSave: (String) -> Unit = {},
        fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            EchoFlowTheme(darkTheme = dark) {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.base)) {
                        PageSection("Connection", "Your OpenRouter account, models and credits")
                        OpenRouterConnectionCard(connection, auth, onSignIn, {}, onSave, {}, {})
                    }
                }
            }
            }
        }
    }

    @Test fun new_user_light() {
        var signIns = 0
        show(onSignIn = { signIns++ })
        composeRule.onNodeWithText("Sign in with OpenRouter").performClick()
        assertEquals(1, signIns)
        composeRule.onRoot().captureRoboImage("build/outputs/openrouter/new-user-light.png")
    }

    @Test fun existing_key_dark_requires_confirmation() {
        var signIns = 0
        show(OpenRouterConnection(connected = true, keyEnding = "a1b2"), dark = true, onSignIn = { signIns++ })
        composeRule.onNodeWithText("Connected with API key").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/outputs/openrouter/existing-key-dark.png")
        composeRule.onNodeWithText("Sign in with OpenRouter").performClick()
        assertEquals(0, signIns)
        composeRule.onNodeWithText("Continue").performClick()
        assertEquals(1, signIns)
    }

    @Test fun signed_in_with_restore_option() {
        show(OpenRouterConnection(true, true, "c3d4", true))
        composeRule.onNodeWithText("Use saved manual key").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/outputs/openrouter/signed-in-light.png")
    }

    @Test fun manual_entry_is_secondary_and_validates_before_save() {
        var saved = ""
        show(onSave = { saved = it })
        // Drive the text-field/IME animation explicitly under Robolectric.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Or add API key manually").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Save key").assertIsNotEnabled()
        composeRule.onNodeWithText("OpenRouter API key").performTextInput("sk-or-v1-manual-test-key")
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Save key").performClick()
        assertEquals("sk-or-v1-manual-test-key", saved)
    }

    @Test fun failed_sign_in_keeps_existing_connection_visible() {
        show(OpenRouterConnection(true, false, "a1b2"), auth = OpenRouterAuthState(
            message = "Couldn’t finish sign-in. Your saved connection hasn’t changed.", error = true))
        composeRule.onNodeWithText("Connected with API key").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/outputs/openrouter/sign-in-error.png")
    }

    @Test @Config(qualifiers = "w320dp-h800dp-xhdpi", sdk = [36])
    fun narrow_screen_large_text() {
        show(OpenRouterConnection(true, true, "c3d4", true), dark = true, fontScale = 1.5f)
        composeRule.onNodeWithText("Switch OpenRouter account").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/outputs/openrouter/large-text-dark.png")
    }
}
