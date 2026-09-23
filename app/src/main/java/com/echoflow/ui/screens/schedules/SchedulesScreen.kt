@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.schedules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.*
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.screens.chat.ModelPickerSheet
import com.echoflow.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SchedulesScreen(
    settingsViewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(context) { ScheduleManager(context.applicationContext) }
    val tasks by manager.tasks.collectAsState(initial = emptyList())
    val selectedModel by settingsViewModel.selectedModel.collectAsState()
    val customModels by settingsViewModel.customModels.collectAsState()
    val customProviderModels by settingsViewModel.customProviderModels.collectAsState()
    val localModels by settingsViewModel.localModels.collectAsState()
    val localEnabled by settingsViewModel.localModelsEnabled.collectAsState()
    val models = remember(customModels, customProviderModels) {
        (DefaultChatModels.BUILT_IN + customModels.map { it.id to it.name } +
            customProviderModels.filterNot { it.isLocalLike }.map { it.id to "${it.group}: ${it.name}" })
            .distinctBy { it.first }
    }
    val localEntries = remember(localModels, localEnabled, customProviderModels) {
        ((if (localEnabled) localModels.map { it.id to it.name } else emptyList()) +
            customProviderModels.filter { it.isLocalLike }.map { it.id to "${it.group}: ${it.name}" })
            .distinctBy { it.first }
    }
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(ScheduleTask.ACTIVE) }
    var editorTask by remember { mutableStateOf<ScheduleTask?>(null) }
    var creating by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var screenError by remember { mutableStateOf<String?>(null) }
    val goBack = { if (creating || editorTask != null) { creating = false; editorTask = null } else onClose() }
    BackHandler { goBack() }

    if (settingsOpen) AlertDialog(
        onDismissRequest = { settingsOpen = false },
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text("About schedules") },
        text = { Text("Each task uses its chosen model when it runs. Local models load on this device. " +
            "Tasks needing current information may also use your configured web search provider. " +
            "Android can delay background work; missed and failed runs appear in each task's history.") },
        confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } },
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when {
            creating || editorTask != null -> ScheduleEditor(
                original = editorTask,
                initialModel = editorTask?.modelId ?: selectedModel,
                models = models, localModels = localEntries,
                onBack = goBack,
                onSave = { draft ->
                    scope.launch {
                        runCatching {
                            val task = draft.toTask(editorTask?.id ?: java.util.UUID.randomUUID().toString())
                            val original = editorTask
                            val sameTiming = original != null && original.unit == draft.unit &&
                                original.interval == draft.interval && original.toPickerSignature() ==
                                task.toPickerSignature()
                            manager.save(task.copy(
                                status = original?.status ?: ScheduleTask.ACTIVE,
                                anchorAt = if (sameTiming) original!!.anchorAt else task.anchorAt,
                                zoneId = if (sameTiming) original!!.zoneId else task.zoneId,
                            ))
                        }.onSuccess { creating = false; editorTask = null; filter = it.status }
                            .onFailure { screenError = it.message ?: "Couldn't save this schedule." }
                    }
                },
            )
            else -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    ScheduleTopBar(tasks.count { it.status == filter }, onClose, { settingsOpen = true })
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        listOf(ScheduleTask.ACTIVE to "Active", ScheduleTask.PAUSED to "Paused",
                            ScheduleTask.COMPLETED to "Completed").forEach { (key, label) ->
                            FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                        }
                    }
                    val visible = tasks.filter { it.status == filter }
                    if (visible.isEmpty()) {
                        ScheduleEmpty(filter, Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.base, Spacing.s, Spacing.base, 112.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            items(visible, key = { it.id }) { task ->
                                ScheduleCard(task, manager,
                                    onEdit = { editorTask = task },
                                    onStatus = { status -> scope.launch {
                                        runCatching { manager.setStatus(task.id, status) }
                                            .onFailure { screenError = it.message ?: "Couldn't update this schedule." }
                                    } },
                                    onRunNow = { scope.launch { manager.runNow(task.id) } },
                                    onStopRun = { scope.launch { manager.stopCurrentRun(task.id) } },
                                    onOpenChat = onOpenChat,
                                )
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { creating = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) { Icon(Icons.Default.Add, "Create schedule") }
            }
        }
    }
    screenError?.let { message -> AlertDialog(
        onDismissRequest = { screenError = null }, title = { Text("Schedule not saved") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { screenError = null }) { Text("OK") } },
    ) }
}

@Composable
private fun ScheduleTopBar(count: Int, onBack: () -> Unit, onSettings: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(Spacing.base),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f).padding(start = Spacing.s)) {
                Text("Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(if (count == 1) "1 task in this view" else "$count tasks in this view",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Schedule settings") }
        }
    }
}

@Composable
private fun ScheduleEmpty(filter: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Default.Schedule, null, Modifier.padding(28.dp).size(36.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(Spacing.l))
        Text(when (filter) {
            ScheduleTask.PAUSED -> "Nothing paused"
            ScheduleTask.COMPLETED -> "No completed schedules"
            else -> "Make time for what matters"
        }, style = MaterialTheme.typography.headlineSmall)
        Text(when (filter) {
            ScheduleTask.ACTIVE -> "Tap + to describe what you want EchoFlow to do and when."
            ScheduleTask.PAUSED -> "Schedules you pause will stay here until you resume them."
            else -> "Stopped and finished schedules will appear here."
        }, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleCard(
    task: ScheduleTask, manager: ScheduleManager,
    onEdit: () -> Unit, onStatus: (String) -> Unit, onRunNow: () -> Unit,
    onStopRun: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val runs by remember(task.id) { manager.runs(task.id) }.collectAsState(initial = emptyList())
    val running = runs.any { it.status == ScheduleRun.RUNNING }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(Icons.Default.Schedule, null, Modifier.padding(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(task.modelId.substringAfterLast('/'), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Task actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) })
                        if (running) DropdownMenuItem(text = { Text("Stop this run") },
                            onClick = { menuOpen = false; onStopRun() },
                            leadingIcon = { Icon(Icons.Default.Stop, null) })
                        if (task.status == ScheduleTask.ACTIVE) DropdownMenuItem(
                            text = { Text("Pause") }, onClick = { menuOpen = false; onStatus(ScheduleTask.PAUSED) },
                            leadingIcon = { Icon(Icons.Default.Pause, null) })
                        if (task.status == ScheduleTask.PAUSED) DropdownMenuItem(
                            text = { Text("Resume") }, onClick = { menuOpen = false; onStatus(ScheduleTask.ACTIVE) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                        if (task.status != ScheduleTask.COMPLETED) DropdownMenuItem(
                            text = { Text("Stop schedule") }, onClick = { menuOpen = false; onStatus(ScheduleTask.COMPLETED) },
                            leadingIcon = { Icon(Icons.Default.Stop, null) })
                        if (task.status == ScheduleTask.COMPLETED && task.unit != ScheduleTask.ONCE) DropdownMenuItem(
                            text = { Text("Restart") }, onClick = { menuOpen = false; onStatus(ScheduleTask.ACTIVE) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                    }
                }
            }
            Spacer(Modifier.height(Spacing.m))
            if (running) {
                Text("Running now", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(Spacing.s))
            }
            Text(task.instruction, style = MaterialTheme.typography.bodyLarge, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.m))
            Text(
                when {
                    task.status == ScheduleTask.PAUSED -> "Paused · ${scheduleLabel(task)}"
                    task.status == ScheduleTask.COMPLETED -> "Completed · ${scheduleLabel(task)}"
                    task.nextRunAt != null -> "Next ${formatTime(task.nextRunAt)} · ${scheduleLabel(task)}"
                    else -> scheduleLabel(task)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(Modifier.padding(vertical = Spacing.m),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide runs" else "Run history (${runs.size})")
                }
                Spacer(Modifier.weight(1f))
                if (task.status != ScheduleTask.COMPLETED) TextButton(onClick = onRunNow) { Text("Run now") }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (runs.isEmpty()) Text("No runs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    runs.take(20).forEach { run ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = run.resultChatId != null) {
                            run.resultChatId?.let(onOpenChat)
                        }.padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(run.scheduledAt), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(run.status.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (run.status == ScheduleRun.FAILED || run.status == ScheduleRun.MISSED)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        run.error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(epoch))

private fun scheduleLabel(task: ScheduleTask): String = when (task.unit) {
    ScheduleTask.ONCE -> "One time"
    else -> "Every ${task.interval} ${task.unit}${if (task.interval == 1) "" else "s"}"
}

private fun ScheduleTask.toPickerSignature(): String {
    val c = Calendar.getInstance(java.util.TimeZone.getTimeZone(zoneId)).apply { timeInMillis = anchorAt }
    return when (unit) {
        ScheduleTask.HOUR -> "hour"
        ScheduleTask.DAY -> "${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}"
        ScheduleTask.WEEK -> "${c.get(Calendar.DAY_OF_WEEK)}:${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}"
        ScheduleTask.MONTH -> "${c.get(Calendar.DAY_OF_MONTH)}:${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}"
        else -> anchorAt.toString()
    }
}
