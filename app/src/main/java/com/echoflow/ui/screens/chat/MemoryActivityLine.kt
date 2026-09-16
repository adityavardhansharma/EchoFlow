package com.echoflow.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.sin

/** A quiet constellation, deliberately not a tool card or search pill. */
@Composable
internal fun MemoryActivityLine(label: String, active: Boolean) {
    val reduced = rememberReducedMotion()
    val phase = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "memory constellation")
        transition.animateFloat(0f, 6.28318f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "memory pulse").value
    } else 0f
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val points = listOf(Offset(size.width * .2f, size.height * .6f), Offset(size.width * .5f, size.height * .25f), Offset(size.width * .8f, size.height * .6f))
            drawLine(color.copy(alpha = .25f), points[0], points[1], 1.dp.toPx())
            drawLine(color.copy(alpha = .25f), points[1], points[2], 1.dp.toPx())
            points.forEachIndexed { index, point ->
                val pulse = if (active && !reduced) (sin(phase - index) + 1f) / 2f else 1f
                drawCircle(color.copy(alpha = .4f + .6f * pulse), (2f + pulse * .8f).dp.toPx(), point)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
