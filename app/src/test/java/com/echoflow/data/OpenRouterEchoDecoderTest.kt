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

    @Test fun `finds fusion result with analysis and responses`() {
        val payload = mapOf(
            "status" to "ok",
            "analysis" to mapOf(
                "consensus" to listOf("A is better"),
                "contradictions" to emptyList<Any>(),
            ),
            "responses" to listOf(
                mapOf("model" to "openai/gpt-test", "content" to "hello from gpt"),
                mapOf("model" to "deepseek/v4", "content" to "hello from deepseek"),
            ),
        )
        val direct = OpenRouterEchoDecoder.scanForFusionResult(payload)!!
        assertEquals(2, direct.responses.size)
        assertEquals(listOf("A is better"), direct.consensus)
        assertEquals(true, direct.toolResultFound)
        assertEquals(false, direct.isHardFailure)
    }

    @Test fun `finds fusion result nested inside content json string`() {
        val inner = """{"status":"ok","responses":[{"model":"a/b","content":"hi"}],"analysis":{"consensus":["x"]}}"""
        val found = OpenRouterEchoDecoder.scanForFusionResult(mapOf("content" to inner))!!
        assertEquals(1, found.responses.size)
        assertEquals(listOf("x"), found.consensus)
    }

    @Test fun `empty analysis without failed models is not a hard failure`() {
        val empty = FusionAnalysis(panelName = "p", judgeModel = null, models = listOf("a"), toolResultFound = false)
        assertEquals(false, empty.isHardFailure)
        val hard = FusionAnalysis(
            panelName = "p",
            judgeModel = null,
            models = listOf("a", "b"),
            failedModels = listOf("a", "b"),
            toolResultFound = true,
        )
        assertEquals(true, hard.isHardFailure)
    }

    @Test fun `parses failed_models maps and model_id responses`() {
        val payload = mapOf(
            "status" to "ok",
            "responses" to listOf(
                mapOf("model_id" to "openai/gpt", "content" to "ok"),
            ),
            "failed_models" to listOf(mapOf("model" to "deepseek/x")),
        )
        val result = OpenRouterEchoDecoder.scanForFusionResult(payload)!!
        assertEquals(1, result.responses.size)
        assertEquals("openai/gpt", result.responses.single().model)
        assertEquals(listOf("deepseek/x"), result.failedModels)
    }

    @Test fun `prefers successful fusion over later capped invocation`() {
        val ok = mapOf(
            "status" to "ok",
            "analysis" to mapOf("consensus" to listOf("use solid panels")),
            "responses" to listOf(mapOf("model" to "a/b", "content" to "hi")),
        )
        val capped = mapOf(
            "status" to "error",
            "error" to "Fusion has already been invoked for this request and cannot be called again.",
            "failure_reason" to "fusion_invocation_capped",
        )
        val tree = mapOf(
            "messages" to listOf(
                mapOf("role" to "tool", "content" to ok),
                mapOf("role" to "tool", "content" to capped),
            ),
        )
        val preferred = OpenRouterEchoDecoder.scanForFusionResult(tree)!!
        assertEquals(listOf("use solid panels"), preferred.consensus)
        assertEquals(1, preferred.responses.size)
        // Capped payload must not be collected as a fusion result at all.
        val all = OpenRouterEchoDecoder.scanForAllFusionResults(tree)
        assertEquals(1, all.size)
    }
}
