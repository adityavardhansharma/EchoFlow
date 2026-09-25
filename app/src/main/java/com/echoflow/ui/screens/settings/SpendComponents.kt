@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.R
import com.echoflow.data.usage.UsageProvider
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/** Tabular digits: every figure on the Spend pages keeps its width as it counts or changes. */
internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private enum class SpendTone { Primary, Secondary, Tertiary }

private data class SpendIdentity(
    val shape: RoundedPolygon,
    val tone: SpendTone,
    @DrawableRes val logo: Int? = null,
    val glyph: ImageVector = Icons.Default.Hub,
)

private fun identity(provider: UsageProvider): SpendIdentity = when (provider) {
    UsageProvider.OpenRouter -> SpendIdentity(MaterialShapes.Cookie9Sided, SpendTone.Primary, glyph = Icons.Default.Hub)
    UsageProvider.OpenAi -> SpendIdentity(MaterialShapes.Cookie6Sided, SpendTone.Secondary, R.drawable.logo_openai)
    UsageProvider.Claude -> SpendIdentity(MaterialShapes.Sunny, SpendTone.Primary, R.drawable.logo_claude)
    UsageProvider.Gemini -> SpendIdentity(MaterialShapes.Clover4Leaf, SpendTone.Tertiary, R.drawable.logo_gemini)
    UsageProvider.XAi -> SpendIdentity(MaterialShapes.Diamond, SpendTone.Primary, R.drawable.logo_xai)
    UsageProvider.Cerebras -> SpendIdentity(MaterialShapes.Pentagon, SpendTone.Secondary, R.drawable.logo_cerebras)
    UsageProvider.Sarvam -> SpendIdentity(MaterialShapes.Flower, SpendTone.Tertiary, glyph = Icons.Default.Translate)
    UsageProvider.Deepgram -> SpendIdentity(MaterialShapes.Cookie4Sided, SpendTone.Secondary, R.drawable.logo_deepgram)
    UsageProvider.Exa -> SpendIdentity(MaterialShapes.Gem, SpendTone.Tertiary, glyph = Icons.Default.TravelExplore)
    UsageProvider.Parallel -> SpendIdentity(MaterialShapes.Pill, SpendTone.Primary, glyph = Icons.Default.Stream)
    UsageProvider.Firecrawl -> SpendIdentity(MaterialShapes.Burst, SpendTone.Tertiary, glyph = Icons.Default.LocalFireDepartment)
}

internal fun spendShape(provider: UsageProvider): RoundedPolygon = identity(provider).shape

/**
 * A provider's mark in a shaped, theme-toned medallion — the same language as [com.echoflow.ui
 * .components.ProviderMark], so the Spend pages read as part of the app rather than a wall of
 * brand colours.
 */
@Composable
internal fun SpendMark(provider: UsageProvider, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val id = identity(provider)
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (id.tone) {
        SpendTone.Primary -> cs.primaryContainer to cs.onPrimaryContainer
        SpendTone.Secondary -> cs.secondaryContainer to cs.onSecondaryContainer
        SpendTone.Tertiary -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
    Box(
        modifier.size(size).clip(RoundedPolygonShape(id.shape)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        val logo = id.logo
        if (logo != null) {
            Icon(painterResource(logo), provider.label, Modifier.size(size * 0.54f), tint = content)
        } else {
            Icon(id.glyph, provider.label, Modifier.size(size * 0.48f), tint = content)
        }
    }
}

/**
 * One colour per provider in the stacked chart and the matching dot on its row: the strong theme
 * roles first, then softer versions of them, so the biggest spender always gets the boldest.
 */
@Composable
internal fun shareColors(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return listOf(
        cs.primary, cs.tertiary, cs.secondary,
        cs.primary.copy(alpha = 0.5f), cs.tertiary.copy(alpha = 0.5f), cs.secondary.copy(alpha = 0.5f),
        cs.outline,
    )
}

/**
 * The page hero: a tonal slab with a big figure, a slowly turning shape behind it, and whatever
 * the page wants underneath (share bar, meter). The figure counts to its new value when the
 * window changes.
 */
@Composable
internal fun SpendHero(
    label: String,
    pill: String,
    decoration: RoundedPolygon,
    modifier: Modifier = Modifier,
    figure: @Composable () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val spin by rememberInfiniteTransition(label = "spendHeroSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "spendHeroSpinAngle",
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer,
        contentColor = cs.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 56.dp, y = (-48).dp)
                    .size(200.dp)
                    .rotate(spin)
                    .clip(RoundedPolygonShape(decoration))
                    .background(cs.primary.copy(alpha = 0.06f)),
            )
            Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
                        Text(
                            pill,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s))
                figure()
                content()
            }
        }
    }
}

/** `$12` large and `.40` smaller, baseline-aligned, counting to [amount]. */
@Composable
internal fun HeroDollars(amount: Double) {
    val animated by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "heroDollars",
    )
    val (whole, cents) = SpendFormat.usdParts(if (amount == 0.0) 0.0 else animated.toDouble())
    Row {
        Text(
            whole,
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            cents,
            style = MaterialTheme.typography.headlineMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** A non-dollar hero figure: `1.2M` with its unit beside it. */
@Composable
internal fun HeroFigure(value: String, unit: String) {
    Row {
        Text(
            value,
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            unit,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Spend over the window as bars, each stacked by series in [colors]. Tapping a bar reads out its
 * bucket; tapping it again clears the selection. Bars grow in whenever [animationKey] changes.
 */
@Composable
internal fun SpendChart(
    timeline: SpendTimeline,
    colors: List<Color>,
    format: (Double) -> String,
    animationKey: Any?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val totals = timeline.totals
    val count = totals.size.coerceAtLeast(1)
    var selected by remember(timeline.step, timeline.starts.firstOrNull(), count) { mutableStateOf<Int?>(null) }
    val grow = remember { Animatable(0f) }
    val growSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(animationKey) {
        grow.snapTo(0f)
        grow.animateTo(1f, growSpec)
    }
    val ceiling = SpendBuckets.niceCeiling(totals.maxOrNull() ?: 0.0)
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.tabular().copy(color = cs.onSurfaceVariant)
    val grid = cs.outlineVariant
    val stub = cs.onSurface.copy(alpha = 0.08f)

    Column(modifier.fillMaxWidth()) {
        val readoutIndex = selected ?: totals.indices.maxByOrNull { totals[it] }?.takeIf { totals[it] > 0 }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                when {
                    timeline.isEmpty -> "Nothing itemised in this period yet"
                    selected != null -> SpendBuckets.label(timeline.step, timeline.starts[selected!!], long = true)
                    else -> "Busiest · " + SpendBuckets.label(timeline.step, timeline.starts[readoutIndex!!], long = true)
                },
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (readoutIndex != null && !timeline.isEmpty) {
                Text(format(totals[readoutIndex]), style = MaterialTheme.typography.titleSmall.tabular(), color = cs.onSurface)
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .pointerInput(count) {
                    detectTapGestures { offset ->
                        val index = (offset.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
                        selected = if (selected == index || totals.getOrElse(index) { 0.0 } <= 0.0) null else index
                    }
                },
        ) {
            val top = axisStyle.fontSize.toPx() + 6.dp.toPx()
            val chartHeight = size.height - top
            // Gridlines at the ceiling and half of it, with their values riding on the line.
            listOf(1.0, 0.5).forEach { fraction ->
                val y = top + chartHeight * (1f - fraction.toFloat())
                drawLine(grid.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                val text = measurer.measure(format(ceiling * fraction), axisStyle)
                drawText(text, topLeft = Offset(size.width - text.size.width, y - text.size.height - 2.dp.toPx()))
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

            val slot = size.width / count
            val barWidth = (slot * if (count > 20) 0.64f else 0.52f).coerceAtLeast(2.dp.toPx())
            val radius = CornerRadius(minOf(barWidth / 2f, 8.dp.toPx()))
            val seam = 1.5.dp.toPx()
            timeline.values.forEachIndexed { index, series ->
                val left = index * slot + (slot - barWidth) / 2f
                val total = series.sum()
                if (total <= 0.0) {
                    drawRoundRect(stub, Offset(left, size.height - 3.dp.toPx()), Size(barWidth, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                    return@forEachIndexed
                }
                val height = (total / ceiling).toFloat() * chartHeight * grow.value
                val barTop = size.height - height
                val alpha = if (selected == null || selected == index) 1f else 0.35f
                val shape = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = barTop, right = left + barWidth, bottom = size.height,
                            topLeftCornerRadius = radius, topRightCornerRadius = radius,
                            bottomLeftCornerRadius = CornerRadius(2.dp.toPx()), bottomRightCornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    )
                }
                clipPath(shape) {
                    var bottom = size.height
                    series.forEachIndexed { s, value ->
                        if (value <= 0.0) return@forEachIndexed
                        val h = (value / total).toFloat() * height
                        val color = colors[s.coerceAtMost(colors.lastIndex)]
                        drawRect(color.copy(alpha = color.alpha * alpha), Offset(left, bottom - h), Size(barWidth, (h - seam).coerceAtLeast(h * 0.6f)))
                        bottom -= h
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        if (timeline.starts.isNotEmpty()) {
            val last = timeline.starts.lastIndex
            Row {
                listOf(0, last / 2, last).distinct().forEachIndexed { i, index ->
                    if (i > 0) Spacer(Modifier.weight(1f))
                    Text(SpendBuckets.label(timeline.step, timeline.starts[index]), style = axisStyle)
                }
            }
        }
    }
}

/** The one window filter, as a connected button group. Compact padding keeps "All time" whole. */
@Composable
internal fun SpendWindowFilter(selected: SpendWindow, onSelect: (SpendWindow) -> Unit, modifier: Modifier = Modifier) {
    val options = SpendWindow.entries
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, window ->
            ToggleButton(
                checked = selected == window,
                onCheckedChange = { if (it) onSelect(window) },
                modifier = Modifier.weight(1f).height(48.dp).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = Spacing.xs),
            ) {
                Text(window.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** A small labelled figure inside a hero. */
@Composable
internal fun RowScope.StatTile(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.tabular(), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/** A right-edge fade over horizontally scrolled content, shown while more is off screen. */
internal fun Modifier.fadeTrailingEdge(scroll: ScrollState, color: Color, width: Dp = 28.dp): Modifier = drawWithContent {
    drawContent()
    if (scroll.canScrollForward) {
        val w = width.toPx()
        drawRect(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
        )
    }
}

/** [SettingsPageScaffold] for pages whose body is a lazy list (long request logs). */
@Composable
internal fun SpendLazyScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.base,
                end = Spacing.base,
                top = pad.calculateTopPadding() + Spacing.s,
                bottom = pad.calculateBottomPadding() + Spacing.xxl,
            ),
            content = content,
        )
    }
}

/** A quiet explanatory line with an icon, used for notes under heroes and at page ends. */
@Composable
internal fun SpendNote(text: String, modifier: Modifier = Modifier, leading: (@Composable BoxScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.Top) {
        if (leading != null) {
            Box(Modifier.padding(top = 1.dp, end = Spacing.s).size(18.dp), contentAlignment = Alignment.Center, content = leading)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
