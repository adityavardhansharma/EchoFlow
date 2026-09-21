package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SystemDictationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun reset() {
        context.getSharedPreferences("system_dictation", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun `preference defaults off and setup never enables prematurely`() {
        val repository = SettingsRepository(context)
        assertFalse(repository.getSystemWideDictationDirect())
        val setup = SystemDictationSetup(repository::saveSystemWideDictation)
        setup.begin()
        assertFalse(repository.getSystemWideDictationDirect())
        setup.finish(true)
        assertTrue(repository.getSystemWideDictationDirect())
    }
    @Test fun `denial backing out and revocation all switch off`() {
        val repository = SettingsRepository(context)
        val setup = SystemDictationSetup(repository::saveSystemWideDictation)
        setup.finish(true)
        setup.finish(false)
        assertFalse(repository.getSystemWideDictationDirect())
        setup.begin()
        setup.cancel()
        assertFalse(repository.getSystemWideDictationDirect())
        setup.finish(true)
        // No permissions are granted in this fresh context. Process recreation reconciles them.
        assertFalse(SettingsRepository(context).getSystemWideDictationDirect())
    }
    @Test fun `service auto off reaches a different repository observer`() = runTest {
        val ui = SettingsRepository(context)
        val service = SettingsRepository(context)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ui.systemWideDictation.collect { seen.add(it) }
        }
        testScheduler.runCurrent()
        service.saveSystemWideDictation(true)
        testScheduler.runCurrent()
        service.saveSystemWideDictation(false)
        testScheduler.runCurrent()
        assertEquals(listOf(false, true, false), seen)
        job.cancel()
    }
    @Test fun `paste requires the same still eligible uninterrupted field`() {
        assertTrue(shouldPasteDictation(true, true, true, true))
        assertFalse(shouldPasteDictation(false, true, true, true))
        assertFalse(shouldPasteDictation(true, false, true, true))
        assertFalse(shouldPasteDictation(true, true, false, true))
        // Leaving and returning to the original box cannot revive the captured target.
        assertFalse(shouldPasteDictation(true, true, true, false))
    }
    @Test fun `modern insertion requires the same uninterrupted editor session`() {
        assertTrue(shouldCommitDictation(7, 7, true))
        assertFalse(shouldCommitDictation(7, 8, true))
        assertFalse(shouldCommitDictation(7, null, true))
        assertFalse(shouldCommitDictation(7, 7, false))
    }
    @Test fun `editor restarts retain identity but editor switches invalidate it`() {
        val tracker = DictationEditorTracker()
        val first = tracker.start("chat.app", android.text.InputType.TYPE_CLASS_TEXT, restarting = false)
        val restarted = tracker.start("chat.app", android.text.InputType.TYPE_CLASS_TEXT, restarting = true)
        assertEquals(first.generation, restarted.generation)
        val second = tracker.start("chat.app", android.text.InputType.TYPE_CLASS_TEXT, restarting = false)
        assertNotEquals(first.generation, second.generation)
        tracker.finish()
        assertNull(tracker.current)
        val afterFinish = tracker.start("chat.app", android.text.InputType.TYPE_CLASS_TEXT, restarting = true)
        assertNotEquals(second.generation, afterFinish.generation)
    }
    @Test fun `modern editor eligibility excludes own blank and secret editors`() {
        val text = android.text.InputType.TYPE_CLASS_TEXT
        val externalPackage = "chat.app"
        val ownPackage = "com.echoflow"
        assertTrue(eligibleDictationEditor(text, externalPackage, ownPackage))
        assertFalse(eligibleDictationEditor(text, "", ownPackage))
        assertFalse(eligibleDictationEditor(text, ownPackage, ownPackage))
        assertFalse(eligibleDictationEditor(text or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            externalPackage, ownPackage))
        val numericSecret = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(eligibleDictationEditor(numericSecret, externalPackage, ownPackage))
    }
    @Test fun `only visible focused external editable non secret fields qualify`() {
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            packageName = "other.app"
            isEditable = true
            isEnabled = true
            isVisibleToUser = true
            isFocused = true
        }
        assertTrue(eligibleDictationField(node, "com.echoflow"))
        node.packageName = "com.echoflow"
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.packageName = "other.app"
        node.isPassword = true
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.isPassword = false
        node.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.inputType = android.text.InputType.TYPE_CLASS_TEXT
        node.isEditable = false
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.isEditable = true
        node.isFocused = false
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.isFocused = true
        node.isVisibleToUser = false
        assertFalse(eligibleDictationField(node, "com.echoflow"))
        node.recycle()
    }
    @Test fun `composer and overlay can never own microphone capture together`() {
        val composer = AudioWavRecorder()
        val overlay = AudioWavRecorder()
        try {
            assertTrue(composer.start())
            assertFalse(overlay.start())
            composer.cancel()
            assertTrue(overlay.start())
            assertFalse(composer.start())
        } finally { composer.cancel(); overlay.cancel() }
    }
    @Test fun `copy precedes caret paste and never uses set text`() {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain()
        val shadow = org.robolectric.Shadows.shadowOf(node)
        shadow.setOnPerformActionListener { action, _ ->
            assertEquals("dictated words", clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE, action)
            true
        }
        deliverDictation(context, "dictated words") { node }
        assertEquals(listOf(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE), shadow.performedActions)
        node.recycle()
    }
    @Test fun `missing target and rejected paste both retain the new clipboard`() {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Old", "old content"))
        deliverDictation(context, "no target") { null }
        assertEquals("no target", clipboard.primaryClip!!.getItemAt(0).text.toString())
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain()
        org.robolectric.Shadows.shadowOf(node).setOnPerformActionListener { _, _ -> false }
        deliverDictation(context, "paste rejected") { node }
        assertEquals("paste rejected", clipboard.primaryClip!!.getItemAt(0).text.toString())
        node.recycle()
    }
    @Test fun `bubble edge and fractional vertical position round trip and clamp`() {
        val repository = SettingsRepository(context)
        repository.saveDictationBubblePosition(false, 0.72f)
        val restored = SettingsRepository(context)
        assertFalse(restored.getDictationBubbleRight())
        assertEquals(0.72f, restored.getDictationBubbleY())
        restored.saveDictationBubblePosition(true, 9f)
        assertEquals(1f, restored.getDictationBubbleY())
    }
}
