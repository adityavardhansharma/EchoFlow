package com.echoflow.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GeneratedImageStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var store: GeneratedImageStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        store = GeneratedImageStore(context, database.generatedImageDao())
        runBlocking {
            database.chatDao().insertThread(ChatThread("chat-1", "Chat", 1L, 1L))
        }
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "generated_images").deleteRecursively()
    }

    @Test fun `saveBitmap writes a PNG, cleans temp files and links the version chain`() = runBlocking {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val first = store.saveBitmap("chat-1", "a castle", bitmap, parentId = null)
        val file = File(first.filePath)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0)
        assertTrue(file.name.endsWith(".png"))
        // No .tmp residue anywhere in the images directory.
        assertFalse(file.parentFile!!.listFiles()!!.any { it.name.endsWith(".tmp") })
        // Row exists and is the chat's latest.
        assertEquals(first.id, store.latestForChat("chat-1")?.id)

        val second = store.saveBitmap("chat-1", "a castle, at night", bitmap, parentId = first.id)
        assertEquals(first.id, second.parentId)
        assertEquals(second.id, store.latestForChat("chat-1")?.id)
        assertNotNull(database.generatedImageDao().getById(second.id))
    }

    @Test fun `savePngFile atomically adopts only a native pending result`() = runBlocking {
        val pendingDir = File(context.filesDir, "generated_images/.native_pending").apply { mkdirs() }
        val pending = File(pendingDir, "native-result.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        }

        val saved = store.savePngFile(
            chatId = "chat-1",
            prompt = "a lighthouse",
            pendingFile = pending,
            parentId = null,
        )

        assertFalse(pending.exists())
        assertTrue(File(saved.filePath).isFile)
        assertEquals(saved.id, database.generatedImageDao().getById(saved.id)?.id)

        val outside = File(context.cacheDir, "outside.png").apply { writeBytes(byteArrayOf(1)) }
        try {
            store.savePngFile("chat-1", "invalid", outside, null)
            throw AssertionError("Expected an app-private pending path to be required")
        } catch (_: IllegalArgumentException) {
            // Expected: an arbitrary same-UID path must never be adopted as a generated image.
        } finally {
            outside.delete()
        }
    }
}
