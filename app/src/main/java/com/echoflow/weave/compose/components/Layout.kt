package com.echoflow.weave.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WNode

/** Layout components, 04 §4. */
object Layout {

    fun gap(node: WNode, default: Dp = 8.dp): Dp = TokenResolver.space(node.args["gap"], default)

    private fun ColumnScope.alignChild(child: WNode): Modifier = when (child.word("align")) {
        "center" -> Modifier.align(Alignment.CenterHorizontally)
        "end" -> Modifier.align(Alignment.End)
        "start" -> Modifier.align(Alignment.Start)
        else -> Modifier
    }

    private fun RowScope.alignChild(child: WNode): Modifier {
        val base = when (child.word("align")) {
            "top" -> Modifier.align(Alignment.Top)
            "bottom" -> Modifier.align(Alignment.Bottom)
            "center" -> Modifier.align(Alignment.CenterVertically)
            else -> Modifier
        }
        val span = child.num("span")
        return if (span != null && span > 0) base.weight(span.toFloat()) else base
    }

    private fun horizontalAlignment(w: String?) = when (w) {
        "center" -> Alignment.CenterHorizontally; "end" -> Alignment.End; else -> Alignment.Start
    }

    private fun verticalAlignment(w: String?) = when (w) {
        "top" -> Alignment.Top; "bottom" -> Alignment.Bottom; else -> Alignment.CenterVertically
    }

    private fun justify(w: String?, gap: Dp): Arrangement.Horizontal = when (w) {
        "center" -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
        "end" -> Arrangement.spacedBy(gap, Alignment.End)
        "between" -> Arrangement.SpaceBetween
        "around" -> Arrangement.SpaceAround
        "evenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.spacedBy(gap)
    }

    @Composable
    fun ColumnR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val stretch = (node.word("align") ?: "stretch") == "stretch"
        val m = if (node.bool("scroll")) modifier.verticalScroll(rememberScrollState()) else modifier
        Column(
            m.then(if (stretch) Modifier.fillMaxWidth() else Modifier),
            verticalArrangement = Arrangement.spacedBy(gap(node)),
            horizontalAlignment = horizontalAlignment(node.word("align")),
        ) {
            RenderChildren(node.children, ctx) { child -> alignChild(child).then(if (stretch && child.args["w"] == null) Modifier.fillMaxWidth() else Modifier) }
        }
    }

    /** Row: lays children out in a row; with `wrap: auto` it flows onto more lines when they don't fit (04 §4). */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun RowR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val gap = gap(node)
        val anyWeighted = node.children.any { (it.num("span") ?: 0.0) > 0 }
        if ((node.word("wrap") ?: "auto") == "never" || anyWeighted) {
            Row(modifier.fillMaxWidth(), horizontalArrangement = justify(node.word("justify"), gap), verticalAlignment = verticalAlignment(node.word("align"))) {
                RenderChildren(node.children, ctx) { alignChild(it) }
            }
        } else {
            FlowRow(
                modifier.fillMaxWidth(),
                horizontalArrangement = justify(node.word("justify"), gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                itemVerticalAlignment = verticalAlignment(node.word("align")),
            ) {
                RenderChildren(node.children, ctx)
            }
        }
    }

    @Composable
    fun BoxR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val a = when (node.word("contentAlign")) {
            "top" -> Alignment.TopCenter; "topEnd" -> Alignment.TopEnd; "start" -> Alignment.CenterStart
            "center" -> Alignment.Center; "end" -> Alignment.CenterEnd; "bottomStart" -> Alignment.BottomStart
            "bottom" -> Alignment.BottomCenter; "bottomEnd" -> Alignment.BottomEnd; else -> Alignment.TopStart
        }
        val pad = TokenResolver.space(node.args["pad"], 0.dp)
        Box(modifier.padding(pad), contentAlignment = a) { RenderChildren(node.children, ctx) }
    }

    @Composable
    fun GridR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val cols = (node.num("cols") ?: 2.0).toInt().coerceIn(1, 6)
        val minW = node.num("minW")
        val gap = gap(node)
        // 04 §4: rowGap defaults to gap.
        val rowGap = TokenResolver.space(node.args["rowGap"], gap)
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val n = if (minW != null && minW > 0) maxOf(1, (maxWidth.value / (minW.toFloat() * 8f)).toInt()).coerceAtMost(6) else cols
            Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                node.children.chunked(n).forEach { rowKids ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowKids.forEach { RenderNode(it, ctx.child(), Modifier.weight(1f)) }
                        repeat(n - rowKids.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun FlowR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val gap = gap(node)
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            RenderChildren(node.children, ctx)
        }
    }

    @Composable
    fun CarouselR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val itemW = TokenResolver.height(node.args["itemW"], ctx.screenHeight) ?: 240.dp
        val gap = gap(node)
        if (node.bool("pager")) {
            val state = rememberPagerState { node.children.size }
            HorizontalPager(state, modifier.fillMaxWidth(), pageSpacing = gap) { i ->
                node.children.getOrNull(i)?.let { RenderNode(it, ctx.child()) }
            }
        } else {
            LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                itemsIndexed(node.children) { _, child -> RenderNode(child, ctx.child(), Modifier.width(itemW)) }
            }
        }
    }

    @Composable
    fun SpacerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val size = TokenResolver.space(node.args["size"] ?: node.args["h"], 16.dp)
        Spacer(modifier.height(size).width(size))
    }

    @Composable
    fun DividerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        if (node.bool("vertical")) VerticalDivider(modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        else HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)
    }

    @Composable
    fun CardR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val shape = TokenResolver.shape(node.args["shape"], MaterialTheme.shapes.large)
        val pad = TokenResolver.space(node.args["pad"], 16.dp)
        val tone = TokenResolver.tone(node.word("tone"), node.word("emphasis"), MaterialTheme.colorScheme, ctx.dark)
        val content: @Composable ColumnScope.() -> Unit = {
            Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap(node))) {
                RenderChildren(node.children, ctx) { if (it.args["w"] == null) Modifier.fillMaxWidth() else Modifier }
            }
        }
        val m = modifier.fillMaxWidth()
        when (node.word("style") ?: "filled") {
            "outlined" -> OutlinedCard(m, shape = shape, content = content)
            "elevated" -> ElevatedCard(m, shape = shape, content = content)
            "tonal" -> Card(m, shape = shape, colors = CardDefaults.cardColors(containerColor = tone?.container ?: MaterialTheme.colorScheme.secondaryContainer, contentColor = tone?.content ?: MaterialTheme.colorScheme.onSecondaryContainer), content = content)
            else -> Card(m, shape = shape, colors = CardDefaults.cardColors(containerColor = tone?.container ?: MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = tone?.content ?: MaterialTheme.colorScheme.onSurface), content = content)
        }
    }

    @Composable
    fun SurfaceR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val pair = TokenResolver.tone(node.word("tone") ?: "neutral", node.word("emphasis") ?: "low", MaterialTheme.colorScheme, ctx.dark)
        val pad = TokenResolver.space(node.args["pad"], 12.dp)
        Surface(
            modifier, shape = TokenResolver.shape(node.args["shape"], MaterialTheme.shapes.medium),
            color = pair?.container ?: MaterialTheme.colorScheme.surface, contentColor = pair?.content ?: MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap(node))) { RenderChildren(node.children, ctx) }
        }
    }

    /** Section: a titled group, collapsible when `collapsible` (UI state). */
    @Composable
    fun SectionR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val collapsible = node.bool("collapsible")
        var open by rememberSaveable { mutableStateOf(node.bool("open", true)) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                (if (collapsible) Modifier.clickable { open = !open } else Modifier).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    node.str("title")?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    node.str("subtitle")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (collapsible) Icon(Icons.Filled.KeyboardArrowDown, if (open) "Collapse" else "Expand", Modifier.rotate(if (open) 180f else 0f))
            }
            if (!collapsible || open) Column(verticalArrangement = Arrangement.spacedBy(gap(node))) { RenderChildren(node.children, ctx) }
        }
    }

    /** Split: two panes side by side on wide screens, stacked on compact (04 §4). */
    @Composable
    fun SplitR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val ratio = (node.num("ratio") ?: 0.5).toFloat().coerceIn(0.2f, 0.8f)
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val kids = node.children
            if (maxWidth >= 600.dp && kids.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap(node, 16.dp))) {
                    RenderNode(kids[0], ctx.child(), Modifier.weight(ratio))
                    RenderNode(kids[1], ctx.child(), Modifier.weight(1 - ratio))
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(gap(node, 16.dp))) { RenderChildren(kids, ctx) }
        }
    }

    /** Sticky: content that stays visible; inside a chat block it renders in place (04 §4, phase 2). */
    @Composable
    fun StickyR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        Column(modifier.fillMaxWidth()) { RenderChildren(node.children, ctx) }
    }
}
