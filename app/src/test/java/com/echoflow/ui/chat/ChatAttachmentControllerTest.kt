package com.echoflow.ui.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.MessageAttachment
import com.echoflow.data.extract.ChatAttachmentExtractor.Result
import com.echoflow.ui.PendingAttachment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatAttachmentControllerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private fun doc(name: String, text: String? = null) =
        MessageAttachment("content://docs/$name", "application/pdf", name, text)

    @Test fun `restoring another prompt cancels extraction from the previous draft`() = runTest {
        val pending = CompletableDeferred<Result>()
        var cancelled = false
        val controller = ChatAttachmentController(app, backgroundScope, 3, {}) { _, _ ->
            try { pending.await() } finally { cancelled = true }
        }
        controller.restore(listOf(doc("old.pdf")), extractLocally = true)
        runCurrent()
        assertEquals(PendingAttachment.State.Extracting, controller.pendingAttachments.value.single().state)
        controller.restore(listOf(doc("new.pdf", "new text")), extractLocally = true)
        runCurrent()
        pending.complete(Result.Text("old text"))
        runCurrent()
        assertTrue(cancelled)
        assertEquals("new text", controller.pendingAttachments.value.single().extractedText)
    }

    @Test fun `failed extraction can be retried without replacing the attachment identity`() = runTest {
        var attempts = 0
        val controller = ChatAttachmentController(app, backgroundScope, 3, {}) { _, _ ->
            if (++attempts == 1) Result.Failed("unreadable") else Result.Text("recovered")
        }
        controller.restore(listOf(doc("retry.pdf")), extractLocally = true)
        runCurrent()
        val attachment = controller.pendingAttachments.value.single()
        assertEquals(PendingAttachment.State.Failed, attachment.state)
        controller.retryPendingAttachment(attachment.id)
        runCurrent()
        assertEquals(attachment.id, controller.pendingAttachments.value.single().id)
        assertEquals("recovered", controller.pendingAttachments.value.single().extractedText)
        assertEquals(2, attempts)
    }

    @Test fun `switching to cloud collapses documents and reports dropped files`() = runTest {
        val errors = mutableListOf<String>()
        val controller = ChatAttachmentController(app, backgroundScope, 3, errors::add) { _, _ ->
            Result.Text("unused")
        }
        controller.restore(listOf(doc("one.pdf"), doc("two.pdf")), extractLocally = false)
        controller.reconcilePendingAttachments(imageAllowed = true, pdfAllowed = true, localFilesAllowed = false)
        assertEquals(listOf("one.pdf"), controller.pendingAttachments.value.map { it.name })
        assertEquals(listOf("Only one file can go with this model. Extra files were dropped."), errors)
    }

    @Test fun `clearing the composer cancels in flight work and leaves no attachments`() = runTest {
        var cancelled = false
        val controller = ChatAttachmentController(app, backgroundScope, 3, {}) { _, _ ->
            try { CompletableDeferred<Result>().await() } finally { cancelled = true }
        }
        controller.restore(listOf(doc("pending.pdf")), extractLocally = true)
        runCurrent()
        controller.clearPendingAttachment()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(controller.pendingAttachments.value.isEmpty())
    }

    @Test fun `shared files are copied in and dropped with a message when the model cannot read them`() = runTest {
        val errors = mutableListOf<String>()
        val controller = ChatAttachmentController(app, backgroundScope, 3, errors::add) { _, _ ->
            Result.Text("unused")
        }
        val source = java.io.File(app.cacheDir, "notes.docx").apply { writeText("shared") }
        controller.addSharedFiles(listOf(android.net.Uri.fromFile(source)))
        val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) {
                controller.pendingAttachments.first { it.isNotEmpty() }.single()
            }
        }
        assertEquals("notes.docx", staged.name)
        assertEquals(PendingAttachment.Kind.Doc, staged.kind)
        assertNotEquals(android.net.Uri.fromFile(source).toString(), staged.uri)
        assertEquals("shared", java.io.File(android.net.Uri.parse(staged.uri).path!!).readText())

        controller.reconcilePendingAttachments(imageAllowed = true, pdfAllowed = true, localFilesAllowed = false)
        assertTrue(controller.pendingAttachments.value.isEmpty())
        assertEquals(1, errors.size)
    }
}
