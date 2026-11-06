package com.echoflow.ui.chat

import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * The crash marker for the open temporary chat: if the process dies inside one, the next launch
 * finds this id and deletes the chat, so it never shows up as history.
 *
 * It is written to two places, the prefs file and a small file of its own, because a marker that
 * failed to save would let the chat survive. Either copy is enough to find it again.
 */
internal class TemporaryChatMarker(private val prefs: SharedPreferences, private val file: File) {
    fun read(): String? =
        prefs.getString(KEY_CHAT_ID, null)
            ?: runCatching { file.takeIf { it.exists() }?.readText()?.trim() }.getOrNull()?.ifBlank { null }

    /** False only when neither copy could be saved. */
    fun write(chatId: String): Boolean {
        val savedPrefs = runCatching { prefs.edit().putString(KEY_CHAT_ID, chatId).commit() }.getOrDefault(false)
        val savedFile = runCatching { file.parentFile?.mkdirs(); file.writeText(chatId) }.isSuccess
        if (!savedPrefs || !savedFile) Log.w(TAG, "Temporary chat marker saved to prefs=$savedPrefs file=$savedFile")
        return savedPrefs || savedFile
    }

    /** Clears the marker, unless a newer temporary chat has taken it while [chatId] was deleted. */
    fun clear(chatId: String) {
        if (prefs.getString(KEY_CHAT_ID, null) == chatId) prefs.edit().remove(KEY_CHAT_ID).apply()
        runCatching { if (file.exists() && file.readText().trim() == chatId) file.delete() }
    }

    companion object {
        const val KEY_CHAT_ID = "temporary_chat_id"
        const val FILE_NAME = "temporary_chat_id"
        private const val TAG = "TemporaryChatMarker"
    }
}
