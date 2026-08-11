@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.ResearchRef
import com.echoflow.data.ResearchRun
import com.echoflow.data.ResearchStep
import com.echoflow.data.SearchSource
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * The Deep Research surface: live work as expandable **batch capsules** (≤5 engine steps each),
 * resolving into a single result card when the run finishes.
 *
 * Engines can emit dozens of steps. Rendering one pill per step floods a phone; instead steps
 * fill a capsule as they arrive — 1…5 in batch 1, 6 starts batch 2 — so 73 steps become 15
 * capsules. Each capsule expands like the Task Rows / Thinking references: a header with a mark
 * and a short thinking line, nested detail rows on open.
 *
 * Everything renders from the persisted [ResearchRun] / [ResearchRef], so a backgrounded run
 * replays its whole history on reopen. Pre-redesign research is drawn by
 * `ui/legacy/LegacyResearchComponents.kt`.
 */

private const val ROW_HEIGHT_DP = 48
private const val MARK_SIZE_DP = 24

/** How many engine steps share one capsule before a new one opens. */
internal const val RESEARCH_BATCH_SIZE = 5

private fun hostOfUrl(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

private fun faviconOf(url: String): String =
    "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""}&sz=64"

private fun openInBrowser(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

// Every disclosure in this feature — batch capsules, the workspace slide-up — reveals through
// the same pair, collapsing to an instant cut under reduced motion so the whole surface degrades
// to stillness rather than only its entry animation doing so.
private fun researchRevealEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(300)) + fadeIn(tween(200))

private fun researchRevealExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(240)) + fadeOut(tween(120))

internal fun formatResearchDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

/**
 * One on-screen capsule: up to [RESEARCH_BATCH_SIZE] engine steps, filled progressively as the
 * run grows (never re-chunked after the fact).
 */
internal data class ResearchBatch(
    val index: Int,
    val steps: List<ResearchStep>,
) {
    val hasActive: Boolean get() = steps.any { it.state == ResearchStep.STATE_ACTIVE }
    val hasFailed: Boolean get() = steps.any { it.state == ResearchStep.STATE_FAILED }
    val allTerminal: Boolean get() = steps.isNotEmpty() && steps.all { it.isTerminal }
    val allPending: Boolean get() = steps.isNotEmpty() && steps.all { it.state == ResearchStep.STATE_PENDING }

    val state: String
        get() = when {
            hasFailed && steps.none { it.state == ResearchStep.STATE_ACTIVE } -> ResearchStep.STATE_FAILED
            hasActive -> ResearchStep.STATE_ACTIVE
            allTerminal -> ResearchStep.STATE_DONE
            allPending -> ResearchStep.STATE_PENDING
            else -> ResearchStep.STATE_ACTIVE
        }

    /** Header line: what the agent is doing now, or a settled "Thought for …" once the batch ends. */
    fun headerText(phaseFallback: String?): String {
        steps.firstOrNull { it.state == ResearchStep.STATE_ACTIVE }?.label?.let { return it }
        if (allTerminal) {
            val start = steps.mapNotNull { it.startedAt.takeIf { t -> t > 0L } }.minOrNull()
            val end = steps.mapNotNull { it.endedAt }.maxOrNull()
            if (start != null && end != null && end >= start) {
                return "Thought for ${formatResearchDuration(end - start)}"
            }
            return if (hasFailed) "Couldn't finish" else "Thought through ${steps.size} step${if (steps.size == 1) "" else "s"}"
        }
        steps.lastOrNull { it.state != ResearchStep.STATE_PENDING }?.label?.let { return it }
        return phaseFallback?.takeIf { it.isNotBlank() } ?: "Thinking"
    }
}

/** Progressive groups of [RESEARCH_BATCH_SIZE] — last group may be shorter. */
internal fun chunkResearchSteps(steps: List<ResearchStep>): List<ResearchBatch> =
    steps.chunked(RESEARCH_BATCH_SIZE).mapIndexed { index, group ->
        ResearchBatch(index = index + 1, steps = group)
    }

// ── Live timeline ────────────────────────────────────────────────────────────────────

/**
 * The live run: one expandable capsule per batch of up to five steps.
 *
 * Stop lives on the composer (send → stop), not as a Cancel chip on this card — same grammar as
 * a streaming chat reply.
 */
@Composable
fun ResearchTimeline(
    run: ResearchRun,
    steps: List<ResearchStep>,
    sources: List<SearchSource>,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val sourceByUrl = remember(sources) { sources.associateBy { it.url } }
    val batches = remember(steps) { chunkResearchSteps(steps) }
    // Only the batch currently working starts open; earlier ones stay collapsed until the user
    // reopens them. Manual toggles win over the auto-open default.
    val activeBatchIndex = batches.indexOfFirst { it.hasActive }.let { if (it < 0) batches.lastIndex else it }
    var manualOpen by remember(run.id) { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Quiet engine line only — no Cancel. The composer Stop cancels the run.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (run.engineKind == "data-agent") "Data Agent" else "Deep Research",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                " · ${run.engineLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            run.costInfo?.takeIf { it.isNotBlank() }?.let { cost ->
                Text(
                    cost,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        // No steps yet — still look alive (queued / first network hop).
        if (batches.isEmpty()) {
            ResearchCapsule(
                mark = { StepMark(ResearchStep.STATE_ACTIVE, 0) },
                label = run.phase?.takeIf { it.isNotBlank() } ?: "Thinking",
                meta = null,
                expandable = false,
            )
        }

        batches.forEachIndexed { index, batch ->
            val defaultOpen = index == activeBatchIndex && !batch.allTerminal
            val open = manualOpen[batch.index] ?: defaultOpen
            BatchCapsule(
                batch = batch,
                phaseFallback = run.phase,
                sourceByUrl = sourceByUrl,
                expanded = open,
                onToggle = {
                    manualOpen = manualOpen + (batch.index to !open)
                },
                staggerIndex = index,
                reducedMotion = reducedMotion,
            )
        }
    }
}

@Composable
private fun BatchCapsule(
    batch: ResearchBatch,
    phaseFallback: String?,
    sourceByUrl: Map<String, SearchSource>,
    expanded: Boolean,
    onToggle: () -> Unit,
    staggerIndex: Int,
    reducedMotion: Boolean,
) {
    var shown by remember(batch.index) { mutableStateOf(reducedMotion) }
    LaunchedEffect(batch.index) {
        if (!shown) {
            delay(staggerIndex * 60L)
            shown = true
        }
    }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "batch-enter",
    )

    ResearchCapsule(
        mark = { StepMark(batch.state, batch.index) },
        label = batch.headerText(phaseFallback),
        meta = null,
        expanded = expanded,
        onToggle = onToggle,
        expandable = true,
        dimmed = batch.allPending,
        modifier = Modifier.graphicsLayer {
            alpha = enter
            translationY = (1f - enter) * 12.dp.toPx()
        },
    ) {
        // Nested detail — same rail + row grammar as the Thinking "Steps" reference.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            batch.steps.forEach { step ->
                StepDetailRow(step = step, sourceByUrl = sourceByUrl)
            }
        }
    }
}

@Composable
private fun StepDetailRow(
    step: ResearchStep,
    sourceByUrl: Map<String, SearchSource>,
) {
    val stepSources = remember(step.sourceUrls, sourceByUrl) {
        step.sourceUrls.mapNotNull { sourceByUrl[it] }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DetailMark(step.state)
            Spacer(Modifier.width(Spacing.s))
            Text(
                step.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (step.state == ResearchStep.STATE_PENDING) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            step.detail?.let { meta ->
                Spacer(Modifier.width(Spacing.s))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        stepSources.forEach { source ->
            SourceDetailRow(source, indent = true)
        }
    }
}

/** Compact check / spinner / digit for rows nested inside a batch. */
@Composable
private fun DetailMark(state: String) {
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        when (state) {
            ResearchStep.STATE_ACTIVE -> LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            ResearchStep.STATE_DONE -> Icon(
                Icons.Default.Check,
                null,
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResearchStep.STATE_FAILED -> Icon(
                Icons.Default.Close,
                null,
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            else -> Box(
                Modifier
                    .size(10.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

/**
 * The shared capsule. One geometry for every row in the feature: a 24dp mark, a label, an
 * optional mono meta column, and a chevron that reveals indented detail against a hairline rail.
 * The container relaxes from a 22dp capsule to a 14dp card as it opens.
 */
@Composable
private fun ResearchCapsule(
    mark: @Composable () -> Unit,
    label: String,
    meta: String?,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    expandable: Boolean = false,
    dimmed: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val radius by animateDpAsState(
        targetValue = if (expanded) 14.dp else 22.dp,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "capsule-radius",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "capsule-chevron",
    )

    Surface(
        shape = RoundedCornerShape(radius),
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                    .heightIn(min = ROW_HEIGHT_DP.dp)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mark()
                Spacer(Modifier.width(Spacing.m))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (meta != null) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (expandable) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        if (expanded) "Collapse" else "Expand",
                        Modifier.padding(start = Spacing.xs).size(18.dp).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = researchRevealEnter(reducedMotion),
                exit = researchRevealExit(reducedMotion),
            ) {
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                ) {
                    // The rail sits under the mark, echoing the reference's indent grammar.
                    Box(
                        Modifier.width(MARK_SIZE_DP.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Spacer(Modifier.width(Spacing.m))
                    Box(Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}

/**
 * The state slot. Its position is constant for the whole run — a pending digit becomes the
 * morphing loader, then a check — which is what makes the timeline and the result card read as
 * one object resolving rather than two components in sequence.
 */
@Composable
private fun StepMark(state: String, ordinal: Int) {
    Box(Modifier.size(MARK_SIZE_DP.dp), contentAlignment = Alignment.Center) {
        when (state) {
            ResearchStep.STATE_ACTIVE -> LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MARK_SIZE_DP.dp),
            )
            ResearchStep.STATE_DONE -> FilledMark(
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                icon = Icons.Default.Check,
                description = "Done",
            )
            ResearchStep.STATE_FAILED -> FilledMark(
                container = MaterialTheme.colorScheme.error,
                content = MaterialTheme.colorScheme.onError,
                icon = Icons.Default.Close,
                description = "Failed",
            )
            else -> Box(
                Modifier
                    .size(MARK_SIZE_DP.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ordinal.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilledMark(
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp = MARK_SIZE_DP.dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(size * 0.6f), tint = content) }
}

@Composable
private fun SourceDetailRow(source: SearchSource, indent: Boolean = false) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (indent) Modifier.padding(start = 14.dp + Spacing.s) else Modifier)
            .clickable { openInBrowser(context, source.url) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(faviconOf(source.url), null, Modifier.size(16.dp).clip(CircleShape))
        Spacer(Modifier.width(Spacing.s))
        Text(
            source.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            hostOfUrl(source.url),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Result card ──────────────────────────────────────────────────────────────────────

/**
 * The finished run — one tappable card: open the report (or retry on failure).
 *
 * Steps already lived in the live batch timeline (and in the workspace Steps tab), so this card
 * does not re-expand them. The whole surface is the action — same visual weight as before, but
 * no chevron / provenance disclosure competing with Open report.
 */
@Composable
fun ResearchResultCard(
    research: ResearchRef,
    steps: List<ResearchStep>,
    sources: List<SearchSource>,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = research.error != null
    val container = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onAccent = if (failed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    val meta = remember(research) {
        buildList {
            if (research.sourceCount > 0) add("${research.sourceCount} sources")
            if (research.durationMs > 0) add(formatResearchDuration(research.durationMs))
            research.costInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }
    val actionLabel = when {
        failed -> "Try again"
        research.structured -> "Open data"
        else -> "Open report"
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = if (failed) onRetry else onOpen),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT_DP.dp)
                    .padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledMark(
                    container = accent,
                    content = onAccent,
                    icon = if (failed) Icons.Default.Close else Icons.Default.Check,
                    description = if (failed) "Failed" else "Completed",
                )
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        research.topic,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = research.error ?: meta.ifBlank { research.engineLabel }
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = onContainer.copy(alpha = 0.72f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (failed) {
                    Icon(Icons.Default.Refresh, actionLabel, Modifier.size(18.dp), tint = onContainer)
                } else {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        actionLabel,
                        Modifier.size(18.dp).rotate(-90f),
                        tint = onContainer,
                    )
                }
            }

            if (sources.isNotEmpty()) {
                Row(
                    Modifier.padding(start = Spacing.m + MARK_SIZE_DP.dp + Spacing.m, end = Spacing.m, bottom = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        sources.take(6).forEach { source ->
                            Surface(shape = CircleShape, color = container, modifier = Modifier.size(22.dp)) {
                                AsyncImage(faviconOf(source.url), null, Modifier.padding(2.dp).clip(CircleShape))
                            }
                        }
                    }
                    if (sources.size > 6) {
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            "+${sources.size - 6}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = onContainer.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            // Full-card affordance: the action label is part of the same tappable surface.
            HorizontalDivider(color = onContainer.copy(alpha = 0.16f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (failed) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp), tint = onContainer)
                    Spacer(Modifier.width(Spacing.s))
                }
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    modifier = Modifier.weight(1f),
                )
                if (!failed) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        null,
                        Modifier.size(18.dp).rotate(-90f),
                        tint = onContainer,
                    )
                }
            }
        }
    }
}
