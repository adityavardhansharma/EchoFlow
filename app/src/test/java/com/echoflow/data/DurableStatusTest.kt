package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class DurableStatusTest {
    private val browserTerminal = BrowserSession.TERMINAL_STATUSES

    @Test fun `a new row may take any status`() {
        assertTrue(DurableStatus.canMove(null, BrowserSession.STATUS_RESOLVING, browserTerminal))
    }

    @Test fun `running jobs move freely between live states and into an end`() {
        assertTrue(DurableStatus.canMove(BrowserSession.STATUS_AWAITING_USER, BrowserSession.STATUS_RUNNING, browserTerminal))
        assertTrue(DurableStatus.canMove(BrowserSession.STATUS_RUNNING, BrowserSession.STATUS_COMPLETED, browserTerminal))
    }

    @Test fun `a finished job never goes back to running`() {
        for (end in browserTerminal) {
            assertFalse(end, DurableStatus.canMove(end, BrowserSession.STATUS_RUNNING, browserTerminal))
        }
        assertFalse(DurableStatus.canMove(ResearchRun.STATUS_CANCELLED, ResearchRun.STATUS_SYNTHESIZING, ResearchRun.TERMINAL_STATUSES))
    }

    @Test fun `a finished job doesn't switch to a different ending`() {
        assertFalse(DurableStatus.canMove(BrowserSession.STATUS_STOPPED, BrowserSession.STATUS_FAILED, browserTerminal))
        assertFalse(DurableStatus.canMove(ResearchRun.STATUS_CANCELLED, ResearchRun.STATUS_COMPLETED, ResearchRun.TERMINAL_STATUSES))
    }

    @Test fun `a finished job can still be rewritten with its own status`() {
        assertTrue(DurableStatus.canMove(BrowserSession.STATUS_STOPPED, BrowserSession.STATUS_STOPPED, browserTerminal))
    }

    @Test fun `stored strings are unchanged so older builds still read them`() {
        assertEquals(setOf("completed", "failed", "stopped", "expired"), browserTerminal)
        assertEquals(setOf("completed", "failed", "cancelled"), ResearchRun.TERMINAL_STATUSES)
    }
}
