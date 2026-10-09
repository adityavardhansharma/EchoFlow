package com.echoflow.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the composer's "+" menu is open. Picking a tool closes the menu at once and applies the
 * pick only once the close has played, so the menu never re-lays itself out (tiles dropping, rows
 * swapping) mid-exit and the composer's own reaction (pill, chips, placeholder) follows the close
 * instead of colliding with it. The next open shows the new state.
 */
internal class PlusMenuState(
    private val scope: CoroutineScope,
    private val exitMs: Long = PLUS_MENU_EXIT_MS.toLong(),
) {
    var expanded by mutableStateOf(false)
        private set

    fun open() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }

    /** A tool was picked: close now, run [action] after the exit (at once under reduced motion). */
    fun pick(reducedMotion: Boolean, action: () -> Unit) {
        // A second tap while the menu is already closing is ignored, so only one pick lands.
        if (!expanded) return
        expanded = false
        if (reducedMotion) {
            action()
            return
        }
        scope.launch {
            delay(exitMs)
            action()
        }
    }
}
