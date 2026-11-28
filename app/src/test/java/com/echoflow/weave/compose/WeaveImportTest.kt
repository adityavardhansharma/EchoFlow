package com.echoflow.weave.compose

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WeaveImportTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WeaveImport.pending.value = null
    }

    @After fun tearDown() {
        WeaveImport.pending.value = null
    }

    @Test fun `intents without a URI call onOther synchronously`() {
        val caller = Thread.currentThread()
        val intents = listOf(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello"),
            Intent(Intent.ACTION_MAIN),
            null,
        )
        for (intent in intents) {
            var called = false
            WeaveImport.read(context, intent) {
                assertSame(caller, Thread.currentThread())
                called = true
            }
            assertTrue(called)
            assertNull(WeaveImport.pending.value)
        }
    }

    @Test fun `declared weave MIME type imports the stream`() {
        assertDeclaredImport(Uri.parse("content://weave-import/notes"), "application/x-weave")
    }

    @Test fun `weave URI suffix imports the stream`() {
        assertDeclaredImport(Uri.parse("content://weave-import/notes.weave"), "application/octet-stream")
    }

    @Test fun `ordinary share queries off main and calls onOther on main`() {
        val provider = Robolectric.setupContentProvider(NameProvider::class.java, "weave-import")
        provider.displayName = "photo.png"
        val caller = Thread.currentThread()
        val callbackThread = AtomicReference<Thread?>()

        WeaveImport.read(context, imageShare()) {
            callbackThread.set(Thread.currentThread())
        }

        await { callbackThread.get() != null }
        assertNotNull(provider.queryThread.get())
        assertNotSame(caller, provider.queryThread.get())
        assertSame(Looper.getMainLooper().thread, callbackThread.get())
        assertNull(WeaveImport.pending.value)
    }

    @Test fun `weave display name imports an ordinary share off main`() {
        val provider = Robolectric.setupContentProvider(NameProvider::class.java, "weave-import")
        provider.displayName = "Notes.WEAVE"
        val caller = Thread.currentThread()
        val uri = Uri.parse("content://weave-import/photo")
        val text = "# Weave\nshared notes"
        shadowOf(context.contentResolver).registerInputStream(uri, text.byteInputStream())
        val other = AtomicBoolean(false)

        WeaveImport.read(context, imageShare()) { other.set(true) }

        await { WeaveImport.pending.value != null }
        assertEquals(text, WeaveImport.pending.value)
        assertNotNull(provider.queryThread.get())
        assertNotSame(caller, provider.queryThread.get())
        assertFalse(other.get())
    }

    private fun assertDeclaredImport(uri: Uri, type: String) {
        val text = "# Weave\nnotes"
        shadowOf(context.contentResolver).registerInputStream(uri, text.byteInputStream())
        val other = AtomicBoolean(false)

        WeaveImport.read(context, Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)) {
            other.set(true)
        }

        await { WeaveImport.pending.value != null }
        assertEquals(text, WeaveImport.pending.value)
        assertFalse(other.get())
    }

    private fun imageShare() = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://weave-import/photo"))

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for import")
    }

    class NameProvider : ContentProvider() {
        var displayName = "photo.png"
        val queryThread = AtomicReference<Thread?>()

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queryThread.set(Thread.currentThread())
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf(displayName))
            }
        }

        override fun getType(uri: Uri): String = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
