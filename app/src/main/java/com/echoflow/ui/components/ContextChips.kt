@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.echoflow.data.ImagineMedia
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/**
 * The row directly above the composer: what this next message will be sent *with*.
 *
 * Scrolls horizontally rather than wrapping. Wrapping was pushing the composer up a line
 * every time a couple of capabilities were on, which moves the send button out from under the
 * thumb — the row growing is never worth the input moving.
 */
@Composable
fun ContextChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val edgeFade = remember(scrollState) { { scrollState.canScrollForward to scrollState.canScrollBackward } }
    Row(
        modifier
            // Offscreen FIRST: DstIn multiplies against the alpha of the layer beneath it, so
            // the content has to already be in its own layer. With the order reversed the
            // gradient has nothing to blend into and paints as a solid black bar.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val (forward, backward) = edgeFade()
                val fade = 20.dp.toPx()
                // Fade only the edge that has content beyond it, so the row never looks
                // clipped when it happens to fit.
                if (backward) drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                    blendMode = BlendMode.DstIn,
                )
                if (forward) drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent),
                        size.width - fade,
                        size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The model selector, at the leading edge of the chip row.
 *
 * It sits by the composer rather than in the top bar deliberately: in a multi-provider app the
 * model is something you change mid-thought, and the bottom edge is where your thumb already
 * is. It wears the provider's own mark so which brain is answering is readable without
 * parsing the name.
 */
@Composable
fun ModelPill(
    modelId: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingMark: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "model-pill-press",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = "Model: $label. Tap to change." },
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = Spacing.s, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingMark != null) leadingMark() else ProviderMark(modelId = modelId, size = 22.dp)
            Spacer(Modifier.width(Spacing.s))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.widthIn(max = 168.dp),
            )
            Icon(
                Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Image or video, chosen inside the chip row.
 *
 * A **flat** connected pair, unlike the mode switch's floating tray. That difference is a
 * rule, not an inconsistency: elevation belongs to chrome hovering over scrolling content,
 * while controls resting inside a row stay flat — the same distinction that already separates
 * the floating composer from an in-page text field.
 */
@Composable
fun MediaToggle(
    selected: ImagineMedia,
    onSelect: (ImagineMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ImagineMedia.entries.forEach { media ->
            val isSelected = media == selected
            val label = if (media == ImagineMedia.Video) "Video" else "Image"
            // Outer corners round, inner corners tight, so the pair reads as one control.
            val shape = when (media) {
                ImagineMedia.Image -> RoundedCornerShape(
                    topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 20, bottomEndPercent = 20,
                )
                ImagineMedia.Video -> RoundedCornerShape(
                    topStartPercent = 20, bottomStartPercent = 20, topEndPercent = 50, bottomEndPercent = 50,
                )
            }
            Surface(
                shape = shape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(media)
                            }
                        },
                    )
                    .semantics { contentDescription = "$label mode" },
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.m, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        if (media == ImagineMedia.Video) Icons.Default.Movie else Icons.Default.Image,
                        null, Modifier.size(16.dp), tint = tint,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** A compact chip that opens a picker — ratio, resolution, or any other framing choice. */
@Composable
fun SettingChip(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    description: String = label,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "setting-chip-press",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.m, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
