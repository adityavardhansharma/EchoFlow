@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.Project
import com.echoflow.ui.theme.Spacing

// ── Instructions editor ────────────────────────────────────────────────────────────────

@Composable
internal fun ProjectInstructionsScreen(project: Project, onSave: (String) -> Unit, onBack: () -> Unit) {
    // rememberSaveable, not remember: autosave only fires on leave, so a config change or process
    // death mid-edit must not discard the in-progress text and snap back to the persisted value.
    var text by rememberSaveable(project.id) { mutableStateOf(project.instructions) }
    val leave = { onSave(text.trim()); onBack() }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize()) {
        ProjectHeader(
            onBack = leave,
            trailing = { TextButton(onClick = leave) { Text("Save") } },
        ) {
            Text("Instructions", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.fillMaxSize().padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // A quiet one-line hint — guidance, not a heavy coloured slab competing with the editor.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Sent to the model on every message in this project — a standing brief for tone, role and rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            // The writing surface: a generous card with comfortable inner margins so long briefs
            // read like a document, not a cramped form field.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().weight(1f),
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
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxSize().padding(Spacing.s).imePadding(),
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
