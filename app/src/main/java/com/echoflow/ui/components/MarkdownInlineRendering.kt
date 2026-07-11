
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
internal fun InlineMarkdownText(
    text: String,
    color: Color,
    linkColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val nodes = remember(text) { InlineParser(text).parse() }
    val mathNodes = remember(nodes) { nodes.collectMathNodes() }
    val hasMath = mathNodes.isNotEmpty()
    if (!hasMath) {
        val annotated = remember(text, linkColor) { parseInlineStyles(text, linkColor) }
        Text(annotated, style = style.copy(color = color), modifier = modifier)
        return
    }

    val textStyle = style.copy(color = color, lineHeight = lineHeightForInlineMath(style))
    val config = latexConfigFor(style, scale = 0.86f)
    val measurer = rememberLatexMeasurer(config)
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val measured = remember(mathNodes, config, darkTheme) {
        mathNodes.associate { math -> math.id to measurer.measure(math.latex, config, darkTheme) }
    }
    val annotated = remember(nodes, measured, linkColor) {
        buildAnnotatedString {
            appendNodes(nodes, linkColor, measured.keys)
        }
    }
    val inlineContent = remember(mathNodes, measured, config, density, darkTheme) {
        buildMap {
            mathNodes.forEach { math ->
                val dims = measured[math.id] ?: return@forEach
                put(
                    "math_${math.id}",
                    InlineTextContent(
                        placeholder = Placeholder(
                            width = with(density) { dims.widthPx.toSp() },
                            height = with(density) { dims.heightPx.toSp() },
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        Latex(
                            latex = math.latex,
                            config = config,
                            isDarkTheme = darkTheme
                        )
                    }
                )
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = textStyle,
        modifier = modifier
    )
}

internal fun List<InlineNode>.collectMathNodes(): List<InlineNode.Math> {
    val out = mutableListOf<InlineNode.Math>()
    fun visit(nodes: List<InlineNode>) {
        nodes.forEach { node ->
            when (node) {
                is InlineNode.Math -> out.add(node)
                is InlineNode.Span -> visit(node.children)
                is InlineNode.Link -> visit(node.label)
                else -> Unit
            }
        }
    }
    visit(this)
    return out
}

internal fun AnnotatedString.Builder.appendNodes(
    nodes: List<InlineNode>,
    linkColor: Color,
    measuredMathIds: Set<Int>
) {
    nodes.forEach { node ->
        when (node) {
            is InlineNode.Text -> append(node.text)
            is InlineNode.Math -> {
                if (node.id in measuredMathIds) {
                    appendInlineContent("math_${node.id}", node.raw)
                } else {
                    append(node.raw)
                }
            }
            is InlineNode.Span -> withStyle(node.style) {
                appendNodes(node.children, linkColor, measuredMathIds)
            }
            is InlineNode.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color.Gray.copy(alpha = 0.18f),
                    fontWeight = FontWeight.Medium
                )
            ) { append(node.text) }
            is InlineNode.Link -> withLink(
                LinkAnnotation.Url(
                    node.url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) {
                appendNodes(node.label, linkColor, measuredMathIds)
            }
            is InlineNode.Citation -> withLink(
                LinkAnnotation.Url(
                    node.url,
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
            ) { append(" ${node.label} ") }
        }
    }
}

internal fun lineHeightForInlineMath(style: TextStyle): TextUnit {
    val baseSize = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize
    val current = if (style.lineHeight == TextUnit.Unspecified) baseSize * 1.5f else style.lineHeight
    return current * 1.12f
}

@Composable
internal fun latexConfigFor(style: TextStyle, scale: Float): LatexConfig {
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

internal fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color) {
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
internal fun isCitationLabel(label: String): Boolean =
    label.length in 1..3 && label.all { it.isDigit() }

/** Index of a standalone `*` italic marker (not part of `**`, and opening a non-space run). */
internal fun indexOfItalic(text: String, from: Int): Int {
    var p = text.indexOf("*", from)
    while (p != -1) {
        val isDouble = text.getOrNull(p + 1) == '*' || (p > 0 && text[p - 1] == '*')
        val opensWord = text.getOrNull(p + 1)?.isWhitespace() == false
        if (!isDouble && opensWord) return p
        p = text.indexOf("*", p + 1)
    }
    return -1
}
