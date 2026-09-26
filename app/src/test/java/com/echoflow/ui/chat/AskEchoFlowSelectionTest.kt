package com.echoflow.ui.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AskEchoFlowSelectionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun `ask echoflow is off until the beta toggle turns it on`() {
        assertFalse(AskEchoFlowSelection.isEnabled(app))
        AskEchoFlowSelection.setEnabled(app, true)
        assertTrue(AskEchoFlowSelection.isEnabled(app))
        AskEchoFlowSelection.setEnabled(app, false)
        assertFalse(AskEchoFlowSelection.isEnabled(app))
    }
}
