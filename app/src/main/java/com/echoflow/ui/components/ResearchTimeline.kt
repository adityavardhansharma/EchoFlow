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
 * The Deep Research surface: a stack of capsule step rows while the run is live, resolving into
 * a single result card when it finishes.
 *
 * Both halves deliberately share one row anatomy — a 24dp state slot, a label, a right-aligned
 * mono meta column, a chevron — so the finished card reads as the last capsule of the run rather
 * than a different component that happens to appear underneath. Emphasis is carried by fill
 * (steps sit on a translucent container, the result card at full `primaryContainer`), never by
 * introducing a new shape.
 *
 * Everything renders from the persisted [ResearchRun] / [ResearchRef], so a backgrounded run
 * replays its whole history on reopen instead of resuming at "currently working".
 *
 * Pre-redesign research is not drawn here at all — see `ui/legacy/LegacyResearchComponents.kt`.
 */

private const val ROW_HEIGHT_DP = 48
private const val MARK_SIZE_DP = 24

/** Completed steps kept visible above the active one before the rest fold into a summary row. */
private const val DONE_TAIL = 2

private fun hostOfUrl(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

private fun faviconOf(url: String): String =
    "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""}&sz=64"

private fun openInBrowser(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

/** A step paired with its position in the full run, so folding a prefix doesn't renumber rows. */
private data class NumberedStep(val step: ResearchStep, val ordinal: Int)

// Every disclosure in this feature — step capsules, the result card's provenance, the workspace
// slide-up — reveals through the same pair, collapsing to an instant cut under reduced motion so
// the whole surface degrades to stillness rather than only its entry animation doing so.
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

// ── Live timeline ────────────────────────────────────────────────────────────────────

/**
 * The live run: a label line naming the engine, then one capsule per step.
 *
 * Steps arrive from the engine and are only as granular as the engine can honestly report — the
 * agentic path narrates every sub-question, Firecrawl replays its activity feed, and the
 * single-shot providers show one row rather than invented stages.
 */
@Composable
fun ResearchTimeline(
    run: ResearchRun,
    steps: List<ResearchStep>,
    sources: List<SearchSource>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val sourceByUrl = remember(sources) { sources.associateBy { it.url } }

    // Everything finished more than DONE_TAIL rows back folds away, so a long plan can't push
    // the composer off a phone screen. Failures never fold — they are the reason to look.
    val activeIndex = steps.indexOfFirst { !it.isTerminal }.let { if (it < 0) steps.size else it }
    val foldEnd = (activeIndex - DONE_TAIL).coerceAtLeast(0)
    val folded = steps.take(foldEnd).filter { it.state != ResearchStep.STATE_FAILED }
    val foldedIds = remember(folded) { folded.map { it.id }.toSet() }
    // Keep each step's position in the full run so a folded prefix doesn't renumber the rows.
    val visibleSteps = steps
        .mapIndexed { index, step -> NumberedStep(step, index + 1) }
        .filter { it.step.id !in foldedIds }
    var showFolded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
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
                Spacer(Modifier.width(Spacing.s))
            }
            TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = Spacing.s)) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(Spacing.s))

        // No steps yet (a run that just started, or an engine that failed before narrating
        // anything) still needs to look alive.
        if (steps.isEmpty()) {
            ResearchCapsule(
                mark = { StepMark(ResearchStep.STATE_ACTIVE, 0) },
                label = run.phase ?: "Starting…",
                meta = null,
                expandable = false,
            )
        }

        if (folded.isNotEmpty()) {
            ResearchCapsule(
                mark = { StepMark(ResearchStep.STATE_DONE, 0) },
                label = "${folded.size} earlier step${if (folded.size == 1) "" else "s"}",
                meta = null,
                expanded = showFolded,
                onToggle = { showFolded = !showFolded },
                expandable = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    folded.forEach { step ->
                        DetailRow(step.label, step.detail)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        visibleSteps.forEachIndexed { index, entry ->
            StepCapsule(
                step = entry.step,
                ordinal = entry.ordinal,
                sourceByUrl = sourceByUrl,
                staggerIndex = index,
                reducedMotion = reducedMotion,
            )
            if (index != visibleSteps.lastIndex) Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun StepCapsule(
    step: ResearchStep,
    ordinal: Int,
    sourceByUrl: Map<String, SearchSource>,
    staggerIndex: Int,
    reducedMotion: Boolean,
) {
    var expanded by remember(step.id) { mutableStateOf(false) }
    val stepSources = remember(step.sourceUrls, sourceByUrl) {
        step.sourceUrls.mapNotNull { sourceByUrl[it] }
    }
    val expandable = stepSources.isNotEmpty()

    // Rows fade up as they arrive, 60ms apart, so a seeded plan lands as a sequence rather than
    // a block. Reduced motion skips straight to the resting frame.
    var shown by remember(step.id) { mutableStateOf(reducedMotion) }
    LaunchedEffect(step.id) {
        if (!shown) {
            delay(staggerIndex * 60L)
            shown = true
        }
    }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = 380f, dampingRatio = 0.85f),
        label = "step-enter",
    )

    ResearchCapsule(
        mark = { StepMark(step.state, ordinal) },
        label = step.label,
        meta = step.detail,
        expanded = expanded,
        onToggle = if (expandable) ({ expanded = !expanded }) else null,
        expandable = expandable,
        dimmed = step.state == ResearchStep.STATE_PENDING,
        modifier = Modifier.graphicsLayer {
            alpha = enter
            translationY = (1f - enter) * 12.dp.toPx()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            stepSources.forEach { source -> SourceDetailRow(source) }
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
                    .then(if (onToggle != null) Modifier.clickable(onClick =onToggle) else Modifier)
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
private fun DetailRow(label: String, meta: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            )
        }
    }
}

@Composable
private fun SourceDetailRow(source: SearchSource) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {openInBrowser(context, source.url) }
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
 * The finished run — the last capsule, grown up.
 *
 * Same anatomy as a step row (mark, title, mono meta) so it rhymes with the timeline it replaced,
 * but filled at full `primaryContainer`: it is the only element in the stack carrying that
 * weight, which is how it reads as the payoff without needing a new shape or a bigger icon. The
 * chevron folds the run's own provenance back open inside it; the report itself lives one tap
 * away in the workspace, because a deep research answer is far too long to sit in a bubble.
 *
 * A failed run uses the same geometry against `errorContainer` and swaps Open for Retry.
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
    val reducedMotion = rememberReducedMotion()
    var showSteps by remember(research.runId) { mutableStateOf(false) }
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
    val chevron by animateFloatAsState(
        targetValue = if (showSteps) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "result-chevron",
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (steps.isNotEmpty()) Modifier.clickable {showSteps = !showSteps } else Modifier)
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
                if (steps.isNotEmpty()) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        if (showSteps) "Hide steps" else "Show steps",
                        Modifier.padding(start = Spacing.xs).size(18.dp).rotate(chevron),
                        tint = onContainer,
                    )
                }
            }

            // The proof of work: only research has this, and it is what makes a long wait feel
            // like it bought something.
            if (sources.isNotEmpty()) {
                Row(
                    Modifier.padding(start = Spacing.m + MARK_SIZE_DP.dp + Spacing.m, end = Spacing.m, bottom = Spacing.m),
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

            AnimatedVisibility(
                visible = showSteps,
                enter = researchRevealEnter(reducedMotion),
                exit = researchRevealExit(reducedMotion),
            ) {
                Column(
                    Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (step.state == ResearchStep.STATE_FAILED) Icons.Default.Close else Icons.Default.Check,
                                    null,
                                    Modifier.size(13.dp),
                                    tint = if (step.state == ResearchStep.STATE_FAILED) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        onContainer.copy(alpha = 0.72f)
                                    },
                                )
                            }
                            Spacer(Modifier.width(Spacing.s))
                            Text(
                                step.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainer.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            step.detail?.let {
                                Spacer(Modifier.width(Spacing.s))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = onContainer.copy(alpha = 0.72f),
                                )
                            }
                        }
                    }
                }
            }

            // One action, full width — the grouped-list idiom the capsules above speak, not a
            // tonal button floating in the corner.
            HorizontalDivider(color = onContainer.copy(alpha = 0.16f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick =if (failed) onRetry else onOpen)
                    .heightIn(min = ROW_HEIGHT_DP.dp)
                    .padding(horizontal = Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (failed) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp), tint = onContainer)
                    Spacer(Modifier.width(Spacing.s))
                }
                Text(
                    when {
                        failed -> "Try again"
                        research.structured -> "Open data"
                        else -> "Open report"
                    },
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
