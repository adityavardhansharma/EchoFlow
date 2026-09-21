package com.echoflow.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import org.intellij.markdown.MarkdownTokenTypes
import java.util.WeakHashMap

/**
 * GitHub-flavoured Markdown with LaTeX as an embedded extension. Markdown always owns the document
 * structure; only complete math ranges are delegated to the LaTeX renderer. This is deliberately
 * one Compose tree rather than a response-wide renderer switch.
 */
@Composable
internal fun GfmLatexMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val segments = remember(text) { prepareGfmLatex(text) }
    Column(modifier = modifier) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is GfmLatexSegment.Markdown -> GfmSegment(
                    segment = segment,
                    textColor = textColor,
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                )
                is GfmLatexSegment.DisplayMath -> DisplayMathSegment(segment, style)
            }
            if (index != segments.lastIndex && segment is GfmLatexSegment.DisplayMath) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun GfmSegment(
    segment: GfmLatexSegment.Markdown,
    textColor: Color,
    style: TextStyle,
    modifier: Modifier,
) {
    if (segment.source.isEmpty()) return
    val linkColor = MaterialTheme.colorScheme.primary
    val darkTheme = isSystemInDarkTheme()
    val density = LocalDensity.current
    val latexConfig = latexConfigFor(style, scale = 0.86f)
    val measurer = rememberLatexMeasurer(latexConfig)
    val measured = remember(segment.math, latexConfig, darkTheme) {
        segment.math.mapNotNull { math ->
            measurer.measure(math.latex, latexConfig, darkTheme)?.let { math.token to it }
        }.toMap()
    }
    val inlineContent = markdownInlineContent(
        remember(segment.math, measured, latexConfig, density, darkTheme) {
            segment.math.mapNotNull { math ->
                val dims = measured[math.token] ?: return@mapNotNull null
                math.token to InlineTextContent(
                    placeholder = Placeholder(
                        width = with(density) { dims.widthPx.toSp() },
                        height = with(density) { dims.heightPx.toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    )
                ) {
                    Latex(latex = math.latex, config = latexConfig, isDarkTheme = darkTheme)
                }
            }.toMap()
        }
    )
    val mathByToken = remember(segment.math) { segment.math.associateBy { it.token } }
    val measuredTokens = remember(measured) { measured.keys }
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val annotator = remember(segment.source, mathByToken, measuredTokens, highlightColor) {
        val htmlStacks = WeakHashMap<AnnotatedString.Builder, ArrayDeque<String>>()
        markdownAnnotator { content, child ->
            val raw = content.substring(child.startOffset, child.endOffset)
            when (child.type) {
                MarkdownTokenTypes.TEXT -> appendMathPlaceholders(raw, mathByToken, measuredTokens)
                MarkdownTokenTypes.HTML_TAG -> appendSafeHtmlTag(raw, highlightColor, htmlStacks)
                else -> false
            }
        }
    }
    val markdownState = rememberMarkdownState(
        content = segment.source,
        retainState = true,
    )

    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        colors = markdownColor(
            text = textColor,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
            tableBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = textColor),
            h2 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = textColor),
            h3 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor),
            h4 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textColor),
            h5 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textColor),
            h6 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = textColor),
            text = style.copy(color = textColor),
            paragraph = style.copy(color = textColor),
            ordered = style.copy(color = textColor),
            bullet = style.copy(color = textColor),
            list = style.copy(color = textColor),
            quote = style.copy(color = textColor.copy(alpha = 0.85f), fontStyle = FontStyle.Italic),
            code = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            inlineCode = style.copy(color = textColor),
            textLink = TextLinkStyles(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            ),
            table = style.copy(color = textColor, fontSize = 14.sp),
        ),
        padding = markdownPadding(
            block = 3.dp,
            list = 2.dp,
            listItemTop = 2.dp,
            listItemBottom = 2.dp,
            listIndent = 12.dp,
        ),
        annotator = annotator,
        inlineContent = inlineContent,
        components = markdownComponents(
            codeBlock = {
                MarkdownHighlightedCodeBlock(it.content, it.node, it.typography.code, showHeader = true)
            },
            codeFence = {
                MarkdownHighlightedCodeFence(it.content, it.node, it.typography.code, showHeader = true)
            },
        ),
        animations = markdownAnimations { this },
    )
}

/** Render a deliberately small, non-executable subset of GitHub's inline HTML vocabulary. */
private fun AnnotatedString.Builder.appendSafeHtmlTag(
    raw: String,
    highlightColor: Color,
    stacks: WeakHashMap<AnnotatedString.Builder, ArrayDeque<String>>,
): Boolean {
    val normalized = raw.trim().lowercase()
    if (normalized == "<br>" || normalized == "<br/>" || normalized == "<br />") {
        append('\n')
        return true
    }
    val closing = normalized.startsWith("</")
    val name = normalized.removePrefix("</").removePrefix("<").substringBefore('>').substringBefore(' ').removeSuffix("/")
    val style = when (name) {
        "u", "ins" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "mark" -> SpanStyle(background = highlightColor)
        "sup" -> SpanStyle(fontSize = 0.78.em, baselineShift = BaselineShift.Superscript)
        "sub" -> SpanStyle(fontSize = 0.78.em, baselineShift = BaselineShift.Subscript)
        "strong", "b" -> SpanStyle(fontWeight = FontWeight.Bold)
        "em", "i" -> SpanStyle(fontStyle = FontStyle.Italic)
        "del", "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        else -> return false
    }
    val stack = stacks.getOrPut(this) { ArrayDeque() }
    if (closing) {
        if (stack.lastOrNull() == name) {
            pop()
            stack.removeLast()
        }
    } else {
        pushStyle(style)
        stack.addLast(name)
    }
    return true
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMathPlaceholders(
    raw: String,
    mathByToken: Map<String, InlineLatex>,
    measuredTokens: Set<String>,
): Boolean {
    var cursor = 0
    var consumed = false
    while (cursor < raw.length) {
        val match = mathByToken.keys
            .mapNotNull { token -> raw.indexOf(token, cursor).takeIf { it >= 0 }?.let { token to it } }
            .minByOrNull { it.second }
            ?: break
        consumed = true
        if (match.second > cursor) append(raw.substring(cursor, match.second))
        val math = mathByToken.getValue(match.first)
        if (math.token in measuredTokens) appendInlineContent(math.token, math.raw) else append(math.raw)
        cursor = match.second + match.first.length
    }
    if (!consumed) return false
    if (cursor < raw.length) append(raw.substring(cursor))
    return true
}

@Composable
private fun DisplayMathSegment(segment: GfmLatexSegment.DisplayMath, style: TextStyle) {
    LatexAutoWrap(
        latex = segment.latex,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        config = latexConfigFor(style, scale = 1.05f),
        isDarkTheme = isSystemInDarkTheme(),
    )
}

internal sealed interface GfmLatexSegment {
    data class Markdown(val source: String, val math: List<InlineLatex>) : GfmLatexSegment
    data class DisplayMath(val latex: String, val raw: String) : GfmLatexSegment
}

internal data class InlineLatex(
    val token: String,
    val latex: String,
    val raw: String,
)

/** Splits only top-level display math; inline math remains embedded in its surrounding GFM text. */
internal fun prepareGfmLatex(source: String): List<GfmLatexSegment> {
    if (source.isEmpty()) return listOf(GfmLatexSegment.Markdown("", emptyList()))
    val lines = source.split('\n')
    val segments = mutableListOf<GfmLatexSegment>()
    val markdown = StringBuilder()
    var fence: MarkdownFence? = null
    var i = 0

    fun flushMarkdown() {
        if (markdown.isEmpty()) return
        val raw = markdown.toString()
        val prepared = prepareInlineLatex(raw)
        segments += GfmLatexSegment.Markdown(prepared.first, prepared.second)
        markdown.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]
        val activeFence = fence
        if (activeFence != null) {
            appendLinePreservingSource(markdown, line, i < lines.lastIndex)
            if (closesFence(line, activeFence)) fence = null
            i++
            continue
        }

        val newFence = openingFence(line)
        if (newFence != null) {
            fence = newFence
            appendLinePreservingSource(markdown, line, i < lines.lastIndex)
            i++
            continue
        }

        // Display math is deliberately top-level. Four-space/tab-indented code and nested
        // containers remain entirely under the GFM parser's ownership.
        val opener = if (line == line.trimStart()) when {
            line.startsWith("$$") -> "$$"
            line.startsWith("\\[") -> "\\["
            else -> null
        } else null
        if (opener == null) {
            appendLinePreservingSource(markdown, line, i < lines.lastIndex)
            i++
            continue
        }

        val close = if (opener == "$$") "$$" else "\\]"
        val first = line.trimEnd().removePrefix(opener)
        val sameLineClose = first.indexOf(close)
        if (sameLineClose >= 0 && first.substring(sameLineClose + close.length).isBlank()) {
            val latex = first.substring(0, sameLineClose).trim()
            if (latex.isBlank()) {
                appendLinePreservingSource(markdown, line, i < lines.lastIndex)
            } else {
                flushMarkdown()
                segments += GfmLatexSegment.DisplayMath(latex = latex, raw = line)
            }
            i++
            continue
        }

        var end = i + 1
        var closeAt = -1
        while (end < lines.size) {
            val candidate = lines[end].indexOf(close)
            if (candidate >= 0 && lines[end].substring(candidate + close.length).isBlank()) {
                closeAt = candidate
                break
            }
            end++
        }
        if (end >= lines.size) {
            // Keep an unfinished delimiter in GFM as literal streaming text. It becomes a stable
            // math block only when the closing delimiter arrives.
            appendLinePreservingSource(markdown, line, i < lines.lastIndex)
            i++
            continue
        }
        val latex = buildString {
            if (first.isNotBlank()) append(first.trimStart())
            for (j in i + 1..end) {
                val part = if (j == end) lines[j].substring(0, closeAt) else lines[j]
                if (isNotEmpty()) append('\n')
                append(part)
            }
        }.trim()
        if (latex.isBlank()) {
            for (j in i..end) appendLinePreservingSource(markdown, lines[j], j < lines.lastIndex)
        } else {
            flushMarkdown()
            segments += GfmLatexSegment.DisplayMath(
                latex = latex,
                raw = lines.subList(i, end + 1).joinToString("\n"),
            )
        }
        i = end + 1
    }
    flushMarkdown()
    return segments.ifEmpty { listOf(GfmLatexSegment.Markdown("", emptyList())) }
}

private fun appendLinePreservingSource(out: StringBuilder, line: String, hasNewline: Boolean) {
    out.append(line)
    if (hasNewline) out.append('\n')
}

/** Protects complete inline LaTeX with neutral text tokens before the GFM parser sees the source. */
internal fun prepareInlineLatex(source: String): Pair<String, List<InlineLatex>> {
    val out = StringBuilder(source.length)
    val math = mutableListOf<InlineLatex>()
    val protected = markdownProtectedCharacters(source)
    var tokenPrefix = "ECHOFLOWLATEXPLACEHOLDER"
    while (source.contains(tokenPrefix)) tokenPrefix += "X"
    var i = 0
    while (i < source.length) {
        if (protected[i]) {
            out.append(source[i++])
            continue
        }

        val parenMath = source.startsWith("\\(", i)
        val dollarMath = source[i] == '$' && source.getOrNull(i + 1) != '$' && !isEscaped(source, i)
        val close = when {
            parenMath -> source.indexOfUnprotected("\\)", i + 2, protected)
            dollarMath -> source.findInlineDollarCloseUnprotected(i + 1, protected)
            else -> -1
        }
        if (close >= 0) {
            val contentStart = i + if (parenMath) 2 else 1
            val rawEnd = close + if (parenMath) 2 else 1
            val latex = source.substring(contentStart, close).trim()
            val valid = parenMath || looksLikeMath(latex, source.getOrNull(i - 1), source.getOrNull(rawEnd))
            if (latex.isNotBlank() && valid) {
                val token = "$tokenPrefix${math.size}TOKEN"
                val raw = source.substring(i, rawEnd)
                math += InlineLatex(token, latex, raw)
                out.append(token)
                i = rawEnd
                continue
            }
        }
        out.append(source[i++])
    }
    return out.toString() to math
}

private data class MarkdownFence(val marker: Char, val length: Int)

private fun openingFence(line: String): MarkdownFence? {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > 3 || indent == line.length) return null
    val marker = line[indent]
    if (marker != '`' && marker != '~') return null
    val length = line.runLength(indent, marker)
    if (length < 3) return null
    val suffix = line.substring(indent + length)
    if (marker == '`' && '`' in suffix) return null
    return MarkdownFence(marker, length)
}

private fun closesFence(line: String, fence: MarkdownFence): Boolean {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > 3 || line.getOrNull(indent) != fence.marker) return false
    val length = line.runLength(indent, fence.marker)
    return length >= fence.length && line.substring(indent + length).isBlank()
}

/** Regions whose contents are syntax/code rather than visible Markdown text. */
private fun markdownProtectedCharacters(source: String): BooleanArray {
    val protected = BooleanArray(source.length)
    var fence: MarkdownFence? = null
    var lineStart = 0
    while (lineStart < source.length) {
        val lineEnd = source.indexOf('\n', lineStart).takeIf { it >= 0 } ?: source.length
        val line = source.substring(lineStart, lineEnd)
        val activeFence = fence
        val newFence = if (activeFence == null) openingFence(line) else null
        val indentedCode = activeFence == null && newFence == null &&
            (line.startsWith("    ") || line.startsWith('\t'))
        if (activeFence != null || newFence != null || indentedCode) {
            for (index in lineStart until minOf(lineEnd + 1, source.length)) protected[index] = true
        }
        fence = when {
            activeFence != null && closesFence(line, activeFence) -> null
            activeFence != null -> activeFence
            newFence != null -> newFence
            else -> null
        }
        lineStart = lineEnd + 1
    }

    var i = 0
    while (i < source.length) {
        if (protected[i]) {
            i++
            continue
        }
        if (source[i] == '`') {
            val length = source.runLength(i, '`')
            val close = source.findClosingCodeSpan(i + length, length, protected)
            if (close >= 0) {
                for (index in i until close + length) protected[index] = true
                i = close + length
                continue
            }
        }
        if (source[i] == ']' && source.getOrNull(i + 1) == '(') {
            val close = findLinkDestinationClose(source, i + 1, source.length)
            if (close != null) {
                for (index in i + 1..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        if (source[i] == '<') {
            val close = source.indexOf('>', i + 1)
            if (close >= 0) {
                for (index in i..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        i++
    }
    return protected
}

private fun String.indexOfUnprotected(needle: String, from: Int, protected: BooleanArray): Int {
    var index = indexOf(needle, from)
    while (index >= 0 && protected[index]) index = indexOf(needle, index + needle.length)
    return index
}

private fun String.findClosingCodeSpan(from: Int, length: Int, protected: BooleanArray): Int {
    var index = indexOf('`', from)
    while (index >= 0) {
        val runLength = runLength(index, '`')
        if (!protected[index] && runLength == length) return index
        index = indexOf('`', index + runLength)
    }
    return -1
}

private fun String.findInlineDollarCloseUnprotected(from: Int, protected: BooleanArray): Int {
    var index = from
    while (index < length) {
        if (this[index] == '$' && !protected[index] && !isEscaped(this, index) && getOrNull(index + 1) != '$') {
            return index
        }
        index++
    }
    return -1
}

private fun String.runLength(start: Int, char: Char): Int {
    var end = start
    while (end < length && this[end] == char) end++
    return end - start
}
