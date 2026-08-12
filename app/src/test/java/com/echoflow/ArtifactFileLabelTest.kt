package com.echoflow

import com.echoflow.data.Artifact
import com.echoflow.ui.components.artifactFileLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactFileLabelTest {

    @Test
    fun plainTitleGetsTypeSuffix() {
        assertEquals(
            "Pricing-page.html",
            artifactFileLabel("Pricing page", "Web page", Artifact.TYPE_HTML),
        )
        assertEquals(
            "Quarterly-notes.md",
            artifactFileLabel("Quarterly notes", "Document", Artifact.TYPE_MARKDOWN),
        )
    }

    @Test
    fun dottedHumanTitleStillGetsSuffix() {
        // A period mid-title is not a file extension — must not suppress .html / .md / .tex.
        assertEquals(
            "Dr.-Smith-report.html",
            artifactFileLabel("Dr. Smith report", "Web page", Artifact.TYPE_HTML),
        )
        assertEquals(
            "v2.0-design.md",
            artifactFileLabel("v2.0 design", "Document", Artifact.TYPE_MARKDOWN),
        )
        assertEquals(
            "Section-1.2-outline.tex",
            artifactFileLabel("Section 1.2 outline", "Report", Artifact.TYPE_LATEX),
        )
    }

    @Test
    fun knownExtensionIsNotDoubled() {
        assertEquals(
            "page.html",
            artifactFileLabel("page.html", "Web page", Artifact.TYPE_HTML),
        )
        assertEquals(
            "Notes.MD",
            artifactFileLabel("Notes.MD", "Document", Artifact.TYPE_MARKDOWN),
        )
    }

    @Test
    fun blankTitleFallsBackToTypeLabel() {
        assertEquals(
            "Web-page.html",
            artifactFileLabel("  ", "Web page", Artifact.TYPE_HTML),
        )
    }
}
