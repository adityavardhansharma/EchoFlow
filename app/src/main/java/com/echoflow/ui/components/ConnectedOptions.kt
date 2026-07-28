package com.echoflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/** One segment: a short [label], and an optional second line of detail. */
data class ConnectedOption(
    val key: String,
    val label: String,
    val caption: String? = null,
)

private val TrackHeight = 52.dp

/** Full round at this height — the selected segment reads as a pill lifted out of the track. */
private val FullCorner = TrackHeight / 2

/** Just enough to keep two neighbours from fusing into one shape. */
private val TightCorner = 6.dp

/**
 * A connected group: one continuous track, split into segments, spanning the full width.
 *
 * The alternative — a wrapped row of separate chips — leaves ragged right edges and options at
 * three different widths because their labels happen to differ in length. That reads as an
 * accident. One track edge to edge reads as a designed control, and equal segments say what is
 * true: these are peers, and exactly one of them is on.
 *
 * The Material 3 Expressive move is that selection is **shape**, not just colour. The chosen
 * segment springs to a full pill and takes a little more of the track, while its neighbours
 * tighten and give the space up. Nothing crossfades in place; the selection physically travels,
 * which is what makes a two-state control feel like an object rather than a repaint.
 */
@Composable
fun ConnectedOptionRow(
    options: List<ConnectedOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()

    Row(
        modifier.fillMaxWidth().height(TrackHeight).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.key == selectedKey
            val spec = if (reducedMotion) {
                tween<Float>(0)
            } else {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            }
            // The chosen segment claims a little more room. Small on purpose: enough to feel
            // the selection settle, not enough to make the numbers dance as you compare them.
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 1.18f else 1f,
                animationSpec = spec,
                label = "connected-weight-${option.key}",
            )
            val startCorner by animateDpAsState(
                targetValue = if (isSelected || index == 0) FullCorner else TightCorner,
                animationSpec = if (reducedMotion) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
                label = "connected-start-${option.key}",
            )
            val endCorner by animateDpAsState(
                targetValue = if (isSelected || index == options.lastIndex) FullCorner else TightCorner,
                animationSpec = if (reducedMotion) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
                label = "connected-end-${option.key}",
            )
            val container by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "connected-container-${option.key}",
            )
            val content = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                color = container,
                shape = RoundedCornerShape(
                    topStart = startCorner, bottomStart = startCorner,
                    topEnd = endCorner, bottomEnd = endCorner,
                ),
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(option.key)
                            }
                        },
                    )
                    .semantics {
                        contentDescription = listOfNotNull(option.label, option.caption).joinToString(", ")
                    },
            ) {
                Column(
                    Modifier.padding(horizontal = Spacing.xs),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    option.caption?.let { caption ->
                        Text(
                            caption,
                            style = MaterialTheme.typography.labelSmall,
                            // Held back so the row scans as a set of choices first and a price
                            // list second — you pick a quality, then check what it costs.
                            color = content.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
