package com.echoflow.data

/**
 * What an on-device runtime has loaded. The token budget (and, for MediaPipe, the top-k
 * ceiling) is fixed when the engine is built, so any change means a reload. One value instead
 * of three fields that had to be set and cleared together.
 */
internal data class LoadedEngine(val path: String, val maxTokens: Int, val maxTopK: Int = -1)

/**
 * The chat a runtime's live session is bound to, and how many turns it has seen. Reusing the
 * session is only right for the next turn of the same chat: the history grows by exactly the
 * last answer and the new question.
 */
internal data class SessionBinding(val chatId: String, val historySize: Int) {
    fun continuesWith(chatId: String, historySize: Int): Boolean =
        chatId == this.chatId && historySize == this.historySize + 2
}
