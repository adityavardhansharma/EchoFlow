@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactExport
import com.echoflow.data.ResearchStep
import com.echoflow.data.SearchSource
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.ResearchWorkspaceState
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import org.json.JSONArray
import org.json.JSONObject

private enum class ResearchTab { REPORT, SOURCES, STEPS }

/**
 * The fullscreen Research Workspace — where a finished Deep Research report actually lives.
 *
 * Deep Research answers are long, so the chat keeps only the result card and this holds the
 * substance: the report, the sources it rests on, and the steps that produced it. Copy, share and
 * PDF export live here rather than on the card, which keeps the card down to a single action.
 *
 * Chrome deliberately mirrors [ArtifactWorkspaceScreen] (slide-up, minimize chevron, tab row) —
 * an artifact and a research report are the same kind of object to a user: something the app made
 * that you open.
 */
@Composable
fun ResearchWorkspaceScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
) {
    val state by chatViewModel.researchWorkspace.collectAsState()
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val workspace = state ?: return

    var tab by remember(workspace.research.runId) { mutableStateOf(ResearchTab.REPORT) }

    // Slide up on open, but cut straight in when the user has asked for no animations.
    val reducedMotion = rememberReducedMotion()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 320),
        label = "research-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = if (reducedMotion) 0f else (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxSize()) {
            ResearchTopBar(
                workspace = workspace,
                tab = tab,
                onTab = { tab = it },
                onClose = onClose,
                onCopy = { clipboard.setText(AnnotatedString(workspace.research.report)) },
                onShare = {
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, workspace.research.topic)
                                    putExtra(Intent.EXTRA_TEXT, workspace.research.report)
                                },
                                "Share report",
                            )
                        )
                    }
                },
                onExport = {
                    ArtifactExport.printArtifact(
                        context,
                        exportScope,
                        workspace.research.topic,
                        Artifact.TYPE_MARKDOWN,
                        workspace.research.report,
                    )
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    ResearchTab.REPORT -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.base)
                    ) {
                        SelectionContainer {
                            if (workspace.research.structured) {
                                ResearchDataView(workspace.research.report)
                            } else {
                                RichMarkdown(workspace.research.report, Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                    ResearchTab.SOURCES -> SourcesTab(workspace.sources)
                    ResearchTab.STEPS -> StepsTab(workspace.steps)
                }
            }
        }
    }
}

@Composable
private fun ResearchTopBar(
    workspace: ResearchWorkspaceState,
    tab: ResearchTab,
    onTab: (ResearchTab) -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
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
                Column(Modifier.weight(1f)) {
                    Text(
                        workspace.research.topic,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        workspace.research.engineLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy report", Modifier.size(20.dp)) }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share report", Modifier.size(20.dp)) }
                if (!workspace.research.structured) {
                    FilledTonalIconButton(onClick = onExport) {
                        Icon(Icons.Default.PictureAsPdf, "Export PDF", Modifier.size(20.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ResearchTabButton(
                    label = if (workspace.research.structured) "Data" else "Report",
                    icon = Icons.Default.Description,
                    selected = tab == ResearchTab.REPORT,
                    onClick = { onTab(ResearchTab.REPORT) },
                    modifier = Modifier.weight(1f),
                )
                ResearchTabButton(
                    label = "Sources",
                    count = workspace.sources.size,
                    icon = Icons.Default.Link,
                    selected = tab == ResearchTab.SOURCES,
                    onClick = { onTab(ResearchTab.SOURCES) },
                    modifier = Modifier.weight(1f),
                )
                ResearchTabButton(
                    label = "Steps",
                    count = workspace.steps.size,
                    icon = Icons.Default.AccountTree,
                    selected = tab == ResearchTab.STEPS,
                    onClick = { onTab(ResearchTab.STEPS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ResearchTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
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
            val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Text(
                if (count > 0) "$label $count" else label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SourcesTab(sources: List<SearchSource>) {
    val context = LocalContext.current
    if (sources.isEmpty()) {
        EmptyTab("No sources were recorded for this run.")
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        sources.forEach { source ->
            Surface(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(source.url)))
                    }
                },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(source.url).host }.getOrNull() ?: ""}&sz=64",
                        null,
                        Modifier.size(22.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            source.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            runCatching { android.net.Uri.parse(source.url).host?.removePrefix("www.") }.getOrNull() ?: source.url,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        source.snippet?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun StepsTab(steps: List<ResearchStep>) {
    if (steps.isEmpty()) {
        EmptyTab("This engine didn't report intermediate steps.")
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        steps.forEachIndexed { index, step ->
            val failed = step.state == ResearchStep.STATE_FAILED
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (failed) Icons.Default.Close else Icons.Default.Check,
                            null,
                            Modifier.size(14.dp),
                            tint = if (failed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val elapsed = step.endedAt?.let { it - step.startedAt }?.takeIf { it > 0 }
                        val sub = listOfNotNull(
                            step.detail,
                            elapsed?.let { "${it / 1000}s" },
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun EmptyTab(message: String) {
    Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Data Agent output rendered structurally rather than as a wall of JSON: arrays of objects become
 * stacked cards, nested objects become titled sections, leaves align as label → value.
 *
 * Intentionally a separate implementation from the frozen legacy renderer — the legacy copy must
 * keep drawing old chats exactly as they are, so this one is free to evolve with the workspace.
 */
@Composable
private fun ResearchDataView(json: String, modifier: Modifier = Modifier) {
    val array = remember(json) { runCatching { JSONArray(json) }.getOrNull() }
    val obj = remember(json) { if (array == null) runCatching { JSONObject(json) }.getOrNull() else null }
    when {
        array != null -> JsonArrayBlock(array, modifier)
        obj != null -> JsonObjectBlock(obj, modifier)
        else -> RichMarkdown(json, modifier.fillMaxWidth())
    }
}

@Composable
private fun JsonNode(value: Any?, modifier: Modifier = Modifier) {
    when (value) {
        null, JSONObject.NULL -> Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        is JSONObject -> JsonObjectBlock(value, modifier)
        is JSONArray -> JsonArrayBlock(value, modifier)
        else -> Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
    }
}

@Composable
private fun JsonObjectBlock(obj: JSONObject, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            val nested = value is JSONObject || (value is JSONArray && (0 until value.length()).any { value.opt(it) is JSONObject })
            if (nested) {
                Column {
                    Text(humanKey(key), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.padding(start = Spacing.m, top = Spacing.xs)) { JsonNode(value) }
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        humanKey(key),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.4f).padding(end = Spacing.s),
                    )
                    JsonNode(value, Modifier.weight(0.6f))
                }
            }
        }
    }
}

@Composable
private fun JsonArrayBlock(arr: JSONArray, modifier: Modifier = Modifier) {
    val hasObjects = (0 until arr.length()).any { arr.opt(it) is JSONObject }
    if (hasObjects) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (i in 0 until arr.length()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) { Box(Modifier.padding(Spacing.m)) { JsonNode(arr.opt(i)) } }
            }
        }
    } else {
        Column(modifier) {
            for (i in 0 until arr.length()) {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(arr.opt(i)?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun humanKey(key: String): String =
    key.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
