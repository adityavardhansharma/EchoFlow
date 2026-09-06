package com.echoflow

import android.content.Context

/** JVM tests exercise settings behavior independently of Android Keystore. */
internal fun testSettings(context: Context) =
    context.getSharedPreferences("secure_settings_prefs", Context.MODE_PRIVATE)
