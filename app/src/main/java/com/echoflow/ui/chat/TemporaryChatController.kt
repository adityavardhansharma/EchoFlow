package com.echoflow.ui.chat

import android.app.Application
import com.echoflow.data.memory.MemorySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the temporary chat: its [TemporaryChat] state, the crash marker that keeps process
 * death from turning one into history, and the effects of each transition. The view model
 * tells it when the user navigates or a thread is created; [discard] deletes a chat.
 */
internal class TemporaryChatController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val discard: suspend (chatId: String) -> Unit,
) {
    private val marker = TemporaryChatMarker(
        app.getSharedPreferences(PREFS, Application.MODE_PRIVATE),
        java.io.File(app.noBackupFilesDir, TemporaryChatMarker.FILE_NAME),
    )

    /**
     * Written as soon as a temporary chat gets a row; still set at launch means the process died
     * in it. Both copies, since they can name different chats when one write failed.
     */
    val leftoverChatIds: List<String> = marker.readAll()

    val leftoverChatId: String? get() = leftoverChatIds.firstOrNull()

    private val _state = MutableStateFlow<TemporaryChat>(TemporaryChat.Off)
    val state: StateFlow<TemporaryChat> = _state.asStateFlow()

    /** A temporary chat the process died inside is discarded on the next launch. */
    fun discardLeftover() {
        if (leftoverChatIds.isEmpty()) return
        scope.launch { leftoverChatIds.forEach { id -> discard(id); forget(id) } }
    }

    fun arm() = apply(TemporaryChatMachine.arm(_state.value))

    /** Call before navigating to a thread the app just created; pass the result to [threadCreated]. */
    fun beforeNewThread(): TemporaryChat = _state.value

    fun threadCreated(before: TemporaryChat, chatId: String) {
        TemporaryChatMachine.threadCreated(before, chatId)?.let(::apply)
    }

    /** The user navigated to [to] (null is the blank composer). */
    fun leave(to: String?) = apply(TemporaryChatMachine.leave(_state.value, to))

    fun covers(currentChatId: String?): Boolean = _state.value.covers(currentChatId)

    private fun apply(step: TemporaryChatMachine.Step) {
        _state.value = step.state
        for (effect in step.effects) when (effect) {
            is TemporaryChatEffect.Remember -> marker.write(effect.chatId)
            // Best effort: every turn here already skips memory; this also keeps a manual
            // "learn now" flush from picking the chat up before it is deleted.
            is TemporaryChatEffect.ExcludeFromMemory -> runCatching { MemorySettings(app).exclude(effect.chatId) }
            is TemporaryChatEffect.Discard -> scope.launch { discard(effect.chatId); forget(effect.chatId) }
        }
    }

    /** Clears the marker, unless a newer temporary chat has taken it while [chatId] was deleted. */
    private fun forget(chatId: String) = marker.clear(chatId)

    private companion object {
        const val PREFS = "temporary_chat"
    }
}
