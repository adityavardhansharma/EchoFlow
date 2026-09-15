
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.ui.rememberActionHaptics
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.rememberMorph
import kotlinx.coroutines.launch

@Composable
internal fun SendButton(
    enabled: Boolean,
    isStreaming: Boolean,
    research: Boolean = false,
    transcribing: Boolean = false,
    onStop: () -> Unit = {},
    onCancelTranscribe: () -> Unit = {},
    onClick: () -> Unit,
) {
    // Send and stop are the only two actions that change what the app is *doing*, so they're the
    // two that earn a real haptic — a mirrored rising/falling pair, see [rememberActionHaptics].
    val haptics = rememberActionHaptics()
    if (transcribing) {
        // The "working" signal for dictation lives here, on the send slot: the same spinner
        // language as streaming-Stop, but it means "transcribing…". Tap to cancel and keep
        // whatever text was already in the box.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClickLabel = "Cancel transcription") { onCancelTranscribe() }
                .semantics { contentDescription = "Transcribing. Tap to cancel." },
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    } else if (isStreaming) {
        // Tappable Stop that stays in visual sync with the "Thinking…" row: the same expressive
        // LoadingIndicator keeps spinning so the reply still feels live, with a Stop glyph centered
        // on top so it reads clearly as "tap to stop". Tapping cancels the in-flight reply (cloud
        // stream or on-device generation).
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    haptics.stop()
                    onStop()
                },
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Icon(
                Icons.Default.Stop,
                "Stop generating",
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // The hero action gets the boldest shape — a "Sunny" that morphs to a rounder cookie on
        // press with an expressive (bouncy) spring. In research mode it starts the investigation.
        ShapedIconButton(
            onClick = {
                haptics.send()
                onClick()
            },
            enabled = enabled,
            size = 48.dp,
            restShape = MaterialShapes.Sunny,
            pressedShape = MaterialShapes.Cookie12Sided,
            container = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Icon(
                if (research) Icons.Default.Science else Icons.AutoMirrored.Filled.Send,
                if (research) "Start research" else "Send",
                Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A "fun shape" icon button: filled with a [MaterialShapes] polygon that **morphs to a second
 * shape on press** via the expressive bouncy spring (motion physics). A soft top-lit vertical
 * gradient gives the flat shape a 2.5D sense of volume.
 */
@Composable
internal fun ShapedIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp,
    restShape: RoundedPolygon,
    pressedShape: RoundedPolygon,
    container: Color,
    pulseOnClick: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "icon-shape-morph",
    )
    // One-shot "pop + morph" pulse fired on click (for buttons whose action doesn't change the
    // screen, like attaching a photo, so the feedback is actually seen).
    val scope = rememberCoroutineScope()
    val clickPulse = remember { Animatable(0f) }
    val progress = maxOf(pressProgress, clickPulse.value)
    val morph = rememberMorph(restShape, pressedShape)
    val shape = MorphPolygonShape(morph, progress)
    val popScale = 1f + 0.18f * clickPulse.value
    // 2.5D volume: lighter at the top, base colour at the bottom.
    val brush = Brush.verticalGradient(listOf(lerp(container, Color.White, 0.16f), container))
    Box(
        Modifier
            .size(size)
            .scale(popScale)
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = {
                    if (pulseOnClick) scope.launch {
                        clickPulse.snapTo(0f)
                        clickPulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        clickPulse.animateTo(0f, tween(durationMillis = 180))
                    }
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}
