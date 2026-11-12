package com.echoflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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

/** Long-press peek for a link in the native markdown renderer. */
@Composable
internal fun CitationSourcePeek(url: String, anchor: Rect) {
    CitationPeekPopup(Peek(url, anchor))
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
                val maxY = (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)
                val y = if (above >= gap) above else (a.bottom + gap).toInt().coerceIn(gap, maxY)
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
