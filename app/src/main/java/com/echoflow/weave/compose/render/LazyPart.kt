package com.echoflow.weave.compose.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WRec

/**
 * A `lazy(prompt, …)` node (03 §9.3): generated on first render, shown as it streams, cached by
 * key; when its context changes the old result stays with an "Update" chip.
 */
object LazyPart {
    @Composable
    fun Render(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val name = node.str("name") ?: return
        val prompt = node.args["prompt"] ?: WNull
        val context = node.args["context"] as? WRec ?: WRec(LinkedHashMap())
        val content = ctx.controller.lazyNode(name, prompt, context)
        Box(modifier.fillMaxWidth()) {
            if (content == null) Placeholders.Bar(Modifier.fillMaxWidth(), 168.dp, MaterialTheme.shapes.medium)
            else RenderNode(content, ctx.child())
            if (content != null && ctx.controller.lazyStale(name, prompt, context)) {
                AssistChip({ ctx.controller.refreshLazy(name) }, { Text("Update") }, Modifier.align(Alignment.TopEnd).padding(4.dp),
                    leadingIcon = { Icon(TokenResolver.icon("refresh"), null) })
            }
        }
    }
}
