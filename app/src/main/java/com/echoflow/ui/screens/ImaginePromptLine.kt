@file:OptIn(ExperimentalFoundationApi::class)

package com.echoflow.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing

/**
 * The sentence that produced a result, printed under it.
 *
 * Imagine deliberately has no user bubble — the thing you made should never share the screen
 * with a transcript of you asking for it — but hiding the prompt entirely went too far. Scroll
 * back a week and every image is an orphan: no way to remember what you asked for, and no way
 * to ask for it again slightly differently, which is the whole activity.
 *
 * So it comes back as a caption rather than a message. It is typographically quiet, it never
 * gets a container, and it is aligned to the media rather than to a speaker. What makes it
 * worth its space is that it is *live*: tapping loads it straight back into the composer,
 * which turns every past result into a starting point. Long-press copies it, for the times you
 * want the words somewhere else.
 *
 * Three lines maximum. It is your own sentence — you need to recognise it, not re-read it.
 */
@Composable
internal fun ImaginePromptLine(
    prompt: String,
    onReuse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { copyPrompt(context, prompt) },
                onClick = { onReuse(prompt) },
            )
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.s))
        // The only mark of the affordance. An arrow rather than a refresh glyph: this sends
        // the words up to the composer, it does not re-run them behind your back.
        Icon(
            Icons.Default.NorthEast,
            contentDescription = "Edit this prompt again",
            modifier = Modifier.size(14.dp).padding(top = 3.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun copyPrompt(context: Context, prompt: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Prompt", prompt))
    // Android 13+ shows its own copy confirmation; a toast on top of it is a double report.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
    }
}
