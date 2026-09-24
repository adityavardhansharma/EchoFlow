@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.schedules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.RoundedPolygonShape
import kotlin.math.cos
import kotlin.math.sin

/**
 * EchoFlow's schedule mark: a small watch face. Wherever something belongs to a schedule — the
 * drawer, a conversation's title bar, a home row — this mark says so, and when a time is known its
 * hands show it, so the icon itself carries information.
 */
@Composable
fun ScheduleMark(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    hour: Int = 8,
    minute: Int = 10,
    container: Color? = null,
) {
    if (container != null) {
        Box(
            modifier.size(size).clip(RoundedPolygonShape(MaterialShapes.Cookie9Sided)).background(container),
            contentAlignment = Alignment.Center,
        ) { Face(Modifier.fillMaxSize().padding(size * 0.2f), tint, hour, minute) }
    } else Face(modifier.size(size), tint, hour, minute)
}

@Composable
private fun Face(modifier: Modifier, tint: Color, hour: Int, minute: Int) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val stroke = r * 0.17f
        drawCircle(tint, radius = r - stroke / 2f, style = Stroke(stroke))
        fun hand(degrees: Float, length: Float, width: Float) {
            val a = Math.toRadians(degrees.toDouble() - 90.0)
            drawLine(tint, center, Offset(center.x + (cos(a) * length).toFloat(), center.y + (sin(a) * length).toFloat()),
                strokeWidth = width, cap = StrokeCap.Round)
        }
        hand((hour % 12) * 30f + minute * 0.5f, r * 0.42f, stroke * 1.05f)
        hand(minute * 6f, r * 0.62f, stroke * 0.8f)
        drawCircle(tint, radius = stroke * 0.75f)
    }
}
