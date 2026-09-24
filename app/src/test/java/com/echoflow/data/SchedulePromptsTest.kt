package com.echoflow.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SchedulePromptsTest {
    private val task = ScheduleTask(
        id = "t", title = "Morning brief", instruction = "Summarize tech news", modelId = "m",
        status = ScheduleTask.ACTIVE, unit = ScheduleTask.DAY, interval = 1,
        anchorAt = 1_790_000_000_000L, zoneId = "UTC", nextRunAt = 1_790_000_000_000L,
    )

    @Test fun previewIsTheFirstPlainLine() {
        assertEquals("Top story: chips are back", SchedulePrompts.preview("## **Top story:** [chips](https://x.y) are back\n\nMore"))
        assertEquals("Drink water", SchedulePrompts.preview("\n- Drink water\n- Stretch"))
        assertTrue(SchedulePrompts.preview("x".repeat(400)).endsWith("…"))
    }

    @Test fun runPromptCarriesContinuityAndTheRightWebRules() {
        val base = SchedulePrompts.RunContext(task, task.anchorAt, 4, false, listOf(task.anchorAt - 86_400_000L to "Yesterday's brief"),
            ScheduleWebAccess.None, use24h = false, locale = Locale.US)
        val offline = SchedulePrompts.run(base)
        assertTrue(offline.contains("Yesterday's brief"))
        assertTrue(offline.contains("Run #4"))
        assertTrue(offline.contains("no web access"))
        assertFalse(offline.contains("<tool name=\"web_search\">"))
        assertTrue(SchedulePrompts.run(base.copy(web = ScheduleWebAccess.Client("exa"))).contains("<tool name=\"web_search\">"))
    }

    @Test fun conversationPromptShowsTheUnsavedDraftOnly() {
        val context = SchedulePrompts.ConversationContext(task.anchorAt, "UTC", false, "Model", task, task.toDraft(),
            ScheduleWebAccess.Server, Locale.US)
        assertFalse(SchedulePrompts.conversation(context).contains("Unsaved draft"))
        val edited = context.copy(draft = task.toDraft().copy(hour = 7))
        assertTrue(SchedulePrompts.conversation(edited).contains("Unsaved draft"))
        assertTrue(SchedulePrompts.conversation(edited).contains(ScheduleTools.MANUAL.lines().first()))
    }
}
