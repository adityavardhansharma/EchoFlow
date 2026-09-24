package com.echoflow.ui.screens.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.echoflow.data.ScheduleDays
import com.echoflow.data.ScheduleDraft
import com.echoflow.data.ScheduleTask
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the schedule surfaces that live in the main window (sheets are separate windows and are
 * verified on a device). Output goes to docs/screenshots/schedules for the PR description.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ScheduleScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val out = "../docs/screenshots/schedules"

    private val draft = ScheduleDraft(
        title = "Morning brief", instruction = "Give me a two-minute brief of the most important tech news, five bullets.",
        unit = ScheduleTask.WEEK, hour = 7, minute = 30, weekdays = ScheduleDays.WEEKDAYS, endDate = "2026-10-20",
        modelId = "openai/gpt-4o",
    )

    private fun capture(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            EchoFlowTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.surface) { content() } }
        }
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun marks() = capture("marks", dark = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScheduleMark(size = 20.dp)
                ScheduleMark(size = 44.dp, container = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                ScheduleMark(size = 44.dp, container = MaterialTheme.colorScheme.tertiaryContainer,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }

    @Test fun crownWheels() = capture("crown-wheels", dark = true) {
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrownWheel((1..12).map(Int::toString), 6, {}, "Hour", cyclic = true)
            CrownWheel((0..59).map { "%02d".format(it) }, 30, {}, "Minute", cyclic = true)
            CrownWheel(listOf("AM", "PM"), 0, {}, "AM or PM")
        }
    }

    @Test fun scheduleCardWithUnsavedChanges() = capture("card-unsaved", dark = false) {
        ScheduleCard(draft, isNew = false, dirty = true, problem = null, status = ScheduleTask.ACTIVE, use24h = false,
            onOpen = {}, onSave = {}, onDiscard = {}, onResume = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp))
    }

    @Test fun scheduleCardPaused() = capture("card-paused", dark = true) {
        ScheduleCard(draft, isNew = false, dirty = false, problem = null, status = ScheduleTask.PAUSED, use24h = false,
            onOpen = {}, onSave = {}, onDiscard = {}, onResume = {},
            modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}
