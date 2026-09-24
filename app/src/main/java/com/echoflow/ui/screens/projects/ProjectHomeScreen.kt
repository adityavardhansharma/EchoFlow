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
import androidx.compose.material3.MediumExtendedFloatingActionButton
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
        subtitle = project?.let { "Updated ${DateUtils.getRelativeTimeSpanString(it.updatedAt)}" },
        titleLeading = { ProjectMedallion(colorIndex, size = 40.dp) },
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
            MediumExtendedFloatingActionButton(
                text = { Text("New chat") },
                icon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { chatViewModel.startNewChatInProject(projectId) },
                expanded = fabExpanded,
                containerColor = accent.container,
                contentColor = accent.onContainer,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 128.dp),
        ) {
            item(key = "context-label") { ProjectSectionHeader("Project context") }
            item(key = "context") {
                val brief = project?.instructions?.trim().orEmpty()
                ContextPair(
                    brief = brief,
                    fileCount = documents.size,
                    readingCount = documents.count { it.status.isBusy },
                    onOpenInstructions = onOpenInstructions,
                    onOpenFiles = onOpenFiles,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                )
            }

            item(key = "chats-label") { ProjectSectionHeader("Conversations", count = chats.size) }
            if (chats.isEmpty()) {
                item(key = "chats-empty") { NoChatsCard(colorIndex) }
            } else {
                itemsIndexed(chats, key = { _, it -> it.id }) { index, thread ->
                    ProjectChatRow(
                        thread = thread,
                        colorIndex = colorIndex,
                        shape = groupedItemShape(index, chats.size),
                        onClick = { chatViewModel.openChatFromProject(thread.id) },
                        modifier = Modifier.padding(bottom = 3.dp).animateItem(),
                    )
                }
            }
        }
    }
}

// ── Context pair ─────────────────────────────────────────────────────────────────────────

/**
 * Instructions and Files as a *connected* pair — two equal tiles whose inner corners tighten
 * toward each other, the M3 Expressive button-group move applied to cards. Each tile shows what's
 * actually there (a preview of the brief, a file count) instead of a generic "tap to set up".
 */
@Composable
private fun ContextPair(
    brief: String,
    fileCount: Int,
    readingCount: Int,
    onOpenInstructions: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ContextTile(
            icon = Icons.Default.AutoAwesome,
            title = "Instructions",
            body = brief.ifBlank { "Give every chat a standing brief — role, tone and rules." },
            bodyIsPreview = brief.isNotBlank(),
            footer = if (brief.isBlank()) "Add a brief" else "Edit brief",
            filled = brief.isNotBlank(),
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            onClick = onOpenInstructions,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ContextTile(
            icon = Icons.Default.FolderOpen,
            title = "Files",
            body = when {
                fileCount == 0 -> "Attach reference the model can draw on."
                readingCount > 0 -> "$fileCount attached · reading $readingCount"
                fileCount == 1 -> "1 file attached"
                else -> "$fileCount files attached"
            },
            bodyIsPreview = false,
            footer = if (fileCount == 0) "Add files" else "Manage files",
            filled = fileCount > 0,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
            onClick = onOpenFiles,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ContextTile(
    icon: ImageVector,
    title: String,
    body: String,
    bodyIsPreview: Boolean,
    footer: String,
    filled: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (filled) cs.secondaryContainer else cs.surfaceContainerHigh,
        contentColor = if (filled) cs.onSecondaryContainer else cs.onSurface,
        modifier = modifier,
    ) {
        Column(Modifier.heightIn(min = 164.dp).padding(Spacing.base)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (filled) cs.onSecondaryContainer.copy(alpha = 0.12f) else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (filled) cs.onSecondaryContainer else cs.primary)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Text(
                if (bodyIsPreview) "“$body”" else body,
                style = MaterialTheme.typography.bodySmall,
                color = if (filled) cs.onSecondaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.weight(1f).heightIn(min = Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (filled) cs.onSecondaryContainer else cs.primary,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    Modifier.size(18.dp), tint = if (filled) cs.onSecondaryContainer else cs.primary,
                )
            }
        }
    }
}

// ── Conversations ────────────────────────────────────────────────────────────────────────

@Composable
private fun NoChatsCard(colorIndex: Int) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProjectMedallion(colorIndex, size = 56.dp, glyph = Icons.Outlined.ChatBubbleOutline)
            Text(
                "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.base),
            )
            Text(
                "Tap New chat to start one. It lives here and carries this project's brief and files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(accent.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp), tint = accent.onContainer)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(thread.updatedAt).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
