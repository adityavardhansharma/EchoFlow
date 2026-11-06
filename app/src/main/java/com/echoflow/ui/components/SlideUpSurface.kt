package com.echoflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A full-screen surface that slides up from the bottom and fades in when it first appears.
 * The overlays (artifact workspace, gallery, research report, document reader) open with it;
 * the parent removes it on close. With [reducedMotion] it cuts straight in.
 */
@Composable
fun SlideUpSurface(
    color: Color,
    label: String,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Flipped one frame after entering composition, so the first frame draws from below.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 320),
        label = label,
    )
    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = if (reducedMotion) 0f else (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = color,
        content = content,
    )
}
