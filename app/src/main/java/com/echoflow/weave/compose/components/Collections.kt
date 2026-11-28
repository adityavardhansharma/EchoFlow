package com.echoflow.weave.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.isAction
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.node
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.weaveCompare

/** Collections and data, 04 §8. */
object Collections {

    /** The row a `List` shows for an item (04 §8): `render(item, index)` or the default Item. */
    fun row(node: WNode, ctx: RenderCtx, item: WValue, i: Int): WNode {
        val render = node.args["render"]
        val v = if (render is WFn) ctx.controller.callItem(render, item, i) else null
        return v as? WNode ?: ctx.controller.session?.defaultItem(item, "${node.key}/$i", node.sourceStmt)
            ?: WNode("Item", mapOf("title" to WStr(ctx.fmt(item))), emptyList(), "$i", "")
    }

    @Composable
    fun ListR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val items = node.list("items") ?: emptyList()
        if (items.isEmpty()) { node.node("empty")?.let { RenderNode(it, ctx.child(), modifier) }; return }
        val divider = node.bool("divider", true)
        val lazy = when (val l = node.args["lazy"]) { is com.echoflow.weave.core.eval.WBool -> l.v; else -> items.size > 30 }
        val c = ctx.child()
        if (lazy) {
            val h = TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)
            LazyColumn(modifier.fillMaxWidth().height(h)) {
                itemsIndexed(items) { i, item ->
                    RenderNode(row(node, ctx, item, i), c)
                    if (divider && i < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else Column(modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                RenderNode(row(node, ctx, item, i), c)
                if (divider && i < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    @Composable
    fun ItemR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val tap = node.args["onTap"]
        val lead = node.args["lead"]
        ListItem(
            headlineContent = { Text(ctx.fmt(node.args["title"])) },
            supportingContent = node.str("subtitle")?.let { { Text(it) } },
            overlineContent = node.str("overline")?.let { { Text(it) } },
            leadingContent = when (lead) {
                is WNode -> ({ RenderNode(lead, ctx.child()) })
                is WStr, is WSym -> ({ Icon(TokenResolver.icon(lead.word()), null) })
                else -> null
            },
            trailingContent = node.node("trail")?.let { t -> { RenderNode(t, ctx.child()) } },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = modifier.fillMaxWidth().then(if (isAction(tap) && !node.bool("disabled")) Modifier.clickable { ctx.fire(tap) } else Modifier),
        )
    }

    /** A column of a Table: header, field and display options (04 §8 `col`). */
    data class Col(val header: String, val field: WValue, val format: String?, val align: String?, val minW: Int?)

    fun field(row: WRec, f: WValue, ctx: RenderCtx): WValue = when (f) {
        is WStr -> row[f.v]
        is WSym -> row[f.name]
        is WFn -> ctx.controller.callFn(f, listOf(row)) ?: WNull
        else -> WNull
    }

    @Composable
    fun TableR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val rows = node.list("rows")?.filterIsInstance<WRec>() ?: emptyList()
        val cols = (node.list("cols")?.filterIsInstance<WRec>()?.map {
            Col(ctx.fmt(it["header"]), it["value"], it["format"].word(), it["align"].word(), (it["w"] as? WNum)?.v?.toInt())
        }) ?: rows.firstOrNull()?.fields?.keys?.map { Col(it, WStr(it), null, null, null) } ?: emptyList()
        var sortCol by rememberSaveable { mutableIntStateOf(-1) }
        var sortDir by rememberSaveable { mutableIntStateOf(0) }
        var query by rememberSaveable { mutableStateOf("") }
        var showAll by rememberSaveable { mutableStateOf(false) }
        val dense = node.bool("dense")
        val sortable = node.bool("sort", true)
        val maxRows = (node.num("maxRows") ?: 50.0).toInt().coerceAtLeast(1)
        val filtered = if (query.isBlank()) rows else rows.filter { r -> cols.any { c -> ctx.fmt(field(r, c.field, ctx)).contains(query, ignoreCase = true) } }
        val sorted = if (sortCol in cols.indices && sortDir != 0) {
            val c = cols[sortCol]
            val s = filtered.sortedWith { a, b -> weaveCompare(field(a, c.field, ctx), field(b, c.field, ctx)) ?: 0 }
            if (sortDir < 0) s.reversed() else s
        } else filtered
        val visible = if (showAll) sorted else sorted.take(maxRows)
        val totals = node.list("totals")?.map { ctx.fmt(it) }?.toSet() ?: emptySet()
        val onRow = node.args["onRow"]
        val rowH = if (dense) 36.dp else 48.dp
        fun width(c: Col) = ((c.minW ?: 0) * 8).coerceAtLeast(88).dp
        fun isNum(c: Col) = rows.firstOrNull()?.let { field(it, c.field, ctx) is WNum } == true
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (node.bool("filter")) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Filter") }, leadingIcon = { Icon(TokenResolver.icon("search"), null) }, singleLine = true)
            Column(
                Modifier.horizontalScroll(rememberScrollState()).semantics { collectionInfo = CollectionInfo(visible.size + 1, cols.size) },
            ) {
                Row(Modifier.background(scheme.surfaceContainerHigh).height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    cols.forEachIndexed { i, c ->
                        Row(
                            Modifier.width(width(c)).then(if (sortable) Modifier.clickable {
                                if (sortCol != i) { sortCol = i; sortDir = 1 } else sortDir = when (sortDir) { 1 -> -1; -1 -> 0; else -> 1 }
                            } else Modifier).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if ((c.align ?: if (isNum(c)) "end" else "start") == "end") Arrangement.End else Arrangement.Start,
                        ) {
                            Text(c.header, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (sortCol == i && sortDir != 0) Icon(TokenResolver.icon(if (sortDir > 0) "arrow_upward" else "arrow_downward"), null, Modifier.size(14.dp))
                        }
                    }
                }
                visible.forEach { r ->
                    Row(
                        Modifier.height(rowH).then(if (onRow is WFn) Modifier.clickable { ctx.controller.callFn(onRow, listOf(r))?.let { ctx.fire(it) } } else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cols.forEach { c ->
                            val v = field(r, c.field, ctx)
                            Text(
                                ctx.fmt(v, c.format), Modifier.width(width(c)).padding(horizontal = 12.dp),
                                style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                textAlign = if ((c.align ?: if (v is WNum) "end" else "start") == "end") TextAlign.End else if (c.align == "center") TextAlign.Center else TextAlign.Start,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.width(cols.sumOf { width(it).value.toDouble() }.toFloat().dp), color = scheme.outlineVariant)
                }
                if (totals.isNotEmpty()) Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    cols.forEach { c ->
                        val sum = if (c.header in totals) sorted.sumOf { (field(it, c.field, ctx) as? WNum)?.v ?: 0.0 } else null
                        Text(sum?.let { ctx.fmt(WNum(it), c.format) } ?: "", Modifier.width(width(c)).padding(horizontal = 12.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!showAll && sorted.size > maxRows) TextButton({ showAll = true }) { Text("Show all (${sorted.size})") }
        }
    }

    /** Tree: indented collapsible rows (04 §8, phase 2). */
    @Composable
    fun TreeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val childrenFn = node.args["children"]
        val renderFn = node.args["render"]
        val openLevels = (node.num("open") ?: 1.0).toInt()
        val expanded = remember { mutableStateOf(setOf<String>()) }
        val collapsed = remember { mutableStateOf(setOf<String>()) }
        var count = 0
        @Composable
        fun Rows(v: WValue, depth: Int, path: String) {
            if (count++ > 500) return
            val kids = (ctx.controller.callFn(childrenFn ?: WNull, listOf(v)) as? WList)?.items ?: emptyList()
            val isOpen = (depth < openLevels && path !in collapsed.value) || path in expanded.value
            Row(
                Modifier.fillMaxWidth().padding(start = (16 * depth).dp).clickable(enabled = kids.isNotEmpty()) {
                    if (isOpen) { collapsed.value += path; expanded.value -= path } else { expanded.value += path; collapsed.value -= path }
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (kids.isNotEmpty()) Icon(TokenResolver.icon(if (isOpen) "expand_more" else "chevron_right"), null, Modifier.size(20.dp)) else Box(Modifier.size(20.dp))
                val r = ctx.controller.callFn(renderFn ?: WNull, listOf(v)) as? WNode
                if (r != null) RenderNode(r, ctx.child()) else Text(ctx.fmt(v))
            }
            if (isOpen) kids.forEachIndexed { i, k -> Rows(k, depth + 1, "$path/$i") }
        }
        Column(modifier.fillMaxWidth()) { Rows(node.args["root"] ?: WNull, 0, "0") }
    }

    @Suppress("unused")
    private fun unused(l: WList) = l
}
