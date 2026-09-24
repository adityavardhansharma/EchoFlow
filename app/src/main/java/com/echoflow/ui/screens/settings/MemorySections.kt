@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.echoflow.ui.components.GroupedItemGap
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

/**
 * The way into the library: a large tertiary card rather than one more settings row, because
 * reviewing what's remembered is the thing people most often come here to do.
 */
@Composable
internal fun MemoryLibrarySection(onMemories: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onMemories,
        shape = RoundedCornerShape(28.dp),
        color = cs.tertiaryContainer,
        contentColor = cs.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            MemoryMark(
                Icons.Default.Inventory2, MaterialShapes.Flower,
                container = cs.tertiary, onContainer = cs.onTertiary, size = 52.dp,
            )
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text("My Memories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Browse, search, add or forget — plus your profile and suggestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onTertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.onTertiaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp))
            }
        }
    }
}

/** The fine print, as questions that open in place. */
@Composable
internal fun MemoryNotesSection() {
    MemorySectionHeader("Good to know")
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        MemoryFaqRow(
            "Which chats use memory?",
            "Memory works in standard chats with tool-capable models. Local models and specialised modes may not support automatic recall.",
            index = 0, count = 3,
        )
        MemoryFaqRow(
            "What leaves my device?",
            "Chats used with local models while cloud memory is off are excluded from learning. Never store passwords or API keys.",
            index = 1, count = 3,
        )
        MemoryFaqRow(
            "What happens when I delete a chat?",
            "Deleting a chat only removes its local copy. Manage already-uploaded source conversations in Supermemory.",
            index = 2, count = 3,
        )
    }
}
