package com.echoflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.echoflow.data.SearchSource
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.MarkdownText
import org.intellij.markdown.ast.ASTNode

/**
 * Titles for the pages a reply searched, keyed by URL, so a citation peek can name the page
 * instead of only its site. Provided around an assistant reply; empty everywhere else.
 */
val LocalCitationTitles = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Provides [LocalCitationTitles] from the reply's search sources. */
@Composable
fun ProvideCitationTitles(sources: List<SearchSource>, content: @Composable () -> Unit) {
    val titles = remember(sources) { sources.associate { it.url to it.title } }
    CompositionLocalProvider(LocalCitationTitles provides titles, content = content)
}

private data class Peek(val url: String, val anchor: Rect)

/**
 * Answer text whose `[n](url)` citations can be long-pressed to peek at the source — favicon,
 * page title and site — without leaving the reply. A plain tap still opens the website; the
 * release after a peek is swallowed so it doesn't also navigate.
 */
@Composable
internal fun CitationPeekText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    CitationPeekArea(text) { peekModifier, onTextLayout ->
        Text(
            text = text,
            style = style,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
            modifier = modifier.then(peekModifier),
        )
    }
}

/**
 * Markdown paragraph component for the answer renderer: the library's own paragraph, plus the
 * citation peek. Same annotated text and styling as [MarkdownParagraph]; only the gesture is new.
 */
@Composable
internal fun CitationMarkdownParagraph(content: String, node: ASTNode, style: TextStyle) {
    val settings = annotatorSettings()
    val styled = remember(content, node, style) {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            buildMarkdownAnnotatedString(content = content, node = node, annotatorSettings = settings)
            pop()
        }
    }
    CitationPeekArea(styled) { peekModifier, onTextLayout ->
        MarkdownText(
            content = styled,
            node = node,
            modifier = peekModifier,
            style = style,
            onTextLayout = { result, _ -> onTextLayout(result) },
            sourceContent = content,
        )
    }
}

/**
 * Hosts the long-press peek for whatever draws [text]: [content] gets the gesture modifier and a
 * layout callback to hand to its text composable.
 */
@Composable
private fun CitationPeekArea(
    text: AnnotatedString,
    content: @Composable (peekModifier: Modifier, onTextLayout: (TextLayoutResult) -> Unit) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var peek by remember { mutableStateOf<Peek?>(null) }
    val haptics = LocalHapticFeedback.current
    val hasCitations = remember(text) { text.citationRanges().isNotEmpty() }

    val peekModifier = Modifier
        .onGloballyPositioned { coords = it }
        .then(
            if (!hasCitations) Modifier else Modifier.pointerInput(text) {
                awaitEachGesture {
                    // Initial pass: see the press before the link handler inside the text does.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val hit = layout?.let { result -> text.citationAt(result.getOffsetForPosition(down.position)) }
                        ?: return@awaitEachGesture
                    // Ends early (non-null) on a release — a tap, so the link opens — and also when
                    // the finger travels past touch slop, a second finger lands or something else
                    // takes the press: that is a scroll or another gesture, never a peek.
                    val notAPeek = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed || change.isConsumed) break
                            if (event.changes.count { it.pressed } > 1) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        }
                    }
                    if (notAPeek != null) return@awaitEachGesture
                    val result = layout ?: return@awaitEachGesture
                    val box = result.getBoundingBox(hit.start.coerceAtMost(result.layoutInput.text.length - 1))
                    val origin = coords?.boundsInWindow()?.topLeft ?: return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    peek = Peek(hit.item.url, box.translate(origin))
                    // Hold to read; the release closes the peek and is consumed so the link
                    // underneath never sees a click. The finally also closes it when streaming
                    // text restarts this handler mid-hold, which would otherwise strand it open.
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        peek = null
                    }
                }
            },
        )

    content(peekModifier) { layout = it }
    peek?.let { CitationPeekPopup(it) }
}

@Composable
private fun CitationPeekPopup(peek: Peek) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    val title = LocalCitationTitles.current[peek.url]
    val host = hostOf(peek.url)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) }
    val provider = remember(peek.anchor, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val a = peek.anchor
                val x = (a.center.x - popupContentSize.width / 2f).toInt()
                    .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                val above = (a.top - popupContentSize.height - gap).toInt()
                val y = if (above >= gap) above else (a.bottom + gap).toInt()
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cs.inverseSurface,
            contentColor = cs.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .graphicsLayer {
                    alpha = enter.value
                    val s = 0.94f + 0.06f * enter.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - enter.value) * 6.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = faviconUrl(peek.url),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        title ?: host,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (title != null) {
                        Text(
                            host,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.inverseOnSurface.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun AnnotatedString.citationRanges(): List<AnnotatedString.Range<LinkAnnotation.Url>> =
    getLinkAnnotations(0, length).mapNotNull { range ->
        val item = range.item as? LinkAnnotation.Url ?: return@mapNotNull null
        if (isCitationLabel(substring(range.start, range.end).trim())) {
            AnnotatedString.Range(item, range.start, range.end)
        } else {
            null
        }
    }

private fun AnnotatedString.citationAt(offset: Int): AnnotatedString.Range<LinkAnnotation.Url>? =
    citationRanges().firstOrNull { offset in it.start until it.end }
