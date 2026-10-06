package com.echoflow.ui.screens.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlusMenuStateTest {
    private val swap = PlusMenuState.PLUS_MENU_SWAP_MS

    @Test fun `a toggle closes the menu once the swap has played`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        menu.toolToggled(reducedMotion = false)
        advanceTimeBy(swap - 1)
        assertTrue(menu.expanded)
        advanceTimeBy(2)
        assertFalse(menu.expanded)
    }

    @Test fun `a second toggle restarts the wait`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        menu.toolToggled(reducedMotion = false)
        advanceTimeBy(swap - 100)
        menu.toolToggled(reducedMotion = false)
        advanceTimeBy(swap - 1)
        assertTrue(menu.expanded)
        advanceTimeBy(2)
        assertFalse(menu.expanded)
    }

    @Test fun `dismissing and reopening cancels a pending close`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        menu.toolToggled(reducedMotion = false)
        menu.dismiss()
        menu.open()
        advanceTimeBy(swap * 2)
        runCurrent()
        assertTrue(menu.expanded)
    }

    @Test fun `reduced motion closes at once`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        menu.toolToggled(reducedMotion = true)
        assertFalse(menu.expanded)
    }
}
