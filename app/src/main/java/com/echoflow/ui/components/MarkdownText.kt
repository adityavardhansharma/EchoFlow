package com.echoflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.invoke
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
    val context = LocalContext.current
    val materialStyle = rememberMaterialMarkdownStyle()
    val renderedStyle = remember(materialStyle, textColor, style) {
        materialStyle.merge {
            paragraph {
                color = textColor
                fontSize = style.fontSize
                fontWeight = style.fontWeight
                fontFamily = style.fontFamily
                if (style.lineHeight != TextUnit.Unspecified) lineHeight = style.lineHeight
            }
            h1 { color = textColor }
            h2 { color = textColor }
            h3 { color = textColor }
            h4 { color = textColor }
            list { color = textColor }
            table {
                color = textColor
                headerTextColor = textColor
            }
        }
    }
    EnrichedMarkdownText(
        markdown = text,
        modifier = modifier.fillMaxWidth(),
        style = renderedStyle,
        onLinkClick = { url -> openUrl(context, url) },
    )
}
