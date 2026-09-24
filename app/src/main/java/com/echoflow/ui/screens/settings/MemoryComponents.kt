@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/**
 * The building blocks for Memory settings and My Memories. One vocabulary on both screens:
 * [MaterialShapes] marks for identity, tonal banners for feedback, grouped connected rows for
 * settings, and a wavy progress line for work in flight. Colours are colour-scheme roles only, so
 * every piece follows each palette, dynamic colour, and light and dark.
 */

// ── Identity ─────────────────────────────────────────────────────────────────────────────

/** A glyph set in an expressive [RoundedPolygon] — the shaped badge every memory surface leads with. */
@Composable
internal fun MemoryMark(
    icon: ImageVector,
    polygon: RoundedPolygon,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).clip(RoundedPolygonShape(polygon)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.46f), tint = onContainer)
    }
}

/** A section heading: title, optional supporting line, optional trailing action on the same row. */
@Composable
internal fun MemorySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.xs, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

// ── Feedback ─────────────────────────────────────────────────────────────────────────────

internal enum class MemoryTone { Info, Success, Error }

/**
 * A tonal feedback banner. Errors use the error container, confirmations the tertiary container,
 * and info the neutral high container — each with its own icon so tone never rests on colour
 * alone. Announced politely to accessibility services.
 */
@Composable
internal fun MemoryBanner(
    message: String,
    tone: MemoryTone,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (container, content, icon) = when (tone) {
        MemoryTone.Error -> Triple(cs.errorContainer, cs.onErrorContainer, Icons.Default.ErrorOutline)
        MemoryTone.Success -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, Icons.Default.CheckCircle)
        MemoryTone.Info -> Triple(cs.surfaceContainerHigh, cs.onSurfaceVariant, Icons.Default.Info)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = if (onDismiss != null) Spacing.xs else Spacing.base, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.m))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A wavy progress line that slides in while an account operation is in flight. */
@Composable
internal fun MemoryBusyLine(busy: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = busy,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.s))
    }
}

// ── Settings rows ────────────────────────────────────────────────────────────────────────

/**
 * One switch in a grouped, connected list: a tonal icon badge that brightens when on, a title,
 * the consequence spelt out, and an expressive switch with a check in its thumb. The whole row is
 * the toggle target.
 */
@Composable
internal fun MemorySwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = groupedItemShape(index, count),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = 76.dp)
                .padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (checked) cs.primaryContainer else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (checked) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

/**
 * A question that opens in place — for the "good to know" notes, which read better as answers
 * you reach for than as a wall of paragraphs. Expansion survives rotation.
 */
@Composable
internal fun MemoryFaqRow(question: String, answer: String, index: Int, count: Int) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "faqChevron",
    )
    Surface(
        onClick = { open = !open },
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.s))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        if (open) "Collapse" else "Expand",
                        Modifier.size(18.dp).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(
                    answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}

/** A single figure with its label — used for the learning pipeline's counts. */
@Composable
internal fun MemoryStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    attention: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val active = value > 0
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = when {
            attention && active -> cs.errorContainer
            active -> cs.secondaryContainer
            else -> cs.surfaceContainerHigh
        },
        contentColor = when {
            attention && active -> cs.onErrorContainer
            active -> cs.onSecondaryContainer
            else -> cs.onSurfaceVariant
        },
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.s, vertical = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/**
 * An empty or placeholder state: a shaped mark, a title and a line of guidance, with an optional
 * action slot. Centred, with generous room, so an empty list feels intentional.
 */
@Composable
internal fun MemoryEmptyState(
    icon: ImageVector,
    polygon: RoundedPolygon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        MemoryMark(
            icon, polygon,
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 72.dp,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.xs))
            action()
        }
    }
}
