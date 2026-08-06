package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelSearchV1Test {
    private val jsonAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(Any::class.java)

    @Test
    fun `request body matches v1 search contract`() {
        val body = ParallelSearchV1.requestBody("latest Android Compose news", 7)

        assertEquals("latest Android Compose news", body["objective"])
        assertEquals(listOf("latest Android Compose news"), body["search_queries"])
        assertEquals("basic", body["mode"])
        assertEquals(mapOf("max_results" to 7), body["advanced_settings"])
    }

    @Test
    fun `request body serializes nested advanced_settings`() {
        val body = ParallelSearchV1.requestBody("weather in Paris", 3)
        val json = jsonAdapter.toJson(body)
        val parsed = jsonAdapter.fromJson(json) as Map<*, *>

        assertEquals("basic", parsed["mode"])
        val advanced = parsed["advanced_settings"] as Map<*, *>
        assertEquals(3.0, advanced["max_results"])
        assertEquals(listOf("weather in Paris"), parsed["search_queries"])
    }

    @Test
    fun `parseResults maps v1 response fields into SearchSource`() {
        val response = mapOf(
            "search_id" to "search_abc",
            "session_id" to "session_xyz",
            "results" to listOf(
                mapOf(
                    "url" to "https://example.com/article",
                    "title" to "Example headline",
                    "publish_date" to "2024-01-15",
                    "excerpts" to listOf("First excerpt.", "Second excerpt."),
                ),
            ),
        )

        val sources = ParallelSearchV1.parseResults(response)

        assertEquals(1, sources.size)
        assertEquals(
            SearchSource(
                title = "Example headline",
                url = "https://example.com/article",
                snippet = "First excerpt.\nSecond excerpt.",
                publishedDate = "2024-01-15",
            ),
            sources.single(),
        )
    }

    @Test
    fun `parseResults falls back to url when title is blank`() {
        val response = mapOf(
            "results" to listOf(
                mapOf(
                    "url" to "https://example.com/no-title",
                    "title" to "   ",
                    "excerpts" to listOf("Snippet"),
                ),
            ),
        )

        val source = ParallelSearchV1.parseResults(response).single()
        assertEquals("https://example.com/no-title", source.title)
    }

    @Test
    fun `parseResults returns empty list when results are missing`() {
        assertTrue(ParallelSearchV1.parseResults(emptyMap<String, Any>()).isEmpty())
        assertTrue(ParallelSearchV1.parseResults(mapOf("results" to null)).isEmpty())
    }

    @Test
    fun `parseResults skips malformed entries without url`() {
        val response = mapOf(
            "results" to listOf(
                mapOf("title" to "No URL", "excerpts" to listOf("orphan")),
                mapOf("url" to "https://example.com/ok", "excerpts" to listOf("kept")),
            ),
        )

        val sources = ParallelSearchV1.parseResults(response)
        assertEquals(1, sources.size)
        assertEquals("https://example.com/ok", sources.single().url)
    }

    @Test
    fun `parseResults caps joined excerpts at 3000 characters`() {
        val longExcerpt = "x".repeat(2000)
        val response = mapOf(
            "results" to listOf(
                mapOf(
                    "url" to "https://example.com/long",
                    "title" to "Long",
                    "excerpts" to listOf(longExcerpt, longExcerpt),
                ),
            ),
        )

        val snippet = ParallelSearchV1.parseResults(response).single().snippet
        assertEquals(3000, snippet?.length)
    }
}
