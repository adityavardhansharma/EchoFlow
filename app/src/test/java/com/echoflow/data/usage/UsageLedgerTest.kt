package com.echoflow.data.usage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageLedgerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var dao: UsageDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.usageDao()
    }

    @After fun tearDown() = db.close()

    /** Runs [request] through the interceptor against a canned response and returns what it captured. */
    private fun capture(request: Request, contentType: String, body: String, code: Int = 200): Pair<String, List<UsageCapture>> {
        val captured = mutableListOf<UsageCapture>()
        val client = OkHttpClient.Builder()
            .addInterceptor(UsageInterceptor(sink = { captured += it }, clock = { 5_000L }))
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody(contentType.toMediaType())).build()
            }
            .build()
        val read = client.newCall(request).execute().use { it.body!!.string() }
        return read to captured
    }

    private fun post(url: String, json: String = """{"model":"from-request","messages":[]}""", auth: String = "Bearer sk-or-1") =
        Request.Builder().url(url).header("Authorization", auth).post(json.toRequestBody("application/json".toMediaType())).build()

    @Test fun `a streamed reply passes through untouched and is itemised once`() = runBlocking {
        val image = "A".repeat(5 * 1024 * 1024) // an oversized base64 line must not break capture
        val stream = buildString {
            append(": OPENROUTER PROCESSING\n\n")
            append("data: {\"id\":\"gen-9\",\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n")
            append("data: {\"id\":\"gen-9\",\"choices\":[{\"delta\":{\"images\":[{\"image_url\":{\"url\":\"data:image/png;base64,$image\"}}]}}]}\n\n")
            append("data: {\"id\":\"gen-9\",\"model\":\"openai/gpt-5.5\",\"choices\":[],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":3,\"cost\":0.0012}}\n\n")
            append("data: [DONE]\n\n")
        }
        val (read, captured) = capture(post("https://openrouter.ai/api/v1/chat/completions"), "text/event-stream", stream)
        assertEquals(stream, read)
        assertEquals(1, captured.size)
        assertEquals(1, captured.single().documents.size)

        val record = UsageLedger.write(dao, captured.single())!!
        assertEquals("OpenRouter:gen-9", record.id)
        assertEquals("openai/gpt-5.5", record.model)
        assertEquals(UsageKind.Chat.name, record.kind)
        assertEquals(UsageKeys.hash("sk-or-1"), record.keyHash)
        assertEquals(0.0012, record.costUsd!!, 1e-12)
        assertEquals(5_000L, record.createdAt)
    }

    @Test fun `failed responses, unknown hosts and keyless requests are not recorded`() {
        assertTrue(capture(post("https://openrouter.ai/api/v1/chat/completions"), "application/json", """{"usage":{"cost":1}}""", code = 402).second.isEmpty())
        assertTrue(capture(post("https://example.com/v1/chat/completions"), "application/json", """{"usage":{"cost":1}}""").second.isEmpty())
        val keyless = Request.Builder().url("https://openrouter.ai/api/v1/models").get().build()
        assertTrue(capture(keyless, "application/json", """{"usage":{"cost":1}}""").second.isEmpty())
    }

    @Test fun `the request model fills in when the response names none`() = runBlocking {
        val (_, captured) = capture(
            post("https://api.exa.ai/search", """{"query":"q","model":"exa-deep"}""", auth = "Bearer exa"),
            "application/json", """{"requestId":"r1","costDollars":{"total":0.005}}""",
        )
        val record = UsageLedger.write(dao, captured.single())!!
        assertEquals("exa-deep", record.model)
        assertEquals(UsageKind.Search.name, record.kind)

        val gemini = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?key=g&alt=sse")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        assertEquals("gemini-3.5-flash", UsageLedger.requestModel(gemini))
        val deepgram = Request.Builder().url("https://api.deepgram.com/v1/listen?model=nova-3").header("Authorization", "Token dg").build()
        assertEquals("nova-3", UsageLedger.requestModel(deepgram))
    }

    @Test fun `a polled job keeps one row and the time it was first seen`() = runBlocking {
        fun poll(credits: Int, at: Long) = UsageCapture(
            UsageProvider.Firecrawl, "k", UsageKind.Research,
            Request.Builder().url("https://api.firecrawl.dev/v2/agent/j1").build(), at,
            listOf(okio.Buffer().writeUtf8("""{"id":"j1","status":"processing","creditsUsed":$credits}""")),
        )
        UsageLedger.write(dao, poll(3, 1_000L))
        UsageLedger.write(dao, poll(9, 2_000L))
        val rows = dao.observe(UsageProvider.Firecrawl.name, "k", 0L).first()
        assertEquals(1, rows.size)
        assertEquals(1_000L, rows.single().createdAt)
        assertEquals(9.0, rows.single().credits!!, 0.0)
    }

    @Test fun `totals group by key so a replaced key starts clean`() = runBlocking {
        dao.put(UsageRecord("a", "OpenRouter", "old", 10L, "Chat", costUsd = 1.0))
        dao.put(UsageRecord("b", "OpenRouter", "new", 20L, "Chat", costUsd = 0.25))
        dao.put(UsageRecord("c", "OpenRouter", "new", 30L, "Chat", costUsd = 0.5))
        val totals = dao.observeTotals(15L).first()
        assertEquals(1, totals.size)
        assertEquals("new", totals.single().keyHash)
        assertEquals(0.75, totals.single().costUsd!!, 1e-12)
        assertEquals(2, totals.single().requests)
        assertNotEquals(UsageKeys.hash("sk-a"), UsageKeys.hash("sk-b"))
        assertEquals(16, UsageKeys.hash("sk-a").length)
    }

    @Test fun `deepgram rows wait for their price`() = runBlocking {
        dao.put(UsageRecord("Deepgram:d1", "Deepgram", "k", 1_000L, "Transcription", externalId = "d1", audioSeconds = 4.0))
        dao.put(UsageRecord("Deepgram:d2", "Deepgram", "k", 99_000L, "Transcription", externalId = "d2", audioSeconds = 4.0))
        val due = dao.unpriced("Deepgram", "k", before = 50_000L, limit = 10)
        assertEquals(listOf("Deepgram:d1"), due.map { it.id })
        dao.setCost("Deepgram:d1", 0.0006)
        assertTrue(dao.unpriced("Deepgram", "k", before = 50_000L, limit = 10).isEmpty())
        assertEquals(0.0006, DeepgramCosts.requestUsd("""{"request":{"request_id":"d1","response":{"details":{"usd":0.0006,"duration":4.0}}}}""")!!, 1e-12)
        assertEquals("p1", DeepgramCosts.projectId("""{"projects":[{"project_id":"p1","name":"Mine"}]}"""))
        assertNull(DeepgramCosts.requestUsd("""{"request":{"response":{"details":{}}}}"""))
    }

    @Test fun `openrouter key figures parse`() {
        val spend = OpenRouterKeyUsage.parse(
            """{"data":{"label":"sk-or-v1-abc","usage":42.5,"usage_daily":0.5,"usage_weekly":3.25,"usage_monthly":12.0,"limit":100,"limit_remaining":57.5}}""",
            "k", 7L,
        )!!
        assertEquals(42.5, spend.total, 0.0)
        assertEquals(0.5, spend.today, 0.0)
        assertEquals(3.25, spend.week, 0.0)
        assertEquals(12.0, spend.month, 0.0)
        assertEquals(57.5, spend.limitRemaining!!, 0.0)
        assertNull(OpenRouterKeyUsage.parse("""{"error":{"code":401}}""", "k", 7L))
    }

    @Test fun `migration 29 to 30 creates the ledger`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                }).build()
        )
        helper.use {
            val raw = it.writableDatabase
            AppDatabase.MIGRATION_29_30.migrate(raw)
            raw.execSQL("INSERT INTO usage_records (id, provider, keyHash, createdAt, kind, costUsd) VALUES ('x', 'OpenRouter', 'k', 1, 'Chat', 0.5)")
            raw.query("SELECT costUsd, model FROM usage_records WHERE id = 'x'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0.5, c.getDouble(0), 0.0)
                assertTrue(c.isNull(1))
            }
        }
    }
}
