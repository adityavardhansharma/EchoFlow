@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.theme.Spacing

/**
 * The Projects hub — a fullscreen surface opened from the drawer. It hosts two levels: the list of
 * projects, and one project's home (its chats, instructions and documents). Which is shown is driven
 * by [ChatViewModel.openProjectId], so back steps home → list → closed. This is Option B ("a
 * launchpad, not a container"): a project home surfaces its chats, and points to instructions and
 * documents as focused sub-screens rather than cramming everything onto one page.
 */
@Composable
fun ProjectsHubScreen(chatViewModel: ChatViewModel) {
    val openProjectId by chatViewModel.openProjectId.collectAsState()

    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "projects-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        val projectId = openProjectId
        if (projectId == null) {
            BackHandler { chatViewModel.closeProjectsHub() }
            ProjectsListContent(chatViewModel)
        } else {
            ProjectHomeScreen(chatViewModel, projectId, onBack = { chatViewModel.closeProjectHome() })
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────────────

/** Shared fullscreen header: a down-chevron to leave, a display-voice title, optional trailing. */
@Composable
private fun ProjectTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = Spacing.xs, end = Spacing.s, top = Spacing.s, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Back", Modifier.size(26.dp)) }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.s))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) trailing()
        }
    }
}

// ── Projects list ──────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectsListContent(chatViewModel: ChatViewModel) {
    val projects by chatViewModel.projects.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        ProjectNameDialog(
            title = "New project",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                chatViewModel.createProject(name) { id -> chatViewModel.openProjectHome(id) }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ProjectTopBar(
                title = "Projects",
                subtitle = projects.size.takeIf { it > 0 }?.let { if (it == 1) "1 project" else "$it projects" },
                onBack = { chatViewModel.closeProjectsHub() },
            )
            if (projects.isEmpty()) {
                ProjectsEmptyState(onCreate = { showCreate = true }, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base, Spacing.s, Spacing.base, 104.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectListCard(project) { chatViewModel.openProjectHome(project.id) }
                    }
                }
            }
        }
        if (projects.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Add, "New project") }
        }
    }
}

@Composable
private fun ProjectListCard(project: Project, onClick: () -> Unit) {
    val accent = projectAccent(project.colorIndex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentFolder(accent.container, accent.onContainer, 48.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val hint = project.instructions.trim().ifBlank { "Empty project · tap to set up" }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The project identity mark — a folder glyph on the project's accent, as a soft squircle. */
@Composable
private fun AccentFolder(container: Color, onContainer: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(size * 0.46f), tint = onContainer)
    }
}

// ── Project home ───────────────────────────────────────────────────────────────────────

private enum class ProjectDetail { None, Instructions, Files }

private fun restoreProjectDetail(saved: String): ProjectDetail =
    ProjectDetail.entries.firstOrNull { it.name == saved } ?: ProjectDetail.None

@Composable
private fun ProjectHomeScreen(chatViewModel: ChatViewModel, projectId: String, onBack: () -> Unit) {
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
        p != null && detail == ProjectDetail.Files -> ProjectFilesScreen(
            documents = documents,
            onAdd = { uri -> chatViewModel.addProjectDocument(projectId, uri) },
            onRemove = { chatViewModel.removeProjectDocument(it) },
            onBack = { detail = ProjectDetail.None },
        )
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
    val accent = projectAccent(project?.colorIndex ?: 0)

    if (showRename && project != null) {
        ProjectNameDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Save",
            onDismiss = { showRename = false },
            onConfirm = { showRename = false; chatViewModel.renameProject(projectId, it) },
        )
    }
    if (showDelete && project != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
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

    Column(Modifier.fillMaxSize()) {
        ProjectTopBar(
            title = project?.name ?: "Project",
            onBack = onBack,
            leading = { AccentFolder(accent.container, accent.onContainer, 32.dp) },
            trailing = {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; showRename = true })
                        DropdownMenuItem(text = { Text("Colour") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menuOpen = false; showColor = true })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuOpen = false; showDelete = true })
                    }
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.base, Spacing.base, Spacing.base, Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            // Context strip: two summary cards that point to focused sub-screens. Setup is
            // glanceable, never in the way — the "launchpad not container" idea.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ContextCard(
                        icon = Icons.Default.Description,
                        title = "Instructions",
                        subtitle = project?.instructions?.trim().orEmpty().ifBlank { "Add instructions" },
                        filled = !project?.instructions?.trim().isNullOrBlank(),
                        onClick = onOpenInstructions,
                        modifier = Modifier.weight(1f),
                    )
                    ContextCard(
                        icon = Icons.Default.FolderOpen,
                        title = "Files",
                        subtitle = when (documents.size) {
                            0 -> "Add files"
                            1 -> "1 file attached"
                            else -> "${documents.size} files attached"
                        },
                        filled = documents.isNotEmpty(),
                        onClick = onOpenFiles,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Surface(
                    onClick = { chatViewModel.startNewChatInProject(projectId) },
                    shape = RoundedCornerShape(20.dp),
                    color = accent.container,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(Spacing.base),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Icon(Icons.Default.Add, null, tint = accent.onContainer)
                        Text(
                            "New chat in this project",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent.onContainer,
                        )
                    }
                }
            }

            item {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                )
            }

            if (chats.isEmpty()) {
                item {
                    Text(
                        "No conversations yet. Start one above and it will live in this project.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.s, horizontal = Spacing.xs),
                    )
                }
            } else {
                items(chats, key = { it.id }) { thread ->
                    ProjectChatRow(thread) { chatViewModel.openChatFromProject(thread.id) }
                }
            }
        }
    }
}

@Composable
private fun ContextCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(120.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (filled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null, Modifier.size(18.dp),
                    tint = if (filled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProjectChatRow(thread: com.echoflow.data.ChatThread, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(thread.updatedAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Instructions editor ────────────────────────────────────────────────────────────────

@Composable
private fun ProjectInstructionsScreen(project: Project, onSave: (String) -> Unit, onBack: () -> Unit) {
    // rememberSaveable, not remember: autosave only fires on leave, so a config change or process
    // death mid-edit must not discard the in-progress text and snap back to the persisted value.
    var text by rememberSaveable(project.id) { mutableStateOf(project.instructions) }
    val leave = { onSave(text.trim()); onBack() }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize()) {
        ProjectTopBar(
            title = "Instructions",
            onBack = leave,
            trailing = { TextButton(onClick = leave) { Text("Save") } },
        )
        Column(Modifier.fillMaxSize().padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(
                "Given to the model on every message in this project — a standing brief for tone, role and rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "e.g. You are helping me write a noir mystery. Keep a wry, hard-boiled tone and remember the characters and timeline in the attached files.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxSize().imePadding(),
                )
            }
            Text(
                "Changes save when you leave",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

// ── Files ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectFilesScreen(
    documents: List<ProjectDocument>,
    onAdd: (android.net.Uri) -> Unit,
    onRemove: (ProjectDocument) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAdd(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ProjectTopBar(title = "Files", onBack = onBack)
            Text(
                "Text files are read and used as background knowledge in this project's chats.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.s),
            )
            if (documents.isEmpty()) {
                FilesEmptyState(onAdd = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base, Spacing.s, Spacing.base, 104.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(documents, key = { it.id }) { doc -> DocumentRow(doc) { onRemove(doc) } }
                }
            }
        }
        if (documents.isNotEmpty()) {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Add, "Add file") }
        }
    }
}

@Composable
private fun DocumentRow(document: ProjectDocument, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(document.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(formatBytes(document.sizeBytes))
                        if (!document.hasText) append(" · not read as text")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (document.hasText) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, "Remove file", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ── Empty states & dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun ProjectsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Default.CreateNewFolder,
        title = "No projects yet",
        body = "Group related chats, give them a shared instruction, and attach reference files the model can draw on.",
        actionLabel = "New project",
        actionIcon = Icons.Default.Add,
        onAction = onCreate,
        modifier = modifier,
    )
}

@Composable
private fun FilesEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Default.Description,
        title = "No files yet",
        body = "Add text files — notes, briefs, transcripts — and their contents become background knowledge for this project.",
        actionLabel = "Add file",
        actionIcon = Icons.Default.Add,
        onAction = onAdd,
        modifier = modifier,
    )
}

/** Shared empty state: upper-biased (not dead-centred in a void), display-voice title, real action. */
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.fillMaxHeight(0.24f))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.l))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = Spacing.l)) {
            Icon(actionIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(actionLabel)
        }
    }
}

@Composable
private fun ProjectNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Project name") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifBlank { "Untitled project" }) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProjectColorDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project colour") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                (0 until PROJECT_ACCENT_COUNT).forEach { index ->
                    val accent = projectAccent(index)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.solid)
                            .then(
                                if (index == selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                            .clickable { onPick(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = accent.onContainer)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
