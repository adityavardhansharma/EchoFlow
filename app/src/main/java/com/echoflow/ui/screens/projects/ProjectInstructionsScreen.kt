@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.Project
import com.echoflow.ui.theme.Spacing

// ── Instructions editor ────────────────────────────────────────────────────────────────

/** Starter lines the chips drop into the brief — scaffolding for the parts a good brief covers. */
private val BRIEF_STARTERS = listOf(
    "Role" to "You are ",
    "Tone" to "Tone: ",
    "Always" to "Always ",
    "Never" to "Never ",
    "Format" to "Format answers as ",
)

@Composable
internal fun ProjectInstructionsScreen(project: Project, onSave: (String) -> Unit, onBack: () -> Unit) {
    // rememberSaveable, not remember: autosave only fires on leave, so a config change or process
    // death mid-edit must not discard the in-progress text and snap back to the persisted value.
    var text by rememberSaveable(project.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(project.instructions, TextRange(project.instructions.length)))
    }
    val leave = { onSave(text.text.trim()); onBack() }
    BackHandler { leave() }

    ProjectPageScaffold(
        title = "Instructions",
        subtitle = project.name,
        onBack = leave,
        actions = {
            FilledTonalButton(onClick = leave, modifier = Modifier.padding(end = Spacing.s)) { Text("Done") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // The scaffold padding already covers the nav bar; consume it so the IME inset
                // doesn't count it twice when the keyboard is up.
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            BriefExplainer()

            // The writing surface: a generous card with comfortable inner margins so long briefs
            // read like a document, not a cramped form field. It grows with the text and the page
            // scrolls, which also collapses the app bar out of the way while writing.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "e.g. You are helping me write a noir mystery. Keep a wry, hard-boiled tone and remember the characters and timeline in the attached files.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(Spacing.s),
                )
            }

            // Starter chips: one tap drops the opening of a line into the brief, cursor at its end.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                BRIEF_STARTERS.forEach { (label, starter) ->
                    AssistChip(
                        onClick = {
                            val current = text.text.trimEnd()
                            val next = if (current.isEmpty()) starter else "$current\n$starter"
                            text = TextFieldValue(next, TextRange(next.length))
                        },
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Saves when you leave",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                val count = text.text.trim().length
                Text(
                    if (count == 1) "1 character" else "$count characters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What a brief is, said once in a tonal card rather than as a grey caption under the bar. */
@Composable
private fun BriefExplainer() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "A standing brief",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Sent to the model with every message in this project — set its role, tone and rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
