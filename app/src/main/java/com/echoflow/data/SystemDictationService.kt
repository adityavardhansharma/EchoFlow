package com.echoflow.data

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import com.echoflow.R
import kotlinx.coroutines.*

/** OS-bound owner. No activity/composition is needed, and idle never enters foreground mode. */
class SystemDictationService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: SettingsRepository
    private lateinit var bubble: DictationBubble
    private val recorder = AudioWavRecorder()
    private val transcriber = SpeechToTextTranscriber()
    private var phase = DictationPhase.Idle
    private var target: AccessibilityNodeInfo? = null
    private var uninterrupted = false
    private var session: Job? = null
    private var cap: Job? = null
    private var monitor: Job? = null
    private var generation = 0L
    private var model = ""
    private var key = ""
    private var hinglish = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsRepository(this)
        bubble = DictationBubble(this, settings, ::tap, ::disable)
        scope.launch {
            settings.systemWideDictation.collect { enabled ->
                monitor?.cancel()
                if (!enabled) abort() else {
                    monitor = launch {
                        while (isActive) {
                            reconcile()
                            delay(500) // Also catches revocations/OEM changes without focus events.
                        }
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::settings.isInitialized || !settings.getSystemWideDictationDirect()) return
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId == target?.windowId) {
            uninterrupted = false
        }
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED && target != null) {
            val source = event.source
            if (source != target) uninterrupted = false
            source?.recycle()
        }
        reconcile()
    }

    private fun cloudReady(): Boolean = settings.getSttModeDirect() == SttMode.Cloud &&
        SttCatalog.apiKey(settings.getSttCloudModelDirect(), settings.getApiKeyDirect(),
            settings.getCustomProviderConfigDirect()).isNotBlank()

    private fun reconcile() {
        if (!settings.getSystemWideDictationDirect()) { abort(); return }
        if (!SystemDictationPermissions.granted(this) || !cloudReady()) { disable(); return }
        val node = focusedField()
        if (target != null && node != target) uninterrupted = false
        if (node == null) bubble.hide() else bubble.show(phase)
        node?.recycle()
        // Do not leave a hidden microphone running when the user leaves the text box.
        if (node == null && phase == DictationPhase.Recording) transcribe()
    }

    /** Read only focus/eligibility metadata; never traverse or collect host text for context. */
    private fun focusedField(): AccessibilityNodeInfo? {
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        val visibleWindows = windows
        val appWindows = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        try {
            // A focused EchoFlow window suppresses the circle, including split-screen.
            val window = appWindows.firstOrNull { it.isFocused } ?: return null
            val root = window.root ?: return null
            try {
                val pkg = root.packageName?.toString() ?: return null
                val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    ?.activityInfo?.packageName
                if (pkg == packageName || pkg == home) return null
                val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
                if (eligible(node)) return node
                node.recycle()
                return null
            } finally { root.recycle() }
        } finally { visibleWindows.forEach { it.recycle() } }
    }

    private fun eligible(node: AccessibilityNodeInfo) = eligibleDictationField(node, packageName)

    private fun tap() {
        if (phase == DictationPhase.Transcribing) return
        if (!settings.getSystemWideDictationDirect() || !SystemDictationPermissions.granted(this) || !cloudReady()) {
            disable(); return
        }
        if (phase == DictationPhase.Recording) { transcribe(); return }
        val field = focusedField() ?: return
        generation++
        target = field
        uninterrupted = true
        model = settings.getSttCloudModelDirect()
        key = SttCatalog.apiKey(model, settings.getApiKeyDirect(), settings.getCustomProviderConfigDirect())
        hinglish = settings.getSarvamHinglishEnabledDirect() && model == SttCatalog.SARVAM_MODEL_ID
        try {
            // This service is bound by the system. Promote that same service only on a user tap.
            foreground("Recording dictation")
            if (!recorder.start()) { disable(); return }
            phase = DictationPhase.Recording
            bubble.show(phase)
            if (phase != DictationPhase.Recording) return
            cap = scope.launch { delay(AudioWavRecorder.MAX_SECONDS * 1000L); transcribe() }
        } catch (_: Exception) { disable() }
    }

    private fun transcribe() {
        if (phase != DictationPhase.Recording) return
        if (!settings.getSystemWideDictationDirect() || !SystemDictationPermissions.granted(this) || !cloudReady()) {
            disable(); return
        }
        cap?.cancel()
        phase = DictationPhase.Transcribing
        val field = focusedField()
        if (field == null) bubble.hide() else bubble.show(phase)
        field?.recycle()
        if (phase != DictationPhase.Transcribing) return
        val thisGeneration = generation
        session = scope.launch {
            try {
                if (runCatching { foreground("Transcribing dictation") }.isFailure) { disable(); return@launch }
                val wav = withContext(Dispatchers.IO) { recorder.stop() } ?: return@launch
                transcriber.transcribe(key, model, wav, hinglish).onSuccess { transcript ->
                    ensureActive()
                    if (!settings.getSystemWideDictationDirect() || !SystemDictationPermissions.granted(this@SystemDictationService)) {
                        disable()
                        return@onSuccess
                    }
                    deliver(transcript)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* No secondary UI; return the circle to idle. */ }
            finally {
                // A cancelled request must not clear a later session after off/on or reconnection.
                if (thisGeneration == generation) {
                    clearTarget()
                    key = ""
                    phase = DictationPhase.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    reconcile()
                }
            }
        }
    }

    private fun deliver(transcript: String) = deliverDictation(this, transcript) {
        val remembered = target ?: return@deliverDictation null
        val current = focusedField()
        try {
            if (shouldPasteDictation(current == remembered, current?.let(::eligible) == true,
                    remembered.refresh() && eligible(remembered), uninterrupted)) remembered else null
        } finally { current?.recycle() }
    }

    private fun foreground(text: String) {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Dictation", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.logo)
            .setContentTitle("EchoFlow").setContentText(text).setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION, notification)
    }

    private fun clearTarget() { target?.recycle(); target = null; uninterrupted = false }
    private fun abort() {
        generation++
        cap?.cancel(); cap = null
        session?.cancel(); session = null
        recorder.cancel()
        clearTarget()
        key = ""
        phase = DictationPhase.Idle
        if (::bubble.isInitialized) bubble.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    private fun disable() {
        if (::settings.isInitialized) settings.saveSystemWideDictation(false)
        abort()
    }
    override fun onInterrupt() = disable()
    override fun onUnbind(intent: Intent?): Boolean { disable(); return super.onUnbind(intent) }
    override fun onDestroy() {
        disable()
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "system_dictation"
        private const val NOTIFICATION = 43
    }
}
