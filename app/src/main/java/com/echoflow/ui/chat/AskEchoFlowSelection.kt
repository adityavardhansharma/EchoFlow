package com.echoflow.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The "Ask EchoFlow" entry in other apps' text-selection menu. It is a manifest activity-alias
 * that ships disabled; the Echo Labs beta toggle turns the component on or off, and Android
 * keeps that state itself, so there is no separate preference to drift out of sync.
 */
object AskEchoFlowSelection {
    private fun component(context: Context) =
        ComponentName(context.packageName, "com.echoflow.AskEchoFlowSelection")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
