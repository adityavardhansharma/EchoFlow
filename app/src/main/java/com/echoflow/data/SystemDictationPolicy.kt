package com.echoflow.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Setup is a transaction: pending permission requests never persist an enabled feature. */
internal class SystemDictationSetup(private val save: (Boolean) -> Unit) {
    fun begin() = save(false)
    fun finish(granted: Boolean) = save(granted)
    fun cancel() = save(false)
}

internal object SystemDictationPermissions {
    fun microphone(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    const val CHANNEL = "system_dictation"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Dictation", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    fun notifications(context: Context): Boolean {
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
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

internal fun shouldCommitDictation(recordedEditor: Long, currentEditor: Long?, uninterrupted: Boolean) =
    recordedEditor == currentEditor && uninterrupted

internal fun secretDictationInput(inputType: Int, password: Boolean = false): Boolean {
    val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
    return password ||
        (inputClass == android.text.InputType.TYPE_CLASS_TEXT && variation in listOf(
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
        (inputClass == android.text.InputType.TYPE_CLASS_NUMBER &&
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
}

internal fun eligibleDictationEditor(inputType: Int, editorPackage: String, ownPackage: String): Boolean =
    editorPackage.isNotBlank() && editorPackage != ownPackage && !secretDictationInput(inputType)

internal fun eligibleDictationField(node: AccessibilityNodeInfo, ownPackage: String): Boolean {
    return node.isEditable && node.isEnabled && node.isVisibleToUser && node.isFocused &&
        !secretDictationInput(node.inputType, node.isPassword) &&
        node.packageName?.toString()?.let { it.isNotBlank() && it != ownPackage } == true
}

/** Always retain a recoverable result if the host rejects either insertion mechanism. */
internal fun copyDictation(context: Context, transcript: String) {
    val clip = ClipData.newPlainText("Dictation", transcript)
    // Android owns its clipboard affordance; suppress the transcript preview as sensitive data.
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/** Copy first, then ask for a freshly validated target. Failed paste leaves the copied result intact. */
internal fun deliverDictation(context: Context, transcript: String, pasteTarget: () -> AccessibilityNodeInfo?) {
    copyDictation(context, transcript)
    pasteTarget()?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
}
