package com.echoflow.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleAgentTest {
    @Test fun executesToolsAndStitchesVisibleTextAcrossRounds() = runBlocking {
        val rounds = ArrayDeque(listOf(
            listOf("Setting that up.", "\n<tool name=\"save_schedule\">{}</tool>"),
            listOf("Done — weekdays at 8."),
        ))
        val seen = mutableListOf<List<ChatMessage>>()
        val agent = ScheduleAgent(complete = { history -> seen += history; flowOf(*rounds.removeFirst().toTypedArray()) })
        val streamed = mutableListOf<String>()
        val executed = mutableListOf<String>()
        val reply = agent.run(listOf(ScheduleModelRunner.message("user", "every weekday at 8")),
            execute = { call -> executed += call.name; """{"ok":true}""" }, onText = { streamed += it })
        assertEquals("Setting that up.\n\nDone — weekdays at 8.", reply.text)
        assertEquals(listOf("save_schedule"), executed)
        assertEquals(1, reply.toolCalls)
        assertTrue(streamed.none { it.contains("<tool") })
        assertTrue(seen[1].last().content.contains("<tool_result name=\"save_schedule\">{\"ok\":true}</tool_result>"))
    }

    @Test fun finalToolRoundGetsAnAnswerOnlyTurn() = runBlocking {
        val rounds = ArrayDeque(listOf(
            "Started <tool name=\"save_schedule\">{}</tool>",
            "Done <tool name=\"run_schedule_now\">{}</tool>",
            "The schedule was saved; the run was not started.",
        ))
        val histories = mutableListOf<List<ChatMessage>>()
        val agent = ScheduleAgent(complete = { history ->
            histories += history
            flowOf(rounds.removeFirst())
        }, maxRounds = 2)
        var calls = 0
        val reply = agent.run(emptyList(), execute = { calls++; "{}" })
        assertEquals(1, calls)
        assertEquals("Started\n\nThe schedule was saved; the run was not started.", reply.text)
        assertTrue(histories.last().last().content.contains("No more tools are available"))
    }

    @Test fun answerOnlyTurnFailsOnlyWhenItHasNoVisibleAnswer() = runBlocking {
        val agent = ScheduleAgent(complete = { flowOf("<tool name=\"web_search\">{}</tool>") }, maxRounds = 1)
        val failure = runCatching { agent.run(emptyList(), execute = { "{}" }) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("no answer"))
    }
}
