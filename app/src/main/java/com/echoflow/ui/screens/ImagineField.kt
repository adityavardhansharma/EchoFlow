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
import androidx.compose.ui.graphics.lerp
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
private val TouchPush = 26.dp

/** Dots dim toward the top and bottom so the field never fights the bar or the composer. */
private val EdgeFade = 96.dp

private const val RestAlpha = 0.13f
private const val PeakAlpha = 0.72f

/**
 * The living field: Imagine's blank canvas.
 *
 * Every empty state is a still arrangement of things — a mark, a headline, a few pills — and a
 * still arrangement is exactly what a creative surface should not be. This one is built from
 * the app's own material, the generation dots, and it is **alive and touchable**: a slow
 * current drifts through it constantly, and a finger pushes it apart, leaving a glowing wake
 * that springs shut behind you.
 *
 * That states the whole pitch of the surface without a word of copy. A blank page that moves
 * when you touch it is obviously somewhere you *do* something, not somewhere you read about
 * doing something. And because it is the same texture that will dance while a real generation
 * runs, the first thing anyone learns here is the app's own visual language.
 *
 * Position, size, brightness and colour are all continuous functions of one displacement
 * value, so no channel can pop or drift out of step with another.
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

    val restColor = MaterialTheme.colorScheme.primary
    val crestColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        0.45f,
    )

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
        val restRadius = 1.7.dp.toPx()
        val radius = TouchRadius.toPx()
        val push = TouchPush.toPx()
        val fadeBand = EdgeFade.toPx()

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
                    x += sin(homeY / 150f + t * 0.42f) * 5.5f + sin(homeY / 61f - t * 0.27f) * 2.2f
                    y += cos(homeX / 168f + t * 0.33f) * 5.5f + cos(homeX / 74f - t * 0.23f) * 2.2f
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
                val energy = (displaced / push).coerceIn(0f, 1f)
                // Smoothstep, so the wake has a soft shoulder rather than a hard bright ring.
                val eased = energy * energy * (3f - 2f * energy)

                val edge = (min(y, size.height - y) / fadeBand).coerceIn(0f, 1f)
                val alpha = (RestAlpha + (PeakAlpha - RestAlpha) * eased) * edge
                if (alpha <= 0.004f) continue

                drawCircle(
                    color = lerp(restColor, crestColor, eased),
                    radius = restRadius * (1f + 1.25f * eased),
                    center = Offset(x, y),
                    alpha = alpha,
                )
            }
        }
    }
}
