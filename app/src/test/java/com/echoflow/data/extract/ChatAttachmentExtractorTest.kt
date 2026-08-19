package com.echoflow.data.extract

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class ChatAttachmentExtractorTest {
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    private fun extractorReturning(result: Anydoc.Result) = ChatAttachmentExtractor(
        resolver,
        object : DocumentParser {
            override val available = true
            override fun convert(bytes: ByteArray, filename: String) = result
        },
    )

    private fun stagedUri(bytes: ByteArray = byteArrayOf(1, 2, 3)): Uri {
        val uri = Uri.parse("content://docs/report")
        Shadows.shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    @Test fun `parsed markdown comes back as text`() = runBlocking {
        val result = extractorReturning(Anydoc.Result.Text("# Heading\n\nBody"))
            .extract(stagedUri(), "report.pdf")
        assertTrue(result is ChatAttachmentExtractor.Result.Text)
        assertTrue((result as ChatAttachmentExtractor.Result.Text).markdown.contains("Heading"))
    }

    @Test fun `a doc anydoc declines fails (no OCR tier in chat)`() = runBlocking {
        val result = extractorReturning(Anydoc.Result.TryOcr).extract(stagedUri(), "scan.pdf")
        assertTrue(result is ChatAttachmentExtractor.Result.Failed)
    }

    @Test fun `an encrypted doc fails`() = runBlocking {
        val result = extractorReturning(Anydoc.Result.Failed("encrypted")).extract(stagedUri(), "locked.pdf")
        assertEquals("encrypted", (result as ChatAttachmentExtractor.Result.Failed).reason)
    }

    @Test fun `blank markdown is treated as a failure`() = runBlocking {
        val result = extractorReturning(Anydoc.Result.Text("   ")).extract(stagedUri(), "empty.docx")
        assertTrue(result is ChatAttachmentExtractor.Result.Failed)
    }

    @Test fun `an unreadable uri fails without throwing`() = runBlocking {
        // Nothing registered for this URI → the read returns null → Failed, never an exception.
        val result = extractorReturning(Anydoc.Result.Text("unused"))
            .extract(Uri.parse("content://docs/missing"), "gone.pdf")
        assertTrue(result is ChatAttachmentExtractor.Result.Failed)
    }

    @Test fun `a text file is read even when anydoc is not used`() = runBlocking {
        val uri = Uri.parse("content://docs/notes")
        Shadows.shadowOf(resolver).registerInputStream(
            uri,
            ByteArrayInputStream("hello from notes".toByteArray()),
        )
        // anydoc is never called for .txt; even a failing parser must not block plaintext.
        val result = extractorReturning(Anydoc.Result.Failed("unused")).extract(uri, "notes.txt")
        assertTrue(result is ChatAttachmentExtractor.Result.Text)
        assertEquals("hello from notes", (result as ChatAttachmentExtractor.Result.Text).markdown)
    }

    @Test fun `markdown is read as plaintext`() = runBlocking {
        val uri = Uri.parse("content://docs/readme")
        Shadows.shadowOf(resolver).registerInputStream(
            uri,
            ByteArrayInputStream("# Title\n\nBody".toByteArray()),
        )
        val result = extractorReturning(Anydoc.Result.TryOcr).extract(uri, "readme.md")
        assertTrue(result is ChatAttachmentExtractor.Result.Text)
        assertTrue((result as ChatAttachmentExtractor.Result.Text).markdown.contains("Title"))
    }

    @Test fun `a scanned pdf still fails (no OCR, no plaintext fallback)`() = runBlocking {
        val result = extractorReturning(Anydoc.Result.TryOcr).extract(stagedUri(), "scan.pdf")
        assertTrue(result is ChatAttachmentExtractor.Result.Failed)
    }
}
