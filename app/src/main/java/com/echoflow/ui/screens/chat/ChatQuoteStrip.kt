package com.echoflow.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.QuotedPassage
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/**
 * Quote & reply: the "Replying to" strip that sits on top of the composer while passages are
 * staged. One line per quote, each with its own remove button.
 */
@Composable
internal fun QuoteStrip(
    quotes: List<QuotedPassage>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    // Held through the exit animation, so the strip shrinks away with its last quote showing.
    val held = remember { arrayOf(quotes) }
    if (quotes.isNotEmpty()) held[0] = quotes
    val shown = held[0]
    AnimatedVisibility(
        visible = quotes.isNotEmpty(),
        enter = if (reducedMotion) fadeIn() else fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = if (reducedMotion) fadeOut() else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            // The gap lives inside the animation, so it collapses with the strip.
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Column(
                Modifier
                    .then(if (reducedMotion) Modifier else Modifier.animateContentSize())
                    .padding(start = Spacing.base, end = Spacing.xs, top = Spacing.s, bottom = Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "Replying to EchoFlow",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (shown.size > 1) {
                        Text(
                            " · ${shown.size} quotes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Keyed by content, so removing the middle quote doesn't shuffle the rows' state.
                shown.forEachIndexed { index, quote ->
                    key(quote.messageId, quote.text) {
                        QuoteRow(quote = quote, onRemove = { onRemove(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(quote: QuotedPassage, onRemove: () -> Unit) {
    // One line in the strip; the full passage is what TalkBack reads.
    val preview = remember(quote.text) { quote.text.replace(Regex("\\s+"), " ") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Quote: ${quote.text}" },
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove quote",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
