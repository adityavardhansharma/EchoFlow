package com.echoflow.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val CellSize = 22.dp

/** How far a touch reaches. Wide enough to read as a field being disturbed, not a cursor. */
private val TouchRadius = 84.dp

/** Peak displacement directly under the finger. */
private val TouchPush = 16.dp

/** A single dot at rest. Below a pixel or two it stops being a dot and becomes grain. */
private val DotRadius = 1.1.dp

/** Dots dim toward the top and bottom so the field never fights the bar or the composer. */
private val EdgeFade = 96.dp

private const val RestAlpha = 0.10f
private const val PeakAlpha = 0.40f

/** How much a dot swells at the crest of a wake. Barely — the brightness carries the wake. */
private const val CrestGrowth = 0.42f

/** Fraction of the half-diagonal held at full brightness before the vignette starts. */
private const val VignetteHold = 0.42f

/**
 * The living field: Imagine's blank canvas.
 *
 * Every empty state is a still arrangement of things — a mark, a headline, a few pills — and a
 * still arrangement is exactly what a creative surface should not be. This one is built from
 * the app's own material, the generation dots, and it is **alive and touchable**: a slow
 * current drifts through it constantly, and a finger pushes it apart, leaving a wake that
 * springs shut behind you.
 *
 * That states the whole pitch of the surface without a word of copy. A blank page that moves
 * when you touch it is obviously somewhere you *do* something, not somewhere you read about
 * doing something. And because it is the same texture that will dance while a real generation
 * runs, the first thing anyone learns here is the app's own visual language.
 *
 * The tuning is deliberately below the threshold where you would call it an animation. Every
 * value here was pulled *down* — dimmer, finer, slower, less travel — because the difference
 * between a texture that feels expensive and one that feels like a screensaver is entirely a
 * matter of restraint. It is one hue, never two: a colour shift at the crest is the tell of an
 * effect showing off. Position, size and brightness are all continuous functions of one
 * displacement value, so no channel can pop or drift out of step with another.
 */
@Composable
internal fun ImagineField(modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    var timeMs by remember { mutableLongStateOf(0L) }
    var touch by remember { mutableStateOf<Offset?>(null) }

    // The wake opens under the finger and springs shut on release rather than snapping off,
    // so lifting away feels like letting go of something.
    val reach = remember { Animatable(0f) }

    if (!reducedMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { nanos -> timeMs = nanos / 1_000_000 }
            }
        }
    }
    LaunchedEffect(touch != null) {
        reach.animateTo(
            targetValue = if (touch != null) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (touch != null) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val dotColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier.pointerInput(reducedMotion) {
            if (reducedMotion) return@pointerInput
            // Tracked by hand rather than via a drag detector: the field must answer the
            // instant a finger lands, not once the touch has travelled far enough to count
            // as a drag.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touch = down.position
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    touch = change.position
                }
                touch = null
            }
        }
    ) {
        // Read inside the draw block so the field redraws every frame without recomposing.
        val t = timeMs / 1000f
        val reachNow = reach.value
        val touchNow = touch

        val cell = CellSize.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val originX = (size.width - (cols - 1) * cell) / 2f
        val originY = (size.height - (rows - 1) * cell) / 2f
        val restRadius = DotRadius.toPx()
        val radius = TouchRadius.toPx()
        val push = TouchPush.toPx()
        val fadeBand = EdgeFade.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val halfDiagonal = hypot(centerX, centerY).coerceAtLeast(1f)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val homeX = originX + col * cell
                val homeY = originY + row * cell

                // Two sine layers at different scales and speeds, each axis driven by the
                // *other* axis's coordinate. Cheap, but that cross-coupling is what turns a
                // pulsing grid into something reading as a current, with no repeat to spot.
                var x = homeX
                var y = homeY
                if (!reducedMotion) {
                    x += sin(homeY / 150f + t * 0.28f) * 4.5f + sin(homeY / 61f - t * 0.18f) * 1.8f
                    y += cos(homeX / 168f + t * 0.22f) * 4.5f + cos(homeX / 74f - t * 0.15f) * 1.8f
                }

                if (touchNow != null && reachNow > 0.001f) {
                    val dx = x - touchNow.x
                    val dy = y - touchNow.y
                    val distance = hypot(dx, dy).coerceAtLeast(0.001f)
                    // Gaussian falloff: the disturbance has no edge to notice, it simply
                    // stops mattering. A linear radius would draw a visible circle.
                    val amount = exp(-(distance * distance) / (2f * radius * radius)) * push * reachNow
                    x += dx / distance * amount
                    y += dy / distance * amount
                }

                val displaced = hypot(x - homeX, y - homeY)
                // Smoothstep, so the wake has a soft shoulder rather than a hard bright ring.
                val eased = smoothstep((displaced / push).coerceIn(0f, 1f))

                val edge = (min(y, size.height - y) / fadeBand).coerceIn(0f, 1f)
                // Held flat across the middle, then eased away, so the field dissolves into
                // the corners instead of ending at four hard grid boundaries.
                val fromCenter = hypot(x - centerX, y - centerY) / halfDiagonal
                val vignette = smoothstep(
                    1f - ((fromCenter - VignetteHold) / (1f - VignetteHold)).coerceIn(0f, 1f)
                )

                // A fixed per-dot weight. Held constant over time on purpose: varying it would
                // read as noise, while a still unevenness reads as the grain of a material.
                val grain = 0.72f + 0.56f * hash(col, row)
                val alpha = (RestAlpha * grain + (PeakAlpha - RestAlpha) * eased) * edge * vignette
                if (alpha <= 0.004f) continue

                drawCircle(
                    color = dotColor,
                    radius = restRadius * (1f + CrestGrowth * eased),
                    center = Offset(x, y),
                    alpha = alpha,
                )
            }
        }
    }
}

private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

/** Deterministic per-cell value in 0..1. Stable across frames, so the grain never crawls. */
private fun hash(col: Int, row: Int): Float {
    var h = col * 374761393 + row * 668265263
    h = (h xor (h shr 13)) * 1274126177
    return ((h xor (h shr 16)) and 0xFFFF) / 65535f
}
