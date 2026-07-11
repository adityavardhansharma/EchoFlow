package com.echoflow

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Verifies that users of the first release can upgrade without losing conversations. */
@RunWith(RobolectricTestRunner::class)
class DatabaseUpgradeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "local_chat_database"
    private var openedDatabase: AppDatabase? = null

    @Before
    fun createVersionOneDatabase() {
        context.deleteDatabase(databaseName)
        val file = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE chat_threads (" +
                    "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE chat_messages (" +
                    "id TEXT NOT NULL PRIMARY KEY, chatId TEXT NOT NULL, role TEXT NOT NULL, " +
                    "content TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                    "localAttachmentUri TEXT, localAttachmentMimeType TEXT, localAttachmentName TEXT, " +
                    "FOREIGN KEY(chatId) REFERENCES chat_threads(id) ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX index_chat_messages_chatId ON chat_messages (chatId)")
            db.execSQL("CREATE TABLE custom_models (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("INSERT INTO chat_threads VALUES ('thread-1', 'Saved chat', 100, 200)")
            db.execSQL(
                "INSERT INTO chat_messages VALUES (" +
                    "'message-1', 'thread-1', 'user', 'keep me', 150, " +
                    "'content://attachment', 'application/pdf', 'saved.pdf')"
            )
            db.execSQL("INSERT INTO custom_models VALUES ('model-1', 'Saved model')")
            db.version = 1
        }
    }

    @After
    fun cleanUp() {
        openedDatabase?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `production database upgrades version one through version twelve without data loss`() {
        val database = AppDatabase.getDatabase(context).also { openedDatabase = it }

        database.openHelper.readableDatabase.query(
            "SELECT content, reasoning, localAttachmentUri, localAttachmentName " +
                "FROM chat_messages WHERE id = 'message-1'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("keep me", cursor.getString(0))
            assertEquals(null, cursor.getString(1))
            assertEquals("content://attachment", cursor.getString(2))
            assertEquals("saved.pdf", cursor.getString(3))
        }
        runBlocking {
            assertEquals("Saved chat", database.chatDao().getThreadById("thread-1")?.title)
            assertEquals("Saved model", database.customModelDao().getCustomModelById("model-1")?.name)
        }
    }
}
