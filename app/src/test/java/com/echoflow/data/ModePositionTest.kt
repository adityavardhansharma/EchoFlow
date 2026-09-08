package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A blank composer is a position. Every case here is a way the app could otherwise reopen a
 * conversation the user had already navigated away from.
 */
@RunWith(RobolectricTestRunner::class)
class ModePositionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    @Test fun `a mode that has never been visited is unset`() {
        val repository = SettingsRepository(context)
        assertEquals(ModePosition.Unset, repository.getLastPositionDirect(AppMode.Chat))
        assertEquals(ModePosition.Unset, repository.getLastPositionDirect(AppMode.Imagine))
    }

    @Test fun `a blank composer survives a round trip as blank, not as unset`() {
        // The bug this whole type exists for: park a fresh composer, come back, and the app
        // must not quietly reopen the conversation you had left before it.
        val repository = SettingsRepository(context)
        repository.saveLastPosition(AppMode.Chat, ModePosition.Thread("chat-1"))
        repository.saveLastPosition(AppMode.Chat, ModePosition.Blank)

        assertEquals(ModePosition.Blank, SettingsRepository(context).getLastPositionDirect(AppMode.Chat))
    }

    @Test fun `each mode remembers its own position independently`() {
        val repository = SettingsRepository(context)
        repository.saveLastPosition(AppMode.Chat, ModePosition.Thread("chat-1"))
        repository.saveLastPosition(AppMode.Imagine, ModePosition.Blank)

        val reloaded = SettingsRepository(context)
        assertEquals(ModePosition.Thread("chat-1"), reloaded.getLastPositionDirect(AppMode.Chat))
        assertEquals(ModePosition.Blank, reloaded.getLastPositionDirect(AppMode.Imagine))
    }

    @Test fun `a position can be moved from blank back to a conversation`() {
        val repository = SettingsRepository(context)
        repository.saveLastPosition(AppMode.Imagine, ModePosition.Blank)
        repository.saveLastPosition(AppMode.Imagine, ModePosition.Thread("imagine-7"))

        assertEquals(ModePosition.Thread("imagine-7"), repository.getLastPositionDirect(AppMode.Imagine))
    }

    @Test fun `clearing a position returns it to unset`() {
        val repository = SettingsRepository(context)
        repository.saveLastPosition(AppMode.Chat, ModePosition.Thread("chat-1"))
        repository.saveLastPosition(AppMode.Chat, ModePosition.Unset)

        assertEquals(ModePosition.Unset, repository.getLastPositionDirect(AppMode.Chat))
    }
}
