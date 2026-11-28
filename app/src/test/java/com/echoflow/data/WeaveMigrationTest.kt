package com.echoflow.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v33 adds Echo UI storage (05 §9.2). Existing chats and messages survive untouched. */
@RunWith(RobolectricTestRunner::class)
class WeaveMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun v32(): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(32) {
                override fun onConfigure(db: SupportSQLiteDatabase) = db.execSQL("PRAGMA foreign_keys = ON")
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS chat_threads (id TEXT NOT NULL, title TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                            "kind TEXT NOT NULL DEFAULT 'chat', pinnedAt INTEGER, projectId TEXT, scheduleId TEXT, " +
                            "modelId TEXT, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS chat_messages (id TEXT NOT NULL, chatId TEXT NOT NULL, " +
                            "role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            }).build()
    )

    private fun SupportSQLiteDatabase.insertBlock(id: String, messageId: String, key: Int) = execSQL(
        "INSERT INTO weave_blocks (id, chatId, messageId, blockKey, blockId, version, form, source, stateJson, " +
            "historyJson, lazyJson, queryJson, consentJson, firedJson, askLogJson, uiStateJson, createdAt, updatedAt) " +
            "VALUES ('$id', 'c1', '$messageId', $key, 'b1', 1, 'source', 'root = Text(\"hi\")', '{}', '[]', '{}', " +
            "'{}', '{}', '{}', '[]', '{}', 1, 1)"
    )

    @Test fun `chats keep following the global level and blocks cascade with their message`() {
        v32().use {
            val db = it.writableDatabase
            db.execSQL("INSERT INTO chat_threads (id, title, createdAt, updatedAt) VALUES ('c1', 'Old chat', 1, 2)")
            db.execSQL("INSERT INTO chat_messages (id, chatId, role, content, createdAt) VALUES ('m1', 'c1', 'assistant', 'Hi', 1)")

            AppDatabase.MIGRATION_32_33.migrate(db)

            db.query("SELECT title, intelligentUi FROM chat_threads WHERE id = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Old chat", c.getString(0))
                assertTrue(c.isNull(1))
            }

            db.insertBlock("w1", "m1", 0)
            db.execSQL("INSERT INTO weave_versions (blockRowId, version, source, cause, createdAt) VALUES ('w1', 1, 's', 'stream', 1)")
            db.query("SELECT COUNT(*) FROM weave_blocks").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }

            db.execSQL("DELETE FROM chat_messages WHERE id = 'm1'")
            db.query("SELECT COUNT(*) FROM weave_blocks").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM weave_versions").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun `one row per block of a message`() {
        v32().use {
            val db = it.writableDatabase
            db.execSQL("INSERT INTO chat_messages (id, chatId, role, content, createdAt) VALUES ('m1', 'c1', 'assistant', 'Hi', 1)")
            AppDatabase.MIGRATION_32_33.migrate(db)
            db.insertBlock("w1", "m1", 0)
            db.insertBlock("w2", "m1", 40)
            val duplicate = runCatching { db.insertBlock("w3", "m1", 40) }
            assertFalse(duplicate.isSuccess)
            db.execSQL(
                "INSERT INTO weave_library (id, title, icon, source, stateJson, lazyJson, pinnedWidgetId, createdAt, updatedAt) " +
                    "VALUES ('l1', 'Tip splitter', 'payments', 's', '{}', '{}', NULL, 1, 1)"
            )
            db.query("SELECT COUNT(*) FROM weave_library").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }
}
