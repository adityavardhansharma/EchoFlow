package com.echoflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/** One segment. A short label and nothing else — detail belongs beside the section heading. */
data class ConnectedOption(val key: String, val label: String)

private val TrackHeight = 44.dp

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
 * **One line per segment, always.** An earlier version stacked a price under each label, and
 * three prices side by side turn a control into a comparison table: everything is shouting, the
 * segments get cramped, and picking a quality becomes an audit. Anything that varies with the
 * selection belongs beside the section heading as a single live readout, where there is room
 * for it to be legible and only one of it at a time.
 *
 * The Material 3 Expressive move is that selection is **shape**, not just colour. The chosen
 * segment springs to a full pill and takes a little more of the track, while its neighbours
 * tighten and give the space up. Nothing crossfades in place; the selection physically travels,
 * which is what makes a segmented control feel like an object rather than a repaint.
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
            // Deliberately the same pair the aspect-ratio options use. Two segmented controls
            // in one sheet wearing two different selection colours is the single loudest way
            // to make a considered layout look assembled from parts.
            val container by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                label = "connected-container-${option.key}",
            )
            val content = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            // Surface's own selectable overload, not a Modifier.selectable passed in from
            // outside: a modifier given to Surface is applied *before* Surface clips itself to
            // its shape, so its ripple ignores the corners and flashes a hard rectangle over a
            // rounded segment on every tap.
            Surface(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option.key)
                    }
                },
                color = container,
                shape = RoundedCornerShape(
                    topStart = startCorner, bottomStart = startCorner,
                    topEnd = endCorner, bottomEnd = endCorner,
                ),
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = option.label
                    },
            ) {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
