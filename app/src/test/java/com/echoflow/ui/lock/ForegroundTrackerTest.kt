package com.echoflow.ui.lock

import org.junit.Assert.*
import org.junit.Test

class ForegroundTrackerTest {
    private val tracker = ForegroundTracker()

    @Test fun `a fresh process reports no time away`() {
        assertNull(tracker.started(now = 100))
    }

    @Test fun `time away is measured from the last stop`() {
        tracker.started(0)
        tracker.stopped(now = 1_000, countsAsLeaving = true)
        assertEquals(4_000L, tracker.started(now = 5_000))
    }

    @Test fun `rotation or the unlock prompt don't start the clock`() {
        tracker.started(0)
        tracker.stopped(now = 1_000, countsAsLeaving = false)
        assertNull(tracker.started(now = 90_000))
    }

    @Test fun `the clock starts only when the last activity stops`() {
        tracker.started(0)
        tracker.started(0) // a Share activity on top
        tracker.stopped(now = 1_000, countsAsLeaving = true)
        tracker.stopped(now = 2_000, countsAsLeaving = true)
        assertEquals(3_000L, tracker.started(now = 5_000))
    }

    @Test fun `a second activity starting while one is showing isn't a return`() {
        tracker.started(0)
        tracker.stopped(now = 1_000, countsAsLeaving = true)
        assertNotNull(tracker.started(now = 2_000))
        assertNull(tracker.started(now = 3_000))
    }

    @Test fun `extra stops never go below zero`() {
        tracker.stopped(now = 1_000, countsAsLeaving = true)
        tracker.stopped(now = 2_000, countsAsLeaving = true)
        assertEquals(1_000L, tracker.started(now = 3_000))
    }
}
