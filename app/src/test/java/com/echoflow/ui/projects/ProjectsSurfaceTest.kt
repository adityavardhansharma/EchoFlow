package com.echoflow.ui.projects

import com.echoflow.ui.mapState
import com.echoflow.ui.projects.ProjectsSurface.Closed
import com.echoflow.ui.projects.ProjectsSurface.Home
import com.echoflow.ui.projects.ProjectsSurface.Hub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectsSurfaceTest {
    @Test fun `back steps home to hub to closed`() {
        val home = Hub.openHome("p1")
        assertEquals(Home("p1"), home)
        assertEquals(Hub, home.closeHome())
        assertEquals(Hub, Hub.closeHome())
    }

    @Test fun `a home always has the hub under it`() {
        assertTrue(Home("p1").hubOpen)
        assertEquals("p1", Home("p1").openProjectId)
        assertNull(Hub.openProjectId)
        assertFalse(Closed.hubOpen)
    }

    @Test fun `opening a home while the hub is closed does nothing`() {
        assertEquals(Closed, Closed.openHome("p1"))
    }

    @Test fun `deleting the open project returns to the list`() {
        assertEquals(Hub, Home("p1").projectDeleted("p1"))
        assertEquals(Home("p1"), Home("p1").projectDeleted("p2"))
        assertEquals(Closed, Closed.projectDeleted("p1"))
    }

    @Test fun `derived flows stay in step with the surface`() = runBlocking {
        val surface = MutableStateFlow<ProjectsSurface>(Closed)
        val hubOpen = surface.mapState { it.hubOpen }
        val projectId = surface.mapState { it.openProjectId }
        assertFalse(hubOpen.value)
        surface.value = Home("p1")
        assertTrue(hubOpen.value)
        assertEquals("p1", projectId.value)
        assertEquals("p1", projectId.first())
    }
}
