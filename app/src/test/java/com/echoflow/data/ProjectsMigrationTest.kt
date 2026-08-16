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
 * Projects ship behind a schema bump (v22). The promise is that the change is purely additive:
 * every existing conversation survives untouched and simply starts life as a loose chat (null
 * projectId). These tests build a v21 `chat_threads` table by hand, run
 * [AppDatabase.MIGRATION_21_22] against it, and assert the new tables/column exist and that a
 * pre-existing thread is preserved with a null project.
 *
 * The risk here is entirely in the three statements of MIGRATION_21_22 (one ALTER, two CREATEs),
 * so we exercise those directly rather than through Room's device-only MigrationTestHelper.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectsMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    /** `chat_threads` as of v21 — name-equivalent (assertions select by name). */
    private val createV21 = """
        CREATE TABLE IF NOT EXISTS chat_threads (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            kind TEXT NOT NULL DEFAULT 'chat',
            pinnedAt INTEGER
        )
    """.trimIndent()

    @Before fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(createV21)
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After fun tearDown() = helper.close()

    private fun insertThread(id: String) {
        db.execSQL(
            "INSERT INTO chat_threads (id, title, createdAt, updatedAt, kind) VALUES (?, ?, ?, ?, ?)",
            arrayOf(id, "Existing chat", 1_000L, 2_000L, "chat"),
        )
    }

    @Test fun `existing threads survive and become loose chats`() {
        insertThread("thread-1")

        AppDatabase.MIGRATION_21_22.migrate(db)

        db.query("SELECT id, title, projectId FROM chat_threads WHERE id = 'thread-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("thread-1", c.getString(0))
            assertEquals("Existing chat", c.getString(1))
            assertNull(c.getString(2)) // null projectId — a loose chat
        }
    }

    @Test fun `projects and documents can be created and a chat linked`() {
        insertThread("thread-1")
        AppDatabase.MIGRATION_21_22.migrate(db)

        db.execSQL(
            "INSERT INTO projects (id, name, instructions, colorIndex, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("proj-1", "Novel", "Keep a noir tone.", 2, 10L, 20L),
        )
        db.execSQL(
            "INSERT INTO project_documents (id, projectId, name, mimeType, sizeBytes, filePath, extractedText, addedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("doc-1", "proj-1", "notes.txt", "text/plain", 12L, "/tmp/doc-1", "hello", 30L),
        )
        db.execSQL("UPDATE chat_threads SET projectId = 'proj-1' WHERE id = 'thread-1'")

        db.query(
            "SELECT t.projectId, p.name, d.extractedText FROM chat_threads t " +
                "JOIN projects p ON p.id = t.projectId " +
                "JOIN project_documents d ON d.projectId = p.id WHERE t.id = 'thread-1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("proj-1", c.getString(0))
            assertEquals("Novel", c.getString(1))
            assertEquals("hello", c.getString(2))
        }
    }

    @Test fun `deleting a project cascades documents but not chats`() {
        insertThread("thread-1")
        AppDatabase.MIGRATION_21_22.migrate(db)
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL(
            "INSERT INTO projects (id, name, instructions, colorIndex, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("proj-1", "Novel", "", 0, 10L, 20L),
        )
        db.execSQL(
            "INSERT INTO project_documents (id, projectId, name, mimeType, sizeBytes, filePath, extractedText, addedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("doc-1", "proj-1", "notes.txt", "text/plain", 12L, "/tmp/doc-1", "hello", 30L),
        )
        db.execSQL("UPDATE chat_threads SET projectId = 'proj-1' WHERE id = 'thread-1'")

        // The app clears assignments in code before deleting (no FK on chat_threads.projectId).
        db.execSQL("UPDATE chat_threads SET projectId = NULL WHERE projectId = 'proj-1'")
        db.execSQL("DELETE FROM projects WHERE id = 'proj-1'")

        db.query("SELECT COUNT(*) FROM project_documents").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) // cascaded away
        }
        db.query("SELECT COUNT(*) FROM chat_threads").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) // chat kept
        }
    }
}
