package com.echoflow.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Deep Research timeline redesign ships behind a schema bump, and the whole point of the
 * split is that existing research survives it untouched. These tests build a v20 `research_runs`
 * table by hand, run [AppDatabase.MIGRATION_20_21] against it, and assert that every pre-existing
 * row keeps its content and lands on the legacy UI version.
 *
 * Room's own MigrationTestHelper lives in androidTest (needs a device); the risk here is entirely
 * in the two ALTER statements, so we exercise those directly.
 */
@RunWith(RobolectricTestRunner::class)
class ResearchMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** `research_runs` exactly as v20 declared it — the shape a shipped install would hold. */
    private val createV20 = """
        CREATE TABLE IF NOT EXISTS research_runs (
            id TEXT NOT NULL PRIMARY KEY,
            chatId TEXT NOT NULL,
            topic TEXT NOT NULL,
            engineId TEXT NOT NULL,
            engineKind TEXT NOT NULL,
            engineLabel TEXT NOT NULL,
            searchProvider TEXT,
            level TEXT,
            costInfo TEXT,
            maxSearches INTEGER NOT NULL,
            maxSources INTEGER NOT NULL,
            maxCredits INTEGER NOT NULL,
            providerJobId TEXT,
            status TEXT NOT NULL,
            phase TEXT,
            progressDone INTEGER NOT NULL,
            progressTotal INTEGER NOT NULL,
            planJson TEXT,
            sourcesJson TEXT,
            report TEXT,
            error TEXT,
            assistantMessageId TEXT,
            localAttachmentUri TEXT,
            localAttachmentMimeType TEXT,
            localAttachmentName TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
    """.trimIndent()

    @Before fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(createV20)
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After fun tearDown() = helper.close()

    private fun insertV20Run(id: String, status: String, report: String?) {
        db.execSQL(
            """
            INSERT INTO research_runs (
                id, chatId, topic, engineId, engineKind, engineLabel,
                maxSearches, maxSources, maxCredits, status, phase,
                progressDone, progressTotal, planJson, sourcesJson, report,
                createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                id, "chat-1", "How do heat pumps work?", "exa-deep", "provider", "Exa Deep",
                5, 20, 2500, status, "Completed",
                3, 3, """["sub question one","sub question two"]""",
                """[{"title":"Example","url":"https://example.com"}]""", report,
                1_000L, 2_000L,
            )
        )
    }

    @Test fun `migration preserves existing research runs`() {
        insertV20Run("run-1", ResearchRun.STATUS_COMPLETED, "# Findings\n\nHeat pumps move heat.")
        insertV20Run("run-2", ResearchRun.STATUS_RESEARCHING, null)

        AppDatabase.MIGRATION_20_21.migrate(db)

        db.query("SELECT id, topic, report, planJson, sourcesJson, status FROM research_runs ORDER BY id").use { c ->
            assertEquals(2, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("run-1", c.getString(0))
            assertEquals("How do heat pumps work?", c.getString(1))
            assertEquals("# Findings\n\nHeat pumps move heat.", c.getString(2))
            assertEquals("""["sub question one","sub question two"]""", c.getString(3))
            assertEquals("""[{"title":"Example","url":"https://example.com"}]""", c.getString(4))
            assertEquals(ResearchRun.STATUS_COMPLETED, c.getString(5))
            assertTrue(c.moveToNext())
            assertEquals("run-2", c.getString(0))
            assertEquals(ResearchRun.STATUS_RESEARCHING, c.getString(5))
        }
    }

    @Test fun `migrated rows are stamped legacy so they keep the old UI`() {
        insertV20Run("run-1", ResearchRun.STATUS_COMPLETED, "report body")

        AppDatabase.MIGRATION_20_21.migrate(db)

        db.query("SELECT uiVersion, stepsJson FROM research_runs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(ResearchRun.UI_VERSION_LEGACY, c.getInt(0))
            assertNull(c.getString(1))
        }
    }

    @Test fun `runs inserted after the migration default to legacy unless stamped`() {
        AppDatabase.MIGRATION_20_21.migrate(db)
        insertV20Run("run-new", ResearchRun.STATUS_QUEUED, null)

        // A row written without an explicit uiVersion must never silently become a v2 run —
        // that is what guarantees an old resumed run cannot switch skins mid-flight.
        db.query("SELECT uiVersion FROM research_runs WHERE id = 'run-new'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(ResearchRun.UI_VERSION_LEGACY, c.getInt(0))
        }
    }

    @Test fun `current ui version is ahead of legacy`() {
        assertTrue(ResearchRun.UI_VERSION_CURRENT > ResearchRun.UI_VERSION_LEGACY)
    }
}
