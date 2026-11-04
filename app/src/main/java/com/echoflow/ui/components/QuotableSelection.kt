@file:OptIn(ExperimentalFoundationApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * Quote & reply. The chat surface provides [LocalQuoteHandler]; a finished reply marks itself as
 * the quote source with [QuotableReply]. Selecting text inside it adds **Quote** to the system
 * text toolbar, beside Copy and Select all, and hands the selected words plus the reply's id to
 * the handler. Anywhere without both — artifacts, research, settings — selection is unchanged.
 */
val LocalQuoteHandler = compositionLocalOf<((text: String, messageId: String) -> Unit)?> { null }

/** The reply a nested [QuotableSelectionContainer] (a code block, a reasoning trace) quotes from. */
private class QuoteSource(val messageId: String, val enabled: Boolean)

private val LocalQuoteSource = compositionLocalOf<QuoteSource?> { null }

private object QuoteMenuKey

/**
 * Makes a reply selectable and, once [enabled] (it has finished streaming), quotable. The tree
 * shape never changes with [enabled], so a reply that finishes keeps its mounted Markdown instead
 * of being rebuilt under a new parent.
 */
@Composable
fun QuotableReply(messageId: String, enabled: Boolean, content: @Composable () -> Unit) {
    if (LocalQuoteHandler.current == null) {
        content()
        return
    }
    CompositionLocalProvider(LocalQuoteSource provides QuoteSource(messageId, enabled)) {
        QuotableSelectionContainer(content = content)
    }
}

/**
 * A [SelectionContainer] that offers Quote when it sits inside a [QuotableReply], and is a plain
 * one everywhere else. Code blocks and reasoning keep their own nested containers, so each one
 * carries the item too.
 */
@Composable
fun QuotableSelectionContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val handler = LocalQuoteHandler.current
    val source = LocalQuoteSource.current
    if (handler == null || source == null) {
        SelectionContainer(modifier, content = content)
        return
    }
    val state = rememberSelectionState()
    val onQuote by rememberUpdatedState(handler)
    val current by rememberUpdatedState(source)
    SelectionContainer(
        state = state,
        modifier = modifier.appendTextContextMenuComponents {
            // Built when the toolbar opens. A nested container's toolbar also walks up through
            // this one; with nothing selected here, it adds no second Quote.
            if (current.enabled && state.selectedText().isNotBlank()) {
                item(key = QuoteMenuKey, label = "Quote") {
                    val text = state.selectedText()
                    val messageId = current.messageId
                    state.clear()
                    close()
                    onQuote(text, messageId)
                }
            }
        },
        content = content,
    )
}

private fun SelectionState.selectedText(): String = selectedTexts.joinToString("\n") { it.text }
