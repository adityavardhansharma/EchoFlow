package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenRouterEchoDecoderTest {
    @Test fun `finds adviser result nested inside encoded content`() {
        // The server result uses the original `model` field; keep the decoder's wire contract
        // rather than teaching the test a synthetic alias.
        val response = mapOf("message" to mapOf("content" to """{"model":"model-a","advice":"use a cache"}"""))
        assertEquals("model-a" to "use a cache", OpenRouterEchoDecoder.scanForAdvisorResult(response))
    }

    @Test fun `collects nested subagent results`() {
        val results = mutableListOf<SubagentResult>()
        OpenRouterEchoDecoder.scanForSubagentResults(
            mapOf("task_name" to "audit", "task_description" to "review", "worker_model" to "worker", "outcome" to "ok"),
            out = results,
        )
        assertEquals("audit", results.single().taskName)
        assertEquals("ok", results.single().outcome)
    }

    @Test fun `ignores unrelated response shapes`() {
        assertNull(OpenRouterEchoDecoder.scanForAdvisorResult(mapOf("content" to "hello")))
        assertNull(OpenRouterEchoDecoder.scanForFusionResult(emptyMap<String, Any>()))
    }
}
