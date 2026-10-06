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

        val refs = dao.cameraPhotoReferences()
        assertTrue(refs.any { "photo_1.jpg" in it })
        assertTrue(refs.any { "photo_2.jpg" in it })
        assertFalse(refs.any { "photo_3.jpg" in it })
    }

    @Test fun `underscore in the folder name is matched literally, not as a wildcard`() = runBlocking {
        val dao = database.messageDao()
        dao.insertMessage(
            ChatMessage("m1", "chat-1", "user", "look", 1L, localAttachmentUri = "content://x/cameraXphotos/photo_1.jpg")
        )
        dao.insertMessage(ChatMessage("m2", "chat-1", "user", "plain", 2L))

        assertTrue(dao.cameraPhotoReferences().isEmpty())
    }

    @Test fun `deleting the chat drops its references`() = runBlocking {
        database.messageDao().insertMessage(
            ChatMessage("m1", "chat-1", "user", "look", 1L, localAttachmentUri = base + "photo_1.jpg")
        )
        database.chatDao().deleteThread(ChatThread("chat-1", "Chat", 1L, 1L))

        assertTrue(database.messageDao().cameraPhotoReferences().isEmpty())
    }
}
