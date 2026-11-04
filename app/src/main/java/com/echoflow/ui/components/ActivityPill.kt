@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.SearchSource
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** One reasoning or web-search step inside an [ActivityPill], in the order the model took it. */
sealed interface ActivityStep {
    val active: Boolean

    data class Thought(val text: String, override val active: Boolean) : ActivityStep

    data class Search(
        val query: String,
        val sources: List<SearchSource>,
        override val active: Boolean,
    ) : ActivityStep
}

// A 56dp-tall pill with 28dp corners is already a stadium, so opening it only adds height —
// the ends never reshape. Inner circles sit 8dp in (12dp for the live orb) to stay concentric.
private val PillShape = RoundedCornerShape(28.dp)
private val PillHeight = 56.dp
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val ThinkingShapes = listOf(
    MaterialShapes.Circle,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
)
private const val TRAIL_MAX = 5

/**
 * Every consecutive reasoning and web-search step of a reply, folded into one surface.
 *
 * Live, it is a capsule that hugs what the model is doing right now — a morphing orb while it
 * thinks, a sonar globe while it searches, the query typing itself out and sources popping in —
 * and springs wider as that content grows. Done, it settles full width into a summary whose step
 * trail shows what happened in order. Tapping opens a timeline where each step expands on its own.
 * Colour is reserved for the live moment; everything settled is neutral.
 */
@Composable
fun ActivityPill(steps: List<ActivityStep>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val live = steps.any { it.active }
    val currentIndex = steps.indexOfLast { it.active }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var openStep by rememberSaveable { mutableIntStateOf(-1) }

    // The finish moment only plays for a pill watched going live → done, never for history.
    var wasLive by remember { mutableStateOf(live) }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(live) {
        if (wasLive && !live) celebrate = true
        wasLive = live
    }
    val sheen = remember { Animatable(0f) }
    LaunchedEffect(celebrate) {
        if (celebrate && !reducedMotion) {
            delay(120)
            sheen.animateTo(1f, tween(1300, easing = Emphasized))
        }
    }

    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        while (live) {
            elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000
            delay(1000)
        }
    }

    val compact = live && !expanded
    val sheenTint = cs.primary.copy(alpha = if (cs.surface.luminance() < 0.5f) 0.26f else 0.16f)
    Surface(
        shape = PillShape,
        color = cs.surfaceContainer,
        modifier = modifier
            .animateContentSize(
                if (reducedMotion) snap() else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            )
            .then(if (compact) Modifier else Modifier.fillMaxWidth()),
    ) {
        Column(
            Modifier.drawWithContent {
                drawContent()
                val p = sheen.value
                if (p > 0f && p < 1f) {
                    val band = size.width * 0.45f
                    val x = -band + (size.width + band) * p
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, sheenTint, Color.Transparent),
                            startX = x,
                            endX = x + band,
                        ),
                    )
                }
            },
        ) {
            val toggle = { expanded = !expanded }
            if (live) {
                LiveHeader(
                    steps = steps,
                    currentIndex = currentIndex,
                    compact = compact,
                    elapsedSeconds = elapsed,
                    reducedMotion = reducedMotion,
                    expanded = expanded,
                    onClick = toggle,
                )
            } else {
                DoneHeader(steps = steps, expanded = expanded, celebrate = celebrate, onClick = toggle)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(480, easing = Emphasized), expandFrom = Alignment.Top) +
                    fadeIn(tween(280)),
                exit = shrinkVertically(tween(320, easing = EmphasizedAccelerate)) + fadeOut(tween(160)),
            ) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = cs.outlineVariant)
                    Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                        steps.forEachIndexed { index, step ->
                            StepRow(
                                step = step,
                                index = index,
                                isLast = index == steps.lastIndex,
                                open = openStep == index && !step.active,
                                reducedMotion = reducedMotion,
                                onToggle = { openStep = if (openStep == index) -1 else index },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Headers ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveHeader(
    steps: List<ActivityStep>,
    currentIndex: Int,
    compact: Boolean,
    elapsedSeconds: Long,
    reducedMotion: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val current = steps[currentIndex]
    val label = when (current) {
        is ActivityStep.Thought -> "Thinking"
        is ActivityStep.Search -> "Searching the web for ${current.query}"
    }
    Row(
        Modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .height(PillHeight)
            .clip(PillShape)
            .clickable(onClickLabel = if (expanded) "Hide steps" else "Show steps", onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(spring(0.55f, Spring.StiffnessMediumLow), initialScale = 0.4f)) togetherWith
                    fadeOut(tween(120))
            },
            label = "activity-orb",
        ) { index ->
            when (steps.getOrNull(index)) {
                is ActivityStep.Search -> SonarOrb(32.dp, reducedMotion)
                else -> ThinkingOrb(32.dp, reducedMotion)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    (slideInVertically(tween(360, easing = Emphasized)) { it / 2 } + fadeIn(tween(240))) togetherWith
                        (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)))
                },
                label = "activity-title",
            ) { index ->
                Text(
                    if (steps.getOrNull(index) is ActivityStep.Search) "Searching" else "Thinking",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 1,
                )
            }
            when (current) {
                is ActivityStep.Thought -> ThoughtTicker(current.text)
                is ActivityStep.Search -> TypedQuery(current.query, reducedMotion)
            }
        }
        if (!compact) Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        when (current) {
            is ActivityStep.Thought -> Text(
                "${elapsedSeconds}s",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = cs.onSurfaceVariant,
            )
            is ActivityStep.Search -> if (current.sources.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaviconStack(current.sources, 18.dp, cs.surfaceContainer, popIn = !reducedMotion)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${current.sources.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneHeader(
    steps: List<ActivityStep>,
    expanded: Boolean,
    celebrate: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val searches = steps.count { it is ActivityStep.Search }
    val thoughts = steps.count { it is ActivityStep.Thought }
    val sources = steps.filterIsInstance<ActivityStep.Search>().flatMap { it.sources }.distinctBy { it.url }
    val title = when {
        searches > 1 -> "Searched $searches times"
        searches == 1 -> "Searched the web"
        else -> "Thought it through"
    }
    val tail = when {
        searches > 0 && thoughts > 0 -> if (thoughts == 1) "1 thought" else "$thoughts thoughts"
        searches > 0 -> if (sources.size == 1) "1 source" else "${sources.size} sources"
        thoughts > 1 -> "$thoughts thoughts"
        else -> "Tap to read"
    }
    val chevron by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "activity-chevron",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(PillHeight)
            .clip(PillShape)
            .clickable(onClickLabel = if (expanded) "Hide steps" else "Show steps", onClick = onClick)
            .semantics(mergeDescendants = true) {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepTrail(steps, celebrate)
                Spacer(Modifier.width(6.dp))
                Text(
                    tail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (sources.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            FaviconStack(sources, 20.dp, cs.surfaceContainer, popIn = celebrate)
            Spacer(Modifier.width(6.dp))
            Text(
                "${sources.size}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(40.dp).background(cs.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(chevron),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

/** The settled summary: one 14dp glyph per step in the order taken, the last five shown. */
@Composable
private fun StepTrail(steps: List<ActivityStep>, celebrate: Boolean) {
    val cs = MaterialTheme.colorScheme
    val shown = steps.takeLast(TRAIL_MAX)
    val hidden = steps.size - shown.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (hidden > 0) {
            Text(
                "+$hidden",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = cs.onSurfaceVariant,
            )
        }
        shown.forEachIndexed { index, step ->
            if (index > 0) {
                Box(Modifier.size(width = 4.dp, height = 1.5.dp).background(cs.outline, RoundedCornerShape(1.dp)))
            }
            Icon(
                stepIcon(step),
                contentDescription = null,
                modifier = Modifier.size(14.dp).popIn(enabled = celebrate, delayMillis = 80L + index * 60L),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

// ── Live pieces ────────────────────────────────────────────────────────────────────────────

/** Thinking: the Expressive loading shapes morphing in primary — circle, cookie, sunny, clover. */
@Composable
private fun ThinkingOrb(diameter: Dp, reducedMotion: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    if (reducedMotion) {
        val shape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }
        Box(Modifier.size(diameter).padding(diameter * 0.12f).clip(shape).background(primary))
    } else {
        LoadingIndicator(Modifier.size(diameter), color = primary, polygons = ThinkingShapes)
    }
}

/** Searching: a primary globe sending out two staggered echo rings — the EchoFlow sonar. */
@Composable
private fun SonarOrb(diameter: Dp, reducedMotion: Boolean) {
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val ring = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "sonar")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = Emphasized), RepeatMode.Restart),
            label = "sonar-ring",
        )
        p
    }
    val strokePx = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        Modifier
            .size(diameter)
            .drawBehind {
                if (!reducedMotion) {
                    listOf(ring, (ring + 0.5f) % 1f).forEach { f ->
                        drawCircle(
                            color = primary.copy(alpha = 0.7f * (1f - f)),
                            radius = size.minDimension / 2f * (0.5f + 1.05f * f),
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(diameter * 0.75f).background(primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Language, null, Modifier.size(diameter * 0.5f), tint = cs.onPrimary)
        }
    }
}

/** The newest words of the reasoning, right-aligned, older ones fading off to the left. */
@Composable
private fun ThoughtTicker(text: String) {
    val tail = remember(text) { plain(text).takeLast(160) }
    Box(
        Modifier
            .width(180.dp)
            .height(16.dp)
            .clipToBounds()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.4f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            tail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true),
        )
    }
}

/** The search query typing itself out, so the capsule visibly grows as the model asks. */
@Composable
private fun TypedQuery(query: String, reducedMotion: Boolean) {
    val cs = MaterialTheme.colorScheme
    var shown by remember(query) { mutableIntStateOf(if (reducedMotion) query.length else 0) }
    LaunchedEffect(query) {
        while (shown < query.length) {
            delay(26)
            shown += 1
        }
    }
    val caret = if (shown < query.length && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "caret")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "caret-alpha",
        )
        a
    } else {
        0f
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            query.take(shown),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (shown < query.length) {
            Box(
                Modifier
                    .padding(start = 1.dp)
                    .size(width = 1.5.dp, height = 13.dp)
                    .graphicsLayer { alpha = caret }
                    .background(cs.primary),
            )
        }
    }
}

// ── Timeline ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepRow(
    step: ActivityStep,
    index: Int,
    isLast: Boolean,
    open: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(70L + index * 45L)
        enter.animateTo(1f, tween(420, easing = Emphasized))
    }
    val chevron by animateFloatAsState(
        if (open) 180f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "step-chevron",
    )
    val rail = cs.outlineVariant
    val density = LocalDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * with(density) { 6.dp.toPx() }
            }
            .drawBehind {
                // The rail runs from under this node to the next one, through any open body.
                if (!isLast) {
                    val x = 20.dp.toPx()
                    val top = (10 + 28 + 4).dp.toPx()
                    drawLine(rail, Offset(x, top), Offset(x, size.height + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(40.dp).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when {
                    step.active && step is ActivityStep.Thought -> ThinkingOrb(28.dp, reducedMotion)
                    step.active && step is ActivityStep.Search -> SonarOrb(28.dp, reducedMotion)
                    else -> Box(
                        Modifier.fillMaxSize().background(cs.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(stepIcon(step), null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !step.active, onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stepTitle(step),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stepMeta(step),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step is ActivityStep.Search && !step.active && !open && step.sources.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FaviconStack(step.sources, 16.dp, cs.surfaceContainer, popIn = false)
                }
                if (!step.active) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).rotate(chevron),
                        tint = cs.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(420, easing = Emphasized)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(260, easing = EmphasizedAccelerate)) + fadeOut(tween(140)),
            ) {
                when (step) {
                    is ActivityStep.Thought -> MarkdownText(
                        step.text,
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, bottom = 12.dp),
                        textColor = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is ActivityStep.Search -> SourceList(step.sources)
                }
            }
        }
    }
}

@Composable
private fun SourceList(sources: List<SearchSource>) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        Modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sources.forEachIndexed { index, source ->
            Surface(
                onClick = { openUrl(context, source.url) },
                shape = groupedItemShape(index, sources.size, large = 16.dp, small = 4.dp),
                color = cs.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = faviconUrl(source.url),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            source.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            hostOf(source.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Shared bits ────────────────────────────────────────────────────────────────────────────

/** Up to three favicons, one per site, overlapping with a ring in the surface colour. */
@Composable
private fun FaviconStack(sources: List<SearchSource>, diameter: Dp, ring: Color, popIn: Boolean) {
    val sites = remember(sources) { sources.distinctBy { hostOf(it.url) }.take(3) }
    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
        sites.forEachIndexed { index, source ->
            Box(
                Modifier
                    .size(diameter)
                    .popIn(enabled = popIn, delayMillis = index * 60L)
                    .background(ring, CircleShape)
                    .padding(2.dp),
            ) {
                AsyncImage(
                    model = faviconUrl(source.url),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}

/** Springs an element in from 40% once, after [delayMillis]; inert when [enabled] is false. */
@Composable
private fun Modifier.popIn(enabled: Boolean, delayMillis: Long = 0L): Modifier {
    val scale = remember { Animatable(if (enabled) 0.4f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(delayMillis)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = ((scale.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
    }
}

private fun stepIcon(step: ActivityStep): ImageVector = when (step) {
    is ActivityStep.Thought -> Icons.Rounded.AutoAwesome
    is ActivityStep.Search -> Icons.Rounded.Language
}

private fun stepTitle(step: ActivityStep): String = when (step) {
    is ActivityStep.Thought -> if (step.active) "Thinking" else "Thought"
    is ActivityStep.Search -> step.query.ifBlank { "Web search" }
}

private fun stepMeta(step: ActivityStep): String = when (step) {
    is ActivityStep.Thought -> if (step.active) plain(step.text).takeLast(80) else plain(step.text)
    is ActivityStep.Search -> {
        val sites = step.sources.map { hostOf(it.url) }.distinct()
        when {
            step.active && step.sources.isEmpty() -> "Searching…"
            step.active -> "Reading ${step.sources.size} sources"
            step.sources.isEmpty() -> "No results"
            else -> buildString {
                append(if (step.sources.size == 1) "1 source" else "${step.sources.size} sources")
                append(" · ")
                append(sites.take(2).joinToString(", "))
                if (sites.size > 2) append(" +${sites.size - 2}")
            }
        }
    }
}

/** Reasoning as a single plain line: markdown marks and line breaks stripped. */
private fun plain(text: String): String =
    text.replace(Regex("[*_`#>]+"), "").replace(Regex("\\s+"), " ").trim()
