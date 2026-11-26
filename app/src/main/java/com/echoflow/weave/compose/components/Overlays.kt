package com.echoflow.weave.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.rec
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WRec

/**
 * Overlays (04 §11) are bindings opened with `!open(name)`. In the flow they render nothing; the
 * block surface draws every open overlay through [Host].
 */
object Overlays {
    val TYPES = setOf("Sheet", "Dialog", "Popover", "Menu")

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Host(ctx: RenderCtx) {
        val c = ctx.controller
        c.openOverlays.forEach { name ->
            val node = c.binding(name) as? WNode ?: return@forEach
            val close = { c.closeOverlay(name) }
            when (node.type) {
                "Sheet" -> ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = node.bool("full"))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        node.str("title")?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                        RenderChildren(node.children, ctx)
                    }
                }
                "Dialog" -> {
                    val confirm = node.rec("confirm"); val dismiss = node.rec("dismiss")
                    AlertDialog(
                        onDismissRequest = close,
                        title = {
                            Column {
                                // the block's title is always shown above the dialog's (anti-spoofing, 05 §13.2)
                                Text(c.title ?: "Interactive block", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ctx.fmt(node.args["title"]))
                            }
                        },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { RenderChildren(node.children, ctx) } },
                        confirmButton = { TextButton({ close(); confirm?.let { ctx.fire(it["onTap"]) } }) { Text(confirm?.let { ctx.fmt(it["label"]) } ?: "OK") } },
                        dismissButton = dismiss?.let { d -> { TextButton({ close(); ctx.fire(d["onTap"]) }) { Text(ctx.fmt(d["label"])) } } },
                    )
                }
                "Popover" -> Popup(onDismissRequest = close) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { RenderChildren(node.children, ctx) }
                    }
                }
                "Menu" -> DropdownMenu(true, close) {
                    node.list("items")?.filterIsInstance<WRec>()?.forEach { item ->
                        DropdownMenuItem({ Text(ctx.fmt(item["label"])) }, { close(); ctx.fire(item["onTap"]) },
                            leadingIcon = item["icon"].word()?.let { ic -> { Icon(TokenResolver.icon(ic), null) } })
                    }
                }
            }
        }
    }
}
