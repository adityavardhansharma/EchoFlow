package com.echoflow.data

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.util.Log
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) abort()
            else if (::settings.isInitialized) reconcile()
        }
    }
    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsRepository(this)
        bubble = DictationBubble(this, settings, ::tap, ::disable)
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        scope.launch {
            settings.systemWideDictation.collect { enabled ->
                monitor?.cancel()
                if (!enabled) abort() else {
                    reconcile()
                    monitor = launch {
                        while (isActive) {
                            // Focus comes from accessibility events; polling only checks prerequisites.
                            delay(if (phase == DictationPhase.Idle) 15_000L else 500L)
                            if (!SystemDictationPermissions.granted(this@SystemDictationService) || !cloudReady()) {
                                disable()
                                break
                            }
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
            val keyboardTop = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .mapNotNull { keyboard ->
                    val bounds = Rect()
                    keyboard.getBoundsInScreen(bounds)
                    bounds.top.takeIf { !bounds.isEmpty }
                }.minOrNull()
            bubble.setKeyboardTop(keyboardTop)
            // A focused EchoFlow window suppresses the button, including split-screen.
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
            if (!recorder.start()) {
                // A busy microphone or a transient capture failure does not revoke the opt-in.
                abort()
                reconcile()
                return
            }
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
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    logTranscriptionFailure(error)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { logTranscriptionFailure(error) }
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
        SystemDictationPermissions.ensureNotificationChannel(this)
        val notification = NotificationCompat.Builder(this, SystemDictationPermissions.CHANNEL).setSmallIcon(R.drawable.logo)
            .setContentTitle("EchoFlow").setContentText(text).setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION, notification)
    }

    private fun logTranscriptionFailure(error: Throwable) {
        // Provider/parser messages can contain response text. Keep type and full stack, omit payloads.
        val seen = java.util.IdentityHashMap<Throwable, Throwable>()
        fun sanitized(source: Throwable): Throwable {
            seen[source]?.let { return it }
            val result = Throwable(source.javaClass.name).apply { stackTrace = source.stackTrace }
            seen[source] = result
            source.cause?.takeIf { it !== source }?.let { result.initCause(sanitized(it)) }
            source.suppressed.filter { it !== source }.forEach { result.addSuppressed(sanitized(it)) }
            return result
        }
        val diagnostic = sanitized(error)
        Log.w("SystemDictation", "Transcription failed", diagnostic)
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
    override fun onInterrupt() {
        abort()
        if (::settings.isInitialized) reconcile()
    }
    override fun onUnbind(intent: Intent?): Boolean { disable(); return super.onUnbind(intent) }
    override fun onDestroy() {
        disable()
        scope.cancel()
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        super.onDestroy()
    }
    companion object {
        private const val NOTIFICATION = 43
    }
}
