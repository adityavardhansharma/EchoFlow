package com.echoflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.JetBrainsMono
import com.swmansion.enriched.markdown.codehighlight.CodeHighlightPlugin
import com.swmansion.enriched.markdown.compose.CodeBlockLayout
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.MarkdownOptions
import com.swmansion.enriched.markdown.compose.MarkdownPlugins
import com.swmansion.enriched.markdown.compose.invoke
import com.swmansion.enriched.markdown.compose.material3.rememberMaterialMarkdownStyle
import com.swmansion.enriched.markdown.math.LatexMathPlugin
import com.swmansion.enriched.markdown.math.compose.inlineMath
import com.swmansion.enriched.markdown.math.compose.math
import com.swmansion.enriched.markdown.spoiler.SpoilerOverlay
import kotlinx.coroutines.delay

/** The shared response renderer used for both streaming and persisted messages. */
@Composable
fun RichMarkdown(text: String, modifier: Modifier = Modifier) {
    MarkdownText(text = text, modifier = modifier)
}

/**
 * GitHub-flavoured Markdown and LaTeX in one native renderer.
 * `$…$` and `$$…$$` are math; fenced code is highlighted. A long-press on a link
 * peeks the source, and a tap opens it.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val materialStyle = rememberMaterialMarkdownStyle()
    val renderedStyle = remember(materialStyle, textColor, style, scheme) {
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
            code { fontFamily = JetBrainsMono }
            codeBlock { fontFamily = JetBrainsMono }
            math {
                color = textColor
                backgroundColor = scheme.surfaceContainerHigh
            }
            inlineMath { color = textColor }
        }
    }
    var peekUrl by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    MarkdownPlugins(LatexMathPlugin, CodeHighlightPlugin) {
        EnrichedMarkdownText(
            markdown = text,
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchor = it.boundsInWindow() },
            style = renderedStyle,
            options = MarkdownOptions(admonitions = true, highlight = true),
            codeBlockLayout = CodeBlockLayout.Scrollable,
            taskListToggleEnabled = false,
            spoilerOverlay = SpoilerOverlay.Solid(cornerRadius = 4.dp),
            onLinkClick = { url ->
                peekUrl = null
                openUrl(context, url)
            },
            onLinkLongClick = { url -> peekUrl = url },
        )
    }
    val url = peekUrl
    val box = anchor
    if (url != null && box != null) {
        CitationSourcePeek(url = url, anchor = box)
        LaunchedEffect(url) {
            delay(2_200)
            if (peekUrl == url) peekUrl = null
        }
    }
}
