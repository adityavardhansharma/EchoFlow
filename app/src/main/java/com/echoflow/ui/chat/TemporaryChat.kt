package com.echoflow.ui.chat

/**
 * Where the user is in a temporary chat. Kept out of the drawer and memory, and deleted once
 * the user leaves it.
 *
 * Off → Armed (blank composer) → Live(chatId) → Off. Leaving a Live chat for anything other
 * than itself discards it; leaving an Armed one just disarms it, since nothing was saved yet.
 */
sealed interface TemporaryChat {
    data object Off : TemporaryChat

    /** A blank composer whose first send will create a temporary chat. */
    data object Armed : TemporaryChat

    /** The open temporary chat, once its first message has created a thread for it. */
    data class Live(val id: String) : TemporaryChat

    /** The temporary chat's thread id, if it has one. */
    val chatId: String? get() = (this as? Live)?.id

    /** True while [currentChatId] is the temporary chat, blank or not. */
    fun covers(currentChatId: String?): Boolean = when (this) {
        Off -> false
        Armed -> true
        is Live -> id == currentChatId
    }
}

/** Side effects a [TemporaryChat] transition asks for. The caller runs them. */
sealed interface TemporaryChatEffect {
    /** Write the crash marker, so process death can't turn [chatId] into history. */
    data class Remember(val chatId: String) : TemporaryChatEffect

    /** Keep [chatId] out of memory before any of its turns exist. */
    data class ExcludeFromMemory(val chatId: String) : TemporaryChatEffect

    /** Delete [chatId] and clear the crash marker. */
    data class Discard(val chatId: String) : TemporaryChatEffect
}

/** The legal [TemporaryChat] transitions, as pure functions. */
object TemporaryChatMachine {
    data class Step(val state: TemporaryChat, val effects: List<TemporaryChatEffect> = emptyList())

    /** The user opened a blank temporary chat. */
    fun arm(state: TemporaryChat): Step = when (state) {
        // Arming from a live chat means the user left it for a fresh blank one.
        is TemporaryChat.Live -> Step(TemporaryChat.Armed, listOf(TemporaryChatEffect.Discard(state.id)))
        else -> Step(TemporaryChat.Armed)
    }

    /** The user navigated to [to] (null is the blank composer). */
    fun leave(state: TemporaryChat, to: String?): Step = when (state) {
        TemporaryChat.Off, TemporaryChat.Armed -> Step(TemporaryChat.Off)
        is TemporaryChat.Live ->
            if (state.id == to) Step(state)
            else Step(TemporaryChat.Off, listOf(TemporaryChatEffect.Discard(state.id)))
    }

    /**
     * The app just created [chatId] from the blank composer. [before] is the state from just
     * before it was opened: if a temporary chat was armed then, the new thread becomes it.
     */
    fun threadCreated(before: TemporaryChat, chatId: String): Step? =
        if (before != TemporaryChat.Armed) null
        else Step(
            TemporaryChat.Live(chatId),
            listOf(TemporaryChatEffect.Remember(chatId), TemporaryChatEffect.ExcludeFromMemory(chatId)),
        )
}
