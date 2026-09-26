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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
                    ProjectFab("Add files", Icons.Default.Add, fabExpanded) { picker.launch(arrayOf("*/*")) }
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
                            ProjectPageNote(
                                "Every chat in this project can use these files as background knowledge.",
                                Modifier.padding(bottom = Spacing.l),
                            )
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
                                ProjectSectionHeader("Attached")
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

/** Files picked but not yet read, shown as one row in the same style as the files below. */
@Composable
private fun QueuedFilesRow(count: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val label = if (count == 1) "1 file queued" else "$count files queued"
    Surface(
        shape = groupedItemShape(0, 1),
        color = cs.surfaceContainer,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(modifier = Modifier.size(24.dp), color = cs.onSecondaryContainer)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Waiting to be read",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
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
