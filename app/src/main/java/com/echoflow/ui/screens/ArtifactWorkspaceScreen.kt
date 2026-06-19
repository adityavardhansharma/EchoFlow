@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * The fullscreen Artifact Workspace — a stripped-down viewer for the chat's current artifact.
 * No address bar and no command composer (iteration happens back in chat): just a minimize action,
 * a Preview/Code mode switcher, a version selector, and per-type actions (Export PDF for reports,
 * Copy source). HTML renders in a sandboxed WebView; markdown/latex render with the native engine.
 */
@Composable
fun ArtifactWorkspaceScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
) {
    val artifact by chatViewModel.currentArtifact.collectAsState()
    val versions by chatViewModel.currentArtifactVersions.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Leave if the artifact vanishes (e.g. chat deleted).
    LaunchedEffect(artifact == null) { if (artifact == null) onClose() }
    val a = artifact ?: return

    var mode by remember { mutableStateOf(ArtifactViewMode.PREVIEW) }
    var selectedVersion by remember(a.id) { mutableIntStateOf(a.currentVersion) }
    // Follow the latest version as new ones stream in, unless the user pinned an older one.
    LaunchedEffect(a.currentVersion) {
        if (selectedVersion < a.currentVersion && selectedVersion == a.currentVersion - 1) {
            selectedVersion = a.currentVersion
        }
    }
    val content = remember(versions, selectedVersion) {
        (versions.firstOrNull { it.versionNumber == selectedVersion } ?: versions.lastOrNull())?.content.orEmpty()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxSize()) {
            ArtifactTopBar(
                artifact = a,
                versions = versions.map { it.versionNumber },
                selectedVersion = selectedVersion,
                onSelectVersion = { selectedVersion = it },
                mode = mode,
                onMode = { mode = it },
                onClose = onClose,
                onExport = { ArtifactExport.printArtifact(context, a.title, a.type, content) },
                onCopy = { clipboard.setText(AnnotatedString(content)) },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (a.isHtml && mode == ArtifactViewMode.PREVIEW) Color.White
                        else MaterialTheme.colorScheme.surface
                    ),
            ) {
                when {
                    mode == ArtifactViewMode.CODE -> CodeView(content)
                    a.isHtml -> ArtifactWebView(html = content, modifier = Modifier.fillMaxSize())
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
                // Export to PDF — reports/docs render their math; an HTML artifact prints as a page.
                FilledTonalIconButton(onClick = onExport) {
                    Icon(Icons.Default.PictureAsPdf, "Export PDF", Modifier.size(20.dp))
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
private fun ArtifactWebView(html: String, modifier: Modifier = Modifier) {
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
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                lastHtml = html
            }
        },
        update = { web ->
            if (lastHtml != html) {
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
