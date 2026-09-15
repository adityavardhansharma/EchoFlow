package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class OpenRouterFusionRunnerTest {
    private val panel = OpenRouterService.FusionRequest("Panel", listOf("model-a", "model-b"), "judge")
    private fun response(answer: String) = mapOf(
        "choices" to listOf(mapOf("message" to mapOf("content" to answer))),
    )
    private val detail = mapOf(
        "status" to "ok",
        "analysis" to mapOf("consensus" to listOf("Use a cache")),
        "responses" to listOf(mapOf("model" to "model-a", "content" to "Cache the result")),
    )

    private fun runner(responses: List<Map<String, Any>>, requests: MutableList<Map<String, Any>>) =
        OpenRouterFusionRunner(
            postForResponse = { key, request ->
                assertEquals("test-key", key)
                requests.add(request)
                responses[requests.lastIndex]
            },
            buildMessagesPayload = { _, system ->
                mutableListOf(mapOf("role" to "system", "content" to system.orEmpty()))
            },
            applyInferenceParams = { _, _ -> },
        )

    @Test fun `missing final answer is synthesized with all fusion tools disabled`() {
        val requests = mutableListOf<Map<String, Any>>()
        val runner = runner(listOf(response("") + ("panel" to detail), response("Final answer")), requests)
        val result = runner.run("test-key", "outer", emptyList(), "System", panel, null)
        assertEquals("Final answer", result.answer)
        assertEquals(2, requests.size)
        assertEquals("required", requests.first()["tool_choice"])
        assertEquals(false, requests.first()["parallel_tool_calls"])
        assertEquals("none", requests.last()["tool_choice"])
        assertFalse(requests.last().containsKey("tools"))
        assertFalse(requests.last().containsKey("plugins"))
        assertFalse(result.analysis.panelDidNotRun)
    }

    @Test fun `a complete panel answer requires only one request`() {
        val requests = mutableListOf<Map<String, Any>>()
        val result = runner(listOf(response("Already complete") + ("panel" to detail)), requests)
            .run("test-key", "outer", emptyList(), "System", panel, null)
        assertEquals("Already complete", result.answer)
        assertEquals(1, requests.size)
    }

    @Test fun `a skipped panel is disclosed without retrying the paid operation`() {
        val requests = mutableListOf<Map<String, Any>>()
        val result = runner(listOf(response("Single model response")), requests)
            .run("test-key", "outer", emptyList(), "System", panel, null)
        assertTrue(result.analysis.panelDidNotRun)
        assertEquals(1, requests.size)
    }
}
