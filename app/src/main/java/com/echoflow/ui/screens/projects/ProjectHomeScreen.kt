@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.theme.Spacing

// ── Project home ───────────────────────────────────────────────────────────────────────

private enum class ProjectDetail { None, Instructions, Files }

private fun restoreProjectDetail(saved: String): ProjectDetail =
    ProjectDetail.entries.firstOrNull { it.name == saved } ?: ProjectDetail.None

@Composable
internal fun ProjectHomeScreen(chatViewModel: ChatViewModel, projectId: String, onBack: () -> Unit) {
    val project by remember(projectId) { chatViewModel.projectFlow(projectId) }.collectAsState(null)
    val documents by remember(projectId) { chatViewModel.projectDocumentsFlow(projectId) }.collectAsState(emptyList())
    val chats by remember(projectId) { chatViewModel.projectChatsFlow(projectId) }.collectAsState(emptyList())
    // Saveable so rotation does not reset to home and drop the editor without calling leave/save.
    var detail by rememberSaveable(
        projectId,
        saver = Saver(
            save = { it.value.name },
            restore = { saved -> mutableStateOf(restoreProjectDetail(saved)) },
        ),
    ) { mutableStateOf(ProjectDetail.None) }

    val p = project
    when {
        p != null && detail == ProjectDetail.Instructions -> ProjectInstructionsScreen(
            project = p,
            onSave = { chatViewModel.setProjectInstructions(projectId, it) },
            onBack = { detail = ProjectDetail.None },
        )
        p != null && detail == ProjectDetail.Files -> {
            val modelReadsFiles by chatViewModel.modelReadsFiles.collectAsState()
            val projectFileError by chatViewModel.projectFileError.collectAsState()
            val importProgress by chatViewModel.projectImportProgress.collectAsState()
            val fileError = projectFileError?.takeIf { it.projectId == projectId }?.message
            val queued = importProgress[projectId]?.queued ?: 0
            ProjectFilesScreen(
                project = p,
                documents = documents,
                queued = queued,
                modelReadsFiles = modelReadsFiles,
                fileError = fileError,
                onClearFileError = { chatViewModel.clearProjectFileError(projectId) },
                onAdd = { uris -> chatViewModel.addProjectDocuments(projectId, uris) },
                onRemove = { chatViewModel.removeProjectDocument(it) },
                onBack = { chatViewModel.clearProjectFileError(projectId); detail = ProjectDetail.None },
            )
        }
        else -> {
            BackHandler { onBack() }
            ProjectHomeContent(
                chatViewModel = chatViewModel,
                project = p,
                projectId = projectId,
                documents = documents,
                chats = chats,
                onBack = onBack,
                onOpenInstructions = { detail = ProjectDetail.Instructions },
                onOpenFiles = { detail = ProjectDetail.Files },
            )
        }
    }
}


@Composable
private fun ProjectHomeContent(
    chatViewModel: ChatViewModel,
    project: Project?,
    projectId: String,
    documents: List<ProjectDocument>,
    chats: List<com.echoflow.data.ChatThread>,
    onBack: () -> Unit,
    onOpenInstructions: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    val colorIndex = project?.colorIndex ?: 0
    val accent = projectAccent(colorIndex)
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    if (showRename && project != null) {
        ProjectNameDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Save",
            colorIndex = colorIndex,
            onDismiss = { showRename = false },
            onConfirm = { showRename = false; chatViewModel.renameProject(projectId, it) },
        )
    }
    if (showDelete && project != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete project?") },
            text = { Text("“${project.name}” will be deleted. Its conversations are kept and return to your chat list.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    chatViewModel.deleteProject(projectId)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
    if (showColor && project != null) {
        ProjectColorDialog(
            selected = project.colorIndex,
            onDismiss = { showColor = false },
            onPick = { showColor = false; chatViewModel.setProjectColor(projectId, it) },
        )
    }

    ProjectPageScaffold(
        title = project?.name ?: "Project",
        subtitle = project?.let { "Updated ${relativeTime(it.updatedAt)}" },
        titleLeading = { ProjectMedallion(colorIndex, size = 32.dp) },
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; showRename = true })
                    DropdownMenuItem(text = { Text("Colour & shape") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menuOpen = false; showColor = true })
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                    DropdownMenuItem(
                        text = { Text("Delete project", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        },
        // The primary action is the FAB, tinted with the project's own accent: a new chat that
        // already carries this project's brief and files. It collapses to its icon once you scroll.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New chat") },
                icon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { chatViewModel.startNewChatInProject(projectId) },
                expanded = fabExpanded,
                shape = RoundedCornerShape(20.dp),
                containerColor = accent.container,
                contentColor = accent.onContainer,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 112.dp),
        ) {
            item(key = "context") {
                val brief = project?.instructions?.trim().orEmpty()
                ProjectContext(
                    brief = brief,
                    fileCount = documents.size,
                    readingCount = documents.count { it.status.isBusy },
                    onOpenInstructions = onOpenInstructions,
                    onOpenFiles = onOpenFiles,
                    modifier = Modifier.padding(bottom = Spacing.l),
                )
            }

            item(key = "chats-label") { ProjectSectionHeader("Conversations", count = chats.size) }
            if (chats.isEmpty()) {
                item(key = "chats-empty") { NoChatsNote() }
            } else {
                itemsIndexed(chats, key = { _, it -> it.id }) { index, thread ->
                    ProjectChatRow(
                        thread = thread,
                        colorIndex = colorIndex,
                        shape = groupedItemShape(index, chats.size),
                        onClick = { chatViewModel.openChatFromProject(thread.id) },
                        modifier = Modifier.padding(bottom = GroupedItemGap).animateItem(),
                    )
                }
            }
        }
    }
}

// ── Project context ─────────────────────────────────────────────────────────────────────

/**
 * Instructions and Files as two connected rows — what the project knows, stated plainly. Each row
 * shows what's actually there (the brief's opening line, a file count) and its state reads from the
 * leading badge: tonal when set, outline-quiet when it's still waiting to be filled in.
 */
@Composable
private fun ProjectContext(
    brief: String,
    fileCount: Int,
    readingCount: Int,
    onOpenInstructions: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        ContextRow(
            icon = Icons.Default.AutoAwesome,
            title = "Instructions",
            detail = brief.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: "Add a standing brief — role, tone and rules",
            isSet = brief.isNotBlank(),
            shape = groupedItemShape(0, 2),
            onClick = onOpenInstructions,
        )
        ContextRow(
            icon = Icons.Default.FolderOpen,
            title = "Files",
            detail = when {
                fileCount == 0 -> "Attach reference the model can draw on"
                readingCount > 0 -> "$fileCount attached · reading $readingCount"
                fileCount == 1 -> "1 file attached"
                else -> "$fileCount files attached"
            },
            isSet = fileCount > 0,
            shape = groupedItemShape(1, 2),
            onClick = onOpenFiles,
        )
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    title: String,
    detail: String,
    isSet: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSet) cs.secondaryContainer else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (isSet) cs.onSecondaryContainer else cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.padding(start = Spacing.s).size(20.dp), tint = cs.onSurfaceVariant,
            )
        }
    }
}

// ── Conversations ────────────────────────────────────────────────────────────────────────

/** Nothing here yet — said in one quiet line under the header, pointing at the FAB. */
@Composable
private fun NoChatsNote() {
    Text(
        "No conversations yet. New chats here carry this project's brief and files.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.base, top = Spacing.xs),
    )
}

/** One conversation in the project's connected list — its title leads, recency trails quietly. */
@Composable
private fun ProjectChatRow(
    thread: com.echoflow.data.ChatThread,
    colorIndex: Int,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = projectAccent(colorIndex)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.base, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = accent.onContainer)
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                thread.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                relativeTime(thread.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
    }
}
