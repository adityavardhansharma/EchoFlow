package com.echoflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The in-chat project pill: a quiet, tinted chip shown under the top bar when the open conversation
 * belongs to a project. It signals "this chat is running with extra context" without nagging, and
 * taps through to the project home. Colour comes from the project's accent so it matches the hub.
 */
@Composable
fun ProjectChip(
    name: String,
    colorIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = projectAccent(colorIndex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = accent.container,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(15.dp), tint = accent.onContainer)
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = accent.onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
