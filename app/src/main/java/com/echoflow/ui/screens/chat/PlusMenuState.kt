package com.echoflow.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the composer's "+" menu is open, and the wait before it closes itself after a tool toggle:
 * the swap (the previous tool's chip and check animating off as the new one's animate on) plays
 * first. A second toggle restarts the wait; opening or dismissing the menu cancels it.
 */
internal class PlusMenuState(
    private val scope: CoroutineScope,
    private val swapMs: Long = PLUS_MENU_SWAP_MS,
) {
    var expanded by mutableStateOf(false)
        private set
    private var autoClose: Job? = null

    fun open() {
        autoClose?.cancel()
        expanded = true
    }

    fun dismiss() {
        autoClose?.cancel()
        expanded = false
    }

    /** A tool was toggled: close once its swap has played, or at once under reduced motion. */
    fun toolToggled(reducedMotion: Boolean) {
        autoClose?.cancel()
        if (reducedMotion) {
            expanded = false
            return
        }
        autoClose = scope.launch {
            delay(swapMs)
            expanded = false
        }
    }

    companion object {
        /** How long a tool swap stays on screen before the menu closes: the chip spring has settled. */
        const val PLUS_MENU_SWAP_MS = 420L
    }
}
