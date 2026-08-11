@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.collectAsState
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

// The most recent versions carried as chips; older ones fold into a single "+N" marker so the row
// never wraps past a couple of lines on a phone.
private const val MAX_VERSION_CHIPS = 4
private const val CARD_RADIUS_DP = 22
private const val PREVIEW_HEIGHT_DP = 150

/**
 * The in-chat artifact card.
 *
 * While [building] it is a live trace — a spinner, what's being written, and a running size — with
 * no code ever leaking into the bubble. Once finished it settles into a result object: a type
 * glyph, its title, and a row of **version chips** that put the otherwise invisible edit history
 * (`v1 · 84 lines`, `v2 +74 −41`) right on the card. The whole settled card is the tap target for
 * the fullscreen workspace.
 *
 * It owns no artifact content itself: given the lineage's [artifactId] it observes [observeVersions]
 * and derives the chips (and, for HTML, a preview) from the persisted rows — so a backgrounded or
 * scrolled-back card always reflects the real store.
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
    onOpen: (version: Int) -> Unit,
    modifier: Modifier = Modifier,
    observeVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
) {
    if (building) {
        BuildingArtifactCard(title = title, artifactType = artifactType, charCount = charCount, modifier = modifier)
        return
    }

    // All versions of this lineage up to and including the one this card was written at — a card in
    // scrolled-back history shows its own era's chips, not the lineage's latest state.
    val versionsFlow = remember(artifactId) {
        if (artifactId != null) observeVersions(artifactId) else flowOf(emptyList())
    }
    val versions by versionsFlow.collectAsState(initial = emptyList())
    val lineage = remember(versions, version) {
        versions.filter { it.versionNumber <= version }.sortedBy { it.versionNumber }
    }
    // Diffing can span large HTML, so fold it off the main thread; the chip row simply appears once
    // the counts land.
    val deltas by produceState(initialValue = emptyList<VersionDelta>(), lineage) {
        value = withContext(Dispatchers.Default) { versionDeltas(lineage) }
    }
    // HTML artifacts get a live render preview on the card — the thing markdown/report artifacts
    // can't offer. Non-HTML stays glyph + chips.
    val previewHtml = remember(lineage, version, artifactType) {
        if (artifactType == Artifact.TYPE_HTML) {
            (lineage.lastOrNull { it.versionNumber == version } ?: lineage.lastOrNull())?.content
        } else {
            null
        }
    }

    SettledArtifactCard(
        title = title,
        artifactType = artifactType,
        version = version,
        truncated = truncated,
        deltas = deltas,
        previewHtml = previewHtml,
        onOpen = { onOpen(version) },
        modifier = modifier,
    )
}

@Composable
private fun SettledArtifactCard(
    title: String,
    artifactType: String,
    version: Int,
    truncated: Boolean,
    deltas: List<VersionDelta>,
    previewHtml: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, typeLabel) = artifactGlyph(artifactType)
    Surface(
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tertiary glyph tile — artifacts keep their tertiary identity while the card body
                // stays neutral so the mono chips carry the colour, like the reference.
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (truncated) "$typeLabel · incomplete" else typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Default.OpenInFull,
                    "Open artifact",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!previewHtml.isNullOrBlank()) {
                ArtifactPreviewThumbnail(
                    html = previewHtml,
                    onOpen = onOpen,
                    modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.m),
                )
            }

            if (deltas.isNotEmpty()) {
                VersionChipRow(
                    deltas = deltas,
                    modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.m),
                )
            }
        }
    }
}

/** The edit history laid out as glanceable chips: newest few, older ones folded into a "+N" marker. */
@Composable
private fun VersionChipRow(deltas: List<VersionDelta>, modifier: Modifier = Modifier) {
    val shown = remember(deltas) { deltas.takeLast(MAX_VERSION_CHIPS) }
    val hidden = deltas.size - shown.size
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (hidden > 0) MoreChip("+$hidden earlier")
        shown.forEachIndexed { index, delta ->
            VersionChip(delta = delta, staggerIndex = index)
        }
    }
}

@Composable
private fun VersionChip(delta: VersionDelta, staggerIndex: Int) {
    val reducedMotion = rememberReducedMotion()
    // A gentle staggered settle as chips arrive, echoing the research capsule enter. Keyed by the
    // chip's identity so it plays once on appear, not on every delta recompute.
    var visible by remember(delta.version) { mutableStateOf(reducedMotion) }
    LaunchedEffect(delta.version) {
        if (!visible) {
            delay(staggerIndex * 55L)
            visible = true
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = 520f, dampingRatio = 0.7f),
        label = "chip-appear",
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.graphicsLayer {
            alpha = appear
            val s = 0.9f + 0.1f * appear
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
                delta.isFirst -> ChipMeta(
                    "${delta.lineCount} lines",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun ChipMeta(text: String, color: androidx.compose.ui.graphics.Color) {
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

/**
 * A live, non-interactive render of an HTML artifact — the card preview markdown/report artifacts
 * can't give. The page renders in the same sandbox as the workspace (opaque origin, no file/content
 * access); a transparent tap layer keeps the gesture with the card, and the render fades in once the
 * page settles so the white band never flashes empty.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ArtifactPreviewThumbnail(html: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    var loaded by remember(html) { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (loaded || reducedMotion) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 260),
        label = "preview-appear",
    )
    var lastHtml by remember { mutableStateOf<String?>(null) }
    Box(
        modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = appear },
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isClickable = false
                    isLongClickable = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION") settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION") settings.allowUniversalAccessFromFileURLs = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    @Suppress("DEPRECATION") settings.setSupportZoom(false)
                    // This preview auto-renders — the user never chose to open it — so untrusted
                    // model HTML must not phone home. Block every network load (remote <script>,
                    // <img>, fetch/XHR, beacons) and refuse navigations; only the inline document
                    // paints, in an opaque origin with no way out. The full workspace (which the
                    // user explicitly opens) keeps normal loading.
                    settings.blockNetworkLoads = true
                    settings.blockNetworkImage = true
                    // Swallow touches so scrolling/taps belong to the card, not the page.
                    setOnTouchListener { _, _ -> true }
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loaded = true }

                        // No navigation out of the inline document, ever.
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                        @Deprecated("Pre-24 navigation guard")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                        // Belt-and-suspenders over blockNetworkLoads: drop any remote subresource.
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
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
                if (lastHtml != html) {
                    loaded = false
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
        // Transparent tap layer: tapping the preview opens the workspace like the rest of the card.
        Box(Modifier.matchParentSize().clickable(onClick = onOpen))
    }
}

/**
 * The live "Building…" state as a trace, in the research-capsule grammar: a spinner where the glyph
 * tile will settle, the file being written as a mono chip, and a running size — then a wavy line
 * for liveliness. No code ever leaks into the bubble. When it finishes the spinner gives way to the
 * settled card's tertiary glyph tile, so building → done reads as one object resolving rather than
 * a component swap.
 */
@Composable
private fun BuildingArtifactCard(
    title: String,
    artifactType: String,
    charCount: Int,
    modifier: Modifier = Modifier,
) {
    val (_, typeLabel) = artifactGlyph(artifactType)
    Surface(
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.m))
                Text(
                    if (charCount > 0) "Writing" else "Starting",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.s))
                // The "file" being written — the reference's mono filename chip.
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        title.ifBlank { typeLabel },
                        Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Spacer(Modifier.weight(1f))
                if (charCount > 0) {
                    Text(
                        "$charCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
            LinearWavyProgressIndicator(
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun artifactGlyph(type: String): Pair<ImageVector, String> = when (type) {
    Artifact.TYPE_MARKDOWN -> Icons.AutoMirrored.Filled.Article to "Document"
    Artifact.TYPE_LATEX -> Icons.Default.PictureAsPdf to "Report"
    else -> Icons.Default.Code to "Web page"
}
