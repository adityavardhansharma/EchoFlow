@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactVersion
import com.echoflow.data.VersionDelta
import com.echoflow.data.versionDeltas
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.diffAdded
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

// Most recent versions as chips; older ones fold into "+N earlier".
private const val MAX_VERSION_CHIPS = 4
private const val CARD_RADIUS_DP = 22
private const val MARK_SIZE_DP = 24
private const val ROW_MIN_DP = 48
private const val PREVIEW_HEIGHT_DP = 150
// Mono filename chips must be width-bounded or long model titles grow the Surface to fit
// the full string and TextOverflow.Ellipsis never engages.
private val FileChipMaxWidth = 160.dp
private val KnownArtifactExtensions = setOf("md", "tex", "html", "htm")

/**
 * In-chat artifact surface for current-app artifacts.
 *
 * **Building** — research-style expandable capsule, **open by default**, with a short narrative
 * step list (planning → scaffolding → writing → …). Not real engine steps; a readable progress
 * story driven by stream size so the card feels alive.
 *
 * **Handoff** — when the stream finishes, the capsule stays for HTML and adds a final
 * "Fetching thumbnail" step while a sandboxed WebView warms the preview **off-screen**. Only
 * once that paint is ready does the **full settled card** animate in as one unit (header +
 * preview + chips). No "card first, WebView fades in later" jank.
 *
 * **Settled** — primaryContainer result object (sibling of [ResearchResultCard]) with
 * file-diff version chips. Non-HTML skips the thumbnail handoff and settles immediately.
 *
 * Pre-redesign rows stay on `ui/legacy`.
 */
@Composable
fun ArtifactCard(
    artifactId: String?,
    title: String,
    artifactType: String,
    version: Int,
    building: Boolean,
    charCount: Int,
    truncated: Boolean,
    onOpen: (artifactId: String, version: Int) -> Unit,
    modifier: Modifier = Modifier,
    observeVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
) {
    val versionsFlow = remember(artifactId) {
        if (artifactId != null) observeVersions(artifactId) else flowOf(emptyList())
    }
    val versions by versionsFlow.collectAsState(initial = emptyList())
    val lineage = remember(versions, version) {
        versions.filter { it.versionNumber <= version }.sortedBy { it.versionNumber }
    }
    val deltas by produceState(initialValue = emptyList<VersionDelta>(), lineage) {
        value = withContext(Dispatchers.Default) { versionDeltas(lineage) }
    }
    val isHtml = artifactType == Artifact.TYPE_HTML
    // Live-build memory must NOT key on artifactId/version. The reducer assigns those only on
    // ArtifactCompleted, in the same update that sets building=false — so remember(id, version)
    // was wiped at the exact moment we needed the handoff, re-init sawLiveBuild to false and
    // skipping the thumbnail warm. Key on composition identity only (fresh when the Lazy item
    // is disposed on chat switch).
    //
    // Historical opens: first frame has building=false → sawLiveBuild stays false → settled only.
    // Live stream: building starts true → sawLiveBuild latches true for the rest of this card.
    var sawLiveBuild by remember { mutableStateOf(building) }
    SideEffect {
        if (building) sawLiveBuild = true
    }

    val previewHtml = remember(lineage, version, isHtml) {
        if (isHtml) {
            (lineage.lastOrNull { it.versionNumber == version } ?: lineage.lastOrNull())?.content
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    // Same stability rule: do not key on artifactId/version or completion resets the warm wait.
    var liveThumbnailReady by remember { mutableStateOf(false) }
    LaunchedEffect(building, sawLiveBuild, isHtml) {
        if (!sawLiveBuild) return@LaunchedEffect
        if (building) {
            liveThumbnailReady = false
        } else if (!isHtml) {
            // Non-HTML settles as soon as the stream ends — no warm step.
            liveThumbnailReady = true
        }
    }
    LaunchedEffect(previewHtml, sawLiveBuild, building, isHtml, liveThumbnailReady) {
        // Timeout so a silent WebView never leaves the capsule stuck.
        if (sawLiveBuild && !building && isHtml && previewHtml != null && !liveThumbnailReady) {
            delay(2_800)
            liveThumbnailReady = true
        }
    }

    val preparingThumbnail =
        sawLiveBuild && !building && isHtml && !liveThumbnailReady
    val showTrace = building || preparingThumbnail

    val openSettled: () -> Unit = { artifactId?.let { onOpen(it, version) } }

    Column(modifier.fillMaxWidth()) {
        if (preparingThumbnail && previewHtml != null) {
            ArtifactPreviewWebView(
                html = previewHtml,
                interactive = false,
                onReady = { liveThumbnailReady = true },
                backgroundColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT_DP.dp)
                    .offset(y = 4000.dp)
                    .graphicsLayer { alpha = 0f },
            )
        }

        if (showTrace) {
            BuildingArtifactTrace(
                title = title,
                artifactType = artifactType,
                charCount = charCount,
                streamFinished = !building,
                fetchingThumbnail = preparingThumbnail,
            )
        } else {
            // Historical reopen and post-handoff: settled card only — never the build capsule.
            SettledArtifactCard(
                title = title,
                artifactType = artifactType,
                truncated = truncated,
                deltas = deltas,
                previewHtml = previewHtml,
                onOpen = openSettled,
            )
        }
    }
}

// ── Building trace (expandable capsule) ──────────────────────────────────────────────

private enum class StepState { Pending, Active, Done }

private data class TraceStep(val label: String, val state: StepState)

/**
 * Narrative steps from stream progress. Not engine telemetry — a readable story that advances
 * as the body grows, then ends on "Fetching thumbnail" when HTML is warming.
 */
private fun buildTraceSteps(
    charCount: Int,
    streamFinished: Boolean,
    fetchingThumbnail: Boolean,
    typeLabel: String,
): List<TraceStep> {
    // Thresholds are soft UX beats, not protocol.
    val planDone = charCount > 0 || streamFinished
    val scaffoldDone = charCount > 180 || streamFinished
    val writeDone = charCount > 900 || streamFinished
    val polishDone = streamFinished

    fun state(done: Boolean, activeGate: Boolean): StepState = when {
        done -> StepState.Done
        activeGate -> StepState.Active
        else -> StepState.Pending
    }

    val steps = mutableListOf(
        TraceStep("Planning $typeLabel structure", state(planDone, !planDone)),
        TraceStep("Scaffolding layout", state(scaffoldDone, planDone && !scaffoldDone)),
        TraceStep(
            if (charCount > 0) "Writing body · $charCount chars" else "Writing body",
            state(writeDone, scaffoldDone && !writeDone),
        ),
        TraceStep("Polishing details", state(polishDone, writeDone && !polishDone)),
    )
    if (fetchingThumbnail || (streamFinished && fetchingThumbnail)) {
        steps.add(
            TraceStep(
                "Fetching thumbnail",
                if (fetchingThumbnail) StepState.Active else StepState.Done,
            ),
        )
    } else if (streamFinished) {
        // Non-HTML settle path: last beat marks complete before the card swap.
        steps.add(TraceStep("Ready", StepState.Done))
    }
    return steps
}

@Composable
private fun BuildingArtifactTrace(
    title: String,
    artifactType: String,
    charCount: Int,
    streamFinished: Boolean,
    fetchingThumbnail: Boolean,
    modifier: Modifier = Modifier,
) {
    val (_, typeLabel) = artifactGlyph(artifactType)
    val fileName = artifactFileLabel(title, typeLabel, artifactType)
    val steps = remember(charCount, streamFinished, fetchingThumbnail, typeLabel) {
        buildTraceSteps(charCount, streamFinished, fetchingThumbnail, typeLabel)
    }
    val reducedMotion = rememberReducedMotion()
    // Open by default while the artifact is under construction / warming the preview.
    var expanded by remember { mutableStateOf(true) }
    val radius by animateDpAsState(
        targetValue = if (expanded) 14.dp else CARD_RADIUS_DP.dp,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "artifact-capsule-radius",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "artifact-capsule-chevron",
    )
    val headerLabel = when {
        fetchingThumbnail -> "Finishing artifact"
        streamFinished -> "Finishing artifact"
        charCount > 0 -> "Building artifact"
        else -> "Starting artifact"
    }
    val meta = when {
        fetchingThumbnail -> "preview"
        charCount > 0 -> "$charCount"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .heightIn(min = ROW_MIN_DP.dp)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(MARK_SIZE_DP.dp), contentAlignment = Alignment.Center) {
                    if (streamFinished && !fetchingThumbnail) {
                        FilledArtifactMark(
                            container = MaterialTheme.colorScheme.primary,
                            content = MaterialTheme.colorScheme.onPrimary,
                            icon = Icons.Default.Check,
                            description = "Done",
                        )
                    } else {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(MARK_SIZE_DP.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Text(
                    headerLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.s))
                // widthIn is required: without a max, Surface sizes to the full string and
                // TextOverflow.Ellipsis never engages. Header label keeps weight(1f) and yields.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.widthIn(max = FileChipMaxWidth),
                ) {
                    Text(
                        fileName,
                        Modifier
                            .padding(horizontal = Spacing.s, vertical = 4.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta != null) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    Modifier.padding(start = Spacing.xs).size(18.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(300)) + fadeIn(tween(200)),
                exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(240)) + fadeOut(tween(120)),
            ) {
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                ) {
                    Box(
                        Modifier.width(MARK_SIZE_DP.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    Spacer(Modifier.width(Spacing.m))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        steps.forEach { step ->
                            TraceStepRow(step)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceStepRow(step: TraceStep) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (step.state) {
            StepState.Done -> FilledArtifactMark(
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                icon = Icons.Default.Check,
                description = "Done",
                sizeDp = 18,
            )
            StepState.Active -> Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            StepState.Pending -> Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            step.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (step.state == StepState.Active) FontWeight.Medium else FontWeight.Normal,
            color = when (step.state) {
                StepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Settled result ───────────────────────────────────────────────────────────────────

@Composable
private fun SettledArtifactCard(
    title: String,
    artifactType: String,
    truncated: Boolean,
    deltas: List<VersionDelta>,
    previewHtml: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (_, typeLabel) = artifactGlyph(artifactType)

    Surface(
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_DP.dp)
                    .padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledArtifactMark(
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    icon = Icons.Default.Check,
                    description = "Artifact ready",
                )
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (truncated) "$typeLabel · incomplete" else typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Default.OpenInFull,
                    "Open artifact",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
            }

            // Preview rides with the settled card — no separate fade after chrome is up.
            if (!previewHtml.isNullOrBlank()) {
                Box(
                    Modifier
                        .padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m)
                        .fillMaxWidth()
                        .height(PREVIEW_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    ArtifactPreviewWebView(
                        html = previewHtml,
                        interactive = false,
                        onReady = null,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.matchParentSize(),
                    )
                    // Card is the gesture target; swallow WebView touches.
                    Box(Modifier.matchParentSize().clickable(onClick = onOpen))
                }
            }

            if (deltas.isNotEmpty()) {
                VersionChipRow(
                    deltas = deltas,
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                )
            }
        }
    }
}

/**
 * Version history only — no filename chip. The settled header already carries the human title;
 * repeating it as `Title-slug.html` was a leftover from the multi-file tool-chip reference.
 */
@Composable
private fun VersionChipRow(
    deltas: List<VersionDelta>,
    modifier: Modifier = Modifier,
) {
    val shown = remember(deltas) { deltas.takeLast(MAX_VERSION_CHIPS) }
    val hidden = deltas.size - shown.size
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(Spacing.m))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (hidden > 0) MoreChip("+$hidden earlier")
            shown.forEachIndexed { index, delta ->
                VersionChip(delta = delta, staggerIndex = index)
            }
        }
    }
}

@Composable
private fun VersionChip(delta: VersionDelta, staggerIndex: Int) {
    val reducedMotion = rememberReducedMotion()
    var visible by remember(delta.version) { mutableStateOf(reducedMotion) }
    LaunchedEffect(delta.version) {
        if (!visible) {
            delay(staggerIndex * 45L)
            visible = true
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = 520f, dampingRatio = 0.75f),
        label = "chip-appear",
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.graphicsLayer {
            alpha = appear
            val s = 0.92f + 0.08f * appear
            scaleX = s
            scaleY = s
        },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "v${delta.version}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                delta.isFirst -> ChipMeta("${delta.lineCount} lines", MaterialTheme.colorScheme.onSurfaceVariant)
                delta.delta.isEmpty -> ChipMeta("±0", MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    if (delta.delta.added > 0) ChipMeta("+${delta.delta.added}", MaterialTheme.colorScheme.diffAdded)
                    if (delta.delta.removed > 0) ChipMeta("−${delta.delta.removed}", MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChipMeta(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
        color = color,
    )
}

@Composable
private fun MoreChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Sandboxed HTML preview ───────────────────────────────────────────────────────────

/**
 * Public, non-interactive HTML thumbnail — the same sandboxed, network-blocked preview the in-chat
 * artifact card uses, exposed for the Artifacts gallery so a tile shows a live sliver of the page
 * rather than a flat glyph. Touches are swallowed; the caller owns the tap.
 */
@Composable
fun ArtifactHtmlThumbnail(html: String, modifier: Modifier = Modifier) {
    ArtifactPreviewWebView(
        html = html,
        interactive = false,
        onReady = null,
        backgroundColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    )
}

/**
 * Auto-rendered HTML preview. Network is fully blocked (model HTML must not phone home on
 * mere compose). [onReady] fires on first [WebViewClient.onPageFinished] so the handoff can
 * wait before revealing the settled card.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ArtifactPreviewWebView(
    html: String,
    interactive: Boolean,
    onReady: (() -> Unit)?,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    var lastHtml by remember { mutableStateOf<String?>(null) }
    var readyFired by remember(html) { mutableStateOf(false) }
    val backgroundArgb = backgroundColor.toArgb()

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor),
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isClickable = interactive
                    isLongClickable = interactive
                    com.echoflow.data.ArtifactWebSecurity.configure(this, offline = true)
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION") settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION") settings.allowUniversalAccessFromFileURLs = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    @Suppress("DEPRECATION") settings.setSupportZoom(false)
                    // Auto-preview: never let model HTML phone home.
                    settings.blockNetworkLoads = true
                    settings.blockNetworkImage = true
                    if (!interactive) {
                        setOnTouchListener { _, _ -> true }
                    }
                    setBackgroundColor(backgroundArgb)
                    webViewClient = object : com.echoflow.data.ArtifactWebSecurity.Client(offline = true) {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!readyFired) {
                                readyFired = true
                                onReady?.invoke()
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = true

                        @Deprecated("Pre-24 navigation guard")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val scheme = request?.url?.scheme?.lowercase()
                            return if (scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss") {
                                WebResourceResponse("text/plain", "utf-8", null)
                            } else {
                                null
                            }
                        }
                    }
                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    lastHtml = html
                }
            },
            update = { web ->
                web.setBackgroundColor(backgroundArgb)
                if (lastHtml != html) {
                    readyFired = false
                    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    lastHtml = html
                }
            },
            onRelease = { web ->
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            },
        )
    }
}

@Composable
private fun FilledArtifactMark(
    container: Color,
    content: Color,
    icon: ImageVector,
    description: String,
    sizeDp: Int = MARK_SIZE_DP,
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(sizeDp.dp * 0.6f), tint = content)
    }
}

private fun artifactGlyph(type: String): Pair<ImageVector, String> = when (type) {
    Artifact.TYPE_MARKDOWN -> Icons.AutoMirrored.Filled.Article to "Document"
    Artifact.TYPE_LATEX -> Icons.Default.PictureAsPdf to "Report"
    else -> Icons.Default.Code to "Web page"
}

/**
 * Mono chip label for the artifact. Always ends with the type suffix unless the title already
 * ends in a known artifact extension (`.md` / `.tex` / `.html`). A mid-title period must not
 * suppress the suffix — "Dr. Smith report" → `Dr.-Smith-report.html`, not bare `Dr.-Smith-report`.
 */
internal fun artifactFileLabel(title: String, typeLabel: String, artifactType: String): String {
    val base = title.trim().ifBlank { typeLabel }.replace(Regex("\\s+"), "-")
    val ext = when (artifactType) {
        Artifact.TYPE_MARKDOWN -> "md"
        Artifact.TYPE_LATEX -> "tex"
        else -> "html"
    }
    val lastDot = base.lastIndexOf('.')
    val hasKnownExt = lastDot in 1 until base.lastIndex &&
        base.substring(lastDot + 1).lowercase() in KnownArtifactExtensions
    return if (hasKnownExt) base else "$base.$ext"
}
