package com.echoflow.data

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
@OptIn(ExperimentalCoroutinesApi::class)
class SystemDictationServiceTest {
    @Test fun `entering EchoFlow releases recording instead of only hiding its bubble`() = runBlocking {
        verifyRecordingEnds(ownWindow = true)
    }

    @Test fun `removing the editor window releases recording`() = runBlocking {
        verifyRecordingEnds(ownWindow = false)
    }

    private suspend fun verifyRecordingEnds(ownWindow: Boolean) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val controller = Robolectric.buildService(SystemDictationService::class.java).create()
        val service = controller.get()
        val capture = AudioWavRecorder()
        val nextCapture = AudioWavRecorder()
        try {
            val settings = SettingsRepository(service)
            ReflectionHelpers.setField(service, "settings", settings)
            ReflectionHelpers.setField(service, "bubble", DictationBubble(service, settings, {}, {}))
            ReflectionHelpers.setField(service, "systemWideEnabled", true)
            ReflectionHelpers.setField(service, "prerequisitesReady", true)
            ReflectionHelpers.setField(service, "phase", DictationPhase.Recording)
            ReflectionHelpers.setField(service, "recorder", capture)
            ReflectionHelpers.setField(service, "uninterrupted", true)
            if (ownWindow) {
                val root = AccessibilityNodeInfo.obtain().apply { packageName = service.packageName }
                val window = AccessibilityWindowInfo.obtain()
                shadowOf(window).apply {
                    setType(AccessibilityWindowInfo.TYPE_APPLICATION)
                    setFocused(true)
                    setId(1)
                    setRoot(root)
                }
                shadowOf(service).setWindows(listOf(window))
            } else shadowOf(service).setWindows(emptyList())
            assertTrue(capture.start())
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                SystemDictationService::class.java.getDeclaredMethod("reconcile", Continuation::class.java)
                    .apply { isAccessible = true }.invoke(service, continuation)
            }
            withTimeout(5_000) { ReflectionHelpers.getField<Job?>(service, "session")?.join() }
            assertNotEquals(DictationPhase.Recording, ReflectionHelpers.getField<DictationPhase>(service, "phase"))
            assertFalse(ReflectionHelpers.getField<Boolean>(service, "uninterrupted"))
            assertNull(ReflectionHelpers.getField<AudioWavRecorder?>(service, "recorder"))
            assertTrue("The composer must be able to acquire the microphone", nextCapture.start())
        } finally {
            capture.cancel()
            nextCapture.cancel()
            controller.destroy()
            Dispatchers.resetMain()
        }
    }
}
