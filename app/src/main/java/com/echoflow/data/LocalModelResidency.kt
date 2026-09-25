package com.echoflow.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * "Keep model loaded" for on-device models. A plain prefs file rather than a
 * [SettingsRepository] field, so the Settings page and the process-wide local engine share it
 * and the engine hears a change immediately.
 */
object LocalModelResidency {
    private const val FILE = "local_runtime"
    private const val KEY_KEEP_LOADED = "keep_model_loaded"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun keepLoaded(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_LOADED, false)

    fun setKeepLoaded(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_LOADED, enabled).apply()
    }

    fun keepLoadedChanges(context: Context): Flow<Boolean> = callbackFlow {
        val store = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_KEEP_LOADED) trySend(keepLoaded(context))
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        trySend(keepLoaded(context))
        awaitClose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}

/** Whether any EchoFlow activity is on screen; false for a process started by a worker. */
object AppVisibility {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                _isForeground.value = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = maxOf(0, started - 1)
                _isForeground.value = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
