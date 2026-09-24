@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/** The shared frame for schedule pickers: title, live readout, content, and one clear action. */
@Composable
internal fun ScheduleSheet(
    title: String,
    readout: String?,
    confirm: String = "Set",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (readout != null) {
                Text(readout, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs))
            }
            Spacer(Modifier.height(Spacing.l))
            content()
            Spacer(Modifier.height(Spacing.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onConfirm) { Text(confirm) }
            }
            Spacer(Modifier.height(Spacing.base))
        }
    }
}

/**
 * Time of day: the standard Material time picker — the dial people already know from the Clock
 * app — following the phone's 12/24-hour setting. The picker shows its own readout.
 */
@Composable
internal fun ScheduleTimeSheet(
    hour: Int,
    minute: Int,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = use24h)
    ScheduleSheet(
        title = "Time of day",
        readout = null,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(state.hour, state.minute) },
    ) {
        TimePicker(state = state)
    }
}

/** A single-number crown wheel ("every 3 weeks", "on the 15th"). */
@Composable
internal fun ScheduleNumberSheet(
    title: String,
    range: IntRange,
    value: Int,
    format: (Int) -> String,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var v by remember { mutableIntStateOf(value.coerceIn(range)) }
    val values = range.toList()
    ScheduleSheet(title = title, readout = label(v), onDismiss = onDismiss, onConfirm = { onConfirm(v) }) {
        CrownWheel(values.map(format), values.indexOf(v), { v = values[it] }, title, width = 120.dp)
    }
}
