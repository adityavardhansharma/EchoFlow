package com.echoflow.data.jev

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JevRouterTest {

    @Test fun `cloud gate keeps local and lan providers out`() {
        assertFalse(JevRouter.isCloudChat(isLocal = true, customProvider = null))
        assertFalse(JevRouter.isCloudChat(isLocal = true, customProvider = "openai"))
        assertFalse(JevRouter.isCloudChat(isLocal = false, customProvider = "ollama"))
        assertFalse(JevRouter.isCloudChat(isLocal = false, customProvider = "openai-compatible"))
    }

    @Test fun `cloud gate allows openrouter and direct cloud brands`() {
        assertTrue(JevRouter.isCloudChat(isLocal = false, customProvider = null))
        assertTrue(JevRouter.isCloudChat(isLocal = false, customProvider = "openai"))
        assertTrue(JevRouter.isCloudChat(isLocal = false, customProvider = "claude"))
        assertTrue(JevRouter.isCloudChat(isLocal = false, customProvider = "gemini"))
        assertTrue(JevRouter.isCloudChat(isLocal = false, customProvider = "sarvam"))
    }

    @Test fun `fixed thresholds are the agreed values`() {
        assertEquals(0.7, JevThresholds.ACT, 0.0)
        assertEquals(0.4, JevThresholds.REVIEW, 0.0)
        assertEquals("jev-latest", JevThresholds.MODEL)
    }

    @Test fun `decision survives a json round trip`() {
        val decision = JevDecision(
            modelVersion = "jev-1.13.0",
            latencyMs = 120,
            needsMemory = 0.92,
            needsSave = 0.05,
            needsWeb = 0.11,
            routeChoice = "memory_only",
            routeProbabilities = mapOf("memory_only" to 0.81, "neither" to 0.1),
            routeConfidence = 0.66,
            memoryRecalled = true,
            webForced = false,
            saveTriggered = false,
            fallback = false,
            createdAt = 123L,
        )
        val restored = JevDecision.fromJson(decision.toJson())
        assertNotNull(restored)
        assertEquals(decision, restored)
    }

    @Test fun `blank json parses to null`() {
        assertNull(JevDecision.fromJson(null))
        assertNull(JevDecision.fromJson(""))
    }

    @Test fun `client parses a systemone response`() {
        val response = JSONObject()
            .put("model", "jev-1.13.0")
            .put("answers", JSONObject()
                .put("needs_memory", JSONObject().put("type", "noul").put("noul", 0.93))
                .put("needs_save", JSONObject().put("type", "noul").put("noul", 0.02))
                .put("needs_web", JSONObject().put("type", "noul").put("noul", 0.2))
                .put("route", JSONObject()
                    .put("type", "choice")
                    .put("choice", "memory_only")
                    .put("probabilities", JSONObject()
                        .put("memory_only", 0.84)
                        .put("neither", 0.1)
                        .put("web_only", 0.03)
                        .put("both", 0.03))
                    .put("confidence", 0.68)))
        val scores = JevClient().parse(response)
        assertEquals("jev-1.13.0", scores.modelVersion)
        assertEquals(0.93, scores.needsMemory, 0.0)
        assertEquals(0.02, scores.needsSave, 0.0)
        assertEquals(0.2, scores.needsWeb, 0.0)
        assertEquals("memory_only", scores.routeChoice)
        assertEquals(0.84, scores.routeProbabilities["memory_only"] ?: -1.0, 0.0)
        assertEquals(0.68, scores.routeConfidence, 0.0)
    }
}
