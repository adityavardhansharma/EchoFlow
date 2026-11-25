package com.echoflow.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.ui.theme.JetBrainsMono
import com.swmansion.enriched.markdown.codehighlight.CodeHighlightPlugin
import com.swmansion.enriched.markdown.codehighlight.compose.CodeHighlightStyleScope
import com.swmansion.enriched.markdown.codehighlight.compose.codeHighlight
import com.swmansion.enriched.markdown.compose.CodeBlockLayout
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.MarkdownOptions
import com.swmansion.enriched.markdown.compose.MarkdownPlugins
import com.swmansion.enriched.markdown.compose.MarkdownStyle
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
 * GitHub-flavoured Markdown and LaTeX in native Enriched Markdown views. A reply is drawn as a
 * column of chunks (see [MarkdownStreamPlanner]): while it streams, finished chunks stay frozen and
 * only the last one is re-parsed, and [streaming] makes the unfinished end render cleanly. The same
 * plan draws a saved reply, so nothing moves when streaming ends. Math may be written as `$…$`,
 * `$$…$$`, `\(…\)`, or `\[…\]`; fenced code is highlighted. A long-press on a link peeks
 * the source at the finger, and a tap opens it.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    val root = LocalView.current
    val renderedStyle = rememberReplyMarkdownStyle(textColor, style)
    var peekUrl by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    // onLinkLongClick reports only the URL. Remember the finger without recomposing on every move.
    val press = remember { LinkPress() }
    val links = remember(context, root) {
        ChunkLinks(
            onClick = { url ->
                peekUrl = null
                openUrl(context, url)
            },
            onLongClick = { url ->
                val next = press.anchor(root.height)
                if (next != null) {
                    anchor = next
                    peekUrl = url
                }
            },
        )
    }
    MarkdownPlugins(LatexMathPlugin, CodeHighlightPlugin) {
        StreamingMarkdown(
            text = text,
            streaming = streaming,
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { press.coordinates = it }
                .pointerInteropFilter { event ->
                    val coordinates = press.coordinates
                    if (
                        coordinates != null &&
                        coordinates.isAttached &&
                        (event.actionMasked == MotionEvent.ACTION_DOWN ||
                            event.actionMasked == MotionEvent.ACTION_MOVE)
                    ) {
                        press.inWindow = coordinates.localToWindow(Offset(event.x, event.y))
                    }
                    false
                },
        ) { chunk -> EnrichedChunk(chunk, renderedStyle, links) }
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

/** What one chunk view draws. Equal specs skip recomposition, so a frozen chunk is never redrawn. */
@Immutable
internal data class MarkdownChunkSpec(
    val source: String,
    val references: String,
    val streamingTail: Boolean,
    val last: Boolean,
)

/**
 * Lays out a reply's plan. [chunk] draws each markdown chunk; registered extensions from
 * [LocalMarkdownBlockExtensions] draw their fenced blocks.
 */
@Composable
internal fun StreamingMarkdown(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    chunk: @Composable (MarkdownChunkSpec) -> Unit,
) {
    val extensions = LocalMarkdownBlockExtensions.current
    val planner = remember(extensions.keys) { MarkdownStreamPlanner(extensions.keys) }
    val plan = remember(planner, text) { planner.plan(text) }
    Column(modifier) {
        plan.blocks.forEachIndexed { index, block ->
            val last = index == plan.blocks.lastIndex
            key(block.key) {
                when (block) {
                    is MarkdownChunk -> chunk(
                        MarkdownChunkSpec(block.source, plan.references, streamingTail = streaming && last, last = last)
                    )
                    is MarkdownExtensionPart -> extensions[block.block.kind]?.Render(block.block, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Immutable
private class ChunkLinks(val onClick: (String) -> Unit, val onLongClick: (String) -> Unit)

@Composable
private fun EnrichedChunk(spec: MarkdownChunkSpec, style: MarkdownStyle, links: ChunkLinks) {
    val markdown = remember(spec.source, spec.references, spec.streamingTail) {
        prepareMarkdownChunk(spec.source, spec.references, spec.streamingTail)
    }
    EnrichedMarkdownText(
        markdown = markdown,
        modifier = Modifier.fillMaxWidth(),
        style = style,
        options = REPLY_MARKDOWN_OPTIONS,
        codeBlockLayout = CodeBlockLayout.Scrollable,
        taskListToggleEnabled = false,
        spoilerOverlay = SpoilerOverlay.Solid(cornerRadius = 4.dp),
        includeLastBlockMargin = !spec.last,
        onLinkClick = links.onClick,
        onLinkLongClick = links.onLongClick,
    )
}

/** GitHub autolinks bare URLs; `==highlight==` carries the HTML `<mark>` subset. */
private val REPLY_MARKDOWN_OPTIONS = MarkdownOptions(admonitions = true, highlight = true, permissiveAutolinks = true)

/**
 * Reply typography on the app's type scale and Material 3 tokens. Prose, lists and quotes share the
 * body face so a reply reads as one column; tinted surfaces are kept to code, where they help scan.
 * Display math sits directly on the page, as it did with the previous renderer.
 */
@Composable
private fun rememberReplyMarkdownStyle(textColor: Color, style: TextStyle): MarkdownStyle {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val materialStyle = rememberMaterialMarkdownStyle()
    return remember(materialStyle, textColor, style, scheme, typography) {
        val bodySize = if (style.fontSize.isSp) style.fontSize else 16.sp
        val bodyLineHeight = if (style.lineHeight.isSp) style.lineHeight else bodySize * 1.5f
        val mutedColor = textColor.copy(alpha = 0.78f)
        materialStyle.merge {
            paragraph {
                color = textColor
                fontSize = bodySize
                fontWeight = style.fontWeight
                fontFamily = style.fontFamily
                lineHeight = bodyLineHeight
            }
            h1 {
                color = textColor
                fontFamily = typography.headlineSmall.fontFamily
                fontWeight = FontWeight.Bold
                fontSize = bodySize * 1.375f
                lineHeight = bodySize * 1.75f
                marginBottom = 10.dp
            }
            h2 {
                color = textColor
                fontFamily = style.fontFamily
                fontWeight = FontWeight.Bold
                fontSize = bodySize * 1.1875f
                lineHeight = bodySize * 1.625f
                marginBottom = 8.dp
            }
            h3 {
                color = textColor
                fontFamily = style.fontFamily
                fontWeight = FontWeight.Bold
                fontSize = bodySize * 1.0625f
                lineHeight = bodyLineHeight
                marginBottom = 6.dp
            }
            h4 {
                color = textColor
                fontFamily = style.fontFamily
                fontWeight = FontWeight.Bold
                fontSize = bodySize
                lineHeight = bodyLineHeight
                marginBottom = 6.dp
            }
            h5 {
                color = mutedColor
                fontFamily = style.fontFamily
                fontWeight = FontWeight.Bold
                fontSize = bodySize * 0.9375f
                lineHeight = bodyLineHeight
                marginBottom = 4.dp
            }
            h6 {
                color = mutedColor
                fontFamily = style.fontFamily
                fontWeight = FontWeight.SemiBold
                fontSize = bodySize * 0.875f
                lineHeight = bodySize * 1.375f
                marginBottom = 4.dp
            }
            list {
                color = textColor
                fontSize = bodySize
                fontWeight = style.fontWeight
                fontFamily = style.fontFamily
                lineHeight = bodyLineHeight
                bulletColor = scheme.onSurfaceVariant
                markerColor = scheme.onSurfaceVariant
                marginStart = 20.dp
                gapWidth = 10.dp
            }
            blockquote {
                color = mutedColor
                fontSize = bodySize
                fontFamily = style.fontFamily
                lineHeight = bodyLineHeight
                backgroundColor = Color.Transparent
                borderColor = scheme.outlineVariant
                borderWidth = 3.dp
                gapWidth = 14.dp
            }
            table {
                color = textColor
                fontFamily = style.fontFamily
                fontSize = bodySize * 0.875f
                lineHeight = bodySize * 1.25f
                headerTextColor = textColor
                headerBackgroundColor = scheme.surfaceContainer
                rowEvenBackgroundColor = Color.Transparent
                rowOddBackgroundColor = scheme.surfaceContainerLow
                borderColor = scheme.outlineVariant
                cornerRadius = 12.dp
            }
            link { color = scheme.primary }
            thematicBreak {
                color = scheme.outlineVariant
                marginTop = 20.dp
                marginBottom = 20.dp
            }
            code {
                fontFamily = JetBrainsMono
                fontSize = bodySize * 0.875f
                color = textColor
                backgroundColor = scheme.surfaceContainerHigh
                borderColor = Color.Transparent
            }
            codeBlock {
                fontFamily = JetBrainsMono
                fontSize = bodySize * 0.8125f
                lineHeight = bodySize * 1.25f
                color = textColor
                backgroundColor = scheme.surfaceContainer
                borderColor = scheme.outlineVariant
                borderWidth = 1.dp
                cornerRadius = 12.dp
                padding = 14.dp
            }
            codeHighlight { if (scheme.surface.luminance() >= 0.5f) lightCodeHighlight() }
            math {
                color = textColor
                backgroundColor = Color.Transparent
                fontSize = bodySize * 1.125f
                padding = 2.dp
                marginTop = 0.dp
                marginBottom = 12.dp
            }
            inlineMath { color = textColor }
        }
    }
}

/** GitHub's light palette; the plugin's default is the dark one, which washes out on a light block. */
private fun CodeHighlightStyleScope.lightCodeHighlight() {
    keyword = Color(0xFFCF222E)
    string = Color(0xFF0A3069)
    number = Color(0xFF0550AE)
    constant = Color(0xFF0550AE)
    comment = Color(0xFF6E7781)
    function = Color(0xFF8250DF)
    type = Color(0xFF953800)
    property = Color(0xFF0550AE)
    tag = Color(0xFF116329)
    attribute = Color(0xFF0550AE)
}

/** Latest finger position for a link peek. Writes here do not recompose. */
private class LinkPress {
    var coordinates: LayoutCoordinates? = null
    var inWindow: Offset? = null

    fun anchor(windowHeight: Int): Rect? {
        inWindow?.let { return pointRect(it) }
        val coordinates = coordinates
        if (coordinates == null || !coordinates.isAttached) return null
        val bounds = coordinates.boundsInWindow()
        val limit = windowHeight.toFloat().coerceAtLeast(1f)
        val top = bounds.top.coerceIn(0f, limit)
        val bottom = bounds.bottom.coerceIn(0f, limit)
        val y = if (bottom > top) (top + bottom) / 2f else top
        return pointRect(Offset(bounds.center.x, y))
    }
}

private fun pointRect(point: Offset) = Rect(point.x, point.y, point.x + 1f, point.y + 1f)
