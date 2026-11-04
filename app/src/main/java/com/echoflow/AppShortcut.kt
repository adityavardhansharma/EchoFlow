package com.echoflow

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutManagerCompat
import java.util.concurrent.atomic.AtomicReference

/**
 * The launcher long-press shortcuts declared in res/xml/shortcuts.xml. Each one launches
 * [ShortcutTrampolineActivity] with its [action]; [id] matches the XML `shortcutId` so usage can
 * be reported back to the launcher for ranking.
 */
enum class AppShortcut(val id: String, val action: String) {
    NewChat("new_chat", "com.echoflow.action.NEW_CHAT"),
    VoiceMode("voice_mode", "com.echoflow.action.VOICE_MODE"),
    TemporaryChat("temporary_chat", "com.echoflow.action.TEMPORARY_CHAT"),
    Imagine("imagine", "com.echoflow.action.IMAGINE");

    companion object {
        fun fromAction(action: String?): AppShortcut? = entries.firstOrNull { it.action == action }

        // Handed from the trampoline to MainActivity in memory, never on an intent: MainActivity
        // is exported, so an intent extra or action could be sent by any app. Only this process
        // can fill this slot.
        private val pending = AtomicReference<AppShortcut?>(null)

        internal fun post(shortcut: AppShortcut) = pending.set(shortcut)

        /** The shortcut the user just picked, once; null if there is none. */
        fun take(): AppShortcut? = pending.getAndSet(null)
    }
}

/**
 * The target of every launcher shortcut. Not exported, so only the launcher (starting it on
 * EchoFlow's behalf) or EchoFlow itself can open it — another app can't start voice mode.
 *
 * Android launches a static shortcut with NEW_TASK | CLEAR_TASK. Aimed at MainActivity, that
 * would destroy it and hang up a live voice call. This activity lives in its own task instead,
 * then brings EchoFlow forward the way the launcher icon does, which keeps its task as it is.
 */
class ShortcutTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppShortcut.fromAction(intent?.action)?.let { shortcut ->
            ShortcutManagerCompat.reportShortcutUsed(this, shortcut.id)
            AppShortcut.post(shortcut)
        }
        startActivity(
            Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
