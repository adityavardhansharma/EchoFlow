package com.echoflow.ui.screens.schedules

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleDraft
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Calendar
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ScheduleEditorScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun review_wheels() {
        val zone = TimeZone.getTimeZone("UTC")
        val anchor = Calendar.getInstance(zone).apply {
            clear()
            set(2040, Calendar.JUNE, 3, 9, 0, 0)
        }.timeInMillis
        val sample = ScheduleTask(
            id = "sample", title = "Morning briefing",
            instruction = "Summarize the latest technology news",
            modelId = "openai/gpt-4o", status = ScheduleTask.ACTIVE,
            unit = ScheduleTask.DAY, interval = 2, anchorAt = anchor,
            zoneId = zone.id, nextRunAt = anchor, needsWeb = true,
        )
        var saved: ScheduleDraft? = null
        compose.setContent {
            EchoFlowTheme {
                ScheduleEditor(sample, sample.modelId,
                    models = listOf(sample.modelId to "GPT-4o"), localModels = emptyList(),
                    onBack = {}, onSave = { saved = it })
            }
        }
        compose.onNodeWithText("Repeat").performScrollTo()
        compose.onRoot().captureRoboImage("build/outputs/schedule-editor-wheels.png")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        assertEquals(2, saved?.interval)
        assertEquals(ScheduleTask.DAY, saved?.unit)
    }
}
