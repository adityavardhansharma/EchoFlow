@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.schedules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.echoflow.ui.screens.settings.ConnectedToggleRow
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The schedule, docked above the composer like a live object in the conversation.
 *
 * Collapsed it is one line — a watch mark showing the time, the name and the cadence — plus Save
 * the moment anything differs from what is scheduled. Expanded it is the whole schedule, editable
 * by hand. Model tool calls land here as they happen, so chat and card never disagree.
 */
@Composable
internal fun ScheduleCard(
    draft: ScheduleDraft,
    isNew: Boolean,
    dirty: Boolean,
    problem: String?,
    nextRun: String?,
    use24h: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val canSave = dirty && problem == null
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse schedule" else "Edit schedule") {
                    onExpandedChange(!expanded)
                }.padding(start = Spacing.m, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScheduleMark(size = 40.dp, tint = colors.onSecondaryContainer, container = colors.secondaryContainer)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(draft.title.ifBlank { "Untitled schedule" }, style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            problem != null && dirty -> problem
                            else -> summary(draft, use24h)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (problem != null && dirty) colors.error else colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(dirty, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isNew) IconButton(onClick = onDiscard) { Icon(Icons.AutoMirrored.Filled.Undo, "Discard changes") }
                        Button(onClick = onSave, enabled = canSave, contentPadding = PaddingValues(horizontal = Spacing.base)) {
                            Text(if (isNew) "Create" else "Save")
                        }
                    }
                }
                if (!dirty) {
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "card-chevron")
                    Icon(Icons.Default.ExpandMore, null, Modifier.padding(Spacing.s).rotate(turn), tint = colors.onSurfaceVariant)
                }
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                        .padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.base),
                ) {
                    CardEditor(draft, use24h, nextRun, onEdit)
                    if (!dirty) TextButton(onClick = { onExpandedChange(false) }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.ExpandLess, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Done")
                    }
                }
            }
        }
    }
}

internal fun summary(draft: ScheduleDraft, use24h: Boolean): String = buildString {
    append(ScheduleText.cadence(draft, use24h))
    ScheduleText.until(draft.endDate)?.let { append(" · ").append(it) }
}

@Composable
private fun CardEditor(
    draft: ScheduleDraft,
    use24h: Boolean,
    nextRun: String?,
    onEdit: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
) {
    var sheet by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    FieldLabel("Name")
    PlainField(draft.title, { v -> onEdit { it.copy(title = v) } }, "Morning brief", singleLine = true)
    FieldLabel("What should happen")
    PlainField(draft.instruction, { v -> onEdit { it.copy(instruction = v) } },
        "Summarize the top tech news in five bullets…", singleLine = false)

    FieldLabel("Repeat")
    ConnectedToggleRow(
        options = listOf(ScheduleTask.ONCE to "Once", ScheduleTask.HOUR to "Hour", ScheduleTask.DAY to "Day",
            ScheduleTask.WEEK to "Week", ScheduleTask.MONTH to "Month"),
        selected = draft.unit,
        onSelect = { unit ->
            onEdit {
                val today = Calendar.getInstance()
                it.copy(
                    unit = unit,
                    weekdays = if (unit == ScheduleTask.WEEK && it.weekdays.isEmpty()) setOf(today.get(Calendar.DAY_OF_WEEK)) else it.weekdays,
                    onceDate = if (unit == ScheduleTask.ONCE && it.onceDate.isBlank()) isoDay(today.timeInMillis) else it.onceDate,
                )
            }
        },
    )
    if (draft.unit != ScheduleTask.ONCE) {
        val unitName = when (draft.unit) { ScheduleTask.HOUR -> "hour"; ScheduleTask.DAY -> "day"; ScheduleTask.WEEK -> "week"; else -> "month" }
        ValueRow(Icons.Default.Repeat, "Every", if (draft.interval == 1) "Every $unitName" else "Every ${draft.interval} ${unitName}s") { sheet = "interval" }
    }
    when (draft.unit) {
        ScheduleTask.WEEK -> {
            FieldLabel("On")
            DayToggles(draft.weekdays) { days -> onEdit { it.copy(weekdays = days) } }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf("Weekdays" to ScheduleDays.WEEKDAYS, "Weekends" to ScheduleDays.WEEKEND, "Every day" to ScheduleDays.EVERY_DAY)
                    .forEach { (label, days) ->
                        FilterChip(selected = draft.weekdays == days, onClick = { onEdit { it.copy(weekdays = days) } }, label = { Text(label) })
                    }
            }
        }
        ScheduleTask.MONTH -> ValueRow(Icons.Default.CalendarMonth, "On", "The ${ScheduleText.ordinal(draft.monthDay)} of the month") { sheet = "monthDay" }
        ScheduleTask.ONCE -> ValueRow(Icons.Default.CalendarMonth, "Date", dateLabel(draft.onceDate) ?: "Pick a date") { sheet = "date" }
    }

    if (draft.unit == ScheduleTask.HOUR) {
        ValueRow(null, "At minute", ":%02d past each hour".format(draft.minute)) { sheet = "minute" }
    } else {
        ValueRow(Icons.Default.AccessTime, "Time", ScheduleText.time(draft.hour, draft.minute, use24h)) { sheet = "time" }
    }
    if (nextRun != null) {
        Text("Next run · $nextRun", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.base).offset(y = -Spacing.s))
    }

    FieldLabel("Ends")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilterChip(selected = draft.endDate == null, onClick = { onEdit { it.copy(endDate = null) } }, label = { Text("Never") })
        listOf("1 week" to (Calendar.WEEK_OF_YEAR to 1), "4 weeks" to (Calendar.WEEK_OF_YEAR to 4),
            "3 months" to (Calendar.MONTH to 3), "1 year" to (Calendar.YEAR to 1)).forEach { (label, span) ->
            val end = endAfter(span.first, span.second)
            FilterChip(selected = draft.endDate == end, onClick = { onEdit { it.copy(endDate = end) } }, label = { Text(label) })
        }
        val custom = draft.endDate != null && listOf(Calendar.WEEK_OF_YEAR to 1, Calendar.WEEK_OF_YEAR to 4, Calendar.MONTH to 3, Calendar.YEAR to 1)
            .none { endAfter(it.first, it.second) == draft.endDate }
        FilterChip(selected = custom, onClick = { sheet = "end" },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(FilterChipDefaults.IconSize)) },
            label = { Text(if (custom) dateLabel(draft.endDate!!)?.let { "Until $it" } ?: "Pick date" else "Pick date") })
    }

    when (sheet) {
        "time" -> ScheduleTimeSheet(draft.hour, draft.minute, use24h, onDismiss = { sheet = null }) { h, m ->
            onEdit { it.copy(hour = h, minute = m) }; sheet = null
        }
        "minute" -> ScheduleNumberSheet("Minute past the hour", 0..59, draft.minute, { "%02d".format(it) }, { ":%02d".format(it) },
            onDismiss = { sheet = null }) { m -> onEdit { it.copy(minute = m) }; sheet = null }
        "interval" -> ScheduleNumberSheet("Repeat every", 1..60, draft.interval, Int::toString, { n ->
            val unit = when (draft.unit) { ScheduleTask.HOUR -> "hour"; ScheduleTask.DAY -> "day"; ScheduleTask.WEEK -> "week"; else -> "month" }
            if (n == 1) "Every $unit" else "Every $n ${unit}s"
        }, onDismiss = { sheet = null }) { n -> onEdit { it.copy(interval = n) }; sheet = null }
        "monthDay" -> ScheduleNumberSheet("Day of the month", 1..31, draft.monthDay, Int::toString, { "The ${ScheduleText.ordinal(it)}" },
            onDismiss = { sheet = null }) { d -> onEdit { it.copy(monthDay = d) }; sheet = null }
        "date" -> DateSheet(draft.onceDate, onDismiss = { sheet = null }) { d -> onEdit { it.copy(onceDate = d) }; sheet = null }
        "end" -> DateSheet(draft.endDate.orEmpty(), onDismiss = { sheet = null }) { d -> onEdit { it.copy(endDate = d) }; sheet = null }
    }
}

/** Seven day toggles that bloom from a circle into a cookie when chosen. The last day can't be cleared. */
@Composable
private fun DayToggles(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ScheduleDays.ORDER.forEach { day ->
            val on = day in selected
            val progress by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "day")
            val morph = rememberMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided)
            Box(
                Modifier.size(40.dp).clip(MorphPolygonShape(morph, progress))
                    .background(if (on) colors.primary else colors.surfaceContainerHighest)
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
private fun ValueRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
        Row(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.m))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs).offset(y = Spacing.s))
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
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
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
