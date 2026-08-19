package com.echoflow.ui.screens

import com.echoflow.data.ExtractionStatus
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectFileImportUiTest {

    @Test fun `queued files say waiting, active parse says reading`() {
        assertEquals("Waiting…", documentStatusHint(ExtractionStatus.PENDING, modelReadsFiles = true))
        assertEquals("Reading…", documentStatusHint(ExtractionStatus.EXTRACTING, modelReadsFiles = true))
        assertNull(documentStatusHint(ExtractionStatus.EXTRACTED, modelReadsFiles = true))
    }

    @Test fun `header names reading and waiting without changing the file count`() {
        val docs = listOf(
            doc("a", ExtractionStatus.EXTRACTING),
            doc("b", ExtractionStatus.EXTRACTING),
            doc("c", ExtractionStatus.PENDING),
            doc("d", ExtractionStatus.EXTRACTED),
        )
        assertEquals("4 files · reading 2 · 1 waiting", filesHeaderSubtitle(docs))
        assertEquals(
            "8 files · reading 2 · 5 waiting",
            filesHeaderSubtitle(docs, queued = 4),
        )
        assertEquals("Background knowledge for this project", filesHeaderSubtitle(emptyList()))
        assertEquals("3 files · 3 waiting", filesHeaderSubtitle(emptyList(), queued = 3))
    }

    @Test fun `import progress queued is selected minus admitted and failed`() {
        val progress = ChatViewModel.ProjectImportProgress(
            projectId = "p",
            selected = 10,
            admitted = 4,
            failed = 1,
        )
        assertEquals(5, progress.queued)
        assertEquals(0, progress.copy(admitted = 9, failed = 1).queued)
    }

    private fun doc(id: String, status: ExtractionStatus) = ProjectDocument(
        id = id,
        projectId = "p",
        name = "$id.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1_024,
        filePath = "/tmp/$id.pdf",
        extractedText = if (status == ExtractionStatus.EXTRACTED) "text" else null,
        addedAt = 1L,
        extractionStatus = status.name,
    )
}
