@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

@Composable
internal fun EchoBrainSection() {
    PageSection("EchoBrain")
    FormCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp)
                    .clip(RoundedPolygonShape(MaterialShapes.Flower))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Schedule, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(Spacing.base))
            Text("Coming soon", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "A future memory system built into EchoFlow. For now, connect your Supermemory account to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MemoryLibrarySection(onMemories: () -> Unit) {
    PageSection("Saved memories", "Review what's known, add a fact, or forget something")
    SettingsNavRow(
        icon = Icons.Default.Psychology,
        polygon = MaterialShapes.Flower,
        title = "My Memories",
        subtitle = "Manage your Supermemory library",
        container = MaterialTheme.colorScheme.tertiaryContainer,
        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
        index = 0,
        count = 1,
        onClick = onMemories,
    )
    Spacer(Modifier.height(Spacing.xl))
    PageSection("How memory works")
    FormCard {
        MemoryExplanation(
            "Supported conversations",
            "Memory works in standard chats with tool-capable models. Local models and specialised modes may not support automatic recall.",
        )
        Spacer(Modifier.height(Spacing.l))
        MemoryExplanation(
            "You control what leaves your device",
            "Chats used with local models while cloud memory is off are excluded from learning. Never store passwords or API keys.",
        )
        Spacer(Modifier.height(Spacing.l))
        MemoryExplanation(
            "Deleting chats and memories",
            "Deleting a chat only removes its local copy. Manage already-uploaded source conversations in Supermemory.",
        )
    }
}

@Composable
private fun MemoryExplanation(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(Spacing.xs))
    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
