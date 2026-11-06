package com.echoflow.ui.components

import com.echoflow.data.ChatMessage
import com.echoflow.data.ReplyVersions

// Lives in ui because the plain-text conversion reuses the Markdown renderer's parser.

/** [copyText] of the reply version the user is looking at. */
fun ReplyVersions.copyText(message: ChatMessage, index: Int): String = copyText(display(message, index))

/** Plain-text answer body for the clipboard — no reasoning, no markdown syntax. */
fun ReplyVersions.copyText(message: ChatMessage): String {
    val fromSegments = textFromSegments(message.segmentsJson)
    return markdownToPlainText(fromSegments.ifBlank { message.content })
}
