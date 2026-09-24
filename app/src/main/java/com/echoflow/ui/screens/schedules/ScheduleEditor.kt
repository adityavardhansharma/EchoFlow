@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.echoflow.ui.screens.schedules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.*
import com.echoflow.ui.screens.chat.ModelPickerSheet
import com.echoflow.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun ScheduleEditor(
    original: ScheduleTask?,
    initialModel: String,
    models: List<Pair<String, String>>,
    localModels: List<Pair<String, String>>,
    onBack: () -> Unit,
    onSave: (ScheduleTask) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var draft by remember(original?.id) { mutableStateOf(original?.toDraft()) }
    var chosenModel by remember(original?.id) { mutableStateOf(initialModel) }
    var showModelPicker by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var turns by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var questionsAsked by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val modelLabel = (models + localModels).firstOrNull { it.first == chosenModel }?.second
        ?: chosenModel.substringAfterLast('/').ifBlank { "Choose a model" }
    val scheduleRunner = remember(context) { ScheduleModelRunner(context) }
    val searchReady = scheduleRunner.activeSearchProvider(chosenModel) != null

    if (showModelPicker) ModelPickerSheet(
        models = models, localModels = localModels, selectedId = chosenModel,
        onSelect = { chosenModel = it; draft = draft?.copy(modelId = it); showModelPicker = false },
        onManage = { showModelPicker = false }, onDismiss = { showModelPicker = false },
    )
    if (showDatePicker) {
        val selectedDay = draft?.onceDate?.take(10)?.let { day ->
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(day)?.time }.getOrNull()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDay)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let { epoch ->
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(epoch))
                    draft = draft?.copy(onceDate = day)
                }
                showDatePicker = false
            }) { Text("Use date") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f).padding(start = Spacing.s)) {
                Text(if (original == null) "New schedule" else "Edit schedule",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Describe it, then review the details", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.base, vertical = Spacing.s)) {
            if (original == null) {
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Text("Tell me what you want EchoFlow to do and when. I’ll ask only if a detail matters.",
                            Modifier.padding(start = Spacing.m), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                turns.forEach { (user, message) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(
                            modifier = Modifier.widthIn(max = 340.dp).padding(bottom = Spacing.s),
                            shape = RoundedCornerShape(22.dp),
                            color = if (user) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) { Text(message, Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.m))
                if (draft == null) {
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text(if (turns.isEmpty()) "For example: Every morning, remind me to plan my day"
                            else "Your answer") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                        shape = RoundedCornerShape(22.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                val message = input.trim()
                                if (message.isEmpty() || busy) return@IconButton
                                input = ""
                                turns = turns + (true to message)
                                error = null
                                busy = true
                                scope.launch {
                                    val transcript = turns.joinToString("\n") { (user, text) ->
                                        "${if (user) "User" else "Assistant"}: $text"
                                    }
                                    runCatching {
                                        ScheduleDraftAssistant(scheduleRunner).respond(
                                            chosenModel, transcript, questionsAsked)
                                    }.onSuccess { proposal ->
                                        when (proposal) {
                                            is ScheduleProposal.Question -> {
                                                questionsAsked++
                                                turns = turns + (false to proposal.text)
                                            }
                                            is ScheduleProposal.Ready -> {
                                                draft = proposal.draft.copy(
                                                    instruction = proposal.draft.instruction.ifBlank {
                                                        turns.firstOrNull { it.first }?.second.orEmpty()
                                                    }, modelId = chosenModel,
                                                )
                                            }
                                        }
                                    }.onFailure { cause ->
                                        error = cause.message ?: "Could not reach the model. You can edit the task manually."
                                    }
                                    busy = false
                                }
                            }, enabled = !busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                        },
                    )
                    if (turns.isNotEmpty() && error != null) TextButton(onClick = {
                        draft = ScheduleDraft(title = "New schedule", instruction = turns.first { it.first }.second, modelId = chosenModel)
                        error = null
                    }) { Text("Continue manually") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Spacing.s)) }
            draft?.let { current ->
                Spacer(Modifier.height(Spacing.m))
                Text("Review schedule", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Spacing.s))
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        OutlinedTextField(current.title, { draft = current.copy(title = it) },
                            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(18.dp))
                        OutlinedTextField(current.instruction, { draft = current.copy(instruction = it) },
                            label = { Text("What should EchoFlow do?") }, modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 5, shape = RoundedCornerShape(18.dp))
                        Text("Model", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { showModelPicker = true }) {
                            Text(modelLabel)
                        }
                        HorizontalDivider()
                        Text("Repeat", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (current.unit != ScheduleTask.ONCE) Column(Modifier.weight(1f)) {
                                Text("Every", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.xs))
                                WheelPicker(
                                    values = (1..99).map(Int::toString),
                                    selected = (current.interval - 1).coerceIn(0, 98),
                                    onSelected = { draft = current.copy(interval = it + 1) },
                                    modifier = Modifier.fillMaxWidth(), label = "Interval",
                                )
                            }
                            Column(Modifier.weight(2f)) {
                            Text("Period", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.xs))
                            WheelPicker(
                                values = listOf("Once", "Hours", "Days", "Weeks", "Months"),
                                selected = listOf(ScheduleTask.ONCE, ScheduleTask.HOUR, ScheduleTask.DAY,
                                    ScheduleTask.WEEK, ScheduleTask.MONTH).indexOf(current.unit).coerceAtLeast(0),
                                onSelected = { draft = current.copy(unit = listOf(ScheduleTask.ONCE,
                                    ScheduleTask.HOUR, ScheduleTask.DAY, ScheduleTask.WEEK,
                                    ScheduleTask.MONTH)[it]) },
                                modifier = Modifier.fillMaxWidth(), label = "Period",
                            )
                            }
                        }
                        when (current.unit) {
                            ScheduleTask.ONCE -> {
                                Text("Date", style = MaterialTheme.typography.labelLarge)
                                OutlinedButton(onClick = { showDatePicker = true }) {
                                    Text(current.onceDate.take(10).ifBlank { "Choose date" })
                                }
                            }
                            ScheduleTask.WEEK -> {
                                Text("Day of week", style = MaterialTheme.typography.labelLarge)
                                WheelPicker(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                                    (current.weekdays.firstOrNull() ?: 2) - 1, { draft = current.copy(weekdays = setOf(it + 1)) },
                                    Modifier.fillMaxWidth(), "Day of week")
                            }
                            ScheduleTask.MONTH -> {
                                Text("Day of month", style = MaterialTheme.typography.labelLarge)
                                WheelPicker((1..31).map(Int::toString), current.monthDay - 1,
                                    { draft = current.copy(monthDay = it + 1) }, Modifier.fillMaxWidth(), "Day of month")
                                Text("Months without this day are skipped.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (current.unit in setOf(ScheduleTask.ONCE, ScheduleTask.DAY,
                                ScheduleTask.WEEK, ScheduleTask.MONTH)) {
                            Text("Time of day", style = MaterialTheme.typography.labelLarge)
                            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(if (is24Hour) (0..23).map { "%02d".format(it) }
                                    else (1..12).map(Int::toString),
                                    if (is24Hour) current.hour else (current.hour + 11) % 12,
                                    { index ->
                                        val hour = if (is24Hour) index else
                                            (index + 1) % 12 + (if (current.hour >= 12) 12 else 0)
                                        draft = current.copy(hour = hour,
                                            onceDate = current.onceDate.withClock(hour, current.minute))
                                    },
                                    Modifier.weight(1f), "Hour")
                                Text(":", style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                WheelPicker((0..59).map { "%02d".format(it) }, current.minute,
                                    { draft = current.copy(minute = it,
                                        onceDate = current.onceDate.withClock(current.hour, it)) },
                                    Modifier.weight(1f), "Minute")
                                if (!is24Hour) WheelPicker(listOf("AM", "PM"),
                                    if (current.hour >= 12) 1 else 0,
                                    { index ->
                                        val hour = current.hour % 12 + if (index == 1) 12 else 0
                                        draft = current.copy(hour = hour,
                                            onceDate = current.onceDate.withClock(hour, current.minute))
                                    }, Modifier.weight(.8f), "AM or PM")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                val preview = remember(current, chosenModel, original) {
                    runCatching { current.copy(modelId = chosenModel).resolve(original) }
                }
                val schedulingAgain = original?.status == ScheduleTask.COMPLETED && current.unit == ScheduleTask.ONCE
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
                        Text("What will happen", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "EchoFlow will use $modelLabel to: ${current.instruction.trim()} " +
                                "${if (current.unit == ScheduleTask.ONCE) "This runs once. " else "It repeats every ${current.interval} ${current.unit}${if (current.interval == 1) "" else "s"}. "}" +
                                "The answer will be saved as a conversation and you’ll get a notification when it finishes.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        preview.onSuccess { task ->
                            ScheduleTime.preview(task).forEach { at ->
                                Text("• ${SimpleDateFormat("EEE, d MMM · h:mm a", locale).format(Date(at))}",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }.onFailure {
                            Text(it.message ?: "Choose a future time", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Button(onClick = { preview.getOrNull()?.let(onSave) },
                    enabled = preview.isSuccess && chosenModel.isNotBlank() &&
                        current.title.isNotBlank() && current.instruction.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(Spacing.s))
                    Text(when {
                        original == null -> "Create schedule"
                        schedulingAgain -> "Schedule again"
                        else -> "Save changes"
                    })
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun String.withClock(hour: Int, minute: Int): String = take(10)

@Composable
private fun WheelPicker(
    values: List<String>, selected: Int, onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier, label: String = "Picker",
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, values.lastIndex))
    val currentSelected by rememberUpdatedState(selected)
    val currentCallback by rememberUpdatedState(onSelected)
    val scope = rememberCoroutineScope()
    val centerIndex = wheelCenterIndex(state) ?: selected
    LaunchedEffect(state, values.size) {
        snapshotFlow { wheelCenterIndex(state) }.collect { index ->
            if (index != null && index in values.indices && index != currentSelected) currentCallback(index)
        }
    }
    Surface(modifier.semantics { stateDescription = "$label, ${values[centerIndex.coerceIn(0, values.lastIndex)]}" },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.height(240.dp)) {
            Surface(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = Spacing.xs).height(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .18f)),
            ) {}
            LazyColumn(
                state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 96.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            ) {
                items(values.size) { index ->
                    val distance = abs(index - centerIndex)
                    Box(Modifier.fillMaxWidth().height(48.dp).clickable {
                        scope.launch { state.animateScrollToItem(index) }
                    }, contentAlignment = Alignment.Center) {
                        Text(values[index],
                            style = if (distance == 0) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.titleMedium,
                            color = if (distance == 0) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (distance == 1) .64f else .32f),
                            fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun wheelCenterIndex(state: LazyListState): Int? {
    // LazyList item offsets include the before-content padding. The visible content's
    // center is halfway between these offsets, not viewportSize / 2.
    val middle = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
    return state.layoutInfo.visibleItemsInfo.minByOrNull { item ->
        abs(item.offset + item.size / 2 - middle)
    }?.index
}
