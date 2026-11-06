package com.echoflow.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/**
 * Unmasks the content top to bottom as [fraction] goes from 0 to 1, with a glowing scanline at
 * the edge. Shared by the generated image and video cards.
 */
internal fun Modifier.scanlineReveal(fraction: () -> Float, color: Color): Modifier = drawWithContent {
    val f = fraction()
    clipRect(bottom = size.height * f) { this@drawWithContent.drawContent() }
    if (f > 0f && f < 1f) {
        val y = size.height * f
        // Density-aware: raw px would be a near-invisible hairline.
        val lineHeight = 2.5.dp.toPx()
        val glowHeight = 40.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.4f)),
                startY = (y - glowHeight).coerceAtLeast(0f),
                endY = y,
            ),
            topLeft = Offset(0f, (y - glowHeight).coerceAtLeast(0f)),
            size = Size(size.width, glowHeight.coerceAtMost(y)),
        )
        drawRect(
            color = color,
            topLeft = Offset(0f, y - lineHeight / 2f),
            size = Size(size.width, lineHeight),
        )
    }
}
