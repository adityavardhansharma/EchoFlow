@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.schedules

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ScheduleDays
import com.echoflow.data.ScheduleDraft
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleText
import com.echoflow.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Unsaved schedule work, docked above the composer: the mark, the name and the cadence, with
 * Discard and Create/Save. It appears only while something is unsaved, so a saved schedule's
 * conversation stays clear. Tapping it opens [ScheduleEditorSheet]; everything else a schedule
 * can do, editing included, lives in the title bar's menu.
 */
@Composable
internal fun ScheduleCard(
    draft: ScheduleDraft,
    isNew: Boolean,
    problem: String?,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    use24h: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val line = when {
        problem != null -> problem
        !isNew -> "Unsaved · " + summary(draft, use24h)
        else -> summary(draft, use24h)
    }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "${draft.title.ifBlank { "Untitled schedule" }}, $line. Tap to edit." },
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleMark(size = 40.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(draft.title.ifBlank { "Untitled schedule" }, style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) colors.error else colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Spacing.s))
            IconButton(onClick = onDiscard) {
                Icon(Icons.Default.Close, if (isNew) "Discard draft" else "Discard changes", tint = colors.onSurfaceVariant)
            }
            Button(onClick = onSave, enabled = problem == null, contentPadding = PaddingValues(horizontal = Spacing.base)) {
                Text(if (isNew) "Create" else "Save")
            }
        }
    }
}

internal fun summary(draft: ScheduleDraft, use24h: Boolean): String = buildString {
    append(ScheduleText.cadence(draft, use24h))
    ScheduleText.until(draft.endDate)?.let { append(" · ").append(it) }
}

/**
 * Editing a schedule by hand: a full-height sheet with Close and Save/Create in its header.
 * Closing keeps the changes as a draft on the docked card, so nothing is lost and nothing is
 * saved by accident.
 */
@Composable
internal fun ScheduleEditorSheet(
    draft: ScheduleDraft,
    isNew: Boolean,
    dirty: Boolean,
    problem: String?,
    nextRun: String?,
    use24h: Boolean,
    onEdit: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.s, end = Spacing.l, bottom = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text(if (isNew) "New schedule" else "Edit schedule", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Text(
                        when {
                            dirty && problem != null -> problem
                            dirty -> if (isNew) "Not scheduled yet" else "Unsaved changes"
                            else -> "All changes saved"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dirty && problem != null) colors.error else colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Button(onClick = onSave, enabled = dirty && problem == null) { Text(if (isNew) "Create" else "Save") }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).imePadding()
                    .padding(horizontal = Spacing.l).padding(top = Spacing.l, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                ScheduleFields(draft, use24h, nextRun, onEdit)
            }
        }
    }
}

@Composable
private fun ScheduleFields(
    draft: ScheduleDraft,
    use24h: Boolean,
    nextRun: String?,
    onEdit: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
) {
    var sheet by remember { mutableStateOf<String?>(null) }

    Section("Name") {
        PlainField(draft.title, { v -> onEdit { it.copy(title = v) } }, "Morning brief", singleLine = true)
    }
    Section("What should happen") {
        PlainField(draft.instruction, { v -> onEdit { it.copy(instruction = v) } },
            "Summarize the top tech news in five bullets…", singleLine = false)
    }

    Section("Repeats") {
        RepeatSelector(draft.unit) { unit ->
            onEdit {
                val today = Calendar.getInstance()
                it.copy(
                    unit = unit,
                    weekdays = if (unit == ScheduleTask.WEEK && it.weekdays.isEmpty()) setOf(today.get(Calendar.DAY_OF_WEEK)) else it.weekdays,
                    onceDate = if (unit == ScheduleTask.ONCE && it.onceDate.isBlank()) isoDay(today.timeInMillis) else it.onceDate,
                )
            }
        }
        if (draft.unit != ScheduleTask.ONCE) {
            ValueRow(Icons.Default.Repeat, "Interval", intervalLabel(draft.unit, draft.interval)) { sheet = "interval" }
        }
        when (draft.unit) {
            ScheduleTask.WEEK -> {
                DayToggles(draft.weekdays) { days -> onEdit { it.copy(weekdays = days) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    listOf("Weekdays" to ScheduleDays.WEEKDAYS, "Weekends" to ScheduleDays.WEEKEND, "Every day" to ScheduleDays.EVERY_DAY)
                        .forEach { (label, days) ->
                            FilterChip(selected = draft.weekdays == days, onClick = { onEdit { it.copy(weekdays = days) } }, label = { Text(label) })
                        }
                }
            }
            ScheduleTask.MONTH -> ValueRow(Icons.Default.CalendarMonth, "Day of month", "The ${ScheduleText.ordinal(draft.monthDay)}") { sheet = "monthDay" }
            ScheduleTask.ONCE -> ValueRow(Icons.Default.CalendarMonth, "Date", dateLabel(draft.onceDate) ?: "Pick a date") { sheet = "date" }
        }
    }

    Section("Time") {
        if (draft.unit == ScheduleTask.HOUR) {
            ValueRow(Icons.Default.AccessTime, "Minute", ":%02d past each hour".format(draft.minute)) { sheet = "minute" }
        } else {
            ValueRow(Icons.Default.AccessTime, "At", ScheduleText.time(draft.hour, draft.minute, use24h)) { sheet = "time" }
        }
        if (nextRun != null) {
            Text("Next run · $nextRun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs))
        }
    }

    Section("Ends") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilterChip(selected = draft.endDate == null, onClick = { onEdit { it.copy(endDate = null) } }, label = { Text("Never") })
            val spans = listOf("1 week" to (Calendar.WEEK_OF_YEAR to 1), "4 weeks" to (Calendar.WEEK_OF_YEAR to 4),
                "3 months" to (Calendar.MONTH to 3), "1 year" to (Calendar.YEAR to 1))
            spans.forEach { (label, span) ->
                val end = endAfter(span.first, span.second)
                FilterChip(selected = draft.endDate == end, onClick = { onEdit { it.copy(endDate = end) } }, label = { Text(label) })
            }
            val custom = draft.endDate != null && spans.none { (_, span) -> endAfter(span.first, span.second) == draft.endDate }
            FilterChip(selected = custom, onClick = { sheet = "end" },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(FilterChipDefaults.IconSize)) },
                label = { Text(if (custom) dateLabel(draft.endDate!!)?.let { "Until $it" } ?: "Pick date" else "Pick date") })
        }
    }

    when (sheet) {
        "time" -> ScheduleTimeSheet(draft.hour, draft.minute, use24h, onDismiss = { sheet = null }) { h, m ->
            onEdit { it.copy(hour = h, minute = m) }; sheet = null
        }
        "minute" -> ScheduleNumberSheet("Minute past the hour", 0..59, draft.minute, { "%02d".format(it) }, { ":%02d".format(it) },
            onDismiss = { sheet = null }) { m -> onEdit { it.copy(minute = m) }; sheet = null }
        "interval" -> ScheduleNumberSheet("Repeat every", 1..60, draft.interval, Int::toString, { n -> intervalLabel(draft.unit, n) },
            onDismiss = { sheet = null }) { n -> onEdit { it.copy(interval = n) }; sheet = null }
        "monthDay" -> ScheduleNumberSheet("Day of the month", 1..31, draft.monthDay, Int::toString, { "The ${ScheduleText.ordinal(it)}" },
            onDismiss = { sheet = null }) { d -> onEdit { it.copy(monthDay = d) }; sheet = null }
        "date" -> DateSheet(draft.onceDate, onDismiss = { sheet = null }) { d -> onEdit { it.copy(onceDate = d) }; sheet = null }
        "end" -> DateSheet(draft.endDate.orEmpty(), onDismiss = { sheet = null }) { d -> onEdit { it.copy(endDate = d) }; sheet = null }
    }
}

private fun intervalLabel(unit: String, n: Int): String {
    val name = when (unit) { ScheduleTask.HOUR -> "hour"; ScheduleTask.DAY -> "day"; ScheduleTask.WEEK -> "week"; else -> "month" }
    return if (n == 1) "Every $name" else "Every $n ${name}s"
}

/** A labelled group of fields; every section shares the same label style and inner spacing. */
@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs))
        content()
    }
}

/**
 * Once / Hour / Day / Week / Month as one segmented track. Equal segments and short labels with no
 * check glyph, so no label is ever clipped, even on a narrow phone.
 */
@Composable
private fun RepeatSelector(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(ScheduleTask.ONCE to "Once", ScheduleTask.HOUR to "Hour", ScheduleTask.DAY to "Day",
        ScheduleTask.WEEK to "Week", ScheduleTask.MONTH to "Month")
    Surface(shape = CircleShape, color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.xs).selectableGroup()) {
            options.forEach { (value, label) ->
                val on = value == selected
                val container by animateColorAsState(if (on) colors.primary else Color.Transparent, label = "repeat-container")
                val content by animateColorAsState(if (on) colors.onPrimary else colors.onSurfaceVariant, label = "repeat-content")
                Box(
                    Modifier.weight(1f).height(44.dp).clip(CircleShape).background(container)
                        .selectable(selected = on, role = Role.RadioButton) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Seven round day toggles. The last chosen day can't be cleared. */
@Composable
private fun DayToggles(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ScheduleDays.ORDER.forEach { day ->
            val on = day in selected
            val container by animateColorAsState(if (on) colors.primary else colors.surfaceContainerHighest, label = "day-container")
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(container)
                    .clickable(role = Role.Checkbox) {
                        val next = if (on) selected - day else selected + day
                        if (next.isNotEmpty()) onChange(next)
                    }
                    .semantics { contentDescription = ScheduleText.longDay(day); this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(ScheduleText.narrowDay(day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = if (on) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ValueRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = Spacing.base, end = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = colors.primary)
            Spacer(Modifier.width(Spacing.m))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.m))
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean) {
    TextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, maxLines = if (singleLine) 1 else 6,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DateSheet(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val today = remember { utc.parse(isoDay(System.currentTimeMillis()))!!.time }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = runCatching { utc.parse(current)?.time }.getOrNull(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(utc.format(Date(it))) } ?: onDismiss() }) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.xs)); Text("Use date")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun isoDay(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(at))

/** The same inclusive rule as the tools: "for 4 weeks" ends the day before four weeks from today. */
private fun endAfter(field: Int, amount: Int): String = Calendar.getInstance().run {
    add(field, amount)
    add(Calendar.DAY_OF_YEAR, -1)
    isoDay(timeInMillis)
}

private fun dateLabel(date: String): String? = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date) ?: return null
    val sameYear = Calendar.getInstance().apply { time = parsed }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    SimpleDateFormat(if (sameYear) "EEE, MMM d" else "EEE, MMM d, yyyy", Locale.getDefault()).format(parsed)
}.getOrNull()
