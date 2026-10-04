package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttachmentFileReferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val base = "content://com.echoflow.fileprovider/camera_photos/"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking { database.chatDao().insertThread(ChatThread("chat-1", "Chat", 1L, 1L)) }
    }

    @After
    fun tearDown() = database.close()

    @Test fun `finds a photo in the single-attachment column and in attachmentsJson`() = runBlocking {
        val dao = database.messageDao()
        dao.insertMessage(ChatMessage("m1", "chat-1", "user", "look", 1L, localAttachmentUri = base + "photo_1.jpg"))
        dao.insertMessage(
            ChatMessage(
                "m2", "chat-1", "user", "and these", 2L,
                attachmentsJson = ToolEventJson.attachmentsToJson(
                    listOf(MessageAttachment(base + "photo_2.jpg", "image/jpeg", "Photo")),
                ),
            )
        )

        assertTrue(dao.isAttachmentFileReferenced("photo_1.jpg"))
        assertTrue(dao.isAttachmentFileReferenced("photo_2.jpg"))
        assertFalse(dao.isAttachmentFileReferenced("photo_3.jpg"))
    }

    @Test fun `underscore in a file name is matched literally, not as a wildcard`() = runBlocking {
        val dao = database.messageDao()
        dao.insertMessage(ChatMessage("m1", "chat-1", "user", "look", 1L, localAttachmentUri = base + "photoX1.jpg"))

        assertFalse(dao.isAttachmentFileReferenced("photo_1.jpg"))
    }

    @Test fun `deleting the chat drops its references`() = runBlocking {
        database.messageDao().insertMessage(
            ChatMessage("m1", "chat-1", "user", "look", 1L, localAttachmentUri = base + "photo_1.jpg")
        )
        database.chatDao().deleteThread(ChatThread("chat-1", "Chat", 1L, 1L))

        assertFalse(database.messageDao().isAttachmentFileReferenced("photo_1.jpg"))
    }
}
