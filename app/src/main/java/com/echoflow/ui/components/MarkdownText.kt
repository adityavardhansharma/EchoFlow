
package com.echoflow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

/**
 * One markdown renderer for the whole app — used identically while a message streams and once it is
 * finished, so the layout never "jumps" or re-styles when streaming completes. It is a lightweight,
 * block-incremental CommonMark/GFM renderer: parsing is memoized on the text and every block is a
 * child composable taking only stable params, so during streaming only the final, still-growing
 * block re-renders each frame. Supports headers, ordered/bulleted (nested) lists, blockquotes,
 * horizontal rules, GFM tables (wrapping cells — never truncated), fenced code blocks with real
 * syntax highlighting, LaTeX math (`$...$`, `$$...$$`, `\(...\)`, `\[...\]`), and inline
 * bold/italic/strikethrough/code/links.
 */
@Composable
fun RichMarkdown(text: String, modifier: Modifier = Modifier) {
    // Delegate to the single renderer so finished messages look exactly like the streamed reveal.
    MarkdownText(text = text, modifier = modifier)
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> MdHeader(block.text, block.level, textColor, linkColor)
                is MarkdownBlock.CodeBlock -> CodeBlockItem(code = block.code, language = block.language)
                is MarkdownBlock.MathBlock -> MdMathBlock(block, textColor, linkColor, style)
                is MarkdownBlock.BulletItem -> MdBullet(block.text, block.indent, block.ordinal, textColor, linkColor, style)
                is MarkdownBlock.Quote -> MdQuote(block.text, textColor, linkColor, style)
                is MarkdownBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                is MarkdownBlock.Table -> MdTable(block, textColor, linkColor, style)
                is MarkdownBlock.Paragraph -> MdParagraph(block.text, textColor, linkColor, style)
            }
        }
    }
}

@Composable
private fun MdHeader(text: String, level: Int, color: Color, linkColor: Color) {
    val base = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    InlineMarkdownText(
        text = text,
        color = color,
        linkColor = linkColor,
        style = base.copy(fontWeight = FontWeight.Bold, color = color),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun MdParagraph(text: String, color: Color, linkColor: Color, style: TextStyle) {
    SelectionContainer {
        InlineMarkdownText(
            text = text,
            color = color,
            linkColor = linkColor,
            style = style.copy(color = color),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MdMathBlock(block: MarkdownBlock.MathBlock, color: Color, linkColor: Color, style: TextStyle) {
    if (!block.complete || block.latex.isBlank()) {
        MdParagraph(block.raw, color, linkColor, style)
        return
    }

    val config = latexConfigFor(style, scale = 1.05f)
    LatexAutoWrap(
        latex = block.latex,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        config = config,
        isDarkTheme = isSystemInDarkTheme()
    )
}

@Composable
private fun MdBullet(text: String, indent: Int, ordinal: Int?, color: Color, linkColor: Color, style: TextStyle) {
    val marker = if (ordinal != null) "$ordinal." else "•"
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (8 + indent * 16).dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$marker ",
            style = style.copy(fontWeight = FontWeight.Bold, color = color),
            modifier = Modifier.widthIn(min = 18.dp)
        )
        SelectionContainer {
            InlineMarkdownText(
                text = text,
                color = color,
                linkColor = linkColor,
                style = style.copy(color = color),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MdQuote(text: String, color: Color, linkColor: Color, style: TextStyle) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(end = 10.dp)
                .width(3.dp)
                .heightIn(min = 20.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )
        SelectionContainer {
            InlineMarkdownText(
                text = text,
                color = color.copy(alpha = 0.85f),
                linkColor = linkColor,
                style = style.copy(color = color.copy(alpha = 0.85f), fontStyle = FontStyle.Italic),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MdTable(table: MarkdownBlock.Table, color: Color, linkColor: Color, style: TextStyle) {
    val cellStyle = style.copy(fontSize = 14.sp)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            // Header row
            TableRow(
                cells = table.headers,
                aligns = table.aligns,
                color = color,
                linkColor = linkColor,
                style = cellStyle.copy(fontWeight = FontWeight.Bold),
                background = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            table.rows.forEachIndexed { i, row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TableRow(
                    cells = row,
                    aligns = table.aligns,
                    color = color,
                    linkColor = linkColor,
                    style = cellStyle,
                    background = if (i % 2 == 1) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f) else Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<TextAlign>,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    background: Color,
) {
    Row(Modifier.fillMaxWidth().background(background), verticalAlignment = Alignment.Top) {
        val columnCount = aligns.size.coerceAtLeast(cells.size)
        for (c in 0 until columnCount) {
            val cellText = cells.getOrNull(c).orEmpty()
            val align = aligns.getOrNull(c) ?: TextAlign.Start
            InlineMarkdownText(
                text = cellText,
                color = color,
                linkColor = linkColor,
                style = style.copy(color = color, textAlign = align),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
