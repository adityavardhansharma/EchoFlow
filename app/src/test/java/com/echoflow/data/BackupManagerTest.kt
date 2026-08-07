package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: SettingsRepository
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SettingsRepository(context)
        manager = BackupManager(context, db, repo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun bundle_captures_chats_and_keys_and_survives_encode_decode() = runBlocking {
        db.chatDao().insertThread(ChatThread(id = "t1", title = "Hello", createdAt = 1, updatedAt = 2))
        db.messageDao().insertMessage(ChatMessage("m1", "t1", "user", "hi there", 1))
        repo.saveApiKey("sk-test-123")
        repo.saveSelectedModel("acme/model-x")

        val bundle = manager.buildBundle()
        assertEquals(listOf("t1"), bundle.threads.map { it.id })
        assertEquals(listOf("m1"), bundle.messages.map { it.id })
        assertTrue(bundle.settings.any { it.key == "openrouter_api_key" && it.value == "sk-test-123" })

        val bytes = manager.encodeToBytes(bundle, "pass-1234")
        val decoded = manager.decodeFromBytes(bytes, "pass-1234")
        assertNotNull(decoded)
        assertEquals("hi there", decoded!!.messages.single().content)
    }

    @Test
    fun wrong_passkey_cannot_decode() = runBlocking {
        val bytes = manager.encodeToBytes(manager.buildBundle(), "the-real-key")
        assertThrows(AEADBadTagException::class.java) {
            manager.decodeFromBytes(bytes, "not-the-key")
        }
        Unit
    }

    @Test
    fun apply_restores_into_a_fresh_database() = runBlocking {
        db.chatDao().insertThread(ChatThread(id = "t9", title = "Keep me", createdAt = 5, updatedAt = 6))
        db.messageDao().insertMessage(ChatMessage("m9", "t9", "assistant", "restored body", 5))
        db.advisorProfileDao().insert(AdvisorProfile("a1", "Coding", "x/adv", "Adv", 1))
        val bundle = manager.buildBundle()

        val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val manager2 = BackupManager(context, db2, repo)
            manager2.applyBundle(bundle)

            assertNotNull(db2.chatDao().getThreadById("t9"))
            assertEquals("restored body", db2.messageDao().getMessagesForChatSync("t9").single().content)
            assertEquals(listOf("a1"), db2.advisorProfileDao().getAllSync().map { it.id })
        } finally {
            db2.close()
        }
    }
}
