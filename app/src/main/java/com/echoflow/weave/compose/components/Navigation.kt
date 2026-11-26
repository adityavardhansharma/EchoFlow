package com.echoflow.weave.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.node
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.truthy

/** Navigation inside a block, 04 §9. */
object Navigation {

    private fun index(node: WNode, key: String, size: Int): Int =
        ((node.args[key] as? WNum)?.v ?: 0.0).toInt().coerceIn(0, maxOf(0, size - 1))

    private fun writeIndex(node: WNode, key: String, ctx: RenderCtx, i: Int) {
        node.places[key]?.let { ctx.controller.input(it, WNum.of(i), node.args["onChange"]) }
    }

    @Composable
    fun TabsR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val labels = node.list("labels")?.map { ctx.fmt(it) } ?: node.children.indices.map { "Tab ${it + 1}" }
        val icons = node.list("icons")
        val sel = index(node, "index", labels.size)
        val scrollable = when (val s = node.args["scrollable"]) { is com.echoflow.weave.core.eval.WBool -> s.v; else -> labels.size > 4 || labels.sumOf { it.length } > 32 }
        val tabs = @Composable {
            labels.forEachIndexed { i, l ->
                Tab(sel == i, { writeIndex(node, "index", ctx, i) }, text = { Text(l, maxLines = 1) },
                    icon = icons?.getOrNull(i)?.let { ic -> { Icon(TokenResolver.icon(ctx.fmt(ic)), null) } })
            }
        }
        Column(modifier.fillMaxWidth().animateContentSize()) {
            if (scrollable) PrimaryScrollableTabRow(sel, edgePadding = 0.dp) { tabs() } else PrimaryTabRow(sel) { tabs() }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(sel, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "tabs") { i ->
                node.children.getOrNull(i)?.let { RenderNode(it, ctx.child()) }
            }
        }
    }

    @Composable
    fun PagerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val n = node.children.size
        val state = rememberPagerState(index(node, "index", n)) { n }
        val external = index(node, "index", n)
        LaunchedEffect(external) { if (state.settledPage != external) state.animateScrollToPage(external) }
        LaunchedEffect(state) { snapshotFlow { state.settledPage }.collect { if (it != index(node, "index", n)) writeIndex(node, "index", ctx, it) } }
        val peek = node.bool("peek")
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val h = TokenResolver.height(node.args["h"], ctx.screenHeight)
            HorizontalPager(
                state, Modifier.fillMaxWidth().then(if (h != null) Modifier.height(h) else Modifier),
                pageSpacing = 8.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (peek) 24.dp else 0.dp),
            ) { i -> node.children.getOrNull(i)?.let { RenderNode(it, ctx.child()) } }
            if (node.bool("dots", true) && n > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(n) { i ->
                    val w by animateDpAsState(if (state.currentPage == i) 24.dp else 8.dp, label = "dot")
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (state.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
    }

    @Composable
    fun WizardR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val labels = node.list("labels")?.map { ctx.fmt(it) } ?: node.children.indices.map { "Step ${it + 1}" }
        val n = node.children.size.coerceAtLeast(1)
        val step = index(node, "step", n)
        val next = node.list("next")
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Numbers.StepsR(WNode("Steps", mapOf("current" to WNum.of(step), "labels" to WList(labels.map { WStr(it) })), emptyList(), "", ""), ctx, Modifier)
            AnimatedContent(step, transitionSpec = {
                if (targetState > initialState) slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                else slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            }, label = "wizard") { i -> node.children.getOrNull(i)?.let { RenderNode(it, ctx.child()) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton({ writeIndex(node, "step", ctx, step - 1) }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                val enabled = next?.getOrNull(step)?.truthy() ?: true
                if (step < n - 1) Button({ writeIndex(node, "step", ctx, step + 1) }, enabled = enabled) { Text("Next") }
                else Button({ ctx.fire(node.args["onFinish"]) }, enabled = enabled) { Text(node.str("finishLabel") ?: "Done") }
            }
        }
    }

    @Composable
    fun AccordionR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val single = node.bool("single", true)
        val place = node.places["open"]
        val bound = node.args["open"]
        var localOpen by rememberSaveable { mutableStateOf(listOf(0)) }
        val open: List<Int> = when {
            place == null -> localOpen
            bound is WNum -> listOf(bound.v.toInt())
            bound is WList -> bound.items.mapNotNull { (it as? WNum)?.v?.toInt() }
            else -> emptyList()
        }
        fun toggle(i: Int) {
            val next = if (i in open) open - i else if (single) listOf(i) else open + i
            if (place == null) localOpen = next
            else ctx.controller.input(place, if (single) (next.firstOrNull()?.let { WNum.of(it) } ?: com.echoflow.weave.core.eval.WNull) else WList(next.map { WNum.of(it) }))
        }
        Column(modifier.fillMaxWidth()) {
            node.children.forEachIndexed { i, sec ->
                val isOpen = i in open
                Row(Modifier.fillMaxWidth().clickable { toggle(i) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sec.str("title") ?: ctx.fmt(sec.args["label"]), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val r by animateFloatAsState(if (isOpen) 180f else 0f, label = "chev")
                    Icon(TokenResolver.icon("expand_more"), null, Modifier.graphicsLayer { rotationZ = r })
                }
                if (isOpen) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { RenderChildren(sec.children, ctx) }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    @Composable
    fun RevealR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var open by rememberSaveable { mutableStateOf(false) }
        val lines = (node.num("lines") ?: 3.0).toInt().coerceIn(1, 40)
        val maxH = (MaterialTheme.typography.bodyLarge.lineHeight.value.takeIf { it > 0 } ?: 24f) * lines
        val bg = MaterialTheme.colorScheme.surface
        Column(modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().animateContentSize().then(
                    if (open) Modifier else Modifier.heightIn(max = maxH.dp).drawWithContent {
                        drawContent()
                        val fade = 24.dp.toPx()
                        drawRect(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg), startY = size.height - fade, endY = size.height), topLeft = Offset(0f, size.height - fade))
                    },
                ),
            ) { RenderChildren(node.children, ctx) }
            TextButton({ open = !open }) { Text(if (open) "Show less" else node.str("label") ?: "Show more") }
        }
    }

    @Composable
    fun FlipR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val place = node.places["side"]
        var localBack by rememberSaveable { mutableStateOf(false) }
        val back = if (place != null) node.word("side") == "back" else localBack
        val rot by animateFloatAsState(if (back) 180f else 0f, tween(400), label = "flip")
        Box(
            modifier.fillMaxWidth().graphicsLayer { rotationY = rot; cameraDistance = 12 * density }.clickable {
                if (place != null) ctx.controller.input(place, WStr(if (back) "front" else "back")) else localBack = !localBack
                ctx.fire(node.args["onTap"])
            },
        ) {
            if (rot <= 90f) Column { RenderChildren(node.children, ctx) }
            else Box(Modifier.graphicsLayer { rotationY = 180f }) { node.node("back")?.let { RenderNode(it, ctx.child()) } }
        }
    }

    @Suppress("unused")
    private val keep = Modifier.size(0.dp)
}
