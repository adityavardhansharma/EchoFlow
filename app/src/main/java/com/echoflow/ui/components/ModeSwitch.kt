@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.abs

/** 48dp so every segment clears the accessibility touch minimum without an expansion hack. */
private val TrayHeight = 48.dp
private val TrayPadding = 4.dp
private val MaxTrayWidth = 220.dp

/** How far the thumb stretches along its direction of travel at the midpoint of a switch. */
private const val TravelStretch = 0.08f

/** The app's one "still working" rhythm — the dot field's cycle, reused so they read as one idea. */
private const val PulseCycleMs = 3000

/**
 * The app's top-level surface switch: Chat or Imagine.
 *
 * Built as a **floating tray with a raised thumb** rather than a flat connected pair, so it
 * wears the same material recipe as the composer at the other edge of the screen —
 * `surfaceContainerHigh`, 3dp tonal, 8dp shadow. The two pills bookend the content between
 * them, and the screen reads as one composition instead of chrome plus a control.
 *
 * That construction is also where the recessed look comes from: the tray casts a soft shadow
 * *down* onto scrolling content while the thumb casts a tight one *into* the tray, so the
 * unselected side reads as carved out. Deliberately no true inner shadow — those need custom
 * drawing, they vanish in dark themes where shadows barely register, and they are foreign to
 * Material's lighting model. Here the tonal contrast carries the depth on its own when
 * shadows cannot.
 *
 * [renderingModes] marks a surface that still has a clip rendering, so switching away from a
 * long job never hides it.
 */
@Composable
fun ModeSwitch(
    selected: AppMode,
    onSelect: (AppMode) -> Unit,
    renderingModes: Set<AppMode>,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val interactions = remember { AppMode.entries.associateWith { MutableInteractionSource() } }
    val anyPressed = interactions.values.map { it.collectIsPressedAsState().value }.any { it }

    // One value drives both the thumb's position and its mid-flight stretch.
    val travel by animateFloatAsState(
        targetValue = if (selected == AppMode.Chat) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "mode-thumb-travel",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (anyPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mode-thumb-press",
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier.height(TrayHeight),
    ) {
        BoxWithConstraints(Modifier.padding(TrayPadding)) {
            val segmentWidth = maxWidth / 2

            // Peaks at the midpoint of travel and is exactly 1 at either rest position, so the
            // thumb elongates as it crosses and settles round — the squash-and-stretch that
            // makes the switch feel like an object rather than a highlight moving.
            val stretch = 1f + TravelStretch * (1f - abs(travel - 0.5f) * 2f)

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = if (anyPressed) 0.dp else 2.dp,
                modifier = Modifier
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .offset(x = segmentWidth * travel)
                    .graphicsLayer {
                        scaleX = stretch * pressScale
                        scaleY = pressScale
                    },
            ) {}

            Row(Modifier.fillMaxHeight().selectableGroup()) {
                AppMode.entries.forEach { mode ->
                    ModeSegment(
                        mode = mode,
                        selected = mode == selected,
                        rendering = mode in renderingModes,
                        interactionSource = interactions.getValue(mode),
                        reducedMotion = reducedMotion,
                        onClick = {
                            if (mode != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(mode)
                            }
                        },
                        modifier = Modifier.width(segmentWidth).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSegment(
    mode: AppMode,
    selected: Boolean,
    rendering: Boolean,
    interactionSource: MutableInteractionSource,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Content colour crossfades as the thumb passes underneath; the label itself never moves,
    // so text stays rock-steady through the switch.
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "mode-segment-content",
    )
    val label = mode.label
    val description = buildString {
        append(label)
        append(" mode")
        if (rendering) append(", video rendering")
    }

    Row(
        modifier
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null, // the thumb IS the selection affordance; a ripple would fight it
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(mode.icon, contentDescription = null, Modifier.size(18.dp), tint = contentColor)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Inline rather than corner-anchored: a badge pinned to a corner that is actively
        // stretching would jitter through every switch.
        if (rendering) {
            Spacer(Modifier.width(Spacing.xs))
            RenderingPulse(color = contentColor, reducedMotion = reducedMotion)
        }
    }
}

/**
 * The app's single "still working" mark — a 6dp dot breathing on the dot field's own rhythm.
 * Used here and on drawer rows and nowhere else, so one glance always means one thing.
 */
@Composable
internal fun RenderingPulse(color: androidx.compose.ui.graphics.Color, reducedMotion: Boolean) {
    val alpha = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "rendering-pulse")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = PulseCycleMs / 2),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rendering-pulse-alpha",
        ).value
    }
    Box(
        Modifier
            .size(6.dp)
            .drawBehind {
                drawCircle(color = color, alpha = alpha, center = Offset(size.width / 2f, size.height / 2f))
            }
    )
}

private val AppMode.label: String
    get() = when (this) {
        AppMode.Chat -> "Chat"
        AppMode.Imagine -> "Imagine"
    }

/**
 * Imagine gets the wand rather than a sparkle: `AutoAwesome` is already spoken for by Artifacts
 * and Echo Labs, and a top-level surface deserves a glyph it does not share.
 */
private val AppMode.icon: ImageVector
    get() = when (this) {
        AppMode.Chat -> Icons.AutoMirrored.Filled.Chat
        AppMode.Imagine -> Icons.Default.AutoFixHigh
    }
