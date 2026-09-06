package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class HardeningPoliciesTest {
    private val field = BrowserElement("node-1", "input", "text", "Message", "", "https://example.com/send", "draft")
    private val page = BrowserSnapshot("https://example.com", "Example", "A page", listOf(field))

    @Test fun `planner cannot submit arbitrary code or selectors`() {
        for (raw in listOf("{\"type\":\"eval\",\"text\":\"steal()\"}",
            "{\"type\":\"click\",\"target\":\"node-1\",\"code\":\"steal()\"}",
            "{\"type\":\"click\",\"target\":\"button:first-child\"}")) {
            assertThrows(Exception::class.java) { BrowserActions.parseAction(raw, page) }
        }
    }
    @Test fun `navigation rejects executable schemes and embedded credentials`() {
        for (url in listOf("javascript:alert(1)", "http://example.com", "https://user:pass@example.com", "file:///etc/passwd")) {
            assertFalse(url, BrowserActions.validUrl(url))
        }
        assertTrue(BrowserActions.validUrl("https://example.com/path?q=1"))
    }
    @Test fun `password and upload fields cannot be targeted`() {
        for (type in listOf("password", "file", "hidden")) {
            val sensitive = page.copy(elements = listOf(field.copy(type = type)))
            assertThrows(IllegalArgumentException::class.java) {
                BrowserActions.validate(BrowserAction("fill", field.id, "secret"), sensitive)
            }
        }
    }
    @Test fun `approval persists exact text target and page and expires`() {
        val attack = "'); process.exit(); //\n</script>"
        val proposal = BrowserActions.proposal(BrowserAction("fill", field.id, attack), page, "write a message", 100)
        assertEquals(proposal, BrowserActions.decode(BrowserActions.encode(proposal)))
        assertEquals(100 + BrowserActions.APPROVAL_LIFETIME_MS, proposal.expiresAt)
        assertTrue(BrowserActions.describe(proposal).contains(attack))
        val script = BrowserActions.executionCode(proposal)
        assertTrue(script.contains("Page or form changed"))
        assertTrue(script.contains("\\n</script>")) // JSON string, never interpolated executable code
        assertFalse(script.contains("eval("))
    }
    @Test fun `irreversible classification takes precedence and matches whole words`() {
        assertEquals(BrowserResolver.RiskKind.HARD, BrowserResolver.classifyInstruction("send a message then transfer money"))
        assertNull(BrowserResolver.classifyInstruction("read the payload description"))
    }
    @Test fun `tool results cannot grow the final request beyond the budget`() {
        val payload = mapOf("messages" to listOf(mapOf("role" to "tool", "content" to "x".repeat(20000))), "max_tokens" to 2048)
        assertThrows(IllegalArgumentException::class.java) { RequestContextBudget.checkedPayload(payload) }
        assertEquals(payload, RequestContextBudget.checkedPayload(payload, 32000))
    }
    @Test fun `inline image cost follows encoded size and rejects oversized data`() {
        val small = "data:image/png;base64," + "A".repeat(8_000)
        assertEquals(
            mapOf("messages" to listOf(mapOf("content" to mapOf("type" to "image_url", "image_url" to mapOf("url" to small))))),
            RequestContextBudget.checkedPayload(mapOf("messages" to listOf(mapOf("content" to mapOf("type" to "image_url", "image_url" to mapOf("url" to small))))), 32_000),
        )
        val large = "data:image/png;base64," + "A".repeat(15_000_000)
        assertThrows(IllegalArgumentException::class.java) {
            RequestContextBudget.checkedPayload(mapOf("image" to large), 32_000)
        }
    }

    private fun message(id: Int, role: String, text: String) = ChatMessage(id.toString(), "chat", role, text, id.toLong())
    @Test fun `budget preserves latest question and drops old whole turns`() {
        val history = listOf(message(1,"user","old".repeat(600)),message(2,"assistant","answer".repeat(300)),
            message(3,"user","recent"),message(4,"assistant","recent answer"),message(5,"user","latest question"))
        val prepared = RequestContextBudget.prepare(history,"system","reference".repeat(2000),2048,512)
        assertEquals("latest question", prepared.history.last().content)
        assertEquals("user", prepared.history.first().role)
        assertTrue(prepared.omitted)
        assertTrue(RequestContextBudget.estimate(prepared.systemPrompt) + prepared.history.sumOf { RequestContextBudget.estimate(it.content) + 16 } < 2048 - 512)
    }
    @Test fun `oversized latest question is rejected instead of silently truncated`() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestContextBudget.prepare(listOf(message(1,"user","x".repeat(10000))),"system",contextTokens=2048,outputTokens=512)
        }
    }
    @Test fun `unicode reference truncation respects budget`() {
        val fitted = RequestContextBudget.fitText("你好🌍".repeat(1000),128)
        assertTrue(RequestContextBudget.estimate(fitted) <= 128)
        assertTrue(fitted.endsWith("[Reference text truncated.]"))
    }
    @Test fun `compact local instructions leave room for a small model`() {
        val prepared = RequestContextBudget.prepare(listOf(message(1,"user","Hello")), SystemPrompts.compactLocal("exa"),contextTokens=2048,outputTokens=512)
        assertEquals(1,prepared.history.size)
    }
    @Test fun `image sampling bounds decoded dimensions before allocation`() {
        assertNull(LocalLlmPlatformSupport.imageSampleSize(0,100))
        val sample = LocalLlmPlatformSupport.imageSampleSize(12000,9000)!!
        assertTrue(12000 / sample <= 1536)
        assertTrue(9000L / sample * (12000 / sample) * 4 < 10 * 1024 * 1024)
    }
}
