package com.echoflow.ui.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemporaryChatMarkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("temporary_chat_test", Context.MODE_PRIVATE)
    private val file = File(context.noBackupFilesDir, "temporary_chat_id_test")

    @Before fun reset() {
        prefs.edit().clear().commit()
        file.delete()
    }

    @Test fun `the marker is saved in both places and cleared from both`() {
        val marker = TemporaryChatMarker(prefs, file)
        assertTrue(marker.write("t1"))
        assertEquals("t1", prefs.getString(TemporaryChatMarker.KEY_CHAT_ID, null))
        assertEquals("t1", file.readText())
        marker.clear("t1")
        assertNull(marker.read())
        assertFalse(file.exists())
    }

    @Test fun `the file copy still finds a chat whose prefs write was lost`() {
        TemporaryChatMarker(prefs, file).write("t1")
        prefs.edit().clear().commit()
        assertEquals("t1", TemporaryChatMarker(prefs, file).read())
    }

    @Test fun `clearing an older chat leaves a newer marker in place`() {
        val marker = TemporaryChatMarker(prefs, file)
        marker.write("new")
        marker.clear("old")
        assertEquals("new", marker.read())
        assertTrue(file.exists())
    }

    @Test fun `the marker from before this change is still found`() {
        prefs.edit().putString(TemporaryChatMarker.KEY_CHAT_ID, "legacy").commit()
        assertEquals("legacy", TemporaryChatMarker(prefs, file).read())
    }

    @Test fun `both copies are recovered when they name different chats`() {
        // The prefs write of the newer chat failed, so prefs still name the older one.
        prefs.edit().putString(TemporaryChatMarker.KEY_CHAT_ID, "older").commit()
        file.writeText("newer")
        val marker = TemporaryChatMarker(prefs, file)
        assertEquals(listOf("older", "newer"), marker.readAll())
        assertEquals("older", marker.read())
    }
}
