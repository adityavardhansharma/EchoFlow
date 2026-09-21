package com.echoflow.data.jev

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

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
            memoryAction = "recall",
            promptVersion = JevThresholds.PROMPT_VERSION,
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
                .put("needs_save", JSONObject().put("type", "noul").put("noul", 0.02))
                .put("needs_web", JSONObject().put("type", "noul").put("noul", 0.2))
                .put("route", JSONObject()
                    .put("type", "choice")
                    .put("choice", "recall")
                    .put("probabilities", JSONObject()
                        .put("recall", 0.84)
                        .put("skip", 0.1)
                        .put("defer", 0.06))
                    .put("confidence", 0.68)))
        val scores = JevClient().parse(response)
        assertEquals("jev-1.13.0", scores.modelVersion)
        assertEquals(0.84, scores.needsMemory, 0.0)
        assertEquals(0.02, scores.needsSave, 0.0)
        assertEquals(0.2, scores.needsWeb, 0.0)
        assertEquals("recall", scores.routeChoice)
        assertEquals(0.84, scores.routeProbabilities["recall"] ?: -1.0, 0.0)
        assertEquals(0.68, scores.routeConfidence, 0.0)
    }

    @Test fun `client rejects incomplete responses instead of fabricating zeros`() {
        val missingQuestion = JSONObject()
            .put("model", "jev-1.13.0")
            .put("answers", JSONObject()
                .put("needs_memory", JSONObject().put("type", "noul").put("noul", 0.5)))
        var failures = 0
        try { JevClient().parse(missingQuestion) } catch (e: JevException) { failures++ }
        val missingRoute = JSONObject()
            .put("model", "jev-1.13.0")
            .put("answers", JSONObject()
                .put("needs_memory", JSONObject().put("type", "noul").put("noul", 0.5))
                .put("needs_save", JSONObject().put("type", "noul").put("noul", 0.1))
                .put("needs_web", JSONObject().put("type", "noul").put("noul", 0.1)))
        try { JevClient().parse(missingRoute) } catch (e: JevException) { failures++ }
        assertEquals(2, failures)
    }

    private fun response(choice: String, probability: Double): JSONObject = JSONObject()
        .put("model", "jev-fixture")
        .put("answers", JSONObject()
            .put("needs_save", JSONObject().put("type", "noul").put("noul", 0.0))
            .put("needs_web", JSONObject().put("type", "noul").put("noul", 0.0))
            .put("route", JSONObject().put("type", "choice").put("choice", choice)
                .put("confidence", 0.5)
                .put("probabilities", JSONObject().apply {
                    listOf("skip", "recall", "defer").forEach {
                        put(it, if (it == choice) probability else (1.0 - probability) / 2)
                    }
                })))

    @Test fun `route thresholds and defer flow through actual response parsing`() = runBlocking {
        for (choice in listOf("skip", "recall", "defer")) {
            for (probability in listOf(0.699, 0.7, 0.701)) {
                val http = OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body(response(choice, probability).toString().toResponseBody()).build()
                }.build()
                val decision = JevRouter.route(JevRouter.RouteInput("fixture", "question", true, true, true), JevClient(http))
                assertFalse(decision.fallback)
                assertEquals(if (probability >= 0.7) choice else "defer", decision.memoryAction)
            }
        }
    }

    @Test fun `recall plan honors force and never lets legacy prefetch bypass a decision`() {
        val skip = JevDecision.fallback().copy(fallback = false, memoryAction = "skip")
        assertEquals(JevRouter.RecallPlan(false, false, "skip"), JevRouter.recallPlan(skip, true, false, true))
        assertEquals(JevRouter.RecallPlan(true, true, "forced_recall"), JevRouter.recallPlan(skip, true, true, false))
        assertEquals(JevRouter.RecallPlan(false, false, "disabled"), JevRouter.recallPlan(skip, false, true, true))
        assertEquals(JevRouter.RecallPlan(false, true, "defer"),
            JevRouter.recallPlan(skip.copy(memoryAction = "defer"), true, false, true))
        assertEquals(JevRouter.RecallPlan(true, true, "recall"),
            JevRouter.recallPlan(skip.copy(memoryAction = "recall"), true, false, false))
        assertEquals(JevRouter.RecallPlan(true, true, "legacy"), JevRouter.recallPlan(null, true, false, true))
    }

    @Test fun `legacy history does not invent a defer action`() {
        val old = JSONObject(JevDecision.fallback().toJson()).apply {
            remove("memoryAction"); remove("promptVersion"); put("memoryRecalled", true)
        }
        val restored = JevDecision.fromJson(old.toString())!!
        assertEquals("legacy", restored.memoryAction)
        assertEquals("legacy-v1", restored.promptVersion)
        assertTrue(restored.memoryRecalled)
    }

    @Test fun `invalid distributions and numeric strings are rejected`() {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("choice", "unknown") },
            { it.put("choice", "skip") },
            { it.put("confidence", "0.8") },
            { it.getJSONObject("probabilities").put("recall", 1.1) },
            { it.getJSONObject("probabilities").put("recall", "0.8") },
            { it.getJSONObject("probabilities").put("skip", 0.4) },
            { it.getJSONObject("probabilities").remove("defer") },
            { it.getJSONObject("probabilities").put("extra", 0.0) },
        )
        mutations.forEach { mutate ->
            val value = response("recall", 0.8)
            mutate(value.getJSONObject("answers").getJSONObject("route"))
            try { JevClient().parse(value); fail("Malformed response accepted") }
            catch (_: JevException) { }
        }
    }

    @Test fun `payload bounds and truncation reflect redacted text`() {
        val client = JevClient()
        val longSecret = "password: " + "x".repeat(7000)
        val state = client.buildState(longSecret,
            listOf("system" to "omit me") + List(5) { "user" to longSecret }, true, true)
        assertFalse(state.getBoolean("message_truncated"))
        assertTrue(state.getBoolean("earlier_turns_omitted"))
        val turns = state.getJSONArray("recent_turns")
        assertEquals(4, turns.length())
        assertFalse(turns.getJSONObject(0).getBoolean("truncated"))
        assertFalse(state.toString().contains("xxx"))
        val expanded = client.buildState("a".repeat(5985) + " password: x",
            listOf("assistant" to ("a".repeat(1485) + " password: x")), true, false)
        assertTrue(expanded.getBoolean("message_truncated"))
        assertEquals(6000, expanded.getString("message").length)
        assertTrue(expanded.getJSONArray("recent_turns").getJSONObject(0).getBoolean("truncated"))
    }

    @Test fun `recall query retains assistant referents without secrets or excessive context`() {
        val query = JevRouter.recallQuery("What did I tell you about him?", listOf(
            "system" to "excluded system text",
            "user" to "password: secret",
            "assistant" to "Do you mean your brother Daniel?",
            "user" to "yes",
        ))
        assertTrue(query.contains("brother Daniel"))
        assertFalse(query.contains("secret"))
        assertFalse(query.contains("excluded system text"))
        assertTrue(JevRouter.recallQuery("x".repeat(9000), List(10) { "user" to "y".repeat(9000) }).length < 4000)
    }
}
