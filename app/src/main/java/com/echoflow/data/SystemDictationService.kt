package com.echoflow.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.app.KeyguardManager
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.util.Log
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.content.res.Configuration
import android.os.Trace
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.echoflow.R
import kotlinx.coroutines.*

/** OS-bound owner. No activity/composition is needed, and idle never enters foreground mode. */
class SystemDictationService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: SettingsRepository
    private lateinit var bubble: DictationBubble
    private var recorder: AudioWavRecorder? = null
    private var captureCleanup: Job? = null
    private val transcriber = SpeechToTextTranscriber()
    private var phase = DictationPhase.Idle
    private var target: AccessibilityNodeInfo? = null
    private var targetEditorGeneration: Long? = null
    private val editorTracker = DictationEditorTracker()
    private var editorInputMethod: SystemDictationInputMethod? = null
    private var uninterrupted = false
    private var session: Job? = null
    private var cap: Job? = null
    private var monitor: Job? = null
    private var generation = 0L
    private var model = ""
    private var key = ""
    private var hinglish = false
    private var prerequisitesReady = false
    private var systemWideEnabled = false
    private var starting = false
    private var focusRevision = 0L
    private var ownWindowId: Int? = null
    private var homePackage: String? = null
    private var connected = false
    private val focusWork = DictationWorkQueue(scope) { reconcile() }
    private val readinessWork = DictationWorkQueue(scope) {
        if (systemWideEnabled && refreshPrerequisites()) queueReconcile()
    }
    private val appOpsListener = AppOpsManager.OnOpChangedListener { _, _ -> readinessWork.request() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) abort()
            else if (::settings.isInitialized) {
                ownWindowId = null
                readinessWork.request()
                queueReconcile()
            }
        }
    }
    private var receiverRegistered = false

    /** Android 13+ exposes the active editor's real InputConnection to accessibility services. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateInputMethod(): InputMethod =
        SystemDictationInputMethod(this, ::onEditorStarted, ::onEditorFinished).also {
            editorInputMethod = it
        }

    private fun onEditorStarted(info: EditorInfo, restarting: Boolean) {
        editorTracker.start(info.packageName.orEmpty(), info.inputType, restarting)
        editorChanged()
    }

    private fun onEditorFinished() {
        editorTracker.finish()
        editorChanged()
    }

    private fun editorChanged() {
        if (targetEditorGeneration != null &&
            editorTracker.current?.generation != targetEditorGeneration) uninterrupted = false
        focusRevision++
        queueReconcile()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connected) return
        connected = true
        scope.launch {
            settings = withContext(Dispatchers.IO) { SettingsRepository(this@SystemDictationService) }
            homePackage = withContext(Dispatchers.IO) {
                packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    ?.activityInfo?.packageName
            }
            bubble = DictationBubble(this@SystemDictationService, settings, ::tap, ::disable)
            val appOps = getSystemService(AppOpsManager::class.java)
            appOps.startWatchingMode(AppOpsManager.OPSTR_RECORD_AUDIO, packageName, appOpsListener)
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, appOpsListener)
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(this@SystemDictationService, screenReceiver, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
            }
            launch {
                settings.dictationConfigurationChanges.collect { readinessWork.request() }
            }
            launch {
                settings.systemWideDictation.collect { enabled ->
                    monitor?.cancel()
                    systemWideEnabled = enabled
                    ownWindowId = null
                    if (!enabled) abort() else {
                        if (!refreshPrerequisites()) return@collect
                        queueReconcile()
                        monitor = launch {
                            while (isActive) {
                                // Fallback for notification-channel/OEM changes without an AppOps callback.
                                // Cryptography and Binder checks run off the UI thread.
                                delay(if (phase == DictationPhase.Idle) 15_000L else 500L)
                                if (!refreshPrerequisites()) break
                                // Recover from a host dropping its final focus/window event too.
                                // The validated own-window path does not query EchoFlow's nodes.
                                queueReconcile()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!systemWideEnabled || event == null) return
        val changes = if (Build.VERSION.SDK_INT >= 28) event.windowChanges else 0
        if (needsDictationReconcile(event.eventType, event.packageName?.toString(), packageName,
                event.windowId, ownWindowId, event.contentChangeTypes, changes)) {
            // Record interruption before coalescing: leaving and returning within 60ms must never
            // revive permission to paste into the old field. No event.source/node IPC in callbacks.
            if (target != null && (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                        changes and (AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or
                            AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0))) uninterrupted = false
            focusRevision++
            queueReconcile()
        }
    }

    private fun queueReconcile() { if (systemWideEnabled) focusWork.request() }

    private suspend fun refreshPrerequisites(): Boolean {
        val valid = withContext(Dispatchers.IO) {
            settings.getSystemWideDictationDirect() &&
                SystemDictationPermissions.granted(this@SystemDictationService) &&
                settings.getDictationConfiguration().ready
        }
        if (!systemWideEnabled) return false
        prerequisitesReady = valid
        if (!valid) disable()
        return valid
    }

    private suspend fun reconcile() {
        if (!systemWideEnabled || !prerequisitesReady) return
        val revision = focusRevision
        val captureGeneration = generation
        val editor = activeModernEditor()
        val node = if (editor == null) focusedField() else {
            updateKeyboardTop()
            null
        }
        if (!systemWideEnabled || revision != focusRevision || captureGeneration != generation) {
            node?.recycle()
            return
        }
        val sameModernTarget = targetEditorGeneration?.let { it == editor?.generation }
        val sameLegacyTarget = target?.let { it == node }
        if (target != null && sameLegacyTarget != true) uninterrupted = false
        if (targetEditorGeneration != null && sameModernTarget != true) uninterrupted = false
        if (editor == null && node == null) bubble.hide() else bubble.show(phase)
        node?.recycle()
        // Do not leave a hidden microphone running when the user leaves the text box.
        val recordingTargetPresent = when {
            targetEditorGeneration != null -> sameModernTarget == true
            target != null -> sameLegacyTarget == true
            else -> false
        }
        if (!recordingTargetPresent && phase == DictationPhase.Recording) {
            uninterrupted = false
            transcribe()
        }
    }

    private fun activeModernEditor(): DictationEditorSession? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        val editor = editorTracker.current ?: return null
        if (!eligibleDictationEditor(editor.inputType, editor.packageName, packageName) ||
            editor.packageName == homePackage) return null
        return editor.takeIf { editorInputMethod?.hasActiveConnection() == true }
    }

    /** Modern editor detection avoids node traversal, but still keeps the bubble above the IME. */
    private suspend fun updateKeyboardTop() {
        var keyboardTop: Int? = null
        try {
            withContext(Dispatchers.IO) {
                val visibleWindows = windows
                try {
                    keyboardTop = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                        .mapNotNull { keyboard ->
                            val bounds = Rect()
                            keyboard.getBoundsInScreen(bounds)
                            bounds.top.takeIf { !bounds.isEmpty }
                        }.minOrNull()
                } finally { visibleWindows.forEach { it.recycle() } }
            }
            bubble.setKeyboardTop(keyboardTop)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }

    /** Read only focus/eligibility metadata; never traverse or collect host text for context. */
    private suspend fun focusedField(): AccessibilityNodeInfo? {
        // Capture the one validated own-window id. A cache miss always inspects the currently
        // focused application; cached foreground packages never suppress an external handoff.
        val knownOwnWindow = ownWindowId
        var result: AccessibilityNodeInfo? = null
        var keyboardTop: Int? = null
        var observedOwnWindow: Int? = null
        try {
            withContext(Dispatchers.IO) {
                Trace.beginSection("Dictation.focus")
                try {
                    if (!getSystemService(PowerManager::class.java).isInteractive ||
                        getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@withContext
                    val visibleWindows = windows
                    val appWindows = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    try {
                        keyboardTop = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                            .mapNotNull { keyboard ->
                                val bounds = Rect()
                                keyboard.getBoundsInScreen(bounds)
                                bounds.top.takeIf { !bounds.isEmpty }
                            }.minOrNull()
                        // A focused EchoFlow window suppresses the button, including split-screen.
                        val window = appWindows.firstOrNull { it.isFocused } ?: return@withContext
                        if (window.id == knownOwnWindow) {
                            observedOwnWindow = window.id
                            return@withContext
                        }
                        val root = (if (Build.VERSION.SDK_INT >= 33) window.getRoot(0) else window.root)
                            ?: return@withContext
                        try {
                            val pkg = root.packageName?.toString() ?: return@withContext
                            if (pkg == packageName) {
                                observedOwnWindow = window.id
                                return@withContext
                            }
                            if (pkg == homePackage) return@withContext
                            val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return@withContext
                            if (eligible(node)) { result = node; return@withContext }
                            node.recycle()
                        } finally { root.recycle() }
                    } finally { visibleWindows.forEach { it.recycle() } }
                } finally { Trace.endSection() }
            }
            ownWindowId = observedOwnWindow
            bubble.setKeyboardTop(keyboardTop)
            return result
        } catch (error: Exception) {
            result?.recycle()
            if (error is CancellationException) throw error
            // A window can disappear between enumeration and the node query. Retry on the next
            // event; never crash the service or keep recording into a missing target.
            return null
        }
    }

    private fun eligible(node: AccessibilityNodeInfo) = eligibleDictationField(node, packageName)

    private fun tap() {
        if (phase == DictationPhase.Transcribing || starting) return
        if (phase == DictationPhase.Recording) { transcribe(); return }
        starting = true
        val beforeStart = generation
        scope.launch {
            try {
                // Idle can become visible before AudioRecord finishes joining its reader thread.
                // Suspend a retry until that capture releases the process-wide microphone owner.
                captureCleanup?.join()
                if (!systemWideEnabled || beforeStart != generation) return@launch
                if (!refreshPrerequisites()) return@launch
                val config = withContext(Dispatchers.IO) { settings.getDictationConfiguration() }
                if (!config.ready) { disable(); return@launch }
                val revision = focusRevision
                val editor = activeModernEditor()
                val field = if (editor == null) focusedField() else null
                if (editor == null && field == null) return@launch
                if (!systemWideEnabled || beforeStart != generation || revision != focusRevision) {
                    field?.recycle()
                    return@launch
                }
                generation++
                val captureGeneration = generation
                if (editor != null) {
                    targetEditorGeneration = editor.generation
                    target = null
                } else {
                    targetEditorGeneration = null
                    target = field
                }
                uninterrupted = true
                model = config.model
                key = config.key
                hinglish = config.hinglish
                try {
                    // This service is bound by the system. Promote that same service only on a user tap.
                    foreground("Recording dictation")
                    val capture = AudioWavRecorder()
                    recorder = capture
                    val started = withContext(NonCancellable + Dispatchers.IO) { capture.start() }
                    if (!isActive || !systemWideEnabled || captureGeneration != generation) {
                        cancelCapture(capture)
                        return@launch
                    }
                    if (!started) {
                        // A busy microphone or a transient capture failure does not revoke the opt-in.
                        abort()
                        queueReconcile()
                        return@launch
                    }
                    phase = DictationPhase.Recording
                    bubble.show(phase)
                    if (phase != DictationPhase.Recording) return@launch
                    cap = scope.launch { delay(AudioWavRecorder.MAX_SECONDS * 1000L); transcribe() }
                    // Focus may have changed while AudioRecord initialized on the worker thread.
                    queueReconcile()
                } catch (_: Exception) { disable() }
            } finally { starting = false }
        }
    }

    private fun transcribe() {
        if (phase != DictationPhase.Recording) return
        cap?.cancel()
        phase = DictationPhase.Transcribing
        // Do not query focus before releasing the microphone. The caller already hid the bubble
        // on focus loss; a user-initiated stop updates only an attached bubble.
        bubble.updatePhase(phase)
        val capture = recorder
        recorder = null
        // Keep microphone release independent of the cancellable transcription request, but
        // tracked by this service so off/on followed by a tap waits for it to finish.
        val release = scope.async(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable + Dispatchers.IO) { capture?.stop() }
        }
        trackCaptureCleanup(release)
        val thisGeneration = generation
        val sessionKey = key
        val sessionModel = model
        val sessionHinglish = hinglish
        session = scope.launch {
            try {
                val wav = release.await() ?: return@launch
                ensureActive()
                if (!refreshPrerequisites()) return@launch
                if (runCatching { foreground("Transcribing dictation") }.isFailure) { disable(); return@launch }
                transcriber.transcribe(sessionKey, sessionModel, wav, sessionHinglish).onSuccess { transcript ->
                    ensureActive()
                    if (!refreshPrerequisites()) return@onSuccess
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
                    queueReconcile()
                }
            }
        }
    }

    private suspend fun deliver(transcript: String) {
        val rememberedEditor = targetEditorGeneration
        if (rememberedEditor != null) {
            val thisGeneration = generation
            val current = activeModernEditor()
            if (!systemWideEnabled || thisGeneration != generation) return
            copyDictation(this, transcript)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shouldCommitDictation(rememberedEditor, current?.generation, uninterrupted)) {
                commitModern(transcript)
            }
            return
        }
        // Own the copy across suspension: abort() may recycle the session target meanwhile.
        val remembered = target?.let { AccessibilityNodeInfo.obtain(it) }
        val thisGeneration = generation
        val revision = focusRevision
        var current: AccessibilityNodeInfo? = null
        try {
            current = focusedField()
            val valid = remembered != null && withContext(Dispatchers.IO) {
                remembered.refresh() && eligible(remembered)
            }
            if (!systemWideEnabled || thisGeneration != generation) return
            deliverDictation(this, transcript) {
                if (shouldPasteDictation(current == remembered, current?.let(::eligible) == true,
                        valid, uninterrupted && revision == focusRevision)) remembered else null
            }
        } finally { current?.recycle(); remembered?.recycle() }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun commitModern(transcript: String) {
        runCatching { editorInputMethod?.commit(transcript) }
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

    private fun clearTarget() {
        target?.recycle()
        target = null
        targetEditorGeneration = null
        uninterrupted = false
    }
    private fun abort() {
        generation++
        cap?.cancel(); cap = null
        session?.cancel(); session = null
        recorder?.let(::cancelCapture)
        recorder = null
        clearTarget()
        key = ""
        phase = DictationPhase.Idle
        if (::bubble.isInitialized) bubble.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    private fun disable() {
        systemWideEnabled = false
        prerequisitesReady = false
        ownWindowId = null
        if (::settings.isInitialized) settings.saveSystemWideDictation(false)
        abort()
    }
    // Begin cleanup even during teardown; only the finite IO release is non-cancellable.
    // Track and serialize releases instead of launching detached cleanup scopes.
    private fun cancelCapture(capture: AudioWavRecorder) {
        val previous = captureCleanup
        trackCaptureCleanup(scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable + Dispatchers.IO) {
                previous?.join()
                capture.cancel()
            }
        })
    }

    private fun trackCaptureCleanup(job: Job) {
        captureCleanup = job
        job.invokeOnCompletion {
            scope.launch {
                // Do not retain a completed Deferred's WAV buffer or clear a newer release.
                if (captureCleanup === job) captureCleanup = null
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::bubble.isInitialized) bubble.invalidatePosition()
        queueReconcile()
    }
    override fun onInterrupt() {
        abort()
        ownWindowId = null
        queueReconcile()
    }
    override fun onUnbind(intent: Intent?): Boolean { disable(); return super.onUnbind(intent) }
    override fun onDestroy() {
        disable()
        focusWork.close()
        readinessWork.close()
        getSystemService(AppOpsManager::class.java).stopWatchingMode(appOpsListener)
        scope.cancel()
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        super.onDestroy()
    }
    companion object {
        private const val NOTIFICATION = 43
    }
}
