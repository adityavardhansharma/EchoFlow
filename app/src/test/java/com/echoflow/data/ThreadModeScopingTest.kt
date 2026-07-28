package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two histories. Everything here protects the same promise: a conversation belongs to the
 * mode that made it, forever, and nothing reclassifies by content.
 */
@RunWith(RobolectricTestRunner::class)
class ThreadModeScopingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ChatRepository(
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            localModelDao = database.localModelDao(),
            researchRunDao = database.researchRunDao(),
            deepResearchModelDao = database.deepResearchModelDao(),
            advisorProfileDao = database.advisorProfileDao(),
            fusionPanelDao = database.fusionPanelDao(),
            agentProfileDao = database.agentProfileDao(),
            browserSessionDao = database.browserSessionDao(),
            browserStepDao = database.browserStepDao(),
            artifactDao = database.artifactDao(),
            artifactVersionDao = database.artifactVersionDao(),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test fun `a thread is stamped with the mode that created it`() = runBlocking {
        assertEquals(AppMode.Chat, repository.createThread(AppMode.Chat).mode)
        assertEquals(AppMode.Imagine, repository.createThread(AppMode.Imagine).mode)
    }

    @Test fun `each mode sees only its own conversations`() = runBlocking {
        repository.createThread(AppMode.Chat, title = "a question")
        repository.createThread(AppMode.Chat, title = "another question")
        repository.createThread(AppMode.Imagine, title = "a lighthouse")

        assertEquals(
            listOf("another question", "a question"),
            repository.threadsForMode(AppMode.Chat).first().map { it.title },
        )
        assertEquals(
            listOf("a lighthouse"),
            repository.threadsForMode(AppMode.Imagine).first().map { it.title },
        )
    }

    @Test fun `a thread written before the split reads as Chat`() = runBlocking {
        // The column default is the entire grandfather rule. An older row — inserted without
        // a kind, exactly as every pre-v18 conversation was — must land in Chat, images and
        // all, rather than being sorted by what it happens to contain.
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO chat_threads (id, title, createdAt, updatedAt) VALUES ('old', 'Old chat', 1, 1)"
        )

        val restored = repository.thread("old")!!
        assertEquals(AppMode.Chat, restored.mode)
        assertEquals(listOf("Old chat"), repository.threadsForMode(AppMode.Chat).first().map { it.title })
        assertEquals(emptyList<String>(), repository.threadsForMode(AppMode.Imagine).first().map { it.title })
    }

    @Test fun `a scoped search can report what the other mode is holding`() = runBlocking {
        repository.createThread(AppMode.Chat, title = "sunset over water")
        repository.createThread(AppMode.Imagine, title = "sunset painting")
        repository.createThread(AppMode.Imagine, title = "sunset timelapse")

        // What the drawer's "also N in Imagine" line is counting: results that exist but are
        // filtered out of the list the user is looking at.
        assertEquals(2, repository.searchMatchCount(AppMode.Imagine, "sunset").first())
        assertEquals(1, repository.searchMatchCount(AppMode.Chat, "sunset").first())
        assertEquals(0, repository.searchMatchCount(AppMode.Imagine, "lighthouse").first())
    }

    @Test fun `default titles follow the mode`() = runBlocking {
        assertEquals("New Conversation", repository.createThread(AppMode.Chat).title)
        assertEquals("New Creation", repository.createThread(AppMode.Imagine).title)
    }
}
