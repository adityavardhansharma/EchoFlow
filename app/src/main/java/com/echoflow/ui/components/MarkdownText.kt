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

sealed class MarkdownBlock {
    data class Header(val text: String, val level: Int) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class MathBlock(val latex: String, val raw: String, val complete: Boolean) : MarkdownBlock()
    /** A list item. [ordinal] is null for bullets, the number for ordered items. */
    data class BulletItem(val text: String, val indent: Int, val ordinal: Int?) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Table(val headers: List<String>, val aligns: List<TextAlign>, val rows: List<List<String>>) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

private val ORDERED_ITEM = Regex("""^(\d+)[.)]\s+(.*)""")
private val BULLET_ITEM = Regex("""^[*\-+]\s+(.*)""")
private val HR_LINE = Regex("""^([-*_])\1{2,}\s*$""")
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)+\|?\s*$""")

/**
 * Parses markdown text into a flat list of renderable blocks. Line-based and allocation-light so it
 * is cheap to re-run on every streaming frame.
 */
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")

    var insideCodeBlock = false
    val codeAccumulator = StringBuilder()
    var codeLanguage: String? = null

    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraph.toString()))
            paragraph.setLength(0)
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Fenced code blocks (``` or ~~~) — take precedence over everything.
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (insideCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeAccumulator.toString().trimEnd('\n'), codeLanguage))
                codeAccumulator.setLength(0)
                codeLanguage = null
                insideCodeBlock = false
            } else {
                flushParagraph()
                insideCodeBlock = true
                codeLanguage = trimmed.substring(3).trim().substringBefore(' ').ifEmpty { null }
            }
            i++
            continue
        }
        if (insideCodeBlock) {
            codeAccumulator.append(line).append('\n')
            i++
            continue
        }

        parseDisplayMathBlock(lines, i)?.let { parsed ->
            flushParagraph()
            blocks.add(parsed.block)
            i = parsed.nextIndex
            continue
        }

        // GFM table: a header line containing '|' immediately followed by a separator row.
        if (line.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(line)
            val aligns = parseTableAligns(lines[i + 1])
            val rows = mutableListOf<List<String>>()
            var j = i + 2
            while (j < lines.size && lines[j].contains('|') && lines[j].isNotBlank()) {
                rows.add(splitTableRow(lines[j]))
                j++
            }
            blocks.add(MarkdownBlock.Table(headers, aligns, rows))
            i = j
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()

            HR_LINE.matches(trimmed) -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Divider)
            }

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                    flushParagraph()
                    blocks.add(MarkdownBlock.Header(trimmed.substring(level + 1).trim(), level))
                } else {
                    appendParagraphLine(paragraph, line)
                }
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Quote(trimmed.removePrefix(">").trim()))
            }

            ORDERED_ITEM.matches(trimmed) -> {
                flushParagraph()
                val m = ORDERED_ITEM.find(trimmed)!!
                blocks.add(MarkdownBlock.BulletItem(m.groupValues[2].trim(), indentOf(line), m.groupValues[1].toIntOrNull() ?: 1))
            }

            BULLET_ITEM.matches(trimmed) -> {
                flushParagraph()
                val m = BULLET_ITEM.find(trimmed)!!
                blocks.add(MarkdownBlock.BulletItem(m.groupValues[1].trim(), indentOf(line), null))
            }

            else -> appendParagraphLine(paragraph, line)
        }
        i++
    }

    // Flush trailing state (handles still-streaming, unclosed blocks gracefully).
    if (insideCodeBlock && codeAccumulator.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeAccumulator.toString().trimEnd('\n'), codeLanguage))
    }
    flushParagraph()
    return blocks
}

/** Per CommonMark a single newline inside a paragraph is a soft break (rendered as a space). */
private fun appendParagraphLine(sb: StringBuilder, line: String) {
    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(line.trim())
}

private fun indentOf(line: String): Int = (line.takeWhile { it == ' ' }.length / 2).coerceAtMost(4)

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun parseTableAligns(separator: String): List<TextAlign> =
    separator.trim().removePrefix("|").removeSuffix("|").split("|").map {
        val s = it.trim()
        val left = s.startsWith(":")
        val right = s.endsWith(":")
        when {
            left && right -> TextAlign.Center
            right -> TextAlign.End
            else -> TextAlign.Start
        }
    }

private data class ParsedDisplayMath(val block: MarkdownBlock.MathBlock, val nextIndex: Int)

private fun parseDisplayMathBlock(lines: List<String>, index: Int): ParsedDisplayMath? {
    val line = lines[index]
    val trimmed = line.trim()
    val delimiter = when {
        trimmed.startsWith("$$") -> "$$"
        trimmed.startsWith("\\[") -> "\\]"
        else -> return null
    }
    val open = if (delimiter == "$$") "$$" else "\\["
    val close = delimiter
    val firstContent = trimmed.removePrefix(open)

    if (firstContent.contains(close)) {
        val latex = firstContent.substringBefore(close).trim()
        val tail = firstContent.substringAfter(close)
        if (tail.isBlank()) {
            return ParsedDisplayMath(
                MarkdownBlock.MathBlock(latex = latex, raw = line, complete = latex.isNotBlank()),
                index + 1
            )
        }
        return null
    }

    val raw = StringBuilder(line)
    val latex = StringBuilder(firstContent.trimStart())
    var i = index + 1
    while (i < lines.size) {
        val current = lines[i]
        raw.append('\n').append(current)
        val closeAt = current.indexOf(close)
        if (closeAt != -1) {
            if (latex.isNotEmpty()) latex.append('\n')
            latex.append(current.substring(0, closeAt).trimEnd())
            return ParsedDisplayMath(
                MarkdownBlock.MathBlock(
                    latex = latex.toString().trim(),
                    raw = raw.toString(),
                    complete = true
                ),
                i + 1
            )
        }
        if (latex.isNotEmpty()) latex.append('\n')
        latex.append(current)
        i++
    }

    return ParsedDisplayMath(
        MarkdownBlock.MathBlock(
            latex = latex.toString().trim(),
            raw = raw.toString(),
            complete = false
        ),
        lines.size
    )
}

private sealed class InlineSegment {
    data class Text(val text: String) : InlineSegment()
    data class Math(val latex: String, val raw: String) : InlineSegment()
}

private fun parseInlineMathSegments(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            segments.add(InlineSegment.Text(plain.toString()))
            plain.setLength(0)
        }
    }

    while (i < text.length) {
        when {
            text.startsWith("\\(", i) -> {
                val end = text.indexOf("\\)", i + 2)
                if (end == -1) {
                    plain.append(text.substring(i))
                    break
                }
                val raw = text.substring(i, end + 2)
                val latex = text.substring(i + 2, end).trim()
                if (latex.isNotEmpty()) {
                    flushPlain()
                    segments.add(InlineSegment.Math(latex, raw))
                } else {
                    plain.append(raw)
                }
                i = end + 2
            }

            text.startsWith("\\[", i) -> {
                val end = text.indexOf("\\]", i + 2)
                if (end == -1) {
                    plain.append(text.substring(i))
                    break
                }
                val raw = text.substring(i, end + 2)
                val latex = text.substring(i + 2, end).trim()
                if (latex.isNotEmpty()) {
                    flushPlain()
                    segments.add(InlineSegment.Math(latex, raw))
                } else {
                    plain.append(raw)
                }
                i = end + 2
            }

            text.startsWith("$$", i) && !isEscaped(text, i) -> {
                val end = text.indexOf("$$", i + 2)
                if (end == -1) {
                    plain.append(text.substring(i))
                    break
                }
                val raw = text.substring(i, end + 2)
                val latex = text.substring(i + 2, end).trim()
                if (latex.isNotEmpty()) {
                    flushPlain()
                    segments.add(InlineSegment.Math(latex, raw))
                } else {
                    plain.append(raw)
                }
                i = end + 2
            }

            text[i] == '$' && !isEscaped(text, i) -> {
                val end = findInlineDollarClose(text, i + 1)
                if (end == -1) {
                    plain.append(text[i])
                    i++
                    continue
                }
                val raw = text.substring(i, end + 1)
                val latex = text.substring(i + 1, end).trim()
                if (looksLikeMath(latex, text.getOrNull(i - 1), text.getOrNull(end + 1))) {
                    flushPlain()
                    segments.add(InlineSegment.Math(latex, raw))
                } else {
                    plain.append(raw)
                }
                i = end + 1
            }

            else -> {
                plain.append(text[i])
                i++
            }
        }
    }

    flushPlain()
    return segments
}

private fun findInlineDollarClose(text: String, from: Int): Int {
    var i = from
    while (i < text.length) {
        if (text[i] == '$' && !isEscaped(text, i) && text.getOrNull(i + 1) != '$') return i
        i++
    }
    return -1
}

private fun isEscaped(text: String, index: Int): Boolean {
    var slashCount = 0
    var i = index - 1
    while (i >= 0 && text[i] == '\\') {
        slashCount++
        i--
    }
    return slashCount % 2 == 1
}

private fun looksLikeMath(content: String, before: Char?, after: Char?): Boolean {
    if (content.isBlank()) return false
    if (content.first().isWhitespace() || content.last().isWhitespace()) return false
    if (content.length > 160) return false
    if (before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true) return false
    val lower = content.lowercase()
    val hasMathSignal = content.any { it in "\\^_=+-*/()[]{}<>|'" } ||
        listOf("frac", "sqrt", "lim", "sum", "int", "text", "sin", "cos", "tan", "log", "ln").any { it in lower }
    val isTinyVariable = content.length <= 4 && content.any { it.isLetter() } && content.none { it.isDigit() }
    return hasMathSignal || isTinyVariable
}

@Composable
private fun InlineMarkdownText(
    text: String,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseInlineMathSegments(text) }
    val hasMath = segments.any { it is InlineSegment.Math }
    if (!hasMath) {
        val annotated = remember(text, linkColor) { parseInlineStyles(text, linkColor) }
        Text(annotated, style = style.copy(color = color), modifier = modifier)
        return
    }

    val config = latexConfigFor(style, scale = 0.92f)
    val measurer = rememberLatexMeasurer(config)
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val measured = remember(segments, config, darkTheme) {
        segments.map { segment ->
            if (segment is InlineSegment.Math) measurer.measure(segment.latex, config, darkTheme) else null
        }
    }
    val annotated = remember(segments, measured, linkColor) {
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                when (segment) {
                    is InlineSegment.Text -> appendInline(segment.text, linkColor)
                    is InlineSegment.Math -> {
                        if (measured[index] != null) {
                            appendInlineContent("math_$index", segment.raw)
                        } else {
                            appendInline(segment.raw, linkColor)
                        }
                    }
                }
            }
        }
    }
    val inlineContent = remember(segments, measured, config, density, darkTheme) {
        buildMap {
            segments.forEachIndexed { index, segment ->
                val dims = measured[index] ?: return@forEachIndexed
                if (segment is InlineSegment.Math) {
                    put(
                        "math_$index",
                        InlineTextContent(
                            placeholder = Placeholder(
                                width = with(density) { dims.widthPx.toSp() },
                                height = with(density) { dims.heightPx.toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Latex(
                                latex = segment.latex,
                                config = config,
                                isDarkTheme = darkTheme
                            )
                        }
                    )
                }
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = style.copy(color = color),
        modifier = modifier
    )
}

@Composable
private fun latexConfigFor(style: TextStyle, scale: Float): LatexConfig {
    val baseSize = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize
    return LatexConfig(
        fontSize = baseSize * scale,
        theme = LatexTheme.material3(),
        accessibilityEnabled = true
    )
}

/**
 * Parses inline markdown — **bold**, *italic*, ~~strikethrough~~, `code`, and [text](url) links —
 * into a styled [AnnotatedString]. Nested spans (e.g. bold containing code) are handled recursively.
 */
fun parseInlineStyles(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    appendInline(text, linkColor)
}

private fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color) {
    var cursor = 0
    val n = text.length
    while (cursor < n) {
        var nextIdx = -1
        var kind = ""
        fun consider(idx: Int, k: String) {
            if (idx != -1 && (nextIdx == -1 || idx < nextIdx)) { nextIdx = idx; kind = k }
        }
        consider(text.indexOf("**", cursor), "bold")
        consider(text.indexOf("~~", cursor), "strike")
        consider(text.indexOf("`", cursor), "code")
        consider(text.indexOf("[", cursor), "link")
        consider(indexOfItalic(text, cursor), "italic")

        if (nextIdx == -1) {
            append(text.substring(cursor))
            break
        }
        if (nextIdx > cursor) append(text.substring(cursor, nextIdx))

        when (kind) {
            "bold" -> {
                val close = text.indexOf("**", nextIdx + 2)
                if (close != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(text.substring(nextIdx + 2, close), linkColor)
                    }
                    cursor = close + 2
                } else { append("**"); cursor = nextIdx + 2 }
            }
            "strike" -> {
                val close = text.indexOf("~~", nextIdx + 2)
                if (close != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(text.substring(nextIdx + 2, close), linkColor)
                    }
                    cursor = close + 2
                } else { append("~~"); cursor = nextIdx + 2 }
            }
            "code" -> {
                val close = text.indexOf("`", nextIdx + 1)
                if (close != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Gray.copy(alpha = 0.18f),
                            fontWeight = FontWeight.Medium
                        )
                    ) { append(text.substring(nextIdx + 1, close)) }
                    cursor = close + 1
                } else { append("`"); cursor = nextIdx + 1 }
            }
            "italic" -> {
                val close = text.indexOf("*", nextIdx + 1)
                if (close != -1 && close > nextIdx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(nextIdx + 1, close), linkColor)
                    }
                    cursor = close + 1
                } else { append("*"); cursor = nextIdx + 1 }
            }
            "link" -> {
                val labelEnd = text.indexOf("]", nextIdx + 1)
                val parenOpen = if (labelEnd != -1) text.getOrNull(labelEnd + 1) else null
                if (labelEnd != -1 && parenOpen == '(') {
                    val parenClose = text.indexOf(")", labelEnd + 2)
                    if (parenClose != -1) {
                        val label = text.substring(nextIdx + 1, labelEnd)
                        val url = text.substring(labelEnd + 2, parenClose)
                        if (isCitationLabel(label)) {
                            // Numbered citation ([1](url)) — render as a tiny raised pill
                            // in the accent color instead of a bare underlined "1".
                            withLink(
                                LinkAnnotation.Url(
                                    url,
                                    TextLinkStyles(
                                        SpanStyle(
                                            color = linkColor,
                                            background = linkColor.copy(alpha = 0.14f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            baselineShift = BaselineShift.Superscript,
                                        )
                                    )
                                )
                            ) { append(" $label ") }
                        } else {
                            withLink(
                                LinkAnnotation.Url(
                                    url,
                                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                                )
                            ) { appendInline(label, linkColor) }
                        }
                        cursor = parenClose + 1
                    } else { append("["); cursor = nextIdx + 1 }
                } else { append("["); cursor = nextIdx + 1 }
            }
        }
    }
}

/** A link label that is just a small number — the `[1](url)` citation convention. */
private fun isCitationLabel(label: String): Boolean =
    label.length in 1..3 && label.all { it.isDigit() }

/** Index of a standalone `*` italic marker (not part of `**`, and opening a non-space run). */
private fun indexOfItalic(text: String, from: Int): Int {
    var p = text.indexOf("*", from)
    while (p != -1) {
        val isDouble = text.getOrNull(p + 1) == '*' || (p > 0 && text[p - 1] == '*')
        val opensWord = text.getOrNull(p + 1)?.isWhitespace() == false
        if (!isDouble && opensWord) return p
        p = text.indexOf("*", p + 1)
    }
    return -1
}

@Composable
fun CodeBlockItem(code: String, language: String?) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    // Real syntax highlighting via the Highlights engine, memoized per (code, language, theme) so
    // finished blocks never recompute and only the actively-streaming block re-tokenizes.
    val highlighted = remember(code, language, darkTheme) {
        highlightCode(code, language, darkTheme)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (language ?: "code").uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Code", code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied into clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}

/** Tokenizes [code] with the Highlights engine and maps the result to a styled [AnnotatedString]. */
private fun highlightCode(code: String, language: String?, darkTheme: Boolean): AnnotatedString {
    val syntaxLanguage = language?.let { runCatching { SyntaxLanguage.getByName(it) }.getOrNull() }
    val highlights = runCatching {
        Highlights.Builder()
            .code(code)
            .theme(SyntaxThemes.default(darkMode = darkTheme))
            .let { if (syntaxLanguage != null) it.language(syntaxLanguage) else it }
            .build()
            .getHighlights()
    }.getOrNull().orEmpty()

    return buildAnnotatedString {
        append(code)
        highlights.forEach { h ->
            val start = h.location.start.coerceIn(0, code.length)
            val end = h.location.end.coerceIn(start, code.length)
            if (start == end) return@forEach
            when (h) {
                is ColorHighlight -> addStyle(SpanStyle(color = Color(h.rgb).copy(alpha = 1f)), start, end)
                is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}
