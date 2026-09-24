@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.schedules

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.data.ChatMessage
import com.echoflow.data.ScheduleEvent
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleText
import com.echoflow.data.toDraft
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ContextChipRow
import com.echoflow.ui.components.ModelPill
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.screens.chat.AssistPill
import com.echoflow.ui.screens.chat.ErrorBanner
import com.echoflow.ui.screens.chat.MessageBubble
import com.echoflow.ui.screens.chat.ModelPickerSheet
import com.echoflow.ui.screens.chat.SendButton
import com.echoflow.ui.screens.chat.ShapedIconButton
import com.echoflow.ui.theme.Spacing
import java.util.Calendar

/**
 * A schedule is a conversation. It is created by describing it, changed by asking — or by
 * touching the docked card — and every run's answer arrives here as a new message. It looks and
 * behaves like an EchoFlow chat because it is one: same bubbles, same composer, same model pill.
 */
@Composable
fun ScheduleChatScreen(
    scheduleId: String,
    threadId: String?,
    defaultModel: String,
    models: List<Pair<String, String>>,
    localModels: List<Pair<String, String>>,
    onBack: () -> Unit,
    onManageModels: () -> Unit,
    seed: String = "",
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: ScheduleChatViewModel = viewModel(
        key = "schedule-chat-$scheduleId",
        factory = ScheduleChatViewModel.factory(application, scheduleId, threadId, defaultModel),
    )
    val context = LocalContext.current
    val use24h = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val saved by vm.savedState.collectAsState()
    val draft by vm.draftState.collectAsState()
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val running by vm.running.collectAsState()
    val error by vm.error.collectAsState()
    val modelId by vm.model.collectAsState()
    val modelLabel = (models + localModels).firstOrNull { it.first == modelId }?.second
        ?: modelId.substringAfterLast('/').ifBlank { "Choose a model" }

    var input by rememberSaveable(scheduleId) { mutableStateOf(seed) }
    var cardExpanded by rememberSaveable(scheduleId) { mutableStateOf(false) }
    var pickingModel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val shown = draft ?: saved?.toDraft()
    val dirty = draft != null
    val preview = remember(shown, saved) { shown?.let(vm::preview) }
    val nextRun = preview?.getOrNull()?.firstOrNull()?.let { ScheduleText.occurrence(it, saved?.zoneId ?: java.util.TimeZone.getDefault().id, use24h) }

    if (pickingModel) ModelPickerSheet(
        models = models, localModels = localModels, selectedId = modelId,
        onSelect = { vm.selectModel(it); pickingModel = false },
        onManage = { pickingModel = false; onManageModels() },
        onDismiss = { pickingModel = false },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Default.DeleteOutline, null) },
        title = { Text("Delete this schedule?") },
        text = { Text("It stops for good. This conversation and its earlier answers stay in your chats.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScheduleChatTopBar(
                title = shown?.title?.takeIf { it.isNotBlank() } ?: "New schedule",
                subtitle = statusLine(saved, dirty, running, use24h),
                hour = shown?.hour ?: 8, minute = shown?.minute ?: 10,
                saved = saved, running = running,
                onBack = onBack,
                onRunNow = vm::runNowFromMenu,
                onStopRun = vm::stopRun,
                onStatus = vm::setStatusFromMenu,
                onEdit = { cardExpanded = true },
                onDelete = { confirmDelete = true },
            )
            error?.let { ErrorBanner(it, vm::clearError) }

            val listState = rememberLazyListState()
            val itemCount = messages.size + (if (streaming != null) 1 else 0) + (if (running) 1 else 0)
            LaunchedEffect(itemCount, streaming?.length) {
                if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && streaming == null) {
                    ScheduleChatEmpty(hasSchedule = shown != null, onTemplate = { input = it })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.base),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ScheduleMessage(message, saved?.zoneId, use24h, onCopy = { clipboard.setText(AnnotatedString(it)) })
                        }
                        if (running) item(key = "running") { RunningPill(onStop = vm::stopRun) }
                        streaming?.let { text -> item(key = "streaming") { StreamingReply(text) } }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = Spacing.base, vertical = Spacing.m),
            ) {
                AnimatedVisibility(shown != null, enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
                    shown?.let { current ->
                        ScheduleCard(
                            draft = current, isNew = saved == null, dirty = dirty,
                            problem = preview?.exceptionOrNull()?.message,
                            nextRun = nextRun, use24h = use24h,
                            expanded = cardExpanded, onExpandedChange = { cardExpanded = it },
                            onEdit = vm::edit, onSave = { vm.saveFromCard(); cardExpanded = false },
                            onDiscard = vm::discardFromCard,
                            modifier = Modifier.padding(bottom = Spacing.m),
                        )
                    }
                }
                ContextChipRow(Modifier.padding(start = Spacing.s, bottom = Spacing.s)) {
                    ModelPill(modelId = modelId, label = modelLabel, onClick = { pickingModel = true })
                    if (shown == null && messages.isEmpty()) {
                        AssistChip(
                            onClick = { vm.startManualDraft(input); cardExpanded = true },
                            label = { Text("Set up by hand") },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            shape = CircleShape,
                        )
                    }
                }
                Composer(
                    text = input, onText = { input = it },
                    placeholder = if (saved == null) "Describe what should happen, and when…" else "Ask for a change, or about a run…",
                    streaming = streaming != null,
                    onSend = { vm.send(input, modelLabel); input = "" },
                    onStop = vm::stopReply,
                )
            }
        }
    }
}

private fun statusLine(saved: ScheduleTask?, dirty: Boolean, running: Boolean, use24h: Boolean): String = when {
    running -> "Running now"
    saved == null -> "Draft · not scheduled yet"
    saved.status == ScheduleTask.PAUSED -> "Paused" + if (dirty) " · unsaved changes" else ""
    saved.status == ScheduleTask.COMPLETED -> "Ended" + if (dirty) " · unsaved changes" else ""
    dirty -> "Unsaved changes"
    else -> saved.nextRunAt?.let { "Next ${ScheduleText.relative(it, zoneId = saved.zoneId)} · ${ScheduleText.occurrence(it, saved.zoneId, use24h)}" } ?: "Active"
}

@Composable
private fun ScheduleChatTopBar(
    title: String,
    subtitle: String,
    hour: Int,
    minute: Int,
    saved: ScheduleTask?,
    running: Boolean,
    onBack: () -> Unit,
    onRunNow: () -> Unit,
    onStopRun: () -> Unit,
    onStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapedIconButton(
            onClick = onBack, enabled = true, size = 44.dp,
            restShape = MaterialShapes.Cookie4Sided, pressedShape = MaterialShapes.Cookie7Sided,
            container = colors.primaryContainer,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp), tint = colors.onPrimaryContainer) }
        Spacer(Modifier.width(Spacing.m))
        Box(contentAlignment = Alignment.Center) {
            if (running) LoadingIndicator(Modifier.size(44.dp), color = colors.tertiary)
            else ScheduleMark(size = 40.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = if (running) colors.tertiary else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Schedule actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                fun item(label: String, icon: ImageVector, tint: Color? = null, action: () -> Unit) = @Composable {
                    DropdownMenuItem(
                        text = { Text(label, color = tint ?: Color.Unspecified) },
                        leadingIcon = { Icon(icon, null, tint = tint ?: LocalContentColor.current) },
                        onClick = { menu = false; action() },
                    )
                }
                item("Edit details", Icons.Default.EditCalendar, action = onEdit)()
                if (saved != null) {
                    if (running) item("Stop this run", Icons.Default.Stop, action = onStopRun)()
                    else if (saved.status != ScheduleTask.COMPLETED) item("Run now", Icons.Default.NotificationsActive, action = onRunNow)()
                    when (saved.status) {
                        ScheduleTask.ACTIVE -> item("Pause", Icons.Default.Pause) { onStatus(ScheduleTask.PAUSED) }()
                        ScheduleTask.PAUSED -> item("Resume", Icons.Default.PlayArrow) { onStatus(ScheduleTask.ACTIVE) }()
                        else -> item("Restart", Icons.Default.RestartAlt) { onStatus(ScheduleTask.ACTIVE) }()
                    }
                    if (saved.status != ScheduleTask.COMPLETED) item("End schedule", Icons.Default.EventBusy) { onStatus(ScheduleTask.COMPLETED) }()
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                    item("Delete", Icons.Default.DeleteOutline, colors.error, onDelete)()
                }
            }
        }
    }
}

@Composable
private fun ScheduleMessage(message: ChatMessage, zoneId: String?, use24h: Boolean, onCopy: (String) -> Unit) {
    val event = ScheduleEvent.parse(message.scheduleEvent)
    when {
        event == null -> MessageBubble(message = message, onCopy = onCopy)
        event.type == ScheduleEvent.RUN -> RunAnswer(message, event, zoneId, use24h)
        event.type == ScheduleEvent.EDITS -> Column {
            MessageBubble(message = message, onCopy = onCopy)
            FlowRow(Modifier.padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                event.items.forEach { EditChip(it) }
            }
        }
        else -> EventPill(message.content, event.type)
    }
}

/** A run's answer: the chat's answer layout, headed by which occurrence produced it. */
@Composable
private fun RunAnswer(message: ChatMessage, event: ScheduleEvent, zoneId: String?, use24h: Boolean) {
    val colors = MaterialTheme.colorScheme
    val at = event.scheduledAt ?: message.createdAt
    val zone = zoneId ?: java.util.TimeZone.getDefault().id
    val clock = Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply { timeInMillis = at }
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScheduleMark(size = 28.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
                Spacer(Modifier.width(Spacing.s))
                Text("Run · ${ScheduleText.occurrence(at, zone, use24h)}", style = MaterialTheme.typography.labelLarge, color = colors.tertiary)
            }
            Spacer(Modifier.height(Spacing.m))
            RichMarkdown(message.content)
        }
    }
}

@Composable
private fun EditChip(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = Spacing.s, end = Spacing.m, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Lifecycle lines sit centred and quiet, like the system speaking between turns. */
@Composable
private fun EventPill(text: String, type: String) {
    val colors = MaterialTheme.colorScheme
    val problem = type == ScheduleEvent.RUN_FAILED || type == ScheduleEvent.MISSED
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = if (problem) colors.errorContainer else colors.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (problem) colors.onErrorContainer else colors.onSurfaceVariant
                if (problem) Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp), tint = tint)
                else ScheduleMark(size = 16.dp, tint = tint)
                Spacer(Modifier.width(Spacing.s))
                Text(text, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RunningPill(onStop: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(start = Spacing.s, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Running now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun StreamingReply(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp, animated = true)
            Spacer(Modifier.width(Spacing.s))
            Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Spacing.s))
        if (text.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Working on your schedule…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else RichMarkdown(text)
    }
}

@Composable
private fun ScheduleChatEmpty(hasSchedule: Boolean, onTemplate: (String) -> Unit) {
    val now = remember { Calendar.getInstance() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WatchDial(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), Modifier.size(168.dp), sweepSeconds = true)
        Spacer(Modifier.height(Spacing.xl))
        Text(if (hasSchedule) "Talk to this schedule" else "What should happen,\nand when?",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.s))
        Text(
            if (hasSchedule) "Ask for a change — “make it 7 am”, “only weekdays”, “shorter” — or edit the card below."
            else "Say it like you'd tell a friend. EchoFlow sets it up, and you can change anything here or on the card.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasSchedule) {
            Spacer(Modifier.height(Spacing.xl))
            val cs = MaterialTheme.colorScheme
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                ScheduleTemplates.forEachIndexed { index, template ->
                    val (container, onContainer) = when (index % 4) {
                        0 -> cs.primaryContainer to cs.onPrimaryContainer
                        1 -> cs.secondaryContainer to cs.onSecondaryContainer
                        2 -> cs.tertiaryContainer to cs.onTertiaryContainer
                        else -> cs.surfaceContainerHigh to cs.onSurface
                    }
                    AssistPill(template.icon, template.label, container, onContainer) { onTemplate(template.prompt) }
                }
            }
        }
    }
}

internal data class ScheduleTemplate(val icon: ImageVector, val label: String, val prompt: String)

internal val ScheduleTemplates = listOf(
    ScheduleTemplate(Icons.Default.WbSunny, "Morning brief", "Every weekday at 7:30 AM, give me a two-minute brief of the most important tech news"),
    ScheduleTemplate(Icons.Default.NotificationsActive, "Reminder", "Remind me to stretch and drink water every 2 hours"),
    ScheduleTemplate(Icons.Default.EditCalendar, "Weekly review", "Every Sunday at 7 PM, help me review my week with three reflective questions"),
    ScheduleTemplate(Icons.Default.Tune, "Daily word", "Every day at 9 AM, teach me one uncommon English word with an example, for the next 4 weeks"),
)

@Composable
private fun Composer(text: String, onText: (String) -> Unit, placeholder: String, streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text, onValueChange = onText,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            SendButton(enabled = text.isNotBlank() && !streaming, isStreaming = streaming, onStop = onStop) { onSend() }
        }
    }
}
