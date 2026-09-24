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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Edit
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
import com.echoflow.ui.theme.Spacing
import java.util.Calendar
import java.util.TimeZone

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
    var editing by rememberSaveable(scheduleId) { mutableStateOf(false) }
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
    val startEditing = {
        if (shown == null) vm.startManualDraft(input)
        editing = true
    }
    if (editing) {
        shown?.let { current ->
            ScheduleEditorSheet(
                draft = current, isNew = saved == null, dirty = dirty,
                problem = preview?.exceptionOrNull()?.message, nextRun = nextRun, use24h = use24h,
                onEdit = vm::edit,
                onSave = { vm.saveFromCard(); editing = false },
                onDismiss = { editing = false },
            )
        }
    }
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
                saved = saved, running = running, hasDraft = draft != null,
                onBack = onBack,
                onRunNow = vm::runNowFromMenu,
                onStopRun = vm::stopRun,
                onStatus = vm::setStatusFromMenu,
                onEdit = startEditing,
                onDiscard = vm::discardFromCard,
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
                        if (running) item(key = "running") { RunningReply(onStop = vm::stopRun) }
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
                            status = saved?.status, use24h = use24h,
                            onOpen = { editing = true },
                            onSave = vm::saveFromCard,
                            onDiscard = vm::discardFromCard,
                            onResume = { vm.setStatusFromMenu(ScheduleTask.ACTIVE) },
                            modifier = Modifier.padding(bottom = Spacing.m),
                        )
                    }
                }
                ContextChipRow(Modifier.padding(start = Spacing.s, bottom = Spacing.s)) {
                    ModelPill(modelId = modelId, label = modelLabel, onClick = { pickingModel = true })
                    if (shown == null) {
                        AssistChip(
                            onClick = startEditing,
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
    dirty -> "Unsaved changes"
    saved.status == ScheduleTask.PAUSED -> "Paused"
    saved.status == ScheduleTask.COMPLETED -> "Ended"
    else -> saved.nextRunAt?.let { "Next run " + ScheduleText.upcoming(it, saved.zoneId, use24h) } ?: "Active"
}

/**
 * Back, the schedule's mark, its name and state — and every action in one menu: edit, run now,
 * pause or resume, end, delete. Nothing needs the card to be opened first.
 */
@Composable
private fun ScheduleChatTopBar(
    title: String,
    subtitle: String,
    saved: ScheduleTask?,
    running: Boolean,
    hasDraft: Boolean,
    onBack: () -> Unit,
    onRunNow: () -> Unit,
    onStopRun: () -> Unit,
    onStatus: (String) -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Spacing.s, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (running) LoadingIndicator(Modifier.size(40.dp), color = colors.primary)
            else ScheduleMark(size = 40.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = if (running) colors.primary else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                item(if (saved == null && !hasDraft) "Set up by hand" else "Edit schedule", Icons.Outlined.Edit, action = onEdit)()
                if (saved == null && hasDraft) item("Discard draft", Icons.Default.Close, action = onDiscard)()
                if (saved != null) {
                    if (running) item("Stop this run", Icons.Default.Stop, action = onStopRun)()
                    else if (saved.status != ScheduleTask.COMPLETED) item("Run now", Icons.Default.Bolt, action = onRunNow)()
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

/**
 * Every answer — a run's or a reply's — is an ordinary EchoFlow message under the EchoFlow mark.
 * A run is introduced by a thin timeline line saying which occurrence produced it; lifecycle
 * events (paused, resumed, ended, missed) are the same quiet line, never a bubble.
 */
@Composable
private fun ScheduleMessage(message: ChatMessage, zoneId: String?, use24h: Boolean, onCopy: (String) -> Unit) {
    val event = ScheduleEvent.parse(message.scheduleEvent)
    val zone = zoneId ?: TimeZone.getDefault().id
    when {
        event == null -> MessageBubble(message = message, onCopy = onCopy)
        event.type == ScheduleEvent.RUN -> Column(Modifier.fillMaxWidth()) {
            val at = event.scheduledAt ?: message.createdAt
            TimelineLine("Scheduled run · ${stamp(at, zone, use24h)}") { tint -> ScheduleMark(size = 16.dp, tint = tint) }
            Spacer(Modifier.height(Spacing.m))
            MessageBubble(message = message, onCopy = onCopy)
        }
        event.type == ScheduleEvent.EDITS -> Column(Modifier.fillMaxWidth()) {
            MessageBubble(message = message, onCopy = onCopy)
            FlowRow(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                event.items.forEach { EditChip(it) }
            }
        }
        else -> {
            val problem = event.type == ScheduleEvent.RUN_FAILED || event.type == ScheduleEvent.MISSED
            TimelineLine(eventLabel(message, event, zone, use24h), problem) { tint ->
                val icon = eventIcon(event.type)
                if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = tint) else ScheduleMark(size = 16.dp, tint = tint)
            }
        }
    }
}

private fun eventIcon(type: String): ImageVector? = when (type) {
    ScheduleEvent.PAUSED -> Icons.Default.Pause
    ScheduleEvent.RESUMED -> Icons.Default.PlayArrow
    ScheduleEvent.ENDED -> Icons.Default.EventBusy
    ScheduleEvent.CREATED -> Icons.Default.EventAvailable
    ScheduleEvent.SAVED -> Icons.Default.Check
    ScheduleEvent.RUN_STARTED -> Icons.Default.Bolt
    ScheduleEvent.RUN_FAILED, ScheduleEvent.MISSED -> Icons.Default.ErrorOutline
    else -> null
}

/** Short words for states that need no explanation; the stored sentence stays for the model. */
private fun eventLabel(message: ChatMessage, event: ScheduleEvent, zone: String, use24h: Boolean): String = when (event.type) {
    ScheduleEvent.PAUSED -> "Paused · ${stamp(message.createdAt, zone, use24h)}"
    ScheduleEvent.ENDED -> "Ended · ${stamp(message.createdAt, zone, use24h)}"
    else -> message.content.trim().removeSuffix(".")
}

/** "2:41 AM" for today, otherwise "Thu, Sep 24 · 2:41 AM". */
private fun stamp(at: Long, zone: String, use24h: Boolean): String {
    val tz = TimeZone.getTimeZone(zone)
    val then = Calendar.getInstance(tz).apply { timeInMillis = at }
    val today = Calendar.getInstance(tz)
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) ScheduleText.time(then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE), use24h)
    else ScheduleText.occurrence(at, zone, use24h)
}

/** A hairline, a small glyph and a few words: the schedule noting something between turns. */
@Composable
private fun TimelineLine(text: String, problem: Boolean = false, icon: @Composable (Color) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (problem) colors.error else colors.onSurfaceVariant
    val rule = colors.outlineVariant.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = rule)
        Row(Modifier.padding(horizontal = Spacing.m).widthIn(max = 300.dp), verticalAlignment = Alignment.CenterVertically) {
            icon(tint)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = tint, textAlign = TextAlign.Center)
        }
        HorizontalDivider(Modifier.weight(1f), color = rule)
    }
}

@Composable
private fun EditChip(label: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = colors.secondaryContainer) {
        Row(Modifier.padding(start = Spacing.s, end = Spacing.m, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = colors.onSecondaryContainer)
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer)
        }
    }
}

/** A run in progress looks like EchoFlow writing a reply, with a way to stop it. */
@Composable
private fun RunningReply(onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp, animated = true)
            Spacer(Modifier.width(Spacing.s))
            Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(28.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Running this schedule…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onStop) { Text("Stop") }
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

/**
 * Before the first message: a quiet invitation, not a showpiece. Scrolls rather than overflowing
 * when the keyboard or the docked card leaves little room.
 */
@Composable
private fun ScheduleChatEmpty(hasSchedule: Boolean, onTemplate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = Spacing.xl, vertical = Spacing.base),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ScheduleMark(size = 64.dp, tint = cs.onSecondaryContainer, container = cs.secondaryContainer)
            Spacer(Modifier.height(Spacing.l))
            Text(if (hasSchedule) "Talk to this schedule" else "What should happen, and when?",
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = cs.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                if (hasSchedule) "Ask for a change in your own words, or tap the card below to edit it."
                else "Say it like you'd tell a friend. EchoFlow sets it up, and you can fine-tune it on the card.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                (if (hasSchedule) ScheduleSuggestions else ScheduleTemplates).forEach { template ->
                    AssistPill(template.icon, template.label, cs.surfaceContainerHigh, cs.onSurface) { onTemplate(template.prompt) }
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

/** Changes people usually ask of an existing schedule; each fills the composer to edit before sending. */
private val ScheduleSuggestions = listOf(
    ScheduleTemplate(Icons.Default.Schedule, "Move it to 7 am", "Move it to 7 am"),
    ScheduleTemplate(Icons.Default.EditCalendar, "Only on weekdays", "Only run it on weekdays"),
    ScheduleTemplate(Icons.Default.Tune, "Keep it shorter", "Keep each answer shorter"),
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
