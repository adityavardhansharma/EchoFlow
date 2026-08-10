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
 * - **Send** — rising envelope: a softer click of anticipation, then a full-scale one on release.
 * - **Stop** — falling envelope: the full-scale hit lands first, then damps out. Halted, not launched.
 *
 * Both accent beats run at scale 1.0. The envelope comes from the *quieter* beat being quieter,
 * never from holding the whole cue back — the system already scales us down by the user's touch
 * feedback intensity, so anything less than full scale here arrives thin.
 *
 * Vibration hardware varies enormously, so we take the best thing the device actually reports and
 * degrade in tiers, keeping the two-beat shape for as long as the hardware allows:
 *
 * 1. **Composition primitives** (API 30+, when the motor supports them) — the real thing.
 * 2. **Shaped waveform** (API 26+ with amplitude control) — same envelope, hand-rolled.
 * 3. **Predefined effects** (API 29+) — one OEM-tuned blip, heavy click vs. double click.
 * 4. **Unshaped waveform** (API 26+) — the envelope carried by durations alone.
 * 5. **Legacy pattern** (API 24–25) — the same durations through the pre-[VibrationEffect] API.
 * 6. **View constants** — only when there is no vibrator at all, so nothing will be felt anyway.
 *
 * Send and stop stay distinguishable at *every* tier, including the last two. A fallback that
 * plays one cue for both actions is worse than no fallback: it actively teaches the wrong thing.
 *
 * No vibrator, or system touch feedback switched off, means we stay silent.
 */
@Composable
internal fun rememberActionHaptics(): ActionHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) { ActionHaptics(context.applicationContext, view) }
}

/** Which of the two cues to play. Both tiers render it; only the fidelity differs. */
internal enum class HapticAction { SEND, STOP }

internal class ActionHaptics(context: Context, private val view: View) {

    private val resolver = context.contentResolver

    private val vibration: VibrationHaptics? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) VibrationHaptics(context) else null

    private val legacy: LegacyVibration? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) LegacyVibration(context) else null

    /** The user committed a message — fire as the reply starts streaming. */
    fun send() = play(HapticAction.SEND, HapticFeedbackConstants.VIRTUAL_KEY)

    /** The user cancelled an in-flight reply. */
    fun stop() = play(HapticAction.STOP, HapticFeedbackConstants.LONG_PRESS)

    private fun play(action: HapticAction, fallback: Int) {
        // Read every time: the user can flip touch feedback off in Settings while we're alive.
        // The setting is deprecated but still the one the platform honours, and USAGE_TOUCH
        // already respects it — this is belt and braces for the view-constant path.
        @Suppress("DEPRECATION")
        val allowed = Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        if (!allowed) return
        // The version check is redundant with `vibration` being non-null only on O+, but lint
        // cannot see that through the field, and it is the check that keeps the build honest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibration?.play(action) == true) return
        if (legacy?.play(action) == true) return
        // Genuinely nothing left: no vibrator at all. The constants differ per action anyway —
        // send and stop must never become the same cue, whichever tier ends up rendering them.
        view.performHapticFeedback(fallback)
    }
}

/**
 * Pre-O devices have a [Vibrator] but no [VibrationEffect], so every tier above is out of reach.
 * The two-beat envelope is not: with no amplitude control the shape lives entirely in the
 * durations, which is exactly what the unshaped waveform encodes. So API 24–25 still gets a real
 * send-versus-stop pair rather than collapsing both onto one platform constant.
 */
private class LegacyVibration(context: Context) {

    private val vibrator: Vibrator? = run {
        @Suppress("DEPRECATION")
        val service = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        service?.takeIf { it.hasVibrator() }
    }

    fun play(action: HapticAction): Boolean {
        val vibrator = vibrator ?: return false
        @Suppress("DEPRECATION")
        vibrator.vibrate(unshapedPattern(action), NO_REPEAT, TOUCH_ATTRIBUTES)
        return true
    }
}

/** The [Vibrator]-backed tiers. Split out so the API 26+ surface stays behind one version check. */
@RequiresApi(Build.VERSION_CODES.O)
private class VibrationHaptics(context: Context) {

    private val vibrator: Vibrator? = run {
        val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        service?.takeIf { it.hasVibrator() }
    }

    // Only CLICK is required: it is both the most widely supported primitive and the only one
    // with real punch. TICK is deliberately unused — it is the weakest thing the motor can do,
    // and using it for the lead beat is what made the first cut of this feel thin.
    private val supportsPrimitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) == true

    /** A low, weighty landing — the nicest possible "stop", where the motor can render it. */
    private val supportsThud = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) == true

    /** @return true if the device actually played something. */
    fun play(action: HapticAction): Boolean {
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
    private fun composed(action: HapticAction): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        return when (action) {
            // The accent beat of each cue runs at full scale; the envelope comes from the *other*
            // beat being softer, not from holding the whole cue back.
            HapticAction.SEND -> composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.65f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, SEND_GAP_MS)
            HapticAction.STOP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsThud) {
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, STOP_GAP_MS)
                } else {
                    // Without a thud, a decaying second click still gives the falling envelope.
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, STOP_GAP_MS)
                }
        }.compose()
    }

    private fun shapedWaveform(action: HapticAction): VibrationEffect = when (action) {
        HapticAction.SEND -> VibrationEffect.createWaveform(
            longArrayOf(0, 12, 34, 24), intArrayOf(0, 165, 0, 255), NO_REPEAT,
        )
        HapticAction.STOP -> VibrationEffect.createWaveform(
            longArrayOf(0, 24, 44, 16), intArrayOf(0, 255, 0, 160), NO_REPEAT,
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(action: HapticAction): VibrationEffect = when (action) {
        // Heavy on both: a plain EFFECT_CLICK is the same blip this tier's devices already use
        // for a keypress, which is exactly the "was that anything?" feel we're avoiding.
        HapticAction.SEND -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        HapticAction.STOP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    private fun plainWaveform(action: HapticAction): VibrationEffect =
        VibrationEffect.createWaveform(unshapedPattern(action), NO_REPEAT)

}

private const val NO_REPEAT = -1

// Wide enough that the two beats stay legible as two. Closer together and a slower motor smears
// them into one long event, which reads as a mushy buzz rather than as a firmer tap — "weak" and
// "blurred" feel like the same thing under a fingertip.
private const val SEND_GAP_MS = 50
private const val STOP_GAP_MS = 55

/**
 * The envelope expressed purely in durations, for the two tiers with no amplitude control: a
 * short beat then a long one for send, the reverse for stop.
 *
 * Longer pulses than the shaped tier on purpose. A device without amplitude control is almost
 * always a rotating-mass motor, which needs tens of milliseconds just to spin up — feed it the
 * crisp 12ms pulse that suits an LRA and it barely moves at all.
 */
private fun unshapedPattern(action: HapticAction): LongArray = when (action) {
    HapticAction.SEND -> longArrayOf(0, 25, 35, 45)
    HapticAction.STOP -> longArrayOf(0, 45, 35, 25)
}

/** Tagged as touch feedback so the system scales and routes it like any other UI haptic. */
private val TOUCH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
