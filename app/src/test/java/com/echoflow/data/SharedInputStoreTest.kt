package com.echoflow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class SharedInputStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun `selected text is preserved and unrelated intent extras are removed`() = runBlocking {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "A selected passage").putExtra("untrusted", "payload")
        val sanitized = SharedInputStore.sanitize(intent)
        assertFalse(sanitized.hasExtra("untrusted"))
        val imported = SharedInputStore(context).import(sanitized)
        assertEquals("A selected passage", imported.text)
        SharedInputStore(context).discard(imported)
    }
    @Test fun `unsafe file URIs and internal provider URIs are rejected`() {
        assertFalse(SharedInputStore.validExternalUri(Uri.parse("file:///data/private"), context.packageName))
        assertFalse(SharedInputStore.validExternalUri(Uri.parse("content://${context.packageName}.fileprovider/shared_inputs/private"), context.packageName))
        assertTrue(SharedInputStore.validExternalUri(Uri.parse("content://sender.document/file"), context.packageName))
    }
    @Test fun `copied files and draft survive loss of the original stream`() = runBlocking {
        val uri = Uri.parse("content://sender.document/readme")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("Shared document body".toByteArray()))
        val store = SharedInputStore(context)
        val input = store.import(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri))
        assertEquals("Shared document body", input.files.single().text)
        store.persist(input)
        val restored = store.load(input.id)
        assertEquals(input, restored)
        assertEquals("Shared document body", context.contentResolver.openInputStream(Uri.parse(restored.files.single().uri))!!.bufferedReader().use { it.readText() })
        store.discard(input)
    }
    @Test fun `too many files and oversized text are rejected before import`() {
        assertThrows(IllegalArgumentException::class.java) { SharedInputStore.sanitize(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "x".repeat(100001))) }
        assertThrows(IllegalArgumentException::class.java) { SharedInputStore.sanitize(Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList((1..4).map { Uri.parse("content://sender/$it") }))) }
    }
}
