@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import com.echoflow.ui.theme.rememberReducedMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/**
 * EchoBrain isn't built yet, so its tab is an honest placeholder with some presence: a slowly
 * morphing hero (still under reduced motion), a "Coming soon" pill, one line on what it will be,
 * and a way straight back to the memory that works today.
 */
@Composable
internal fun EchoBrainSection(onUseSupermemory: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val morph = rememberMorph(BrandShapes.heroStart, BrandShapes.heroEnd)
                val progress = if (rememberReducedMotion()) 0f else {
                    val p by rememberMorphProgress(3400)
                    p
                }
                Box(Modifier.size(156.dp).clip(CircleShape).background(cs.tertiaryContainer.copy(alpha = 0.35f)))
                Box(Modifier.size(120.dp).clip(MorphPolygonShape(morph, progress)).background(cs.tertiaryContainer))
                Icon(Icons.Default.Psychology, null, Modifier.size(48.dp), tint = cs.onTertiaryContainer)
            }
            Surface(
                shape = CircleShape,
                color = cs.tertiary,
                contentColor = cs.onTertiary,
                modifier = Modifier.padding(top = Spacing.xl),
            ) {
                Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Coming soon", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "Memory, built in",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Text(
                "EchoBrain is a future memory system built into EchoFlow. For now, connect your Supermemory account to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.s),
            )
            FilledTonalButton(onClick = onUseSupermemory, modifier = Modifier.padding(top = Spacing.xl).height(48.dp)) {
                Icon(Icons.Default.CloudQueue, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Use Supermemory")
            }
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
