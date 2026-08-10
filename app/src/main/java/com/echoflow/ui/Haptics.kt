package com.echoflow.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Haptics for the composer's two hero actions: **send** and **stop**.
 *
 * The feel we're after is a single deliberate event with some body to it — a bare "tick" is too
 * thin (it reads as a keypress, not as "the message left") and a buzz is too long (it reads as an
 * error). Both cues are two beats inside ~80 ms, and they're deliberate mirrors of each other:
 *
 * - **Send** — rising envelope: a light tick of anticipation, then the firm click of release.
 * - **Stop** — falling envelope: the firm hit lands first, then damps out. Halted, not launched.
 *
 * Vibration hardware varies enormously, so we take the best thing the device actually reports and
 * degrade in tiers, keeping the two-beat shape for as long as the hardware allows:
 *
 * 1. **Composition primitives** (API 30+, when the motor supports them) — the real thing.
 * 2. **Shaped waveform** (API 26+ with amplitude control) — same envelope, hand-rolled.
 * 3. **Predefined effects** (API 29+) — one OEM-tuned blip, click vs. heavy click.
 * 4. **Unshaped waveform / view constants** — two beats at whatever amplitude the motor has.
 *
 * No vibrator, or system touch feedback switched off, means we stay silent.
 */
@Composable
internal fun rememberActionHaptics(): ActionHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { ActionHaptics(context.applicationContext, view) }
}

internal class ActionHaptics(context: Context, private val view: View) {

    private val resolver = context.contentResolver

    private val vibration: VibrationHaptics? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) VibrationHaptics(context) else null

    /** The user committed a message — fire as the reply starts streaming. */
    fun send() = play(VibrationHaptics.Action.SEND, HapticFeedbackConstants.VIRTUAL_KEY)

    /** The user cancelled an in-flight reply. */
    fun stop() = play(VibrationHaptics.Action.STOP, HapticFeedbackConstants.LONG_PRESS)

    private fun play(action: VibrationHaptics.Action, fallback: Int) {
        // Read every time: the user can flip touch feedback off in Settings while we're alive.
        val allowed = Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        if (!allowed) return
        if (vibration?.play(action) == true) return
        // Pre-O, or a device with no vibrator: the platform constants are all we have.
        view.performHapticFeedback(fallback)
    }
}

/** The [Vibrator]-backed tiers. Split out so the API 26+ surface stays behind one version check. */
@RequiresApi(Build.VERSION_CODES.O)
private class VibrationHaptics(context: Context) {

    enum class Action { SEND, STOP }

    private val vibrator: Vibrator? = run {
        val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        service?.takeIf { it.hasVibrator() }
    }

    private val supportsPrimitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        ) == true

    /** A low, weighty landing — the nicest possible "stop", where the motor can render it. */
    private val supportsThud = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true

    /** @return true if the device actually played something. */
    fun play(action: Action): Boolean {
        val vibrator = vibrator ?: return false
        val effect = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsPrimitives -> composed(action)
            vibrator.hasAmplitudeControl() -> shapedWaveform(action)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> predefined(action)
            else -> plainWaveform(action)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            // Tagged as touch feedback so the system scales and routes it like any other UI haptic.
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, TOUCH_ATTRIBUTES)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composed(action: Action): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        return when (action) {
            Action.SEND -> composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, SEND_GAP_MS)
            Action.STOP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsThud) {
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.75f, STOP_GAP_MS)
                } else {
                    // Without a thud, a decaying second click still gives the falling envelope.
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.55f, STOP_GAP_MS)
                }
        }.compose()
    }

    private fun shapedWaveform(action: Action): VibrationEffect = when (action) {
        Action.SEND -> VibrationEffect.createWaveform(
            longArrayOf(0, 12, 34, 24), intArrayOf(0, 100, 0, 215), NO_REPEAT,
        )
        Action.STOP -> VibrationEffect.createWaveform(
            longArrayOf(0, 24, 44, 16), intArrayOf(0, 225, 0, 120), NO_REPEAT,
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(action: Action): VibrationEffect = when (action) {
        Action.SEND -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        Action.STOP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    private fun plainWaveform(action: Action): VibrationEffect = when (action) {
        Action.SEND -> VibrationEffect.createWaveform(longArrayOf(0, 12, 34, 24), NO_REPEAT)
        Action.STOP -> VibrationEffect.createWaveform(longArrayOf(0, 24, 44, 16), NO_REPEAT)
    }

    private companion object {
        const val NO_REPEAT = -1
        const val SEND_GAP_MS = 35
        const val STOP_GAP_MS = 45

        val TOUCH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
