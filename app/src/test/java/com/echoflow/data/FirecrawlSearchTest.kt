package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirecrawlSearchTest {

    @Test
    fun `keyed firecrawl request includes scrape options`() {
        val body = FirecrawlSearch.requestBody("android 16", 5, scrapeMarkdown = true)
        assertEquals("android 16", body["query"])
        assertEquals(5, body["limit"])
        assertEquals(mapOf("formats" to listOf("markdown")), body["scrapeOptions"])
    }

    @Test
    fun `echocrawl request omits scrape so the free quota is not burned`() {
        val body = FirecrawlSearch.requestBody("android 16", 8, scrapeMarkdown = false)
        assertEquals(8, body["limit"])
        assertNull(body["scrapeOptions"])
    }

    @Test
    fun `parse v2 data web with description snippets`() {
        val response = mapOf(
            "success" to true,
            "data" to mapOf(
                "web" to listOf(
                    mapOf(
                        "url" to "https://example.com/a",
                        "title" to "Headline",
                        "description" to "A short snippet from the index.",
                    ),
                ),
            ),
        )

        val sources = FirecrawlSearch.parseResults(response, snippetChars = 1500, preferMarkdown = false)
        assertEquals(1, sources.size)
        assertEquals("Headline", sources.single().title)
        assertEquals("https://example.com/a", sources.single().url)
        assertEquals("A short snippet from the index.", sources.single().snippet)
    }

    @Test
    fun `echocrawl prefers description over markdown`() {
        val response = mapOf(
            "data" to mapOf(
                "web" to listOf(
                    mapOf(
                        "url" to "https://example.com/b",
                        "title" to "Page",
                        "markdown" to "# Huge scraped page",
                        "description" to "Indexed snippet",
                    ),
                ),
            ),
        )
        val snippet = FirecrawlSearch.parseResults(response, snippetChars = 80, preferMarkdown = false)
            .single().snippet
        assertEquals("Indexed snippet", snippet)
    }

    @Test
    fun `paid firecrawl prefers markdown`() {
        val response = mapOf(
            "data" to mapOf(
                "web" to listOf(
                    mapOf(
                        "url" to "https://example.com/c",
                        "title" to "Page",
                        "markdown" to "# Scraped",
                        "description" to "Indexed snippet",
                    ),
                ),
            ),
        )
        val snippet = FirecrawlSearch.parseResults(response, snippetChars = 80, preferMarkdown = true)
            .single().snippet
        assertEquals("# Scraped", snippet)
    }

    @Test
    fun `parse data as a top-level list`() {
        val response = mapOf(
            "data" to listOf(
                mapOf("url" to "https://example.com/list", "title" to "Listed", "snippet" to "From results[]"),
            ),
        )
        val source = FirecrawlSearch.parseResults(response, snippetChars = 100, preferMarkdown = false).single()
        assertEquals("Listed", source.title)
        assertEquals("From results[]", source.snippet)
    }

    @Test
    fun `success false throws the api error`() {
        val thrown = runCatching {
            FirecrawlSearch.parseResults(
                mapOf("success" to false, "error" to "Insufficient credits"),
                snippetChars = 100,
                preferMarkdown = false,
            )
        }.exceptionOrNull()
        assertTrue(thrown is Exception)
        assertEquals("Insufficient credits", thrown?.message)
    }

    @Test
    fun `apiErrorMessage reads error then message`() {
        assertEquals("nope", FirecrawlSearch.apiErrorMessage(mapOf("error" to "nope", "message" to "other")))
        assertEquals("other", FirecrawlSearch.apiErrorMessage(mapOf("message" to "other")))
        assertNull(FirecrawlSearch.apiErrorMessage(emptyMap<String, Any>()))
    }

    @Test
    fun `blank urls are skipped and duplicates collapse`() {
        val response = mapOf(
            "data" to mapOf(
                "web" to listOf(
                    mapOf("url" to "", "title" to "Empty"),
                    mapOf("url" to "https://example.com/d", "title" to "First"),
                    mapOf("url" to "https://example.com/d", "title" to "Dup"),
                ),
            ),
        )
        val sources = FirecrawlSearch.parseResults(response, snippetChars = 40, preferMarkdown = false)
        assertEquals(1, sources.size)
        assertEquals("First", sources.single().title)
    }
}
