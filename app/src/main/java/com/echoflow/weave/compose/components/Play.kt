package com.echoflow.weave.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.WeaveRandom
import kotlinx.coroutines.launch

/** Learning and play, 04 §15. */
object Play {

    private fun write(node: WNode, ctx: RenderCtx, key: String, v: WValue) { node.places[key]?.let { ctx.controller.input(it, v) } }

    @Composable
    fun QuizR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val questions = node.list("questions")?.filterIsInstance<WRec>() ?: emptyList()
        val answers = (node.args["answers"] as? WList)?.items ?: emptyList()
        val i = answers.size
        if (questions.isEmpty()) return
        if (i >= questions.size) {
            val score = questions.indices.count { k -> (answers.getOrNull(k) as? WNum)?.v?.toInt() == (questions[k]["answer"] as? WNum)?.v?.toInt() }
            Card(modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score / ${questions.size}", style = MaterialTheme.typography.displaySmall)
                    Text("Score", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    OutlinedButton({ write(node, ctx, "answers", WList.EMPTY) }) { Text("Try again") }
                }
            }
            return
        }
        val q = questions[i]
        val options = (q["options"] as? WList)?.items ?: emptyList()
        val correct = (q["answer"] as? WNum)?.v?.toInt() ?: -1
        var picked by remember(i) { mutableStateOf<Int?>(null) }
        Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Question ${i + 1} of ${questions.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(ctx.fmt(q["q"]), style = MaterialTheme.typography.titleMedium)
                options.forEachIndexed { k, o ->
                    val state = picked
                    val tone = when { state == null -> null; k == correct -> "success"; k == state -> "error"; else -> null }
                    val pair = TokenResolver.tone(tone, "medium", scheme, ctx.dark)
                    OutlinedButton(
                        { if (picked == null) { picked = k; ctx.controller.hostEffect("haptic", listOf(WStr(if (k == correct) "confirm" else "reject"))) } },
                        Modifier.fillMaxWidth(),
                        colors = if (pair != null) androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = pair.container, contentColor = pair.content) else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(ctx.fmt(o), Modifier.weight(1f))
                        if (state != null && k == correct) Icon(TokenResolver.icon("check"), null)
                    }
                }
                if (picked != null) {
                    (q["why"] as? WStr)?.let { Text(it.v, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                    Button({
                        val next = answers + WNum.of(picked ?: -1)
                        write(node, ctx, "answers", WList(next))
                        if (next.size == questions.size) {
                            val score = questions.indices.count { k -> (next.getOrNull(k) as? WNum)?.v?.toInt() == (questions[k]["answer"] as? WNum)?.v?.toInt() }
                            (node.args["onDone"] as? WFn)?.let { f -> ctx.controller.callFn(f, listOf(WNum.of(score)))?.let { ctx.fire(it) } }
                        }
                    }, Modifier.align(Alignment.End)) { Text(if (i + 1 < questions.size) "Next" else "Finish") }
                }
            }
        }
    }

    @Composable
    fun FlashcardsR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val cards = node.list("cards")?.filterIsInstance<WRec>() ?: emptyList()
        if (cards.isEmpty()) return
        val boxes = (node.args["state"] as? WRec)?.fields ?: LinkedHashMap()
        val spaced = node.bool("spaced", true)
        var order by rememberSaveable { mutableIntStateOf(0) }
        val i = if (spaced) cards.indices.minByOrNull { k -> ((boxes["$k"] as? WNum)?.v ?: 1.0) * 1000 + ((k - order + cards.size) % cards.size) } ?: 0 else order % cards.size
        var back by remember(i, order) { mutableStateOf(false) }
        fun grade(delta: Int) {
            val m = LinkedHashMap(boxes)
            val cur = ((m["$i"] as? WNum)?.v ?: 1.0).toInt()
            m["$i"] = WNum.of((if (delta < 0) 1 else cur + delta).coerceIn(1, 5))
            write(node, ctx, "state", WRec(m)); order = (order + 1) % cards.size
        }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth().aspectRatio(1.6f).clickable { back = !back }) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(ctx.fmt(cards[i][if (back) "back" else "front"]), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
            }
            Text("${boxes.values.count { ((it as? WNum)?.v ?: 1.0) >= 4 }} / ${cards.size}", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ grade(-1) }, Modifier.weight(1f)) { Text("Again") }
                FilledTonalButton({ grade(1) }, Modifier.weight(1f)) { Text("Good") }
                Button({ grade(2) }, Modifier.weight(1f)) { Text("Easy") }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun BlanksR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val text = ctx.fmt(node.args["text"])
        val parts = Regex("""\[\[(.+?)]]""").let { rx -> rx.split(text) to rx.findAll(text).map { it.groupValues[1] }.toList() }
        val values = (node.args["values"] as? WList)?.items?.map { ctx.fmt(it) } ?: emptyList()
        var checked by remember { mutableStateOf(false) }
        val scheme = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                parts.first.forEachIndexed { k, seg ->
                    seg.split(' ').filter { it.isNotEmpty() }.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    parts.second.getOrNull(k)?.let { answer ->
                        val v = values.getOrElse(k) { "" }
                        val ok = v.trim().equals(answer.trim(), ignoreCase = true)
                        OutlinedTextField(v, { nv ->
                            val list = MutableList(parts.second.size) { j -> values.getOrElse(j) { "" } }
                            list[k] = nv; write(node, ctx, "values", WList(list.map { WStr(it) })); checked = false
                        }, Modifier.width(96.dp), singleLine = true, isError = checked && !ok,
                            colors = if (checked && ok) androidx.compose.material3.OutlinedTextFieldDefaults.colors(unfocusedBorderColor = TokenResolver.semantic(145f, scheme, ctx.dark).solid) else androidx.compose.material3.OutlinedTextFieldDefaults.colors())
                    }
                }
            }
            Button({ checked = true }) { Text("Check") }
        }
    }

    @Composable
    fun MatchR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val left = node.list("left")?.map { ctx.fmt(it) } ?: emptyList()
        val right = node.list("right")?.map { ctx.fmt(it) } ?: emptyList()
        val pairs = (node.args["pairs"] as? WList)?.items?.mapNotNull { p -> (p as? WList)?.items?.let { ((it.getOrNull(0) as? WNum)?.v?.toInt() ?: return@let null) to ((it.getOrNull(1) as? WNum)?.v?.toInt() ?: return@let null) } } ?: emptyList()
        var pick by remember { mutableStateOf<Int?>(null) }
        var checked by remember { mutableStateOf(false) }
        val scheme = MaterialTheme.colorScheme
        fun color(pairIx: Int) = TokenResolver.series(pairIx, scheme)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    left.forEachIndexed { i, l ->
                        val pi = pairs.indexOfFirst { it.first == i }
                        Surface(Modifier.fillMaxWidth().clickable { pick = i; checked = false }, shape = MaterialTheme.shapes.small,
                            color = if (pick == i) scheme.secondaryContainer else scheme.surfaceContainer,
                            border = if (pi >= 0) androidx.compose.foundation.BorderStroke(2.dp, if (checked) (if (pairs[pi].second == i) TokenResolver.semantic(145f, scheme, ctx.dark).solid else scheme.error) else color(pi)) else null) {
                            Text(l, Modifier.padding(12.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    right.forEachIndexed { j, r ->
                        val pi = pairs.indexOfFirst { it.second == j }
                        Surface(Modifier.fillMaxWidth().clickable {
                            val l = pick ?: return@clickable
                            val next = pairs.filterNot { it.first == l || it.second == j } + (l to j)
                            write(node, ctx, "pairs", WList(next.map { WList(listOf(WNum.of(it.first), WNum.of(it.second))) })); pick = null
                        }, shape = MaterialTheme.shapes.small, color = scheme.surfaceContainer,
                            border = if (pi >= 0) androidx.compose.foundation.BorderStroke(2.dp, color(pi)) else null) {
                            Text(r, Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Button({ checked = true }) { Text("Check") }
        }
    }

    @Composable
    fun BoardR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val rows = (node.num("rows") ?: 3.0).toInt().coerceIn(1, 20)
        val cols = (node.num("cols") ?: 3.0).toInt().coerceIn(1, 20)
        val cells = (node.args["cells"] as? WList)?.items ?: emptyList()
        val render = node.args["render"] as? WFn
        val onCell = node.args["onCell"] as? WFn
        val gap = TokenResolver.space(node.args["gap"], 2.dp)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until cols) {
                    val cell = ((cells.getOrNull(r) as? WList)?.items?.getOrNull(c)) ?: WNull
                    val n = render?.let { ctx.controller.callFn(it, listOf(cell, WNum.of(r), WNum.of(c))) } as? WNode
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onCell?.let { f -> ctx.controller.callFn(f, listOf(WNum.of(r), WNum.of(c)))?.let { ctx.fire(it) } } },
                        contentAlignment = Alignment.Center,
                    ) { if (n != null) RenderNode(n, ctx.child()) else if (cell != WNull) Text(ctx.fmt(cell)) }
                }
            }
        }
    }

    @Composable
    fun DiceR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val count = (node.num("count") ?: 1.0).toInt().coerceIn(1, 6)
        val sides = (node.num("sides") ?: 6.0).toInt().coerceIn(2, 100)
        val values = (node.args["values"] as? WList)?.items?.map { (it as? WNum)?.v?.toInt() ?: 1 } ?: List(count) { 1 }
        var rolls by rememberSaveable { mutableIntStateOf(0) }
        val spin = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val seed = ctx.controller.blockId ?: "dice"
        Row(modifier.clickable {
            scope.launch {
                spin.snapTo(0f); spin.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                rolls++
                val v = (0 until count).map { i -> WNum.of(WeaveRandom.randInt(WStr("$seed$rolls"), i.toLong(), 1.0, sides.toDouble())) }
                write(node, ctx, "values", WList(v))
                (node.args["onRoll"] as? WFn)?.let { f -> ctx.controller.callFn(f, listOf(WList(v)))?.let { ctx.fire(it) } }
            }
        }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(count) { i ->
                Surface(Modifier.size(56.dp).rotate(spin.value * 720f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 2.dp) {
                    Box(contentAlignment = Alignment.Center) { Text("${values.getOrElse(i) { 1 }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    @Composable
    fun SpinnerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options")?.map { ctx.fmt(it) } ?: emptyList()
        if (options.isEmpty()) return
        val angle = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val scheme = MaterialTheme.colorScheme
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelMedium
        var spins by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TokenResolver.icon("arrow_drop_down"), null, Modifier.size(32.dp))
            Canvas(Modifier.size(220.dp).clickable {
                scope.launch {
                    spins++
                    val turns = 3 + WeaveRandom.rand(WStr("${ctx.controller.blockId}spin$spins"), 0) * 2
                    val target = angle.value + (turns * 360).toFloat()
                    var lastSeg = -1
                    angle.animateTo(target, tween(2000 + (turns * 400).toInt(), easing = androidx.compose.animation.core.CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f))) {
                        val seg = (((360 - value % 360) % 360) / (360f / options.size)).toInt()
                        if (seg != lastSeg) { lastSeg = seg; ctx.controller.hostEffect("haptic", listOf(WStr("tick"))) }
                    }
                    val seg = (((360 - target % 360) % 360) / (360f / options.size)).toInt().coerceIn(0, options.lastIndex)
                    write(node, ctx, "choice", WStr(options[seg]))
                }
            }) {
                val sweep = 360f / options.size
                rotate(angle.value, center) {
                    options.forEachIndexed { i, o ->
                        drawArc(TokenResolver.series(i % 8, scheme), -90f + i * sweep, sweep, true)
                        val a = Math.toRadians((-90 + i * sweep + sweep / 2).toDouble())
                        val t = measurer.measure(o.take(12), labelStyle.copy(color = scheme.surface))
                        drawText(t, topLeft = Offset(center.x + (size.minDimension * 0.32 * kotlin.math.cos(a)).toFloat() - t.size.width / 2, center.y + (size.minDimension * 0.32 * kotlin.math.sin(a)).toFloat() - t.size.height / 2))
                    }
                }
            }
            (node.args["choice"] as? WStr)?.let { Text(it.v, style = MaterialTheme.typography.titleMedium) }
        }
    }


    @Composable
    fun ScratchR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val cover = TokenResolver.color(node.args["cover"], MaterialTheme.colorScheme, ctx.dark, fill = true) ?: MaterialTheme.colorScheme.surfaceContainerHighest
        val points = remember { mutableStateListOf<Offset>() }
        var area by remember { mutableStateOf(1f) }
        val cleared = (points.size * 24f * 24f * 3.14f / area).coerceAtMost(1f)
        val fade by androidx.compose.animation.core.animateFloatAsState(if (cleared > 0.6f) 0f else 1f, label = "scratch")
        Box(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { RenderChildren(node.children, ctx) }
            if (fade > 0f) Canvas(
                Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = fade }
                    .pointerInput(Unit) { area = (size.width * size.height).toFloat().coerceAtLeast(1f); detectDragGestures { c, _ -> points += c.position } },
            ) {
                drawRoundRect(cover, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                points.forEach { drawCircle(androidx.compose.ui.graphics.Color.Black, 24.dp.toPx(), it, blendMode = BlendMode.Clear) }
            }
        }
    }
}
