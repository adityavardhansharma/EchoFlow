package com.echoflow.ui.screens.schedules

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An analog watch face for schedule times.
 *
 * It behaves like the real thing: the knurled bezel turns as time changes, the hands travel the
 * short way round on a spring, and dragging anywhere on the face winds the minute hand — carrying
 * the hour hand with it past twelve, exactly as setting a watch by its crown does. Each five-minute
 * step lands with a clock tick. [window] is the little aperture at three o'clock ("PM", "MON").
 *
 * Every colour is a theme role, so the dial reads as the same object in every palette.
 */
@Composable
fun WatchDial(
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    window: String? = null,
    sweepSeconds: Boolean = false,
    onTimeChange: ((hour: Int, minute: Int) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val numeralStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    val windowStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = colors.primary)

    val target = (hour * 60 + minute).toFloat()
    val total = remember { Animatable(target) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(target, dragging) {
        // Shortest way round the 24-hour dial, then spring — unless a finger is on the hands.
        var delta = (target - total.value) % 1440f
        if (delta > 720f) delta -= 1440f
        if (delta < -720f) delta += 1440f
        if (dragging || reducedMotion) total.snapTo(total.value + delta)
        else total.animateTo(total.value + delta, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val seconds by if (sweepSeconds && !reducedMotion) rememberInfiniteTransition(label = "seconds").animateFloat(
        0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    ) else remember { mutableStateOf(0f) }

    val change by rememberUpdatedState(onTimeChange)
    val current by rememberUpdatedState(hour * 60 + minute)
    val input = if (onTimeChange == null) Modifier else Modifier.pointerInput(Unit) {
        var wound = 0f
        var lastAngle = 0f
        var lastStep = 0
        detectDragGestures(
            onDragStart = { at ->
                dragging = true
                wound = current.toFloat()
                lastAngle = angleOf(at, Offset(size.width / 2f, size.height / 2f))
                lastStep = (wound / 5f).roundToInt()
            },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false },
        ) { pointer, _ ->
            val angle = angleOf(pointer.position, Offset(size.width / 2f, size.height / 2f))
            var turn = angle - lastAngle
            if (turn > 180f) turn -= 360f
            if (turn < -180f) turn += 360f
            lastAngle = angle
            wound += turn / 6f // 6° per minute
            val step = (wound / 5f).roundToInt()
            if (step != lastStep) {
                lastStep = step
                val minutes = Math.floorMod(step * 5, 1440)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                change?.invoke(minutes / 60, minutes % 60)
            }
        }
    }

    Canvas(
        modifier.aspectRatio(1f).then(input).semantics {
            contentDescription = "Watch showing %d:%02d".format(hour, minute)
        },
    ) {
        val r = size.minDimension / 2f
        val t = total.value
        drawBezel(r, bezelTurn = -t * 0.25f, colors.surfaceContainerHighest, colors.outlineVariant, colors.surfaceBright, colors.outline)
        val face = r * 0.84f
        drawCircle(Brush.radialGradient(listOf(colors.surfaceContainerLow, colors.surfaceContainer, colors.surfaceContainerHigh),
            center, face), face)
        // Sunburst: faint radial brushing, the finish of a dress watch.
        rotate(t * 0.05f) {
            for (i in 0 until 90) {
                val a = i * 4f
                drawLine(colors.onSurface.copy(alpha = 0.025f), polar(face * 0.15f, a), polar(face * 0.98f, a), 1f)
            }
        }
        // Minute track.
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val inner = face * if (major) 0.80f else 0.88f
            drawLine(
                if (major) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.55f),
                polar(inner, i * 6f), polar(face * 0.94f, i * 6f),
                strokeWidth = if (major) r * 0.022f else r * 0.008f, cap = StrokeCap.Round,
            )
        }
        // Numerals, with the date window taking three o'clock when present.
        listOf(12 to 0f, 3 to 90f, 6 to 180f, 9 to 270f).forEach { (n, a) ->
            if (n == 3 && window != null) return@forEach
            val layout = measurer.measure(n.toString(), numeralStyle.copy(fontSize = (r * 0.13f).toSp()))
            val p = polar(face * 0.64f, a)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        if (window != null) {
            val layout = measurer.measure(window, windowStyle.copy(fontSize = (r * 0.075f).toSp()))
            val w = layout.size.width + r * 0.08f
            val h = layout.size.height + r * 0.04f
            val c = polar(face * 0.62f, 90f)
            drawRoundRect(colors.surfaceContainerLowest, Offset(c.x - w / 2f, c.y - h / 2f), Size(w, h), CornerRadius(h * 0.3f))
            drawRoundRect(colors.outlineVariant, Offset(c.x - w / 2f, c.y - h / 2f), Size(w, h), CornerRadius(h * 0.3f), style = Stroke(1.2f))
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
        }
        val hourAngle = (t / 60f % 12f) * 30f
        val minuteAngle = (t % 60f) * 6f
        val shadow = colors.scrim.copy(alpha = 0.18f)
        val lift = Offset(r * 0.012f, r * 0.03f)
        hand(hourAngle, face * 0.5f, r * 0.075f, shadow, lift)
        hand(minuteAngle, face * 0.78f, r * 0.05f, shadow, lift)
        hand(hourAngle, face * 0.5f, r * 0.075f, colors.onSurface)
        hand(hourAngle, face * 0.44f, r * 0.03f, colors.primaryContainer, from = face * 0.14f)
        hand(minuteAngle, face * 0.78f, r * 0.05f, colors.primary)
        if (sweepSeconds) {
            hand(seconds + 180f, face * 0.16f, r * 0.018f, colors.tertiary)
            hand(seconds, face * 0.86f, r * 0.012f, colors.tertiary)
        }
        drawCircle(colors.onSurface, r * 0.065f)
        drawCircle(colors.tertiary, r * 0.03f)
    }
}

/** The knurled bezel: fine grooves that turn with the time, lit from above. */
private fun DrawScope.drawBezel(r: Float, bezelTurn: Float, body: Color, groove: Color, highlight: Color, rim: Color) {
    drawCircle(body, r)
    rotate(bezelTurn) {
        for (i in 0 until 120) {
            val a = i * 3f
            // Grooves facing the light read brighter: shade by angle for a machined, 3D edge.
            val light = ((cos(Math.toRadians(a.toDouble() - 30.0)) + 1.0) / 2.0).toFloat()
            drawLine(if (i % 2 == 0) groove.copy(alpha = 0.5f + 0.5f * (1f - light)) else highlight.copy(alpha = 0.35f + 0.65f * light),
                polar(r * 0.875f, a), polar(r * 0.985f, a), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
        }
    }
    drawCircle(rim.copy(alpha = 0.35f), r * 0.845f, style = Stroke(r * 0.012f))
    drawCircle(rim.copy(alpha = 0.25f), r * 0.995f, style = Stroke(r * 0.01f))
}

private fun DrawScope.hand(degrees: Float, length: Float, width: Float, color: Color, offset: Offset = Offset.Zero, from: Float = -length * 0.16f) {
    drawLine(color, polar(from, degrees) + offset, polar(length, degrees) + offset, strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.polar(radius: Float, degrees: Float): Offset {
    val a = (degrees - 90f) * PI.toFloat() / 180f
    return Offset(center.x + radius * cos(a), center.y + radius * sin(a))
}

/** Clockwise degrees from twelve o'clock. */
private fun angleOf(p: Offset, c: Offset): Float {
    val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat() + 90f
    return (deg + 360f) % 360f
}
