package com.echoflow.data.memory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.ChatMessage
import com.echoflow.data.StreamChunk
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class MemoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private fun api(code: Int = 200, response: (String) -> String = { "{}" }): SupermemoryClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer test-key", request.header("Authorization"))
            val buffer = Buffer(); request.body?.writeTo(buffer)
            val raw = buffer.readUtf8()
            requests.add(request.url.encodedPath to JSONObject(raw.ifBlank { "{}" }))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(response(request.url.encodedPath).toResponseBody("application/json".toMediaType())).build()
        }.build()
        return SupermemoryClient("test-key", "test-user", http)
    }
    private fun settings(): MemorySettings = MemorySettings(context.getSharedPreferences("memory-test", Context.MODE_PRIVATE)).apply {
        disconnect(); connect("test-key", "test-user"); recall = true
    }

    @Test fun `list uses memoryEntries and respects pagination and forgotten records`() = runBlocking {
        val page = api { """{"memoryEntries":[{"id":"m1","memory":"Likes cricket","history":[{"memory":"Likes sport"}],"documentIds":["d1"]},{"id":"m2","memory":"Old fact","isForgotten":true}],"pagination":{"totalPages":2}}""" }.list()
        assertEquals(listOf("Likes cricket"), page.entries.map { it.text })
        assertEquals(listOf("Likes sport"), page.entries.single().history)
        assertTrue(page.hasMore)
        assertEquals("test-user", requests.single().second.getJSONArray("containerTags").getString(0))
    }
    @Test fun `search sends focused query not full profile`() = runBlocking {
        api { """{"results":[{"id":"m1","memory":"Building EchoFlow"}]}""" }.search("EchoFlow project decisions")
        val body = requests.single().second
        assertEquals("EchoFlow project decisions", body.getString("q"))
        assertEquals("memories", body.getString("searchMode"))
        assertEquals(8, body.getInt("limit"))
        assertEquals(0.45, body.getDouble("threshold"), 0.001)
        assertTrue(body.getBoolean("rerank"))
        assertTrue(body.getBoolean("rewriteQuery"))
        assertEquals("test-user", body.getString("containerTag"))
    }
    @Test fun `explicit save is immediate deduplicated and only reports success after request`() = runBlocking {
        val events = mutableListOf<StreamChunk>()
        val tools = MemoryTools(context, "chat", false, settings(), api { """{"memories":[{"id":"m1","memory":"I like cricket"}]}""" })
        repeat(2) { tools.execute("remember_memory", """{"content":"I like cricket"}""") { events += it } }
        assertEquals(1, requests.size)
        assertEquals("/v4/memories", requests.single().first)
        assertTrue((events.first() as StreamChunk.MemoryActivity).active)
        assertEquals("Memory saved", (events.last() as StreamChunk.MemoryActivity).label)
    }
    @Test fun `quota failure is not a successful save and does not leak response body`() = runBlocking {
        val events = mutableListOf<StreamChunk>()
        val tools = MemoryTools(context, "chat", false, settings(), api(402) { "secret provider response" })
        val result = tools.execute("remember_memory", """{"content":"I like cricket"}""") { events += it }
        assertTrue(result.contains("failed"))
        assertFalse(result.contains("secret provider response"))
        assertFalse((events.last() as StreamChunk.MemoryActivity).active)
    }
    @Test fun `disconnect invalidates tools already created for a turn`() = runBlocking {
        val settings = settings()
        val tools = MemoryTools(context, "chat", false, settings, api())
        settings.disconnect()
        assertEquals("Memory is disabled.", tools.execute("search_memory", """{"query":"project"}""") {})
        assertTrue(requests.isEmpty())
    }
    @Test fun `billing credits are not inferred from token counts`() {
        assertNull(SupermemoryClient.findCredits(JSONObject("""{"usage":{"tokens":{"used":200,"limit":500}}}""")))
        val credit = SupermemoryClient.findCredits(JSONObject("""{"features":[{"id":"usd_credits","used":2,"limit":20}]}"""))
        assertEquals(2, credit!!.getInt("used"))
    }
    @Test fun `plan policy includes current Max and safe unknown fallback`() {
        assertEquals(5, MemorySettings.batchSize("free")); assertEquals(5, MemorySettings.batchSize("unknown"))
        assertEquals(3, MemorySettings.batchSize("api_pro")); assertEquals(1, MemorySettings.batchSize("max"))
        assertEquals(1, MemorySettings.batchSize("scale")); assertEquals(1, MemorySettings.batchSize("enterprise"))
    }
    @Test fun `transcript contains only eligible user authored text`() {
        val old = ChatMessage("1", "chat", "user", "old secret", 1)
        val current = ChatMessage("2", "chat", "assistant", "Visible reply", 3,
            reasoning = "private reasoning", toolEventsJson = "tool content", attachmentsJson = "attachment text")
        val system = ChatMessage("3", "chat", "system", "system instruction", 3)
        val text = MemoryLearning.transcript(listOf(old, current, system), 2)
        assertEquals("", text) // The reply belongs to a pre-consent user turn.
        val eligible = ChatMessage("4", "chat", "user", "Current question", 2)
        assertEquals("user: Current question",
            MemoryLearning.transcript(listOf(old, eligible, current, system), 2))
        assertEquals(MemoryLearning.revision(text), MemoryLearning.revision(text))
        assertNotEquals(MemoryLearning.revision(text), MemoryLearning.revision("changed"))
    }
    @Test fun `bulk automatic facts use one request`() = runBlocking {
        val api = api { """{"memories":[{"id":"m1","memory":"Name"},{"id":"m2","memory":"Age"}]}""" }
        api.addAll(listOf("The user's name is Aditya.", "The user is 22 years old."))
        assertEquals(1, requests.size)
        assertEquals(2, requests.single().second.getJSONArray("memories").length())
        assertEquals("automatic", requests.single().second.getJSONArray("memories").getJSONObject(0)
            .getJSONObject("metadata").getString("source"))
    }
    @Test fun `search removes assistant meta memories and duplicates`() = runBlocking {
        val found = api { """{"results":[
            {"id":"m1","memory":"The assistant can retrieve the user's name."},
            {"id":"m2","memory":"The user's name is Aditya."},
            {"id":"m3","memory":"The user's name is Aditya!"}
        ]}""" }.search("name")
        assertEquals(listOf("The user's name is Aditya."), found.map { it.text })
    }
    @Test fun `identity recall combines profile and memory search`() = runBlocking {
        val tools = MemoryTools(context, "chat", false, settings(), api { path -> when (path) {
            "/v4/profile" -> """{"profile":{"static":["The user's name is Aditya."],"dynamic":[]}}"""
            else -> """{"results":[]}"""
        } })
        val result = tools.execute("search_memory", """{"query":"user's name or preferred name"}""") {}
        assertEquals(listOf("/v4/profile", "/v4/search"), requests.map { it.first })
        assertTrue(result.contains("Aditya"))
    }
    @Test fun `assistant meta claim is rejected before a write`() = runBlocking {
        val tools = MemoryTools(context, "chat", false, settings(), api())
        val result = tools.execute("remember_memory", """{"content":"The assistant can retrieve my name."}""") {}
        assertTrue(result.contains("Do not save claims"))
        assertTrue(requests.isEmpty())
    }
    @Test fun `unavailable encrypted storage fails closed`() {
        val settings = MemorySettings(null)
        assertFalse(settings.connected)
        assertThrows(IllegalStateException::class.java) { settings.connect("key", "space") }
    }
    @Test fun `profile cache is bounded and cleared on reconnect`() {
        val prefs = context.getSharedPreferences("memory-cache-test", Context.MODE_PRIVATE)
        val settings = MemorySettings(prefs)
        settings.disconnect(); settings.connect("key", "space")
        settings.cacheProfile(MemoryProfile(listOf("Name is Aditya"), listOf("Building EchoFlow")))
        assertEquals(listOf("Name is Aditya"), settings.cachedProfile()!!.stable)
        assertNull(settings.cachedProfile(maxAgeMs = -1))
        settings.connect("new-key", "space")
        assertNull(settings.cachedProfile())
    }
    @Test fun `known credentials are redacted without erasing ordinary project context`() {
        val text = "I'm building EchoFlow. api_key=abcdefgh12345678 and Bearer abcdefgh1234567890"
        val redacted = MemoryPrivacy.redact(text)
        assertTrue(redacted.contains("building EchoFlow"))
        assertFalse(redacted.contains("abcdefgh"))
    }
    @Test fun `empty creation response never becomes a saved confirmation`() = runBlocking {
        val events = mutableListOf<StreamChunk>()
        val tools = MemoryTools(context, "chat", false, settings(), api())
        val result = tools.execute("remember_memory", """{"content":"I like cricket"}""") { events += it }
        assertTrue(result.contains("failed"))
        assertFalse(events.any { it is StreamChunk.MemoryActivity && it.label == "Memory saved" })
    }
}
