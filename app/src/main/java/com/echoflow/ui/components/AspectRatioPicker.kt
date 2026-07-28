@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.VideoRequestPolicy
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/** The box every ratio glyph is drawn to fit inside, so the row reads as one set. */
private val GlyphBounds = 26.dp

/**
 * Framing, chosen from a small anchored popover rather than a full sheet — it is a two-second
 * decision and a modal sheet would make it feel like a bigger one.
 *
 * Each option is drawn at its **true proportion**, and the outline springs to its new shape as
 * the selection moves. That is the composer-scale echo of the card's stretch-to-aspect
 * animation: the same idea in miniature, so the control and the result speak the same language.
 *
 * [supported] dims what the current model cannot do rather than hiding it, so the popover
 * never rearranges itself between openings. A null or empty set means the model takes its
 * framing from elsewhere and every option stays live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AspectRatioPopover(
    expanded: Boolean,
    selected: String,
    supported: List<String>?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    unsupportedNote: String? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).widthIn(max = 260.dp)) {
            SectionLabel("Shape")
            Spacer(Modifier.height(Spacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                VideoRequestPolicy.ASPECT_RATIOS.forEach { ratio ->
                    val enabled = supported.isNullOrEmpty() || ratio in supported
                    AspectRatioOption(
                        ratio = ratio,
                        selected = ratio == selected,
                        enabled = enabled,
                        onClick = { onSelect(ratio); onDismiss() },
                    )
                }
            }
            if (unsupportedNote != null) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    unsupportedNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AspectRatioOption(
    ratio: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    // The glyph animates its own proportions, so the outline visibly re-shapes rather than
    // one box switching off and another switching on.
    val target = VideoRequestPolicy.aspectRatioValue(ratio)
    val animatedAspect by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ratio-glyph-$ratio",
    )
    val outline = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics {
            contentDescription = if (enabled) "$ratio aspect ratio" else "$ratio, not supported by this model"
        },
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(GlyphBounds), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(GlyphBounds).drawBehind {
                        // Fit the true proportion inside a fixed box so portrait and landscape
                        // options share a baseline instead of jumping around the row.
                        val w: Float
                        val h: Float
                        if (animatedAspect >= 1f) {
                            w = size.width
                            h = size.width / animatedAspect
                        } else {
                            h = size.height
                            w = size.height * animatedAspect
                        }
                        val stroke = 1.5.dp.toPx()
                        drawRoundRect(
                            color = outline,
                            topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                            style = Stroke(width = stroke),
                        )
                    }
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                ratio,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}
