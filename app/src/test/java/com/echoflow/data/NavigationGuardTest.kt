package com.echoflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps a slow thread lookup from landing on top of wherever the user actually
 * went. Both times this has broken, it broke by a restore applying its result unconditionally.
 */
class NavigationGuardTest {

    @Test fun `a token stays good while nothing moves`() {
        val guard = NavigationGuard()
        val token = guard.begin()
        assertTrue(guard.stillCurrent(token))
    }

    @Test fun `navigating invalidates a token taken before it`() {
        val guard = NavigationGuard()
        val token = guard.begin()
        guard.navigated()
        assertFalse(guard.stillCurrent(token))
    }

    @Test fun `a token taken after the navigation is the good one`() {
        // Two restores in flight — a switch, then a second switch. The later one wins, which
        // is the whole point: the newer intent is the user's real one.
        val guard = NavigationGuard()
        val stale = guard.begin()
        guard.navigated()
        val fresh = guard.begin()

        assertFalse(guard.stillCurrent(stale))
        assertTrue(guard.stillCurrent(fresh))
    }

    @Test fun `returning to the same conversation does not revive a stale token`() {
        // Counting moves rather than comparing thread ids matters here: navigating away and
        // back leaves the id identical, and an id comparison would call the stale restore
        // current again and let it overwrite.
        val guard = NavigationGuard()
        val token = guard.begin()
        guard.navigated()
        guard.navigated()
        assertFalse(guard.stillCurrent(token))
    }

    @Test fun `every navigation invalidates, not just the first`() {
        val guard = NavigationGuard()
        repeat(5) {
            val token = guard.begin()
            guard.navigated()
            assertFalse("navigation $it did not invalidate its token", guard.stillCurrent(token))
        }
    }
}
