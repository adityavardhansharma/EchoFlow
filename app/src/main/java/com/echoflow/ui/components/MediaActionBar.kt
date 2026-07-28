package com.echoflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/** One entry in a [MediaActionBar]. */
data class MediaAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The row of things you can do with a finished image or clip.
 *
 * It used to be a line of 48dp filled circles, and four filled circles under every result is a
 * lot of furniture for actions almost nobody takes on any given image — they compete with the
 * picture, which is the only thing on the screen worth looking at. This is one low object
 * instead: a single quiet pill, hairline-divided, sized to the text.
 *
 * Everything lives inside the one container, including whatever a screen adds for itself.
 * A caller that appended its own button got a detached fifth circle floating beside the set,
 * which read as an afterthought — because it was one.
 *
 * Labels rather than icons alone. These actions are infrequent enough that no one builds a
 * memory of the glyphs, and "what does this arrow do to my image" is a worse cost than the
 * width of the word.
 */
@Composable
fun MediaActionBar(actions: List<MediaAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions.forEachIndexed { index, action ->
                if (index > 0) HairlineDivider()
                MediaActionItem(action)
            }
        }
    }
}

@Composable
private fun MediaActionItem(action: MediaAction) {
    Row(
        modifier = Modifier
            .clickable(onClick = action.onClick)
            .semantics { contentDescription = action.label }
            .heightIn(min = 40.dp)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Inset top and bottom so it reads as a seam in one object, not a wall between two. */
@Composable
private fun HairlineDivider() {
    Surface(
        modifier = Modifier.width(1.dp).height(18.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        content = {},
    )
}
