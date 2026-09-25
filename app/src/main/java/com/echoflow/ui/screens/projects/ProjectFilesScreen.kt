@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.data.ProjectFileOpener
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.screens.chat.ErrorBanner
import com.echoflow.ui.theme.Spacing

// ── Files ──────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProjectFilesScreen(
    project: Project,
    documents: List<ProjectDocument>,
    queued: Int,
    modelReadsFiles: Boolean,
    fileError: String?,
    onClearFileError: () -> Unit,
    onAdd: (List<android.net.Uri>) -> Unit,
    onRemove: (ProjectDocument) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // The file opened in the in-app Markdown reader, or null when the list is showing. Keyed by id
    // so a Room refresh (e.g. re-extraction finishing) re-resolves to the live row.
    var openedDocId by remember { mutableStateOf<String?>(null) }
    val openedDoc = openedDocId?.let { id -> documents.firstOrNull { it.id == id } }
    // The reader owns Back while it's up; the list only handles Back once it's closed.
    BackHandler(enabled = openedDoc == null) { onBack() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onAdd(uris)
    }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val hasContent = documents.isNotEmpty() || queued > 0

    Box(Modifier.fillMaxSize()) {
        ProjectPageScaffold(
            title = "Files",
            subtitle = filesHeaderSubtitle(documents, queued),
            onBack = onBack,
            floatingActionButton = {
                if (hasContent) {
                    ExtendedFloatingActionButton(
                        text = { Text("Add files") },
                        icon = { Icon(Icons.Default.Add, null) },
                        onClick = { picker.launch(arrayOf("*/*")) },
                        expanded = fabExpanded,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // A file that couldn't be added is reported here, on the screen that raised it —
                // not on the chat surface behind the hub, where the user would never see it.
                var bannerMessage by remember { mutableStateOf("") }
                if (fileError != null) bannerMessage = fileError
                AnimatedVisibility(
                    visible = fileError != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    // Keep ErrorBanner composed (and its last message) through the exit
                    // transition — `fileError?.let` removed it the moment the error cleared.
                    ErrorBanner(bannerMessage, onDismiss = onClearFileError)
                }
                if (!hasContent) {
                    FilesEmptyState(onAdd = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 112.dp),
                    ) {
                        item(key = "purpose") {
                            FilesPurposeNote(Modifier.padding(bottom = Spacing.l))
                        }
                        if (queued > 0) {
                            item(key = "queued-batch") {
                                QueuedFilesRow(
                                    count = queued,
                                    modifier = Modifier.padding(bottom = Spacing.l),
                                )
                            }
                        }
                        if (documents.isNotEmpty()) {
                            item(key = "attached-label") {
                                ProjectSectionHeader("Attached", count = documents.size)
                            }
                        }
                        itemsIndexed(documents, key = { _, it -> it.id }) { index, doc ->
                            DocumentRow(
                                document = doc,
                                shape = groupedItemShape(index, documents.size),
                                modelReadsFiles = modelReadsFiles,
                                onOpenExternal = {
                                    if (!ProjectFileOpener.openExternally(context, doc)) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No app can open this file",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onOpenMarkdown = { openedDocId = doc.id },
                                onRemove = { onRemove(doc) },
                                modifier = Modifier.padding(bottom = GroupedItemGap).animateItem(),
                            )
                        }
                    }
                }
            }
        }
        // The Markdown reader slides up over the whole page when a file is opened as Markdown.
        if (openedDoc != null) {
            ProjectDocumentReaderScreen(
                document = openedDoc,
                onClose = { openedDocId = null },
            )
        }
    }
}

/**
 * A quiet caption above the list saying what these files are for — plain text, not a card, so it
 * informs without competing with the files themselves.
 */
@Composable
private fun FilesPurposeNote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Info, null,
            Modifier.padding(top = 1.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            "Every chat in this project can draw on these files as background knowledge.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueuedFilesRow(count: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (count == 1) "1 file queued" else "$count files queued"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 file queued" else "$count files queued",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Waiting to be read…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

internal fun filesHeaderSubtitle(documents: List<ProjectDocument>, queued: Int = 0): String {
    val total = documents.size + queued
    val count = when (total) {
        0 -> "Background knowledge for this project"
        1 -> "1 file"
        else -> "$total files"
    }
    if (total == 0) return count
    val reading = documents.count { it.status.isExtracting }
    val waiting = documents.count { it.status.isQueued } + queued
    return buildList {
        add(count)
        if (reading > 0) add("reading $reading")
        if (waiting > 0) add("$waiting waiting")
    }.joinToString(" · ")
}
