package com.echoflow.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long EchoFlow can sit in the background before it locks again. */
enum class LockAfter(val storageKey: String, val label: String, val millis: Long) {
    Immediately("now", "Now", 0L),
    OneMinute("1m", "1 min", 60_000L),
    FiveMinutes("5m", "5 min", 5 * 60_000L),
    ThirtyMinutes("30m", "30 min", 30 * 60_000L);

    companion object {
        fun fromStorage(value: String?): LockAfter = entries.firstOrNull { it.storageKey == value } ?: OneMinute
    }
}

/**
 * The lock itself, once per process. Every MainActivity — the launcher one and any opened by
 * Share or Ask EchoFlow — reads the same setting and the same locked flag, so turning the lock on
 * in one protects them all, and unlocking one unlocks the app. Time in the background is measured
 * by [ProcessLifecycleOwner], which ignores rotation and hops between EchoFlow's own activities,
 * so neither of those asks for another unlock; a fresh process always starts locked.
 */
internal object AppLockState : DefaultLifecycleObserver {
    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lockAfter = MutableStateFlow(LockAfter.OneMinute)
    val lockAfter: StateFlow<LockAfter> = _lockAfter.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // When the app last left the foreground; null while it is showing.
    private var backgroundedAt: Long? = null
    // The system prompt (or, before Android 10, the confirm-credential screen) is up. It can send
    // the app to the background, which must not count as leaving it.
    var authenticating = false
    // One automatic prompt per lock; after a cancel the user taps Unlock themselves.
    var promptedThisLock = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _lockAfter.value = LockAfter.fromStorage(prefs.getString(KEY_LOCK_AFTER, null))
        _locked.value = _enabled.value
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (_enabled.value && SystemClock.elapsedRealtime() - since >= _lockAfter.value.millis) lock()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (authenticating) return
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        if (!on) _locked.value = false
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun setLockAfter(value: LockAfter) {
        _lockAfter.value = value
        prefs.edit().putString(KEY_LOCK_AFTER, value.storageKey).apply()
    }

    fun unlocked() {
        _locked.value = false
        promptedThisLock = false
    }

    private fun lock() {
        _locked.value = true
        promptedThisLock = false
    }

    private const val PREFS = "app_lock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOCK_AFTER = "lock_after"
}

/**
 * Echo Labs beta: locks EchoFlow behind the phone's own screen lock. Unlocking goes through the
 * system prompt, so it is whatever the device already uses — Pixel's fingerprint, face or Google
 * PIN, One UI's Samsung biometrics or PIN, or any other OEM's — with the PIN, pattern or password
 * always offered as a fallback. EchoFlow never sees or stores a credential.
 *
 * One per [MainActivity][com.echoflow.MainActivity], created with it so it can register its
 * activity-result launcher; the state it shows lives in [AppLockState].
 */
class AppLock(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    val enabled: StateFlow<Boolean> get() = AppLockState.enabled
    val lockAfter: StateFlow<LockAfter> get() = AppLockState.lockAfter
    val locked: StateFlow<Boolean> get() = AppLockState.locked

    private var pendingResult: ((Boolean) -> Unit)? = null

    private val confirmCredential =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            finish(it.resultCode == Activity.RESULT_OK)
        }

    init {
        activity.lifecycle.addObserver(this)
    }

    /** Reads the saved setting; call before the first frame so a locked app never flashes its chats. */
    fun load() = AppLockState.init(activity)

    override fun onResume(owner: LifecycleOwner) {
        // Another instance may have changed the setting while this one was in the background.
        applyRecentsPreview()
        if (AppLockState.locked.value && !AppLockState.promptedThisLock && !AppLockState.authenticating) {
            AppLockState.promptedThisLock = true
            // Posted so the prompt opens once the window is fully up.
            activity.window.decorView.post { if (AppLockState.locked.value) unlock() }
        }
    }

    /** True when the phone has a PIN, pattern or password — without one there is nothing to unlock with. */
    fun deviceSecure(): Boolean = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Asks for the device unlock before turning the lock on, so it's known to work. */
    fun enable(onResult: (Boolean) -> Unit) {
        if (!deviceSecure()) return onResult(false)
        authenticate("Turn on app lock", "Confirm it's you to lock EchoFlow") { ok ->
            if (ok) {
                AppLockState.setEnabled(true)
                applyRecentsPreview()
            }
            onResult(ok)
        }
    }

    fun disable() {
        AppLockState.setEnabled(false)
        applyRecentsPreview()
    }

    fun setLockAfter(value: LockAfter) = AppLockState.setLockAfter(value)

    /** Shows the system unlock prompt over the lock screen. */
    fun unlock() {
        if (!AppLockState.locked.value || AppLockState.authenticating) return
        // The screen lock was removed since the app lock was turned on (which itself needed the
        // old credential): there is nothing left to check against, so don't strand the user.
        if (!deviceSecure()) {
            disable()
            return
        }
        authenticate("Unlock EchoFlow", null) { ok -> if (ok) AppLockState.unlocked() }
    }

    private fun authenticate(title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        pendingResult = onResult
        AppLockState.authenticating = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builder = BiometricPrompt.Builder(activity).setTitle(title)
            subtitle?.let(builder::setSubtitle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Weak (class 2) biometrics are fine without a crypto object, and let the face
                // unlock many OEMs ship — Samsung's among them — open the app too.
                builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            builder.build().authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(true)
                    // A single unrecognised finger keeps the prompt open; only errors end it.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish(false)
                },
            )
        } else {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent == null) finish(false) else confirmCredential.launch(intent)
        }
    }

    private fun finish(ok: Boolean) {
        AppLockState.authenticating = false
        pendingResult?.let { pendingResult = null; it(ok) }
    }

    // With the lock on, the Recents thumbnail is blanked so chats don't show in the app switcher
    // (Android 13+). Screenshots inside the app still work (unlike FLAG_SECURE).
    private fun applyRecentsPreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!AppLockState.enabled.value)
        }
    }

    companion object {
        /** Recents can only be blanked on Android 13 and newer. */
        val hidesRecents: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}

/** The activity's [AppLock], for the lock screen and its Echo Labs settings row. */
val LocalAppLock = staticCompositionLocalOf<AppLock?> { null }
