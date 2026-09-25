package com.echoflow.data.usage

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAccumulatorTest {
    private fun read(provider: UsageProvider, vararg docs: String): UsageReading {
        val acc = UsageAccumulator(provider)
        docs.forEach { UsageJson.read(Buffer().writeUtf8(it))?.let(acc::accept) }
        return acc.reading()
    }

    @Test fun `openrouter final chunk carries the exact charge`() {
        val r = read(
            UsageProvider.OpenRouter,
            """{"id":"gen-1","model":"anthropic/claude-sonnet-5","choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":340,"prompt_tokens_details":{"cached_tokens":800},"completion_tokens_details":{"reasoning_tokens":120},"cost":0.00421}}""",
        )
        assertEquals("gen-1", r.externalId)
        assertEquals("anthropic/claude-sonnet-5", r.model)
        assertEquals(1200L, r.inputTokens)
        assertEquals(340L, r.outputTokens)
        assertEquals(800L, r.cachedTokens)
        assertEquals(120L, r.reasoningTokens)
        assertEquals(0.00421, r.costUsd!!, 1e-12)
    }

    @Test fun `anthropic splits usage across start and cumulative deltas and has no price`() {
        val r = read(
            UsageProvider.Claude,
            """{"type":"message_start","message":{"id":"msg_1","model":"claude-opus-5-5","content":[],"usage":{"input_tokens":20,"cache_creation_input_tokens":100,"cache_read_input_tokens":500,"output_tokens":1}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":42}}""",
        )
        assertEquals("msg_1", r.externalId)
        assertEquals("claude-opus-5-5", r.model)
        assertEquals(620L, r.inputTokens)
        assertEquals(500L, r.cachedTokens)
        assertEquals(42L, r.outputTokens)
        assertNull(r.costUsd)
    }

    @Test fun `gemini counts thinking as output and keeps the cumulative maximum`() {
        val r = read(
            UsageProvider.Gemini,
            """{"responseId":"g1","modelVersion":"gemini-3.5-pro","candidates":[{"content":{"parts":[{"text":"Hi"}]}}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":2,"thoughtsTokenCount":5}}""",
            """{"responseId":"g1","usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":30,"thoughtsTokenCount":5}}""",
        )
        assertEquals("g1", r.externalId)
        assertEquals(10L, r.inputTokens)
        assertEquals(35L, r.outputTokens)
        assertEquals(5L, r.reasoningTokens)
    }

    @Test fun `openai responses report on completion`() {
        val r = read(
            UsageProvider.OpenAi,
            """{"type":"response.completed","response":{"id":"resp_1","model":"gpt-5.5","output":[{"type":"message"}],"usage":{"input_tokens":50,"input_tokens_details":{"cached_tokens":10},"output_tokens":70,"output_tokens_details":{"reasoning_tokens":30}}}}""",
        )
        assertEquals("resp_1", r.externalId)
        assertEquals("gpt-5.5", r.model)
        assertEquals(50L, r.inputTokens)
        assertEquals(70L, r.outputTokens)
        assertEquals(10L, r.cachedTokens)
        assertEquals(30L, r.reasoningTokens)
        assertNull(r.costUsd)
    }

    @Test fun `xai ticks convert to dollars`() {
        val r = read(UsageProvider.XAi, """{"id":"x1","model":"grok-4.5","choices":[],"usage":{"prompt_tokens":5,"completion_tokens":6,"cost_in_usd_ticks":37756000}}""")
        assertEquals(0.0037756, r.costUsd!!, 1e-12)
    }

    @Test fun `exa firecrawl parallel and deepgram report in their own terms`() {
        assertEquals(0.007, read(UsageProvider.Exa, """{"requestId":"e1","results":[{"text":"long"}],"costDollars":{"total":0.007,"search":{"neural":0.007}}}""").costUsd!!, 1e-12)

        val firecrawl = read(UsageProvider.Firecrawl, """{"success":true,"id":"f1","creditsUsed":2,"data":{"web":[]}}""")
        assertEquals(2.0, firecrawl.credits!!, 0.0)
        assertEquals("f1", firecrawl.externalId)
        val scrape = read(UsageProvider.Firecrawl, """{"success":true,"data":{"markdown":"# page","metadata":{"creditsUsed":1,"scrapeId":"s1"}}}""")
        assertEquals(1.0, scrape.credits!!, 0.0)

        val parallel = read(UsageProvider.Parallel, """{"search_id":"p1","results":[],"usage":[{"name":"sku_search","count":1},{"name":"sku_extract_excerpts","count":3}]}""")
        assertEquals("p1", parallel.externalId)
        assertEquals("search × 1, extract excerpts × 3", parallel.detail)

        val deepgram = read(UsageProvider.Deepgram, """{"metadata":{"request_id":"d1","duration":12.5,"models":["abc"]},"results":{"channels":[]}}""")
        assertEquals("d1", deepgram.externalId)
        assertEquals(12.5, deepgram.audioSeconds!!, 0.0)
        assertTrue(deepgram.hasUsage)
    }

    @Test fun `ids and models alone are not usage`() {
        assertFalse(read(UsageProvider.OpenRouter, """{"id":"gen-2","model":"x","choices":[{"delta":{"content":"hi"}}]}""").hasUsage)
        assertFalse(read(UsageProvider.OpenRouter, """{"data":[{"id":"model-a"}]}""").hasUsage)
    }

    @Test fun `unreadable documents are ignored`() {
        assertFalse(read(UsageProvider.OpenRouter, "not json", "[1,2]").hasUsage)
    }
}
