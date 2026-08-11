package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room-path behaviour for research runs.
 *
 * [ResearchMigrationTest] covers the SQL column default; these cover what that default cannot
 * reach — the entity round trip, where `ResearchRun.uiVersion` defaults to `UI_VERSION_CURRENT` in
 * Kotlin and only rows read back from the database carry the legacy value.
 */
@RunWith(RobolectricTestRunner::class)
class ResearchRunDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ResearchRunDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.researchRunDao()
    }

    @After fun tearDown() = db.close()

    private fun run(
        id: String,
        chatId: String = "chat-1",
        status: String = ResearchRun.STATUS_QUEUED,
        uiVersion: Int = ResearchRun.UI_VERSION_CURRENT,
    ) = ResearchRun(
        id = id,
        chatId = chatId,
        topic = "How do heat pumps work?",
        engineId = "exa-deep",
        engineKind = "provider",
        engineLabel = "Exa Deep",
        status = status,
        uiVersion = uiVersion,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test fun `a resumed legacy run stays on the legacy UI when re-persisted`() = runBlocking {
        // The service reloads a run and writes it back on every event. Since the Kotlin default is
        // UI_VERSION_CURRENT, a copy() that dropped the loaded value would silently switch an
        // in-flight run to the new UI mid-run -- the exact thing the version stamp exists to stop.
        dao.upsert(run("run-1", uiVersion = ResearchRun.UI_VERSION_LEGACY))

        val loaded = dao.getById("run-1")!!
        assertTrue(loaded.usesLegacyUi)

        dao.upsert(loaded.copy(status = ResearchRun.STATUS_RESEARCHING, phase = "Researching…"))

        val reloaded = dao.getById("run-1")!!
        assertEquals(ResearchRun.UI_VERSION_LEGACY, reloaded.uiVersion)
        assertTrue(reloaded.usesLegacyUi)
    }

    @Test fun `a newly constructed run defaults to the current UI`() = runBlocking {
        dao.upsert(run("run-2"))
        assertFalse(dao.getById("run-2")!!.usesLegacyUi)
    }

    @Test fun `insertIfChatIdle refuses a second run while one is in flight`() = runBlocking {
        assertTrue(dao.insertIfChatIdle(run("run-a", status = ResearchRun.STATUS_RESEARCHING)))
        // Two retry taps racing: the second must lose, or the app starts two foreground services
        // for one conversation.
        assertFalse(dao.insertIfChatIdle(run("run-b")))
        assertEquals(1, dao.countActiveForChat("chat-1"))
    }

    @Test fun `insertIfChatIdle allows a retry once the previous run is terminal`() = runBlocking {
        dao.upsert(run("run-a", status = ResearchRun.STATUS_FAILED))
        assertTrue(dao.insertIfChatIdle(run("run-b")))
        assertEquals(1, dao.countActiveForChat("chat-1"))
    }

    @Test fun `an active run in another chat does not block a retry`() = runBlocking {
        dao.upsert(run("run-a", chatId = "chat-other", status = ResearchRun.STATUS_RESEARCHING))
        assertTrue(dao.insertIfChatIdle(run("run-b", chatId = "chat-1")))
    }
}
