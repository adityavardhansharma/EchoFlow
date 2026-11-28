package com.echoflow.weave.compose.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

/** `!confetti` (03 §10.3): a short burst of theme-coloured pieces over the block. */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    // reduced motion shows no burst at all (04 §13.1)
    if (trigger == 0 || com.echoflow.ui.theme.rememberReducedMotion()) return
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, scheme.primaryContainer)
    val t = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val r = Random(trigger)
        List(60) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.3f, (r.nextFloat() - 0.5f) * 0.6f, 0.6f + r.nextFloat(), r.nextFloat() * 360f, r.nextInt(colors.size).toFloat()) }
    }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(1600, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        pieces.forEach { f ->
            val x = (f[0] + f[2] * p) * size.width
            val y = (f[1] + f[3] * p * p * 1.2f) * size.height
            rotate(f[4] + 540f * p, Offset(x, y)) {
                drawRect(colors[f[5].toInt()].copy(alpha = 1f - p), Offset(x - 4f, y - 7f), Size(8f, 14f))
            }
        }
    }
}

/** Reduced motion (animator duration scale 0) and device-motion effects. */
object Motion {
    /**
     * Without a Compose context: `areAnimatorsEnabled` on API 26+, else the global animator scale.
     * Inside composition, use `rememberReducedMotion()`.
     */
    fun reducedMotion(context: android.content.Context): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 26) !android.animation.ValueAnimator.areAnimatorsEnabled()
        else android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

/**
 * `tilt` and `shake` effects (03 §8.2, tier 4): an accelerometer listener registered only while
 * the block is composed and the app is in the foreground.
 */
@Composable
fun MotionSensors(controller: com.echoflow.weave.runtime.WeaveBlockController) {
    if (!controller.hasMotionEffects) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(controller, lifecycle) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var lastShake = 0L
        var lastTilt = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val now = System.currentTimeMillis()
                if (now - lastTilt > 50) { lastTilt = now; controller.tilt((-x / 9.81).toDouble(), (y / 9.81).toDouble()) }
                val g = kotlin.math.sqrt(x * x + y * y + z * z) / 9.81f
                if (g > 2.5f && now - lastShake > 800) { lastShake = now; controller.shake() }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            when (ev) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> sensor?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME) }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> sm?.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); sm?.unregisterListener(listener) }
    }
}
