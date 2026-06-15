@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.FusionAnalysis
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/** Short, human label for an OpenRouter model id, e.g. "anthropic/claude-opus-4.8" → "claude-opus-4.8". */
private fun shortModel(id: String): String = id.substringAfterLast('/').ifBlank { id }

// ── Echo Adviser ────────────────────────────────────────────────────────────────────────

/**
 * One Echo Adviser consultation in the reply timeline: a tertiary-tinted side-channel card.
 * While [active] it pulses "Consulting <name>"; once resolved it collapses to a tap-to-expand
 * card revealing the question the model asked and the advisor's advice. This is the
 * transparency win — the user watches the answering model escalate to a stronger mind.
 */
@Composable
fun AdvisorCard(
    advisorName: String,
    advisorModel: String,
    prompt: String,
    advice: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: active
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "advisor-chevron")
    val hasBody = !prompt.isBlank() || !advice.isNullOrBlank()

    Surface(
        onClick = { if (hasBody) userToggled = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie7Sided))
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Psychology, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                Spacer(Modifier.width(Spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active) "Consulting $advisorName" else "$advisorName advised",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (advisorModel.isNotBlank()) {
                        Text(
                            shortModel(advisorModel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (active) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                } else if (hasBody) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                        Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded && hasBody, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.s)) {
                    if (prompt.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(Spacing.m)) {
                                Icon(Icons.Default.CompareArrows, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(Spacing.s))
                                Column {
                                    Text("Asked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    if (!advice.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.s))
                        RichMarkdown(advice, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ── Echo Fusion ─────────────────────────────────────────────────────────────────────────

/**
 * One Echo Fusion deliberation in the reply timeline. While [active] it shows the (real) panel
 * roster shimmering under a staged "deliberating → synthesizing" header — we can't observe
 * per-model completion, so the roster is honest but the timing is indeterminate. Once resolved
 * it morphs into the structured analysis: consensus, contradictions (opposing stances),
 * unique insights, blind spots, and an accordion of each model's full response.
 */
@Composable
fun FusionCard(
    panelName: String,
    models: List<String>,
    analysis: FusionAnalysis?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val roster = analysis?.models?.takeIf { it.isNotEmpty() } ?: models

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedPolygonShape(MaterialShapes.Clover4Leaf))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.AccountTree, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (panelName.isNotBlank()) "Fusion · $panelName" else "Echo Fusion",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (active) "Deliberating across ${roster.size} models" else "${roster.size} models · judged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active) LoadingIndicator(color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(Spacing.m))
            PanelRoster(roster, shimmer = active)

            if (active) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Panel deliberating → judge synthesizing…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (analysis != null) {
                FusionAnalysisBody(analysis)
            }
        }
    }
}

/** The panel members as a horizontal strip of model chips; [shimmer] pulses them while deliberating. */
@Composable
private fun PanelRoster(models: List<String>, shimmer: Boolean) {
    val transition = rememberInfiniteTransition(label = "roster")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "roster-pulse",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        models.forEach { model ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.alpha(if (shimmer) pulse else 1f),
            ) {
                Row(
                    Modifier.padding(start = Spacing.s, end = Spacing.m, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Bolt, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(shortModel(model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FusionAnalysisBody(analysis: FusionAnalysis) {
    Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (analysis.consensus.isNotEmpty()) {
            FusionSection("Consensus", Icons.Default.CheckCircle, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                BulletList(analysis.consensus, MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (analysis.contradictions.isNotEmpty()) {
            FusionSection("Disagreements", Icons.Default.CompareArrows, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    analysis.contradictions.forEach { c ->
                        Column {
                            Text(c.topic, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onErrorContainer)
                            c.stances.forEach { stance ->
                                Row(Modifier.padding(top = 2.dp)) {
                                    Text("⟂ ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    Text(stance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (analysis.uniqueInsights.isNotEmpty()) {
            FusionSection("Unique insights", Icons.Default.Lightbulb, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    analysis.uniqueInsights.forEach { i ->
                        Column {
                            if (i.model.isNotBlank()) {
                                Text(shortModel(i.model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(i.insight, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }
        if (analysis.blindSpots.isNotEmpty()) {
            FusionSection("Blind spots", Icons.Default.VisibilityOff, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface) {
                BulletList(analysis.blindSpots, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (analysis.responses.isNotEmpty()) {
            ModelResponsesDisclosure(analysis)
        }
        if (analysis.failedModels.isNotEmpty()) {
            Text(
                "Did not respond: ${analysis.failedModels.joinToString(", ") { shortModel(it) }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        analysis.judgeModel?.takeIf { it.isNotBlank() }?.let {
            Text("Judged by ${shortModel(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FusionSection(
    title: String,
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    content: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = onContainer)
                Spacer(Modifier.width(Spacing.s))
                Text(title, style = MaterialTheme.typography.labelLarge, color = onContainer)
            }
            Spacer(Modifier.height(Spacing.s))
            content()
        }
    }
}

@Composable
private fun BulletList(items: List<String>, color: Color) {
    Column {
        items.forEach { item ->
            Row(Modifier.padding(vertical = 1.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodySmall, color = color)
                Text(item, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

/** Collapsed accordion holding each panel model's full answer, rendered as markdown. */
@Composable
private fun ModelResponsesDisclosure(analysis: FusionAnalysis) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "fusion-resp-chevron")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "Each model's answer · ${analysis.responses.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    analysis.responses.forEach { resp ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                Icon(Icons.Default.Bolt, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(4.dp))
                                Text(shortModel(resp.model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            RichMarkdown(resp.content, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
