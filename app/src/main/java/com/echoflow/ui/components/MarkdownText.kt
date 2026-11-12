package com.echoflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.material3.rememberMaterialMarkdownStyle

/** The shared response renderer used for both streaming and persisted messages. */
@Composable
fun RichMarkdown(text: String, modifier: Modifier = Modifier) {
    MarkdownText(text = text, modifier = modifier)
}

/** Renders GitHub-flavoured Markdown and LaTeX in one native view. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val materialStyle = rememberMaterialMarkdownStyle()
    EnrichedMarkdownText(
        markdown = text,
        modifier = modifier.fillMaxWidth(),
        style = materialStyle,
    )
}
