package com.echoflow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactManager
import com.echoflow.data.ChatMessage
import com.echoflow.data.ChatThread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistenceCharacterizationTest {
    private lateinit var db: AppDatabase

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = db.close()

    @Test
    fun threadsAndMessagesUseTheirDocumentedOrdering() = runBlocking {
        db.chatDao().insertThread(thread("older", created = 1, updated = 10))
        db.chatDao().insertThread(thread("newer", created = 2, updated = 20))
        db.messageDao().insertMessage(message("second", "older", "later", 200))
        db.messageDao().insertMessage(message("first", "older", "earlier", 100))

        assertEquals(listOf("newer", "older"), db.chatDao().getAllThreads().first().map { it.id })
        assertEquals(listOf("first", "second"), db.messageDao().getMessagesForChatSync("older").map { it.id })
    }

    @Test
    fun messageSearchIsSubstringBasedAndThreadDeleteCascades() = runBlocking {
        val chat = thread("chat", 1, 1)
        db.chatDao().insertThread(chat)
        db.messageDao().insertMessage(message("message", chat.id, "A Mixed CASE needle appears.", 1))

        assertEquals(listOf(chat.id), db.messageDao().searchChatIdsByContent("case NEED").first())

        db.chatDao().deleteThread(chat)

        assertTrue(db.messageDao().getMessagesForChatSync(chat.id).isEmpty())
    }

    @Test
    fun artifactManagerCreatesOneLineageAndAppendsOrderedVersions() = runBlocking {
        db.chatDao().insertThread(thread("chat", 1, 1))
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())

        val first = manager.saveVersion("chat", "", "md", "one", "first prompt")
        val second = manager.saveVersion("chat", "", Artifact.TYPE_MARKDOWN, "two", "second prompt")

        assertEquals(first.artifactId, second.artifactId)
        assertEquals(1, first.version)
        assertEquals(2, second.version)
        assertEquals("Document", second.title)
        assertEquals(Artifact.TYPE_MARKDOWN, second.type)
        assertEquals("two", manager.getLatestVersionContent("chat"))
        assertEquals(listOf(1, 2), manager.observeVersions(first.artifactId).first().map { it.versionNumber })
    }

    @Test
    fun artifactRowsCascadeWhenOwningThreadIsDeleted() = runBlocking {
        val chat = thread("chat", 1, 1)
        db.chatDao().insertThread(chat)
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())
        manager.saveVersion(chat.id, "Draft", "html", "<p>body</p>", "prompt")

        db.chatDao().deleteThread(chat)

        assertNull(manager.getLatestForChat(chat.id))
    }

    private fun thread(id: String, created: Long, updated: Long) =
        ChatThread(id, id, created, updated)

    private fun message(id: String, chatId: String, content: String, created: Long) =
        ChatMessage(id, chatId, "user", content, created)
}
