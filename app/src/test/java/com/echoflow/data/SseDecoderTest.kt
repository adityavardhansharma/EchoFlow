package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Framing tests for the Deep Research event streams.
 *
 * Parallel's task run events and Exa's agent run stream both need paid keys to exercise
 * end-to-end, so these pin down the half that doesn't: that a realistic wire transcript decodes
 * into the frames the engine expects, keep-alives don't produce phantom events, and a stream cut
 * off mid-flight doesn't lose its last frame.
 */
class SseDecoderTest {
    private fun decode(wire: String): List<SseEvent> {
        val decoder = SseDecoder()
        val out = wire.lines().mapNotNull { decoder.accept(it) }.toMutableList()
        decoder.flush()?.let { out.add(it) }
        return out
    }

    @Test fun `decodes a named event with a json payload`() {
        val frames = decode(
            """
            event: task_run.progress_msg.plan
            data: {"message":"Breaking the question into sub-topics"}

            """.trimIndent()
        )
        assertEquals(1, frames.size)
        assertEquals("task_run.progress_msg.plan", frames[0].name)
        assertEquals("""{"message":"Breaking the question into sub-topics"}""", frames[0].data)
    }

    @Test fun `decodes a realistic parallel transcript in order`() {
        val frames = decode(
            """
            : keep-alive

            event: task_run.state
            data: {"run":{"status":"running"}}

            event: task_run.progress_msg.plan
            data: {"message":"Planning research"}

            event: task_run.progress_msg.search
            data: {"message":"Searching for heat pump efficiency data"}

            event: task_run.progress_stats
            data: {"num_sources_considered":40,"num_sources_read":12}

            event: task_run.state
            data: {"run":{"status":"completed"}}

            """.trimIndent()
        )
        assertEquals(
            listOf(
                "task_run.state",
                "task_run.progress_msg.plan",
                "task_run.progress_msg.search",
                "task_run.progress_stats",
                "task_run.state",
            ),
            frames.map { it.name },
        )
        assertEquals("""{"num_sources_considered":40,"num_sources_read":12}""", frames[3].data)
    }

    @Test fun `decodes an exa agent transcript`() {
        val frames = decode(
            """
            event: agent_run.created
            data: {"id":"run_abc123","status":"queued"}

            event: agent_run.source.added
            data: {"url":"https://example.com","title":"Example"}

            event: agent_run.completed
            data: {"output":{"text":"done"}}

            """.trimIndent()
        )
        assertEquals(listOf("agent_run.created", "agent_run.source.added", "agent_run.completed"), frames.map { it.name })
        assertEquals("""{"id":"run_abc123","status":"queued"}""", frames[0].data)
    }

    @Test fun `joins multi-line data into one payload`() {
        val frames = decode(
            """
            event: task_run.progress_msg.result
            data: {"message":
            data: "split across frames"}

            """.trimIndent()
        )
        assertEquals(1, frames.size)
        assertEquals("{\"message\":\n\"split across frames\"}", frames[0].data)
    }

    @Test fun `keep-alives and unknown fields produce no frames`() {
        assertEquals(emptyList<SseEvent>(), decode(": ping\n\nid: 42\nretry: 3000\n\n"))
    }

    @Test fun `an unnamed event still carries its payload`() {
        val frames = decode("data: {\"type\":\"error\"}\n\n")
        assertEquals(1, frames.size)
        assertNull(frames[0].name)
        assertEquals("""{"type":"error"}""", frames[0].data)
    }

    @Test fun `a stream cut off without a trailing blank line keeps its last frame`() {
        // A dropped connection mid-run must not silently swallow the final step.
        val frames = decode("event: agent_run.completed\ndata: {\"ok\":true}")
        assertEquals(1, frames.size)
        assertEquals("agent_run.completed", frames[0].name)
    }

    @Test fun `the event name does not leak into the next frame`() {
        val frames = decode("event: task_run.progress_msg.plan\ndata: {\"a\":1}\n\ndata: {\"b\":2}\n\n")
        assertEquals(2, frames.size)
        assertEquals("task_run.progress_msg.plan", frames[0].name)
        assertNull(frames[1].name)
    }
}
