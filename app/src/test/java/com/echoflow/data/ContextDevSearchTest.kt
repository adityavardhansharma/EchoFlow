package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDevSearchTest {

    @Test
    fun `request asks for main-content markdown with a partial-results deadline`() {
        val body = ContextDevSearch.requestBody("android 16", 5)
        assertEquals("android 16", body["query"])
        assertEquals(5, body["numResults"])
        val markdown = body["markdownOptions"] as Map<*, *>
        assertEquals(true, markdown["enabled"])
        assertEquals(true, markdown["useMainContentOnly"])
        val timeout = body["timeoutOpts"] as Map<*, *>
        assertEquals("return-partial", timeout["behavior"])
        assertTrue((timeout["milliseconds"] as Int) in 1_000..59_000)
    }

    @Test
    fun `request clamps result count and query length to the API limits`() {
        assertEquals(1, ContextDevSearch.requestBody("q", 0)["numResults"])
        assertEquals(10, ContextDevSearch.requestBody("q", 50)["numResults"])
        assertEquals(500, (ContextDevSearch.requestBody("x".repeat(900), 5)["query"] as String).length)
    }

    @Test
    fun `parse prefers scraped markdown when it succeeded`() {
        val response = mapOf(
            "results" to listOf(
                mapOf(
                    "url" to "https://example.com/a",
                    "title" to "Headline",
                    "description" to "Short description",
                    "relevance" to "high",
                    "markdown" to mapOf("markdown" to "# Full page", "code" to "SUCCESS"),
                ),
            ),
            "key_metadata" to mapOf("credits_consumed" to 2, "credits_remaining" to 998),
        )
        val source = ContextDevSearch.parseResults(response).single()
        assertEquals("Headline", source.title)
        assertEquals("https://example.com/a", source.url)
        assertEquals("# Full page", source.snippet)
        assertNull(source.publishedDate)
    }

    @Test
    fun `parse falls back to description when the scrape failed`() {
        val response = mapOf(
            "results" to listOf(
                mapOf(
                    "url" to "https://example.com/b",
                    "title" to "",
                    "description" to "Indexed snippet",
                    "markdown" to mapOf("markdown" to null, "code" to "WEBSITE_BLOCKED"),
                ),
            ),
        )
        val source = ContextDevSearch.parseResults(response).single()
        assertEquals("https://example.com/b", source.title)
        assertEquals("Indexed snippet", source.snippet)
    }

    @Test
    fun `parse truncates long pages, drops duplicates and items without a url`() {
        val response = mapOf(
            "results" to listOf(
                mapOf("url" to "https://example.com/c", "title" to "C", "markdown" to mapOf("markdown" to "a".repeat(9000), "code" to "SUCCESS")),
                mapOf("url" to "https://example.com/c", "title" to "C again"),
                mapOf("title" to "No url"),
                mapOf("url" to "", "title" to "Blank url"),
            ),
        )
        val sources = ContextDevSearch.parseResults(response, snippetChars = 4000)
        assertEquals(1, sources.size)
        assertEquals(4000, sources.single().snippet!!.length)
    }

    @Test
    fun `parse returns nothing when results are missing`() {
        assertTrue(ContextDevSearch.parseResults(mapOf("message" to "bad")).isEmpty())
    }

    @Test
    fun `context dev is a keyed chat provider but not a deep research backend`() {
        assertTrue(ClientSearchProviders.requiresApiKey(ClientSearchProviders.CONTEXT_DEV))
        assertTrue(ClientSearchProviders.CONTEXT_DEV in ClientSearchProviders.chatIds)
        assertTrue(ClientSearchProviders.isReady(ClientSearchProviders.CONTEXT_DEV, "key"))
        assertTrue(!ClientSearchProviders.isReady(ClientSearchProviders.CONTEXT_DEV, ""))
        assertTrue(ClientSearchProviders.CONTEXT_DEV !in ClientSearchProviders.researchIds)
    }
}
