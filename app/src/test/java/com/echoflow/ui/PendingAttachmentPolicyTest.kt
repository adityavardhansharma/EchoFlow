package com.echoflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAttachmentPolicyTest {
    private fun doc(
        id: String,
        pdf: Boolean = true,
        text: String? = null,
        state: PendingAttachment.State = PendingAttachment.State.Ready,
    ) = PendingAttachment(
        id = id,
        uri = "content://$id",
        mimeType = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        name = if (pdf) "$id.pdf" else "$id.docx",
        kind = PendingAttachment.Kind.Doc,
        state = state,
        extractedText = text,
    )

    private fun image(id: String) = PendingAttachment(
        id = id,
        uri = "content://$id",
        mimeType = "image/png",
        name = "$id.png",
        kind = PendingAttachment.Kind.Image,
        state = PendingAttachment.State.Ready,
    )

    @Test fun `local keeps several docs up to the cap`() {
        val kept = PendingAttachmentPolicy.keep(
            listOf(doc("a"), doc("b"), doc("c"), doc("d")),
            imageAllowed = false,
            pdfAllowed = false,
            localFilesAllowed = true,
            cap = 3,
        )
        assertEquals(listOf("a", "b", "c"), kept.map { it.id })
    }

    @Test fun `cloud collapses several PDFs to the first`() {
        val kept = PendingAttachmentPolicy.keep(
            listOf(doc("a"), doc("b"), doc("c")),
            imageAllowed = true,
            pdfAllowed = true,
            localFilesAllowed = false,
        )
        assertEquals(listOf("a"), kept.map { it.id })
    }

    @Test fun `cloud prefers a staged image over extra PDFs`() {
        val kept = PendingAttachmentPolicy.keep(
            listOf(doc("a"), image("pic"), doc("b")),
            imageAllowed = true,
            pdfAllowed = true,
            localFilesAllowed = false,
        )
        assertEquals(listOf("pic"), kept.map { it.id })
    }

    @Test fun `docx is dropped when leaving the local files path`() {
        val kept = PendingAttachmentPolicy.keep(
            listOf(doc("word", pdf = false, text = "body")),
            imageAllowed = true,
            pdfAllowed = true,
            localFilesAllowed = false,
        )
        assertTrue(kept.isEmpty())
    }

    @Test fun `a ready doc with no text still needs extraction`() {
        assertTrue(PendingAttachmentPolicy.needsExtraction(doc("a", text = null)))
        assertFalse(PendingAttachmentPolicy.needsExtraction(doc("a", text = "# hi")))
        assertFalse(
            PendingAttachmentPolicy.needsExtraction(
                doc("a", text = null, state = PendingAttachment.State.Extracting),
            ),
        )
        assertFalse(
            PendingAttachmentPolicy.needsExtraction(
                doc("a", text = null, state = PendingAttachment.State.Failed),
            ),
        )
        assertFalse(PendingAttachmentPolicy.needsExtraction(image("pic")))
    }
}
