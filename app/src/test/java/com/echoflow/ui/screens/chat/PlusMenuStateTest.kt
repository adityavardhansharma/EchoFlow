package com.echoflow.ui.screens.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlusMenuStateTest {
    private val exit = PLUS_MENU_EXIT_MS.toLong()

    @Test fun `a pick closes the menu at once and lands after the exit`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        var picks = 0
        menu.pick(reducedMotion = false) { picks++ }
        assertFalse(menu.expanded)
        advanceTimeBy(exit - 1)
        assertEquals(0, picks)
        advanceTimeBy(2)
        assertEquals(1, picks)
    }

    @Test fun `a second pick while closing is ignored`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        var first = 0
        var second = 0
        menu.pick(reducedMotion = false) { first++ }
        menu.pick(reducedMotion = false) { second++ }
        advanceTimeBy(exit * 2)
        assertEquals(1, first)
        assertEquals(0, second)
    }

    @Test fun `reduced motion closes and picks at once`() = runTest {
        val menu = PlusMenuState(backgroundScope).apply { open() }
        var picks = 0
        menu.pick(reducedMotion = true) { picks++ }
        assertFalse(menu.expanded)
        assertEquals(1, picks)
    }
}
