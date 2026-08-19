package com.echoflow.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.extract.FileExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * anydoc holds a full file in memory (up to 25 MB). The manager must therefore parse
 * at most [ProjectManager.EXTRACT_BATCH_SIZE] files at once; the rest sit PENDING.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectManagerImportBatchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var hold: CompletableDeferred<Unit>
    private lateinit var extractor: CountingExtractor
    private lateinit var manager: ProjectManager

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hold = CompletableDeferred()
        extractor = CountingExtractor(hold)
        manager = ProjectManager(
            context = context,
            projectDao = database.projectDao(),
            projectDocumentDao = database.projectDocumentDao(),
            chatDao = database.chatDao(),
            extractor = extractor,
        )
    }

    @After fun tearDown() {
        hold.complete(Unit)
        database.close()
        File(context.filesDir, "project_documents").deleteRecursively()
    }

    @Test fun `extracts four files at a time and queues the rest`() = runBlocking {
        assertEquals(4, ProjectManager.EXTRACT_BATCH_SIZE)
        val projectId = manager.createProject("Batch")
        val admitted = AtomicInteger(0)
        val jobs = (1..6).map { i ->
            async(Dispatchers.IO) {
                val file = File(context.cacheDir, "batch-$i.docx").apply { writeText("doc $i") }
                manager.addDocument(projectId, Uri.fromFile(file), onAdmitted = { admitted.incrementAndGet() })
            }
        }

        withTimeout(5_000) {
            while (true) {
                val rows = database.projectDocumentDao().getForProjectSync(projectId)
                if (rows.size == 6 &&
                    admitted.get() == 6 &&
                    rows.count { it.status == ExtractionStatus.EXTRACTING } == 4 &&
                    rows.count { it.status == ExtractionStatus.PENDING } == 2
                ) break
                delay(10)
            }
        }
        assertEquals(4, extractor.maxInFlight.get())
        // Admission is the Room insert, not extract finishing — all six are in before anydoc resumes.
        assertEquals(6, admitted.get())

        hold.complete(Unit)
        val added = jobs.awaitAll()
        assertTrue(added.all { it != null })
        val done = database.projectDocumentDao().getForProjectSync(projectId)
        assertEquals(6, done.size)
        assertTrue(done.all { it.status == ExtractionStatus.EXTRACTED })
        assertEquals(4, extractor.maxInFlight.get())
    }

    private class CountingExtractor(
        private val hold: CompletableDeferred<Unit>,
    ) : FileExtractor() {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        override suspend fun extract(file: File, mime: String, name: String): Result {
            val n = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(n) { a, b -> maxOf(a, b) }
            try {
                hold.await()
                return Result("ok", ExtractionStatus.EXTRACTED, ExtractionTier.ANYDOC)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }
}
