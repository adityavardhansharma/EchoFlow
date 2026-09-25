package com.echoflow.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListPricesTest {
    private val prices = ListPrices.parse(
        """{"data":[
            {"id":"openai/gpt-5.5","pricing":{"prompt":"0.00000125","completion":"0.00001","input_cache_read":"0.000000125"}},
            {"id":"anthropic/claude-opus-4.5","pricing":{"prompt":"0.000005","completion":"0.000025"}},
            {"id":"google/gemini-3.5-flash","pricing":{"prompt":"0.0000003","completion":"0.0000025"}},
            {"id":"openrouter/auto","pricing":{"prompt":"-1","completion":"-1"}}
        ]}"""
    )

    @Test fun `direct ids map onto each lab's listed model`() {
        assertEquals(0.00000125, ListPrices.find(prices, UsageProvider.OpenAi, "gpt-5.5-2026-08-01")!!.prompt, 0.0)
        assertEquals(0.000005, ListPrices.find(prices, UsageProvider.Claude, "claude-opus-4-5-20251101")!!.prompt, 0.0)
        assertEquals(0.0000025, ListPrices.find(prices, UsageProvider.Gemini, "models/gemini-3.5-flash")!!.completion, 0.0)
        assertNull(ListPrices.find(prices, UsageProvider.OpenAi, "gpt-unknown"))
        assertNull(ListPrices.find(prices, UsageProvider.XAi, "grok-4.5"))
        assertEquals(3, prices.size) // variable-priced routers are dropped
    }

    @Test fun `cached input is billed at its own rate`() {
        val price = prices.getValue("openai/gpt-5.5")
        // 8,000 fresh + 2,000 cached input, 1,000 output.
        assertEquals(8_000 * 0.00000125 + 2_000 * 0.000000125 + 1_000 * 0.00001, ListPrices.cost(price, 10_000, 1_000, 2_000), 1e-12)
        val noCacheRate = prices.getValue("anthropic/claude-opus-4.5")
        assertEquals(10_000 * 0.000005 + 500 * 0.000025, ListPrices.cost(noCacheRate, 10_000, 500, 4_000), 1e-12)
    }
}
