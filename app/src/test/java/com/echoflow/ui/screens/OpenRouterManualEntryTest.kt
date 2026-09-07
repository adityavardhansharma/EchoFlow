package com.echoflow.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.echoflow.data.OpenRouterConnection
import com.echoflow.ui.OpenRouterAuthState
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behavioral tests run independently from native screenshot rendering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenRouterManualEntryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `manual key validates retains draft on failure and closes only after success`() {
        val auth = mutableStateOf(OpenRouterAuthState())
        var saved = ""
        var completeSave: () -> Unit = {}
        var attempts = 0
        composeRule.setContent {
            EchoFlowTheme {
                OpenRouterConnectionCard(OpenRouterConnection(), auth.value, {}, {}, { key, done ->
                    attempts++
                    saved = key
                    completeSave = done
                    auth.value = OpenRouterAuthState(busy = true)
                }, {}, {})
            }
        }
        composeRule.onNodeWithText("Or add API key manually").performClick()
        composeRule.onNodeWithText("Save key").assertIsNotEnabled()
        composeRule.onNodeWithText("OpenRouter API key").performTextInput("invalid")
        composeRule.onNodeWithText("Save key").assertIsNotEnabled()
        val draft = "sk-or-v1-manual-test-key"
        composeRule.onNodeWithText("OpenRouter API key").performTextReplacement(draft)
        composeRule.onNodeWithText("Save key").assertIsEnabled().performClick()
        assertEquals(draft, saved)
        composeRule.onNodeWithText("Saving…").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        assertEquals(1, attempts)

        composeRule.runOnIdle { auth.value = OpenRouterAuthState(message = "Couldn’t save. Please retry.", error = true) }
        composeRule.onNodeWithText("OpenRouter API key").assertExists()
        composeRule.onNodeWithText("Save key").assertIsEnabled().performClick()
        assertEquals(draft, saved)
        assertEquals(2, attempts)
        composeRule.runOnIdle { auth.value = OpenRouterAuthState(); completeSave() }
        composeRule.onNodeWithText("OpenRouter API key").assertDoesNotExist()
    }
}
