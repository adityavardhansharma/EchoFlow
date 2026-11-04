package com.echoflow

import android.content.Intent

/**
 * The launcher long-press shortcuts declared in res/xml/shortcuts.xml. Each one launches
 * [MainActivity] with its [action]; [id] matches the XML `shortcutId` so usage can be reported
 * back to the launcher for ranking.
 */
enum class AppShortcut(val id: String, val action: String) {
    NewChat("new_chat", "com.echoflow.action.NEW_CHAT"),
    VoiceMode("voice_mode", "com.echoflow.action.VOICE_MODE"),
    TemporaryChat("temporary_chat", "com.echoflow.action.TEMPORARY_CHAT"),
    Imagine("imagine", "com.echoflow.action.IMAGINE");

    companion object {
        /** The shortcut an intent was launched from, or null for any other intent. */
        fun from(intent: Intent?): AppShortcut? {
            val action = intent?.action ?: return null
            return entries.firstOrNull { it.action == action }
        }
    }
}
