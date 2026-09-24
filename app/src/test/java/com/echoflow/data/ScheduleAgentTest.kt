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

    @Test fun theLastRoundFailsInsteadOfReportingUnexecutedToolsAsSuccess() = runBlocking {
        val agent = ScheduleAgent(complete = { flowOf("Again <tool name=\"run_schedule_now\"/>") }, maxRounds = 2)
        var calls = 0
        val streamed = mutableListOf<String>()
        val failure = runCatching { agent.run(emptyList(), execute = { calls++; "{}" }, onText = { streamed += it }) }.exceptionOrNull()
        assertEquals(1, calls)
        assertTrue(failure?.message.orEmpty().contains("tool limit"))
        assertEquals("", streamed.last())
    }
}
