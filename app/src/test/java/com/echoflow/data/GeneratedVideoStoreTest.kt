package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class GeneratedVideoStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var store: GeneratedVideoStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        store = GeneratedVideoStore(context, database.generatedVideoDao())
        runBlocking { database.chatDao().insertThread(ChatThread("chat-1", "Chat", 1L, 1L)) }
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "generated_videos").deleteRecursively()
    }

    @Test fun `a job row exists before the provider is ever called`() = runBlocking {
        val job = store.createJob("chat-1", "a kite over dunes", "google/veo-3.1", "16:9", "720p")

        assertEquals(GeneratedVideo.STATUS_QUEUED, job.status)
        assertNull(job.filePath)
        assertEquals(job.id, database.generatedVideoDao().getById(job.id)?.id)
        // Nothing playable yet, so the chat must not treat it as the latest clip.
        assertNull(store.latestForChat("chat-1"))
    }

    @Test fun `the provider handle is stored so a cold start can resume the run`() = runBlocking {
        val job = store.createJob("chat-1", "a kite", "google/veo-3.1", "16:9", "720p")
        store.markSubmitted(job, jobId = "job-42", pollingUrl = "https://openrouter.ai/api/v1/videos/job-42")

        val unfinished = store.unfinished()
        assertEquals(1, unfinished.size)
        assertEquals("job-42", unfinished.first().jobId)
        assertEquals("https://openrouter.ai/api/v1/videos/job-42", unfinished.first().pollingUrl)
        assertEquals(GeneratedVideo.STATUS_PENDING, unfinished.first().status)
    }

    @Test fun `download completes the row and leaves no partial file`() = runBlocking {
        val job = store.createJob("chat-1", "a kite", "google/veo-3.1", "16:9", "720p")
        val submitted = store.markSubmitted(job, "job-42", "https://example.test/job-42")

        val done = store.completeWithDownload(submitted, ByteArrayInputStream(ByteArray(2048) { 7 }))

        val file = File(done.filePath!!)
        assertTrue(file.isFile)
        assertEquals(2048L, file.length())
        assertTrue(file.name.endsWith(".mp4"))
        assertFalse(file.parentFile!!.listFiles()!!.any { it.name.endsWith(".tmp") })
        assertEquals(GeneratedVideo.STATUS_COMPLETED, done.status)
        assertEquals(done.id, store.latestForChat("chat-1")?.id)
        // A completed run is no longer a resume candidate.
        assertTrue(store.unfinished().isEmpty())
    }

    @Test fun `a connection dropped mid-download leaves neither file nor completed row`() = runBlocking {
        val job = store.createJob("chat-1", "a kite", "google/veo-3.1", "16:9", "720p")
        val submitted = store.markSubmitted(job, "job-42", "https://example.test/job-42")

        val truncating: InputStream = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw java.io.IOException("connection reset")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served >= 512) throw java.io.IOException("connection reset")
                served += len.coerceAtMost(512)
                return len.coerceAtMost(512)
            }
        }

        try {
            store.completeWithDownload(submitted, truncating)
            throw AssertionError("Expected the interrupted download to fail")
        } catch (_: java.io.IOException) {
            // Expected — a half-written clip must never be presented as playable.
        }

        val dir = File(context.filesDir, "generated_videos")
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertNull(database.generatedVideoDao().getById(submitted.id)?.filePath)
    }

    @Test fun `deleting a chat's files removes the MP4s before the rows cascade away`() = runBlocking {
        val job = store.createJob("chat-1", "a kite", "google/veo-3.1", "16:9", "720p")
        val done = store.completeWithDownload(job, ByteArrayInputStream(ByteArray(16)))
        assertTrue(File(done.filePath!!).exists())

        store.deleteFilesForChat("chat-1")

        assertFalse(File(done.filePath).exists())
    }
}
