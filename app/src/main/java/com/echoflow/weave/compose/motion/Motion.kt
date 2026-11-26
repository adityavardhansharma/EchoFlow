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
    if (trigger == 0) return
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
