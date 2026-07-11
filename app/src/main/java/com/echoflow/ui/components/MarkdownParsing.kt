
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
internal fun appendParagraphLine(sb: StringBuilder, line: String) {
    if (sb.isNotEmpty()) sb.append(' ')
    sb.append(line.trim())
}

internal fun indentOf(line: String): Int = (line.takeWhile { it == ' ' }.length / 2).coerceAtMost(4)

internal fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

internal fun parseTableAligns(separator: String): List<TextAlign> =
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

internal data class ParsedDisplayMath(val block: MarkdownBlock.MathBlock, val nextIndex: Int)

internal fun parseDisplayMathBlock(lines: List<String>, index: Int): ParsedDisplayMath? {
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

internal fun findInlineDollarClose(text: String, from: Int): Int {
    var i = from
    while (i < text.length) {
        if (text[i] == '$' && !isEscaped(text, i) && text.getOrNull(i + 1) != '$') return i
        i++
    }
    return -1
}

internal fun isEscaped(text: String, index: Int): Boolean {
    var slashCount = 0
    var i = index - 1
    while (i >= 0 && text[i] == '\\') {
        slashCount++
        i--
    }
    return slashCount % 2 == 1
}

internal fun looksLikeMath(content: String, before: Char?, after: Char?): Boolean {
    if (content.isBlank()) return false
    if (content.first().isWhitespace() || content.last().isWhitespace()) return false
    if (content.length > 160) return false
    if (before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true) return false
    if (content.all { it.isDigit() || it == '.' || it == ',' }) {
        return before != '$' && after?.isLetter() != true && content.any { it.isDigit() }
    }
    val lower = content.lowercase()
    val hasMathSignal = content.any { it in "\\^_=+-*/()[]{}<>|'" } ||
        listOf("frac", "sqrt", "lim", "sum", "int", "text", "sin", "cos", "tan", "log", "ln").any { it in lower }
    val isTinyVariable = content.length <= 4 && content.any { it.isLetter() } && content.none { it.isDigit() }
    return hasMathSignal || isTinyVariable
}

internal sealed class InlineNode {
    data class Text(val text: String) : InlineNode()
    data class Math(val id: Int, val latex: String, val raw: String) : InlineNode()
    data class Span(val style: SpanStyle, val children: List<InlineNode>) : InlineNode()
    data class Code(val text: String) : InlineNode()
    data class Link(val label: List<InlineNode>, val url: String) : InlineNode()
    data class Citation(val label: String, val url: String) : InlineNode()
}

internal class InlineParser(private val source: String) {
    private var nextMathId = 0

    fun parse(): List<InlineNode> = parseRange(0, source.length)

    private fun parseRange(start: Int, end: Int): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        var cursor = start
        while (cursor < end) {
            var nextIdx = -1
            var kind = ""
            fun consider(idx: Int, k: String) {
                if (idx in cursor until end && (nextIdx == -1 || idx < nextIdx)) {
                    nextIdx = idx
                    kind = k
                }
            }
            consider(source.indexOf("\\(", cursor).takeIf { it < end } ?: -1, "parenMath")
            consider(source.indexOf("\\[", cursor).takeIf { it < end } ?: -1, "bracketMath")
            consider(indexOfDoubleDollar(cursor, end), "doubleDollarMath")
            consider(indexOfDollar(cursor, end), "dollarMath")
            consider(source.indexOf("**", cursor).takeIf { it < end } ?: -1, "bold")
            consider(source.indexOf("~~", cursor).takeIf { it < end } ?: -1, "strike")
            consider(source.indexOf("`", cursor).takeIf { it < end } ?: -1, "code")
            consider(source.indexOf("[", cursor).takeIf { it < end } ?: -1, "link")
            consider(indexOfItalic(source, cursor).takeIf { it < end } ?: -1, "italic")

            if (nextIdx == -1) {
                nodes.add(InlineNode.Text(source.substring(cursor, end)))
                break
            }
            if (nextIdx > cursor) nodes.add(InlineNode.Text(source.substring(cursor, nextIdx)))

            when (kind) {
                "parenMath" -> {
                    val close = source.indexOf("\\)", nextIdx + 2).takeIf { it in 0 until end }
                    val parsed = close?.let { mathNode(nextIdx + 2, it, it + 2) }
                    if (parsed != null) {
                        nodes.add(parsed)
                        cursor = close + 2
                    } else {
                        nodes.add(InlineNode.Text(source.substring(nextIdx, end)))
                        break
                    }
                }
                "bracketMath" -> {
                    val close = source.indexOf("\\]", nextIdx + 2).takeIf { it in 0 until end }
                    val parsed = close?.let { mathNode(nextIdx + 2, it, it + 2) }
                    if (parsed != null) {
                        nodes.add(parsed)
                        cursor = close + 2
                    } else {
                        nodes.add(InlineNode.Text(source.substring(nextIdx, end)))
                        break
                    }
                }
                "doubleDollarMath" -> {
                    val close = source.indexOf("$$", nextIdx + 2).takeIf { it in 0 until end }
                    val parsed = close?.let { mathNode(nextIdx + 2, it, it + 2) }
                    if (parsed != null) {
                        nodes.add(parsed)
                        cursor = close + 2
                    } else {
                        nodes.add(InlineNode.Text("$$"))
                        cursor = nextIdx + 2
                    }
                }
                "dollarMath" -> {
                    val close = findInlineDollarClose(source, nextIdx + 1).takeIf { it in 0 until end }
                    val latex = close?.let { source.substring(nextIdx + 1, it).trim() }
                    if (close != null && latex != null && looksLikeMath(latex, source.getOrNull(nextIdx - 1), source.getOrNull(close + 1))) {
                        nodes.add(mathNode(nextIdx + 1, close, close + 1)!!)
                        cursor = close + 1
                    } else {
                        nodes.add(InlineNode.Text("$"))
                        cursor = nextIdx + 1
                    }
                }
                "bold" -> {
                    val close = source.indexOf("**", nextIdx + 2).takeIf { it in 0 until end }
                    if (close != null) {
                        nodes.add(InlineNode.Span(SpanStyle(fontWeight = FontWeight.Bold), parseRange(nextIdx + 2, close)))
                        cursor = close + 2
                    } else {
                        nodes.add(InlineNode.Text("**"))
                        cursor = nextIdx + 2
                    }
                }
                "strike" -> {
                    val close = source.indexOf("~~", nextIdx + 2).takeIf { it in 0 until end }
                    if (close != null) {
                        nodes.add(InlineNode.Span(SpanStyle(textDecoration = TextDecoration.LineThrough), parseRange(nextIdx + 2, close)))
                        cursor = close + 2
                    } else {
                        nodes.add(InlineNode.Text("~~"))
                        cursor = nextIdx + 2
                    }
                }
                "italic" -> {
                    val close = source.indexOf("*", nextIdx + 1).takeIf { it in 0 until end }
                    if (close != null && close > nextIdx + 1) {
                        nodes.add(InlineNode.Span(SpanStyle(fontStyle = FontStyle.Italic), parseRange(nextIdx + 1, close)))
                        cursor = close + 1
                    } else {
                        nodes.add(InlineNode.Text("*"))
                        cursor = nextIdx + 1
                    }
                }
                "code" -> {
                    val close = source.indexOf("`", nextIdx + 1).takeIf { it in 0 until end }
                    if (close != null) {
                        nodes.add(InlineNode.Code(source.substring(nextIdx + 1, close)))
                        cursor = close + 1
                    } else {
                        nodes.add(InlineNode.Text("`"))
                        cursor = nextIdx + 1
                    }
                }
                "link" -> {
                    val labelEnd = source.indexOf("]", nextIdx + 1).takeIf { it in 0 until end }
                    val parenOpen = labelEnd?.let { source.getOrNull(it + 1) }
                    val parenClose = if (parenOpen == '(') source.indexOf(")", labelEnd + 2).takeIf { it in 0 until end } else null
                    if (labelEnd != null && parenClose != null) {
                        val labelText = source.substring(nextIdx + 1, labelEnd)
                        val url = source.substring(labelEnd + 2, parenClose)
                        nodes.add(
                            if (isCitationLabel(labelText)) {
                                InlineNode.Citation(labelText, url)
                            } else {
                                InlineNode.Link(parseRange(nextIdx + 1, labelEnd), url)
                            }
                        )
                        cursor = parenClose + 1
                    } else {
                        nodes.add(InlineNode.Text("["))
                        cursor = nextIdx + 1
                    }
                }
            }
        }
        return nodes
    }

    private fun mathNode(contentStart: Int, contentEnd: Int, rawEnd: Int): InlineNode.Math? {
        val latex = source.substring(contentStart, contentEnd).trim()
        if (latex.isEmpty()) return null
        return InlineNode.Math(nextMathId++, latex, source.substring(contentStart - delimiterPrefixLength(contentStart), rawEnd))
    }

    private fun delimiterPrefixLength(contentStart: Int): Int =
        when {
            contentStart >= 2 && source.startsWith("\\(", contentStart - 2) -> 2
            contentStart >= 2 && source.startsWith("\\[", contentStart - 2) -> 2
            contentStart >= 2 && source.startsWith("$$", contentStart - 2) -> 2
            else -> 1
        }

    private fun indexOfDoubleDollar(from: Int, end: Int): Int {
        var i = source.indexOf("$$", from)
        while (i != -1 && i < end) {
            if (!isEscaped(source, i)) return i
            i = source.indexOf("$$", i + 2)
        }
        return -1
    }

    private fun indexOfDollar(from: Int, end: Int): Int {
        var i = source.indexOf("$", from)
        while (i != -1 && i < end) {
            if (!isEscaped(source, i) && source.getOrNull(i + 1) != '$' && source.getOrNull(i - 1) != '$') return i
            i = source.indexOf("$", i + 1)
        }
        return -1
    }
}

