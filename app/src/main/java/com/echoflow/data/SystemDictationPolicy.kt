package com.echoflow.data

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/** Setup is a transaction: pending permission requests never persist an enabled feature. */
internal class SystemDictationSetup(private val save: (Boolean) -> Unit) {
    fun begin() = save(false)
    fun finish(granted: Boolean) = save(granted)
    fun cancel() = save(false)
}

internal object SystemDictationPermissions {
    fun microphone(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun notifications(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun accessibility(context: Context): Boolean {
        val expected = ComponentName(context, SystemDictationService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == expected }
    }
    fun granted(context: Context) = microphone(context) && Settings.canDrawOverlays(context) &&
        accessibility(context) && notifications(context)
}

/** Identity is node identity (window + accessibility source id), never view-id/text equality. */
internal fun shouldPasteDictation(sameNode: Boolean, eligible: Boolean, targetRefreshed: Boolean, uninterrupted: Boolean) =
    sameNode && eligible && targetRefreshed && uninterrupted

internal fun eligibleDictationField(node: AccessibilityNodeInfo, ownPackage: String): Boolean {
    val variation = node.inputType and android.text.InputType.TYPE_MASK_VARIATION
    val inputClass = node.inputType and android.text.InputType.TYPE_MASK_CLASS
    val secret = node.isPassword ||
        (inputClass == android.text.InputType.TYPE_CLASS_TEXT && variation in listOf(
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
        (inputClass == android.text.InputType.TYPE_CLASS_NUMBER && variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    return node.isEditable && node.isEnabled && node.isVisibleToUser && node.isFocused &&
        !secret && node.packageName?.toString()?.let { it.isNotBlank() && it != ownPackage } == true
}


/** Copy first, then ask for a freshly validated target. Failed paste leaves the copied result intact. */
internal fun deliverDictation(context: Context, transcript: String, pasteTarget: () -> AccessibilityNodeInfo?) {
    val clip = ClipData.newPlainText("Dictation", transcript)
    // Android owns its clipboard affordance; suppress the transcript preview as sensitive data.
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    pasteTarget()?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
}
