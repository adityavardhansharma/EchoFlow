package com.echoflow.weave.compose.render

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.eval.WAction
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.truthy
import com.echoflow.weave.runtime.WeaveBlockController

/** What every renderer gets besides its node (05 §5.4). Immutable per composition pass. */
@Immutable
class RenderCtx(
    val controller: WeaveBlockController,
    val formatter: Formatter,
    val screenHeight: Dp,
    val dark: Boolean,
    /** Animate `enter` for nodes appearing now (false when restored from storage, 04 §2.3). */
    val animateEnter: Boolean,
    val depth: Int = 0,
) {
    fun child() = RenderCtx(controller, formatter, screenHeight, dark, animateEnter, depth + 1)
    fun fmt(v: WValue?): String = runCatching { formatter.auto(v ?: WNull) }.getOrDefault("")
    fun fmt(v: WValue?, spec: String?): String = runCatching { formatter.applyText(v ?: WNull, spec) }.getOrDefault("")
    fun fire(action: WValue?, data: WValue? = null) {
        if (action is WAction || action is WList || action is WFn) controller.fire(action, data)
    }
}

/** Renders one node (05 §5.4); unknown or broken nodes render an error chip (04 §2.8). */
typealias NodeRendererFn = @Composable (node: WNode, ctx: RenderCtx, modifier: Modifier) -> Unit

val LocalRenderCtx = compositionLocalOf<RenderCtx?> { null }

// ---- argument helpers (renderers must be total, 05 §5.3) -------------------------------------------

fun WNode.str(k: String): String? = when (val v = args[k]) { is WStr -> v.v; null, WNull -> null; else -> null }
fun WNode.num(k: String): Double? = (args[k] as? WNum)?.v
fun WNode.bool(k: String, default: Boolean = false): Boolean = args[k]?.let { if (it == WNull) default else it.truthy() } ?: default
fun WNode.word(k: String): String? = args[k].word()
fun WNode.list(k: String): List<WValue>? = (args[k] as? WList)?.items
fun WNode.rec(k: String): WRec? = args[k] as? WRec
fun WNode.node(k: String): WNode? = args[k] as? WNode
fun WNode.has(k: String): Boolean = args[k] != null && args[k] != WNull

private val SELF_GESTURE = setOf("Button", "IconButton", "Item", "Chip", "Fab", "Link", "Toggle", "Check", "Radio", "Flip", "Reveal")

@Composable
fun RenderNode(node: WNode, ctx: RenderCtx, modifier: Modifier = Modifier) {
    val visible = node.args["show"]?.let { if (it == WNull) false else it.truthy() } ?: true
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        RenderVisible(node, ctx, modifier)
    }
}

@Composable
private fun RenderVisible(node: WNode, ctx: RenderCtx, modifier: Modifier) {
    when {
        node.error != null -> ErrorChip(node.error ?: "", modifier)
        node.waiting -> Placeholders.ForNode(node, ctx, modifier)
        else -> {
            val r = Registry.renderers[node.type]
            if (r == null) ErrorChip("Can't show ${node.type}", modifier)
            else {
                // 04 §2.6: nodes whose statement is being rewritten by !ask show a pending shimmer
                val pending = node.sourceStmt.isNotEmpty() && node.sourceStmt in ctx.controller.pendingNames
                val m = modifier.then(commonModifier(node, ctx)).then(if (pending) Modifier.pendingOverlay() else Modifier)
                val badge = node.args["badge"]
                if (badge != null && badge != WNull && badge != WBool.FALSE) {
                    BadgedBox(badge = { BadgeFor(badge, ctx) }) { r(node, ctx, m) }
                } else r(node, ctx, m)
            }
        }
    }
}

@Composable
private fun BadgeFor(v: WValue, ctx: RenderCtx) {
    when (v) {
        WBool.TRUE -> Badge()
        is WNum -> Badge { Text(if (v.v > 999) "999+" else ctx.fmt(v)) }
        else -> Badge { Text(ctx.fmt(v)) }
    }
}

/** Children in order, keyed by position (04 §2.5). */
@Composable
fun RenderChildren(children: List<WNode>, ctx: RenderCtx, modifierFor: (WNode) -> Modifier = { Modifier }) {
    val c = ctx.child()
    children.forEachIndexed { i, child ->
        key(child.args["key"]?.let { c.fmt(it) } ?: "$i:${child.type}") { RenderNode(child, c, modifierFor(child)) }
    }
}

/**
 * The common modifier chain in 04 §2.2 order: semantics/label, width, height, (align is applied
 * by the parent), clip(shape), background/border, gestures, enter animation, padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun commonModifier(node: WNode, ctx: RenderCtx): Modifier {
    val scheme = MaterialTheme.colorScheme
    var m: Modifier = Modifier
    val label = node.str("label")
    val disabled = node.bool("disabled")
    if (label != null || disabled) m = m.semantics {
        if (label != null && node.type !in LABELLED) contentDescription = label
        if (disabled) disabled()
    }
    // width (04 §1.3)
    when (val w = node.args["w"]) {
        is WNum -> m = if (w.v in 0.0..1.0) m.fillMaxWidth(w.v.toFloat()) else m.width(w.v.toFloat().coerceIn(0f, 2000f).dp)
        else -> when (w.word()) { "fill" -> m = m.fillMaxWidth(); "wrap" -> m = m.wrapContentWidth() }
    }
    // height
    if (node.type !in OWN_HEIGHT) TokenResolver.height(node.args["h"], ctx.screenHeight)?.let { m = m.height(it) }
    TokenResolver.height(node.args["minH"], ctx.screenHeight)?.let { m = m.heightIn(min = it) }
    // clip + background + border
    val shapeArg = node.args["shape"]
    val bg = TokenResolver.color(node.args["bg"], scheme, ctx.dark, fill = true)
    val border = node.args["border"]
    if (shapeArg != null && shapeArg != WNull && node.type !in OWN_SHAPE) m = m.clip(TokenResolver.shape(shapeArg))
    val shape = TokenResolver.shape(shapeArg)
    if (bg != null) m = m.background(bg, shape)
    when {
        border == WBool.TRUE -> m = m.border(1.dp, scheme.outlineVariant, shape)
        border != null && border !is WBool && border != WNull -> TokenResolver.color(border, scheme, ctx.dark)?.let { m = m.border(1.dp, it, shape) }
    }
    // gestures
    val tap = node.args["onTap"]; val long = node.args["onLongPress"]; val dbl = node.args["onDoubleTap"]
    if (node.type !in SELF_GESTURE && !disabled && (isAction(tap) || isAction(long) || isAction(dbl))) {
        m = m.combinedClickable(
            onClick = { if (isAction(tap)) ctx.fire(tap) },
            onLongClick = if (isAction(long)) ({ ctx.fire(long) }) else null,
            onDoubleClick = if (isAction(dbl)) ({ ctx.fire(dbl) }) else null,
        )
    }
    if (disabled) m = m.alpha(0.38f)
    // enter animation (04 §2.3)
    val enter = node.word("enter") ?: if (ctx.depth == 1) "fade" else "none"
    if (ctx.animateEnter && enter != "none") m = m.then(enterModifier(enter, node.args["delay"]))
    // inner padding
    if (node.type !in OWN_PADDING) {
        val pad = TokenResolver.space(node.args["pad"], 0.dp)
        val px = node.args["padX"]?.let { TokenResolver.space(it, pad) } ?: pad
        val py = node.args["padY"]?.let { TokenResolver.space(it, pad) } ?: pad
        if (px > 0.dp || py > 0.dp) m = m.padding(horizontal = px, vertical = py)
    }
    return m
}

@Composable
private fun enterModifier(kind: String, delayArg: WValue?): Modifier {
    val delayMs = ((delayArg as? com.echoflow.weave.core.eval.WDur)?.ms ?: 0L).coerceIn(0, 3000)
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        a.animateTo(1f, if (kind == "pop") spring(dampingRatio = Spring.DampingRatioMediumBouncy) else tween(300))
    }
    val density = LocalDensity.current
    return Modifier.graphicsLayer {
        val p = a.value
        alpha = p.coerceIn(0f, 1f)
        when (kind) {
            "rise" -> translationY = with(density) { (16.dp * (1 - p)).toPx() }
            "slide" -> translationX = with(density) { (24.dp * (1 - p)).toPx() }
            "scale" -> { scaleX = 0.92f + 0.08f * p; scaleY = scaleX }
            "pop" -> { scaleX = 0.6f + 0.4f * p; scaleY = scaleX }
        }
    }
}

fun isAction(v: WValue?) = v is WAction || v is WFn || (v is WList && v.items.any { it is WAction })

private val LABELLED = setOf("Button", "Slider", "TextField", "Select", "Segmented", "Toggle", "Check", "Chips", "Stepper", "Metric")
private val OWN_HEIGHT = setOf("Chart", "LineChart", "AreaChart", "BarChart", "PieChart", "DonutChart", "ScatterChart", "Sparkline", "Histogram", "List", "Canvas", "Image", "Map", "Video", "Model3D", "Shader", "Sketch", "Pad2D", "Carousel", "Spacer")
private val OWN_SHAPE = setOf("Button", "Card", "Surface", "Avatar", "IconButton", "Chip", "Fab", "Image")
private val OWN_PADDING = setOf("Card", "Surface", "Section", "Callout", "Button", "Box")

/** 04 §2.6: a moving highlight over a node whose definition is being regenerated. */
@Composable
fun Modifier.pendingOverlay(): Modifier {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pending")
    val x by t.animateFloat(-1f, 2f, androidx.compose.animation.core.infiniteRepeatable(tween(1200)), label = "pending-x")
    val c = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    return this.then(Modifier.drawWithContent {
        drawContent()
        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, c, androidx.compose.ui.graphics.Color.Transparent), startX = size.width * x - size.width / 2, endX = size.width * x + size.width / 2))
    })
}

/** 04 §2.8: a compact chip in place of a node that can't render. */
@Composable
fun ErrorChip(text: String, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier.clip(MaterialTheme.shapes.small).background(s.errorContainer).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = s.onErrorContainer)
    }
}
