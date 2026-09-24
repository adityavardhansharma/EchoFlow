package com.echoflow.ui.screens.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EchoFlow's schedule mark: the plain Material clock. The drawer, a conversation's title bar, the
 * docked card, the home list and notifications all use this one glyph, so a schedule looks the
 * same wherever it appears. With a [container] it sits in a circle, glyph at 55% of the size.
 */
@Composable
fun ScheduleMark(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    container: Color? = null,
) {
    if (container != null) {
        Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(size * 0.55f), tint = tint)
        }
    } else Icon(Icons.Outlined.Schedule, null, modifier.size(size), tint = tint)
}
