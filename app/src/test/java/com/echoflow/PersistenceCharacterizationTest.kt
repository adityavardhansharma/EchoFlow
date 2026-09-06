package com.echoflow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.AppDatabase
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactDao
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
    fun artifactVersionFailureRollsBackLineageUpdate() = runBlocking {
        db.chatDao().insertThread(thread("chat", 1, 1))
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())
        val first = manager.saveVersion("chat", "Original", "html", "one", "first")
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_artifact_version BEFORE INSERT ON artifact_versions
            BEGIN SELECT RAISE(ABORT, 'injected failure'); END
        """.trimIndent())
        var failed = false
        try { manager.saveVersion("chat", "Changed", "html", "two", "second") }
        catch (_: android.database.sqlite.SQLiteException) { failed = true }
        assertTrue(failed)
        assertEquals("Original", manager.getById(first.artifactId)?.title)
        assertEquals("one", manager.getLatestVersionContent("chat"))
        assertEquals(1, manager.observeVersions(first.artifactId).first().size)
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

    @Test
    fun hidingFromGalleryLeavesChatAndArtifact() = runBlocking {
        val chat = thread("chat", 1, 1)
        db.chatDao().insertThread(chat)
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())
        val ref = manager.saveVersion(chat.id, "Draft", "html", "<p>body</p>", "prompt")

        assertEquals(1, db.artifactDao().observeListed().first().size)
        manager.hideFromGallery(ref.artifactId)

        assertTrue(db.artifactDao().observeListed().first().isEmpty())
        assertEquals(ref.artifactId, manager.getLatestForChat(chat.id)?.id)
        assertEquals("chat", db.chatDao().getThreadById(chat.id)?.id)
        assertEquals(true, manager.getById(ref.artifactId)?.hiddenFromGallery)
    }

    @Test
    fun newVersionKeepsGalleryHide() = runBlocking {
        db.chatDao().insertThread(thread("chat", 1, 1))
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())
        val first = manager.saveVersion("chat", "Draft", "html", "<p>one</p>", "p1")
        manager.hideFromGallery(first.artifactId)
        manager.saveVersion("chat", "Draft", "html", "<p>two</p>", "p2")

        assertTrue(db.artifactDao().observeListed().first().isEmpty())
        assertEquals(true, manager.getLatestForChat("chat")?.hiddenFromGallery)
        assertEquals(2, manager.getLatestForChat("chat")?.currentVersion)
    }

    @Test
    fun transactionalAppendPreservesHideCommittedBeforeIt() = runBlocking {
        db.chatDao().insertThread(thread("chat", 1, 1))
        val inner = db.artifactDao()
        val first = ArtifactManager(inner, db.artifactVersionDao())
            .saveVersion("chat", "Draft", "html", "<p>one</p>", "p1")

        val staleReadDao = object : ArtifactDao by inner {
            override suspend fun appendVersion(chatId: String, title: String, type: String, content: String, sourcePrompt: String): com.echoflow.data.ArtifactRef {
                inner.getLatestForChat(chatId)?.id?.let { inner.hideFromGallery(it) }
                return inner.appendVersion(chatId, title, type, content, sourcePrompt)
            }
        }
        ArtifactManager(staleReadDao, db.artifactVersionDao())
            .saveVersion("chat", "Draft", "html", "<p>two</p>", "p2")

        assertTrue(inner.observeListed().first().isEmpty())
        assertEquals(true, inner.getById(first.artifactId)?.hiddenFromGallery)
        assertEquals(2, inner.getById(first.artifactId)?.currentVersion)
    }

    @Test
    fun deletingOwningThreadRemovesHiddenArtifact() = runBlocking {
        val chat = thread("chat", 1, 1)
        db.chatDao().insertThread(chat)
        val manager = ArtifactManager(db.artifactDao(), db.artifactVersionDao())
        val ref = manager.saveVersion(chat.id, "Draft", "html", "<p>body</p>", "prompt")
        manager.hideFromGallery(ref.artifactId)

        db.chatDao().deleteThread(chat)

        assertNull(manager.getById(ref.artifactId))
        assertNull(db.chatDao().getThreadById(chat.id))
    }

    private fun thread(id: String, created: Long, updated: Long) =
        ChatThread(id, id, created, updated)

    private fun message(id: String, chatId: String, content: String, created: Long) =
        ChatMessage(id, chatId, "user", content, created)
}
