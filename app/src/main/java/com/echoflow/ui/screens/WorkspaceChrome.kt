package com.echoflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/*
 * The chrome the full-screen workspaces share (artifact, research report, project document):
 * a top bar with a minimise arrow, and pill tabs under it.
 */

/** The workspace top bar: the minimise arrow, then [title] and actions, then [below] (e.g. tabs). */
@Composable
internal fun WorkspaceTopBar(
    onClose: () -> Unit,
    closeLabel: String = "Minimize to chat",
    below: @Composable ColumnScope.() -> Unit = {},
    title: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, closeLabel, Modifier.size(26.dp))
                }
                title()
            }
            below()
        }
    }
}

/** A pill tab in a workspace top bar; [count] shows after the label when above zero. */
@Composable
internal fun WorkspaceTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Text(
                if (count > 0) "$label $count" else label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
