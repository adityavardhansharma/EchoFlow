package com.echoflow.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** v31 gives each chat its own model. Existing chats survive with no model remembered yet. */
@RunWith(RobolectricTestRunner::class)
class ChatModelMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `existing chats survive and remember no model until they send`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(30) {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(
                        "CREATE TABLE IF NOT EXISTS chat_threads (id TEXT NOT NULL, title TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                            "kind TEXT NOT NULL DEFAULT 'chat', pinnedAt INTEGER, projectId TEXT, scheduleId TEXT, " +
                            "PRIMARY KEY(id))"
                    )
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            db.execSQL("INSERT INTO chat_threads (id, title, createdAt, updatedAt) VALUES ('c1', 'Old chat', 1, 2)")

            AppDatabase.MIGRATION_30_31.migrate(db)

            db.query("SELECT title, modelId FROM chat_threads WHERE id = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Old chat", c.getString(0))
                assertTrue(c.isNull(1))
            }
            db.execSQL("UPDATE chat_threads SET modelId = 'openai/gpt-6' WHERE id = 'c1'")
            db.query("SELECT modelId FROM chat_threads WHERE id = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("openai/gpt-6", c.getString(0))
            }
        }
    }
}
