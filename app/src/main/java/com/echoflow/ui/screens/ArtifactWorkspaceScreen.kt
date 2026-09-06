@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactExport
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.theme.Spacing

private enum class ArtifactViewMode { PREVIEW, CODE }

/**
 * The fullscreen Artifact Workspace — a stripped-down viewer for one artifact lineage.
 * No address bar and no command composer (iteration happens back in chat): just a minimize action,
 * a Preview/Code mode switcher, a version selector, and per-type actions (Export PDF for reports,
 * Copy source). HTML renders in a sandboxed WebView; markdown/latex render with the native engine.
 *
 * Which lineage is shown is decided at open time ([ChatViewModel.openArtifactWorkspace] with an
 * artifact id), not by "latest for this chat", so a historical card always opens the artifact it
 * represents even if the chat later holds more than one lineage.
 */
@Composable
fun ArtifactWorkspaceScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
) {
    val offline by chatViewModel.artifactsOffline.collectAsState()
    val artifact by chatViewModel.workspaceArtifact.collectAsState()
    val versions by chatViewModel.workspaceArtifactVersions.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Leave if the artifact vanishes (e.g. chat deleted) — but NOT on the initial null the
    // WhileSubscribed flow caches before its first emission, which would slam the screen shut the
    // instant it opens. Only close once we've actually shown an artifact and it later disappears.
    var hadArtifact by remember { mutableStateOf(false) }
    LaunchedEffect(artifact) {
        if (artifact != null) hadArtifact = true
        else if (hadArtifact) onClose()
    }
    val a = artifact ?: return

    var mode by remember { mutableStateOf(ArtifactViewMode.PREVIEW) }
    // Open at the version of the card that was tapped (a scrolled-back card represents an older
    // version), falling back to that lineage's latest when the open request omitted a version.
    // Key selection on the open *session*, not just lineage id: reopening the same artifact from
    // a different card (v2 → close → v1) must re-seed, and id alone would keep the prior version.
    val requestedVersion by chatViewModel.artifactInitialVersion.collectAsState()
    val openSession by chatViewModel.workspaceOpenSession.collectAsState()
    var selectedVersion by remember(openSession) {
        mutableIntStateOf((requestedVersion ?: a.currentVersion).coerceIn(1, a.currentVersion))
    }
    // Follow a genuinely newer version as it streams in — but only if the user was viewing the
    // previously-latest. Tracking the last-seen version (rather than testing currentVersion - 1)
    // keeps a deliberately-opened older version pinned instead of snapping it to the newest.
    var lastKnownVersion by remember(openSession) { mutableIntStateOf(a.currentVersion) }
    LaunchedEffect(openSession, a.currentVersion) {
        if (a.currentVersion > lastKnownVersion && selectedVersion == lastKnownVersion) {
            selectedVersion = a.currentVersion
        }
        lastKnownVersion = a.currentVersion
    }
    val content = remember(versions, selectedVersion) {
        (versions.firstOrNull { it.versionNumber == selectedVersion } ?: versions.lastOrNull())?.content.orEmpty()
    }

    // Smooth open: the screen is always present (so "Open" is reliable); we just slide it up from
    // the bottom and fade it in on first appearance via a graphics layer, then reverse none — the
    // parent removes it on close. Driven by a flag flipped one frame after entering composition.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "artifact-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxSize()) {
            ArtifactTopBar(
                artifact = a,
                versions = versions.map { it.versionNumber },
                selectedVersion = selectedVersion,
                onSelectVersion = { selectedVersion = it },
                mode = mode,
                onMode = { mode = it },
                onClose = onClose,
                onExport = { ArtifactExport.printArtifact(context, a.title, a.type, content, offline = offline) },
                onCopy = { clipboard.setText(AnnotatedString(content)) },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (a.isHtml) Color.White
                        else MaterialTheme.colorScheme.surface
                    ),
            ) {
                when {
                    a.isHtml -> {
                        // Keep the WebView mounted across Preview/Code so toggling modes doesn't
                        // destroy and reload it (which flashes black). The Code view overlays it.
                        ArtifactWebView(html = content, offline = offline, modifier = Modifier.fillMaxSize())
                        if (mode == ArtifactViewMode.CODE) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                CodeView(content)
                            }
                        }
                    }
                    mode == ArtifactViewMode.CODE -> CodeView(content)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.base)) {
                        RichMarkdown(content, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactTopBar(
    artifact: Artifact,
    versions: List<Int>,
    selectedVersion: Int,
    onSelectVersion: (Int) -> Unit,
    mode: ArtifactViewMode,
    onMode: (ArtifactViewMode) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize to chat", Modifier.size(26.dp))
                }
                Text(
                    artifact.title.ifBlank { "Artifact" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (versions.size > 1) VersionMenu(versions, selectedVersion, onSelectVersion)
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy source", Modifier.size(20.dp)) }
                // Export to PDF only for documents/reports (markdown/LaTeX), where it renders the
                // math/prose to a page. HTML pages aren't meant to be flattened to a PDF, so the
                // action is hidden for them.
                if (!artifact.isHtml) {
                    FilledTonalIconButton(onClick = onExport) {
                        Icon(Icons.Default.PictureAsPdf, "Export PDF", Modifier.size(20.dp))
                    }
                }
            }
            // Preview / Code segmented switcher.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ModeTab(
                    label = if (artifact.isHtml) "Preview" else "Reading",
                    icon = Icons.Default.Preview,
                    selected = mode == ArtifactViewMode.PREVIEW,
                    onClick = { onMode(ArtifactViewMode.PREVIEW) },
                    modifier = Modifier.weight(1f),
                )
                ModeTab(
                    label = if (artifact.isHtml) "Code" else "Source",
                    icon = Icons.Default.Code,
                    selected = mode == ArtifactViewMode.CODE,
                    onClick = { onMode(ArtifactViewMode.CODE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null, Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionMenu(versions: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.s, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("v$selected", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.UnfoldMore, "Pick version", Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            versions.sortedDescending().forEach { v ->
                DropdownMenuItem(
                    text = { Text("Version $v") },
                    trailingIcon = { if (v == selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onSelect(v); open = false },
                )
            }
        }
    }
}

@Composable
private fun CodeView(code: String) {
    SelectionContainer {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.base)) {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(Spacing.xl))
        }
    }
}

/**
 * Sandboxed WebView for an HTML artifact. Loaded with a null base URL (opaque origin) and with file
 * access disabled — the artifact is untrusted, model-generated code, so it gets no path to the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArtifactWebView(html: String, offline: Boolean, modifier: Modifier = Modifier) {
    var lastHtml by remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // Opaque white base so the view never paints a black frame while (re)loading.
                setBackgroundColor(android.graphics.Color.WHITE)
                com.echoflow.data.ArtifactWebSecurity.configure(this, offline)
                webViewClient = com.echoflow.data.ArtifactWebSecurity.Client(offline)
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                lastHtml = html
            }
        },
        update = { web ->
            val policyChanged = web.settings.blockNetworkLoads != offline
            if (policyChanged) web.stopLoading()
            com.echoflow.data.ArtifactWebSecurity.configure(web, offline)
            web.webViewClient = com.echoflow.data.ArtifactWebSecurity.Client(offline)
            if (lastHtml != html || policyChanged) {
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
