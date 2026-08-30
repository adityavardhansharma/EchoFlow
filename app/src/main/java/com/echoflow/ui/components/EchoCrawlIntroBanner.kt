package com.echoflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/**
 * One-time chat intro for EchoCrawl. Same surface as the "Editing prompt" bar
 * (large shape, secondaryContainer). Copy wraps fully; actions are labeled
 * TextButtons — no truncated line and no icon-only arrow. Either action
 * dismisses the banner permanently.
 */
@Composable
fun EchoCrawlIntroBanner(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.xs),
    ) {
        Column(Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "EchoCrawl",
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer,
                )
            }
            Text(
                "Free web search for local and cloud models. No API key needed — turn it on in Web search settings.",
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = Spacing.s, end = Spacing.s),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Don't show again")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Open settings")
                }
            }
        }
    }
}
