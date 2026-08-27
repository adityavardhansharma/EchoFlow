package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonidSearchV1Test {
    private val jsonAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Any::class.java)

    @Test
    fun `request body targets context-dev web search`() {
        val body = MonidSearchV1.requestBody("latest Android Compose news", 5)

        assertEquals("context.dev", body["provider"])
        assertEquals("/web/search", body["endpoint"])
        assertEquals(
            mapOf("query" to "latest Android Compose news", "numResults" to 5),
            body["input"],
        )
    }

    @Test
    fun `request body serializes nested input`() {
        val json = jsonAdapter.toJson(MonidSearchV1.requestBody("weather in Paris", 3))
        val parsed = jsonAdapter.fromJson(json) as Map<*, *>
        val input = parsed["input"] as Map<*, *>
        assertEquals("context.dev", parsed["provider"])
        assertEquals("/web/search", parsed["endpoint"])
        assertEquals("weather in Paris", input["query"])
        assertEquals(3.0, input["numResults"])
    }

    @Test
    fun `parseCompletedRun maps context-dev results into SearchSource`() {
        val response = mapOf(
            "status" to "COMPLETED",
            "output" to mapOf(
                "results" to listOf(
                    mapOf(
                        "url" to "https://example.com/article",
                        "title" to "Example headline",
                        "description" to "A short snippet.",
                        "date" to "2026-08-20",
                    ),
                ),
            ),
        )

        val sources = MonidSearchV1.parseCompletedRun(response)

        assertEquals(1, sources.size)
        assertEquals(
            SearchSource(
                title = "Example headline",
                url = "https://example.com/article",
                snippet = "A short snippet.",
                publishedDate = "2026-08-20",
            ),
            sources.single(),
        )
    }

    @Test
    fun `parseCompletedRun prefers description then markdown`() {
        val markdownOnly = mapOf(
            "output" to mapOf(
                "results" to listOf(
                    mapOf(
                        "url" to "https://example.com/md",
                        "title" to "Markdown page",
                        "markdown" to mapOf("content" to "Extracted page text."),
                    ),
                ),
            ),
        )
        assertEquals("Extracted page text.", MonidSearchV1.parseCompletedRun(markdownOnly).single().snippet)
    }

    @Test
    fun `parseCompletedRun falls back to url when title is blank`() {
        val source = MonidSearchV1.parseCompletedRun(
            mapOf(
                "output" to mapOf(
                    "results" to listOf(
                        mapOf("url" to "https://example.com/no-title", "title" to "   "),
                    ),
                ),
            ),
        ).single()
        assertEquals("https://example.com/no-title", source.title)
    }

    @Test
    fun `parseCompletedRun returns empty list when results are missing`() {
        assertTrue(MonidSearchV1.parseCompletedRun(emptyMap<String, Any>()).isEmpty())
        assertTrue(MonidSearchV1.parseCompletedRun(mapOf("output" to null)).isEmpty())
        assertTrue(MonidSearchV1.parseCompletedRun(mapOf("output" to mapOf("results" to null))).isEmpty())
    }

    @Test
    fun `parseCompletedRun skips malformed entries without url`() {
        val sources = MonidSearchV1.parseCompletedRun(
            mapOf(
                "output" to mapOf(
                    "results" to listOf(
                        mapOf("title" to "No URL"),
                        mapOf("url" to "https://example.com/ok", "description" to "kept"),
                    ),
                ),
            ),
        )
        assertEquals(1, sources.size)
        assertEquals("https://example.com/ok", sources.single().url)
    }

    @Test
    fun `parseCompletedRun caps snippets at 2000 characters`() {
        val long = "x".repeat(4000)
        val snippet = MonidSearchV1.parseCompletedRun(
            mapOf(
                "output" to mapOf(
                    "results" to listOf(
                        mapOf("url" to "https://example.com/long", "description" to long),
                    ),
                ),
            ),
        ).single().snippet
        assertEquals(2000, snippet?.length)
    }

    @Test
    fun `poll url and terminal statuses`() {
        assertEquals("https://api.monid.ai/v1/runs/01HXYZ", MonidSearchV1.pollUrl("01HXYZ"))
        assertTrue(MonidSearchV1.isTerminal("COMPLETED"))
        assertTrue(MonidSearchV1.isTerminal("BLOCKED"))
        assertTrue(!MonidSearchV1.isTerminal("RUNNING"))
    }

    @Test
    fun clientSearchProvidersIncludeMonid() {
        assertTrue(ClientSearchProviders.asSet.contains("monid"))
        assertEquals("monid_api_key", ClientSearchProviders.prefKey("monid"))
        assertEquals("Monid", ClientSearchProviders.displayName("monid"))
    }
}
