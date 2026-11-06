package com.echoflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayStackTest {
    @Test
    fun nothingOpenMeansBackIsTheChats() {
        val stack = OverlayStack.of()
        assertNull(stack.top)
        assertNull(stack.backTarget)
    }

    @Test
    fun artifactWorkspaceClosesBeforeTheGalleryUnderIt() {
        val stack = OverlayStack.of(artifactsGallery = true, artifactWorkspace = true)
        assertEquals(Overlay.ArtifactWorkspace, stack.backTarget)
        assertEquals(Overlay.ArtifactsGallery, OverlayStack.of(artifactsGallery = true).backTarget)
    }

    @Test
    fun layersStackInDrawingOrder() {
        val stack = OverlayStack.of(
            artifactWorkspace = true,
            browserWorkspace = true,
            researchWorkspace = true,
            artifactsGallery = true,
        )
        assertEquals(
            listOf(Overlay.BrowserWorkspace, Overlay.ResearchWorkspace, Overlay.ArtifactsGallery, Overlay.ArtifactWorkspace),
            stack.open,
        )
    }

    @Test
    fun selfHandledLayerOnTopTakesBackItself() {
        assertNull(OverlayStack.of(artifactsGallery = true, projectsHub = true).backTarget)
        assertNull(OverlayStack.of(researchWorkspace = true, schedules = true).backTarget)
    }

    @Test
    fun artifactWorkspaceStillClosesOverSchedules() {
        assertEquals(Overlay.ArtifactWorkspace, OverlayStack.of(schedules = true, artifactWorkspace = true).backTarget)
    }
}
