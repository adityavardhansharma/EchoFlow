package com.echoflow.ui.chat

import com.echoflow.data.ChatMode

/**
 * Which mode each chat was left in, so a mode belongs to the chat it was turned on in: leaving a
 * chat parks its mode, opening one restores that chat's own (Normal if it never had one). Deep
 * Research or Artifact therefore never follows the user into an unrelated chat. Session-only.
 */
internal class ChatModeMemory {
    private val byChat = mutableMapOf<String, ChatMode>()

    /**
     * The mode to show after moving from [leaving] (currently in [current]) to [opening]. [carry]
     * is for a chat just created from the blank composer: it keeps the mode picked there.
     */
    fun switch(leaving: String?, current: ChatMode, opening: String?, carry: Boolean): ChatMode {
        if (leaving == opening) return current
        if (leaving != null) park(leaving, current)
        return if (carry) current else opening?.let { byChat[it] } ?: ChatMode.Normal
    }

    fun parked(chatId: String): ChatMode? = byChat[chatId]

    fun forget(chatId: String) {
        byChat.remove(chatId)
    }

    private fun park(chatId: String, mode: ChatMode) {
        when (mode) {
            // Imagine's media modes are armed per send, never parked.
            ChatMode.Normal, ChatMode.ImageGen, ChatMode.VideoGen -> byChat.remove(chatId)
            else -> byChat[chatId] = mode
        }
    }
}
