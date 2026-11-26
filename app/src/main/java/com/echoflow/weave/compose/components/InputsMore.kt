package com.echoflow.weave.compose.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.rec
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WImage
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WTime
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.weaveEquals
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Inputs beyond the phase 1 set (04 §7). */
object InputsMore {

    private fun write(node: WNode, ctx: RenderCtx, key: String, v: WValue, drag: Boolean = false) {
        node.places[key]?.let { ctx.controller.input(it, v, node.args["onChange"], drag) }
    }

    @Composable
    fun FabR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val icon = TokenResolver.icon(node.str("icon") ?: node.word("icon"))
        val label = node.str("label")
        val pair = TokenResolver.tone(node.word("tone") ?: "primary", "medium", MaterialTheme.colorScheme, ctx.dark)
        val onClick = { ctx.fire(node.args["onTap"]) }
        val container = pair?.container ?: MaterialTheme.colorScheme.primaryContainer
        val content = pair?.content ?: MaterialTheme.colorScheme.onPrimaryContainer
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            when {
                node.bool("expanded") && label != null -> ExtendedFloatingActionButton({ Text(label) }, { Icon(icon, null) }, onClick, containerColor = container, contentColor = content)
                node.word("size") == "s" -> SmallFloatingActionButton(onClick, containerColor = container, contentColor = content) { Icon(icon, label) }
                node.word("size") == "l" -> LargeFloatingActionButton(onClick, containerColor = container, contentColor = content) { Icon(icon, label) }
                else -> FloatingActionButton(onClick, containerColor = container, contentColor = content) { Icon(icon, label) }
            }
        }
    }

    @Composable
    fun SplitButtonR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var open by remember { mutableStateOf(false) }
        val items = node.list("menu")?.filterIsInstance<WRec>() ?: emptyList()
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val outlined = node.word("variant") == "outlined"
            val tonal = node.word("variant") == "tonal"
            val lead = @Composable { Text(ctx.fmt(node.args["label"])) }
            when {
                outlined -> OutlinedButton({ ctx.fire(node.args["onTap"]) }, shape = MaterialTheme.shapes.small.copy(topEnd = androidx.compose.foundation.shape.CornerSize(4.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp))) { lead() }
                tonal -> FilledTonalButton({ ctx.fire(node.args["onTap"]) }) { lead() }
                else -> Button({ ctx.fire(node.args["onTap"]) }) { lead() }
            }
            Box {
                FilledTonalIconButton({ open = true }) { Icon(TokenResolver.icon("arrow_drop_down"), "More") }
                DropdownMenu(open, { open = false }) {
                    items.forEach { it ->
                        DropdownMenuItem({ Text(ctx.fmt(it["label"])) }, { open = false; ctx.fire(it["onTap"]) },
                            leadingIcon = it["icon"].word()?.let { ic -> { Icon(TokenResolver.icon(ic), null) } })
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ButtonGroupR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options")
        if (options == null || node.places["value"] == null) {
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) { RenderChildren(node.children, ctx) }
            return
        }
        val multi = node.bool("multi")
        val value = node.args["value"]
        val sel = when (value) { is WList -> value.items; null, WNull -> emptyList(); else -> listOf(value) }
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { opt ->
                val on = sel.any { weaveEquals(it, opt) }
                ToggleButton(on, {
                    if (multi) {
                        val next = if (on) sel.filterNot { weaveEquals(it, opt) } else sel + opt
                        write(node, ctx, "value", WList(options.filter { o -> next.any { weaveEquals(it, o) } }))
                    } else write(node, ctx, "value", opt)
                }, Modifier.weight(1f)) { Text(ctx.fmt(opt), maxLines = 1) }
            }
        }
    }

    @Composable
    fun RangeSliderR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val min = node.num("min") ?: 0.0
        val max = (node.num("max") ?: 100.0).let { if (it <= min) min + 1 else it }
        val step = node.num("step")
        val v = (node.args["value"] as? WList)?.items?.mapNotNull { (it as? WNum)?.v }
        val lo = v?.getOrNull(0) ?: min; val hi = v?.getOrNull(1) ?: max
        val fmt = { x: Double -> (node.str("prefix") ?: "") + ctx.fmt(WNum(x), node.str("format")) + (node.str("suffix") ?: "") }
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text(node.str("label") ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${fmt(lo)} – ${fmt(hi)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            val steps = step?.let { s -> (Math.round((max - min) / s) - 1).toInt().takeIf { it in 1..100 } } ?: 0
            RangeSlider(lo.toFloat()..hi.toFloat(), { r ->
                write(node, ctx, "value", WList(listOf(WNum.of(Inputs.snap(r.start.toDouble(), min, step)), WNum.of(Inputs.snap(r.endInclusive.toDouble(), min, step)))), drag = true)
            }, valueRange = min.toFloat()..max.toFloat(), steps = steps, onValueChangeFinished = { ctx.controller.endDrag() })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DatePickerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var open by remember { mutableStateOf(false) }
        val v = node.args["value"] as? WDate
        val minD = (node.args["min"] as? WDate)?.v; val maxD = (node.args["max"] as? WDate)?.v
        PickerField(ctx.fmt(node.args["label"]), v?.let { ctx.formatter.date(it.v) } ?: "", "calendar_month", modifier) { open = true }
        if (open) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = v?.v?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return (minD == null || !d.isBefore(minD)) && (maxD == null || !d.isAfter(maxD))
                    }
                },
            )
            DatePickerDialog({ open = false }, confirmButton = {
                TextButton({ open = false; state.selectedDateMillis?.let { write(node, ctx, "value", WDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) } }) { Text("OK") }
            }, dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(state) }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DateRangeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var open by remember { mutableStateOf(false) }
        val v = (node.args["value"] as? WList)?.items?.filterIsInstance<WDate>()
        val text = if (v != null && v.size == 2) "${ctx.formatter.date(v[0].v)} – ${ctx.formatter.date(v[1].v)}" else ""
        PickerField(ctx.fmt(node.args["label"]), text, "date_range", modifier) { open = true }
        if (open) {
            val state = rememberDateRangePickerState()
            DatePickerDialog({ open = false }, confirmButton = {
                TextButton({
                    open = false
                    val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis
                    if (s != null && e != null) write(node, ctx, "value", WList(listOf(s, e).map { WDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }))
                }) { Text("OK") }
            }, dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DateRangePicker(state, Modifier.height(480.dp)) }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TimePickerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var open by remember { mutableStateOf(false) }
        val v = (node.args["value"] as? WTime)?.v
        PickerField(ctx.fmt(node.args["label"]), v?.let { ctx.formatter.time(java.time.LocalDateTime.of(LocalDate.now(), it)) } ?: "", "schedule", modifier) { open = true }
        if (open) {
            val state = rememberTimePickerState(v?.hour ?: 9, v?.minute ?: 0)
            AlertDialog({ open = false }, confirmButton = {
                TextButton({ open = false; write(node, ctx, "value", WTime(LocalTime.of(state.hour, state.minute))) }) { Text("OK") }
            }, dismissButton = { TextButton({ open = false }) { Text("Cancel") } }, text = { TimePicker(state) })
        }
    }

    @Composable
    private fun PickerField(label: String, value: String, icon: String, modifier: Modifier, onOpen: () -> Unit) {
        Box(modifier.fillMaxWidth()) {
            OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, trailingIcon = { Icon(TokenResolver.icon(icon), null) }, modifier = Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        }
    }

    @Composable
    fun ColorPickR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options")?.mapNotNull { it.word() } ?: listOf("primary", "secondary", "tertiary", "error", "success", "warning", "neutral")
        val sel = node.args["value"].word()
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { o ->
                val c = TokenResolver.color(WSym(o), MaterialTheme.colorScheme, ctx.dark, fill = false) ?: MaterialTheme.colorScheme.primary
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(c)
                        .then(if (o == sel) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { write(node, ctx, "value", WSym(o)) },
                    contentAlignment = Alignment.Center,
                ) { if (o == sel) Icon(TokenResolver.icon("check"), o, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface) }
            }
        }
    }

    @Composable
    fun RatingInputR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val max = (node.num("max") ?: 5.0).toInt().coerceIn(1, 10)
        val half = node.bool("half")
        val v = (node.args["value"] as? WNum)?.v ?: 0.0
        var width by remember { mutableStateOf(1f) }
        fun at(x: Float): Double { val raw = (x / width * max).coerceIn(0f, max.toFloat()); return if (half) Math.ceil(raw * 2.0) / 2 else Math.ceil(raw.toDouble()) }
        Row(
            modifier.pointerInput(max, half) {
                width = size.width.toFloat()
                detectTapGestures { write(node, ctx, "value", WNum.of(at(it.x))); ctx.controller.hostEffect("haptic", listOf(WStr("tick"))) }
            }.pointerInput(max, half) {
                detectDragGestures(onDragEnd = { ctx.controller.endDrag() }) { c, _ ->
                    val n = at(c.position.x)
                    if (n != (node.args["value"] as? WNum)?.v) { write(node, ctx, "value", WNum.of(n), drag = true); ctx.controller.hostEffect("haptic", listOf(WStr("tick"))) }
                }
            },
        ) {
            for (i in 1..max) {
                val icon = when { v >= i -> "star"; v >= i - 0.5 -> "star_half"; else -> "star_outline" }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(TokenResolver.icon(icon), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.tertiary) }
            }
        }
    }

    @Composable
    fun Pad2DR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val xr = node.rec("x"); val yr = node.rec("y")
        val xMin = (xr?.get("min") as? WNum)?.v ?: 0.0; val xMax = (xr?.get("max") as? WNum)?.v ?: 1.0
        val yMin = (yr?.get("min") as? WNum)?.v ?: 0.0; val yMax = (yr?.get("max") as? WNum)?.v ?: 1.0
        val v = node.args["value"] as? WRec
        val vx = (v?.get("x") as? WNum)?.v ?: (xMin + xMax) / 2; val vy = (v?.get("y") as? WNum)?.v ?: (yMin + yMax) / 2
        val scheme = MaterialTheme.colorScheme
        fun set(o: Offset, w: Float, h: Float, drag: Boolean) {
            val x = xMin + (o.x / w).coerceIn(0f, 1f) * (xMax - xMin)
            val y = yMin + (1 - (o.y / h).coerceIn(0f, 1f)) * (yMax - yMin)
            write(node, ctx, "value", WRec.of("x" to WNum.of(x), "y" to WNum.of(y)), drag)
        }
        Column(modifier.fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxWidth().height(TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)).clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh)
                    .pointerInput(Unit) { detectTapGestures { set(it, size.width.toFloat(), size.height.toFloat(), false) } }
                    .pointerInput(Unit) { detectDragGestures(onDragEnd = { ctx.controller.endDrag() }) { c, _ -> set(c.position, size.width.toFloat(), size.height.toFloat(), true) } },
            ) {
                for (i in 1..3) {
                    drawLine(scheme.outlineVariant, Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
                    drawLine(scheme.outlineVariant, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
                }
                val px = ((vx - xMin) / (xMax - xMin)).toFloat() * size.width
                val py = (1 - ((vy - yMin) / (yMax - yMin)).toFloat()) * size.height
                drawCircle(scheme.primary, 12.dp.toPx(), Offset(px, py))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(ctx.fmt(xr?.get("label")), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text(ctx.fmt(yr?.get("label")), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    /** Sketch: strokes in onSurface; each stroke end writes a PNG to the cache dir as a WImage. */
    @Composable
    fun SketchR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val context = LocalContext.current
        val scheme = MaterialTheme.colorScheme
        val strokes = remember { mutableStateListOf<Pair<String, List<Offset>>>() }
        var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
        var tool by remember { mutableStateOf("pen") }
        var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
        val density = LocalDensity.current
        fun save() {
            val w = canvasSize.width.coerceIn(1, 1024); val h = (canvasSize.height * w / canvasSize.width.coerceAtLeast(1)).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(scheme.surfaceContainerLowest.toArgb())
            val sx = w.toFloat() / canvasSize.width
            strokes.forEach { (t, pts) ->
                val p = android.graphics.Paint().apply {
                    isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                    color = if (t == "eraser") scheme.surfaceContainerLowest.toArgb() else scheme.onSurface.copy(alpha = if (t == "marker") 0.4f else 1f).toArgb()
                    strokeWidth = with(density) { (if (t == "pen") 3.dp else 12.dp).toPx() } * sx
                }
                for (i in 1 until pts.size) c.drawLine(pts[i - 1].x * sx, pts[i - 1].y * sx, pts[i].x * sx, pts[i].y * sx, p)
            }
            val f = File(context.cacheDir, "weave-sketch-${System.nanoTime()}.png")
            runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            write(node, ctx, "value", WImage(android.net.Uri.fromFile(f).toString(), w, h))
        }
        Column(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                (node.list("tools")?.mapNotNull { it.word() } ?: listOf("pen", "marker", "eraser")).forEach { t ->
                    IconButton({ tool = t }) {
                        Icon(TokenResolver.icon(when (t) { "marker" -> "brush"; "eraser" -> "ink_eraser"; else -> "edit" }), t, tint = if (tool == t) scheme.primary else scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton({ if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); save() } }) { Icon(TokenResolver.icon("undo"), "Undo") }
                IconButton({ strokes.clear(); save() }) { Icon(TokenResolver.icon("delete"), "Clear") }
            }
            Canvas(
                Modifier.fillMaxWidth().height(TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)).clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerLowest)
                    .pointerInput(Unit) {
                        canvasSize = size
                        detectDragGestures(onDragStart = { current = listOf(it) }, onDragEnd = { strokes += tool to current; current = emptyList(); save() }) { c, _ -> current = current + c.position }
                    },
            ) {
                (strokes + listOf(tool to current)).forEach { (t, pts) ->
                    if (pts.size < 2) return@forEach
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(path, if (t == "eraser") scheme.surfaceContainerLowest else scheme.onSurface.copy(alpha = if (t == "marker") 0.4f else 1f),
                        style = Stroke((if (t == "pen") 3.dp else 12.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }

    @Composable
    fun SwipeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val deck = (node.args["deck"] as? WList)?.items ?: emptyList()
        val render = node.args["render"] as? WFn
        val scope = rememberCoroutineScope()
        val dx = remember(deck.size) { Animatable(0f) }
        val h = TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)
        fun dismiss(right: Boolean) {
            val item = deck.firstOrNull() ?: return
            node.places["deck"]?.let { ctx.controller.input(it, WList(deck.drop(1))) }
            val handler = node.args[if (right) "onRight" else "onLeft"] as? WFn
            handler?.let { ctx.controller.callFn(it, listOf(item))?.let { a -> ctx.fire(a) } }
        }
        if (deck.isEmpty()) { TextContent.EmptyR(WNode("Empty", mapOf("title" to WStr("All done")), emptyList(), "", ""), ctx, modifier); return }
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(h)) {
                val w = constraints.maxWidth.toFloat()
                deck.take(3).withIndex().reversed().forEach { (i, item) ->
                    val card = (render?.let { ctx.controller.callFn(it, listOf(item)) } as? WNode)
                    Surface(
                        Modifier.fillMaxSize().padding(top = (8 * i).dp)
                            .graphicsLayer { val s = 1f - 0.05f * i; scaleX = s; scaleY = s; if (i == 0) { translationX = dx.value; rotationZ = 12f * (dx.value / w) } }
                            .then(if (i == 0) Modifier.pointerInput(deck.size) {
                                detectDragGestures(onDragEnd = {
                                    scope.launch {
                                        if (abs(dx.value) > w * 0.35f) { val right = dx.value > 0; dx.animateTo(if (right) w * 1.5f else -w * 1.5f); dismiss(right) }
                                        else dx.animateTo(0f)
                                    }
                                }) { c, d -> c.consume(); scope.launch { dx.snapTo(dx.value + d.x) } }
                            } else Modifier),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp,
                    ) { Box(Modifier.padding(16.dp)) { if (card != null) RenderNode(card, ctx.child()) else Text(ctx.fmt(item), style = MaterialTheme.typography.titleMedium) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledTonalIconButton({ dismiss(false) }) { Icon(TokenResolver.icon("close"), "Left") }
                FilledTonalIconButton({ dismiss(true) }) { Icon(TokenResolver.icon("favorite"), "Right") }
            }
        }
    }

    @Composable
    fun RankR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val items = (node.args["value"] as? WList)?.items ?: emptyList()
        val render = node.args["render"] as? WFn
        var dragging by remember { mutableStateOf<Int?>(null) }
        var offset by remember { mutableStateOf(0f) }
        val rowH = with(LocalDensity.current) { 56.dp.toPx() }
        fun move(from: Int, to: Int) {
            if (from == to || to !in items.indices) return
            val l = items.toMutableList(); val x = l.removeAt(from); l.add(to, x)
            node.places["value"]?.let { ctx.controller.input(it, WList(l)) }
        }
        Column(modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .offset { IntOffset(0, if (dragging == i) offset.roundToInt() else 0) }
                        .pointerInput(items) {
                            detectDragGesturesAfterLongPress(onDragStart = { dragging = i; offset = 0f }, onDragEnd = {
                                val to = (i + (offset / rowH).roundToInt()).coerceIn(0, items.lastIndex)
                                move(i, to); dragging = null; offset = 0f
                            }) { c, d -> c.consume(); offset += d.y }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TokenResolver.icon("drag_indicator"), "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    val n = render?.let { ctx.controller.callFn(it, listOf(item)) } as? WNode
                    Box(Modifier.weight(1f)) { if (n != null) RenderNode(n, ctx.child()) else Text(ctx.fmt(item)) }
                    IconButton({ move(i, i - 1) }, enabled = i > 0) { Icon(TokenResolver.icon("arrow_upward"), "Move up") }
                    IconButton({ move(i, i + 1) }, enabled = i < items.lastIndex) { Icon(TokenResolver.icon("arrow_downward"), "Move down") }
                }
            }
        }
    }

    /** Kanban: tap a card to move it to the next column, or pick the column from its menu. */
    @Composable
    fun SortR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val columns = node.list("columns")?.map { ctx.fmt(it) } ?: emptyList()
        val board = node.args["value"] as? WRec ?: WRec(LinkedHashMap())
        val render = node.args["render"] as? WFn
        fun moveTo(item: WValue, from: String, to: String) {
            val m = LinkedHashMap<String, WValue>()
            columns.forEach { c ->
                val l = (board[c] as? WList)?.items ?: emptyList()
                m[c] = WList(when (c) { from -> l.filterNot { it === item || weaveEquals(it, item) }; to -> l + item; else -> l })
            }
            node.places["value"]?.let { ctx.controller.input(it, WRec(m)) }
        }
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val colW = maxWidth * (node.num("colW") ?: 0.8).toFloat().coerceIn(0.4f, 1f)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                columns.forEach { c ->
                    val items = (board[c] as? WList)?.items ?: emptyList()
                    Surface(Modifier.width(colW), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$c · ${items.size}", style = MaterialTheme.typography.titleSmall)
                            items.forEach { item ->
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    Surface(Modifier.fillMaxWidth().clickable { menu = true }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                        Box(Modifier.padding(12.dp)) {
                                            val n = render?.let { ctx.controller.callFn(it, listOf(item)) } as? WNode
                                            if (n != null) RenderNode(n, ctx.child()) else Text(ctx.fmt(if (item is WRec) item.fields.values.firstOrNull() ?: item else item))
                                        }
                                    }
                                    DropdownMenu(menu, { menu = false }) {
                                        columns.filter { it != c }.forEach { to -> DropdownMenuItem({ Text("Move to $to") }, { menu = false; moveTo(item, c, to) }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
