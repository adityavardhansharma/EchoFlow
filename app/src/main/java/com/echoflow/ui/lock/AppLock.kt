package com.echoflow.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
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
 * Echo Labs beta: locks EchoFlow behind the phone's own screen lock. Unlocking goes through the
 * system prompt, so it is whatever the device already uses — Pixel's fingerprint, face or Google
 * PIN, One UI's Samsung biometrics or PIN, or any other OEM's — with the PIN, pattern or password
 * always offered as a fallback. EchoFlow never sees or stores a credential.
 *
 * Owned by [MainActivity][com.echoflow.MainActivity] and created with it, before it is started,
 * so it can register its activity-result launcher.
 */
class AppLock(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    private val prefs by lazy { activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lockAfter = MutableStateFlow(LockAfter.OneMinute)
    val lockAfter: StateFlow<LockAfter> = _lockAfter.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // When the app last left the foreground; null while it is showing.
    private var backgroundedAt: Long? = null
    // The system prompt (or, before Android 10, the confirm-credential screen) is up. It can
    // stop this activity, which must not count as leaving the app.
    private var authenticating = false
    // One automatic prompt per lock; after a cancel the user taps Unlock themselves.
    private var promptedThisLock = false
    private var pendingResult: ((Boolean) -> Unit)? = null

    private val confirmCredential =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            finish(it.resultCode == Activity.RESULT_OK)
        }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _lockAfter.value = LockAfter.fromStorage(prefs.getString(KEY_LOCK_AFTER, null))
        // A fresh process always starts locked.
        _locked.value = _enabled.value
        applyRecentsPreview()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (_enabled.value && SystemClock.elapsedRealtime() - since >= _lockAfter.value.millis) lock()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (_locked.value && !promptedThisLock && !authenticating) {
            promptedThisLock = true
            // Posted so the prompt opens once the window is fully up.
            activity.window.decorView.post { if (_locked.value) unlock() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (activity.isChangingConfigurations || authenticating) return
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** True when the phone has a PIN, pattern or password — without one there is nothing to unlock with. */
    fun deviceSecure(): Boolean = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Asks for the device unlock before turning the lock on, so it's known to work. */
    fun enable(onResult: (Boolean) -> Unit) {
        if (!deviceSecure()) return onResult(false)
        authenticate("Turn on app lock", "Confirm it's you to lock EchoFlow") { ok ->
            if (ok) {
                _enabled.value = true
                prefs.edit().putBoolean(KEY_ENABLED, true).apply()
                applyRecentsPreview()
            }
            onResult(ok)
        }
    }

    fun disable() {
        _enabled.value = false
        _locked.value = false
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        applyRecentsPreview()
    }

    fun setLockAfter(value: LockAfter) {
        _lockAfter.value = value
        prefs.edit().putString(KEY_LOCK_AFTER, value.storageKey).apply()
    }

    /** Shows the system unlock prompt over the lock screen. */
    fun unlock() {
        if (!_locked.value || authenticating) return
        // The screen lock was removed since the app lock was turned on (which itself needed the
        // old credential): there is nothing left to check against, so don't strand the user.
        if (!deviceSecure()) {
            disable()
            return
        }
        authenticate("Unlock EchoFlow", null) { ok ->
            if (ok) {
                _locked.value = false
                promptedThisLock = false
            }
        }
    }

    private fun lock() {
        _locked.value = true
        promptedThisLock = false
    }

    private fun authenticate(title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        pendingResult = onResult
        authenticating = true
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
        authenticating = false
        pendingResult?.let { pendingResult = null; it(ok) }
    }

    // With the lock on, the Recents thumbnail is blanked so chats don't show in the app switcher.
    // Screenshots inside the app still work (unlike FLAG_SECURE).
    private fun applyRecentsPreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!_enabled.value)
        }
    }

    private companion object {
        const val PREFS = "app_lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_LOCK_AFTER = "lock_after"
    }
}

/** The activity's [AppLock], for the lock screen and its Echo Labs settings row. */
val LocalAppLock = staticCompositionLocalOf<AppLock?> { null }
