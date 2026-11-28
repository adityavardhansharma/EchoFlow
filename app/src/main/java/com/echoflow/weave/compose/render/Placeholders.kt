package com.echoflow.weave.compose.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.PlaceholderKind
import com.echoflow.weave.core.eval.WNode

/** 04 §2.9 streaming placeholders, with the 04 §2.6 shimmer. */
object Placeholders {

    @Composable
    fun ForNode(node: WNode, ctx: RenderCtx, modifier: Modifier = Modifier) {
        val kind = BuiltinCatalog.component(node.type)?.placeholder ?: PlaceholderKind.BAR
        when (kind) {
            // a container keeps its own layout while an argument is pending (a waiting value reads
            // as null, so its default applies); only leaf components show a placeholder
            PlaceholderKind.NONE -> Registry.renderers[node.type]?.invoke(node, ctx, modifier.then(commonModifier(node, ctx))) ?: RenderChildren(node.children, ctx)
            PlaceholderKind.CHART -> Bar(modifier.fillMaxWidth(), TokenResolver.heightOr(node.args["h"], "m", ctx.screenHeight), RoundedCornerShape(20.dp))
            else -> Of(kind, modifier)
        }
    }

    @Composable
    fun Of(kind: PlaceholderKind, modifier: Modifier = Modifier) {
        when (kind) {
            PlaceholderKind.TEXT -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bar(Modifier.fillMaxWidth(), 12.dp); Bar(Modifier.fillMaxWidth(0.6f), 12.dp)
            }
            PlaceholderKind.METRIC -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bar(Modifier.fillMaxWidth(0.4f), 14.dp); Bar(Modifier.fillMaxWidth(0.6f), 28.dp)
            }
            PlaceholderKind.TABLE -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { Bar(Modifier.fillMaxWidth(), 16.dp) }
            }
            PlaceholderKind.INPUT -> Bar(modifier.fillMaxWidth(), 48.dp, CircleShape)
            PlaceholderKind.BUTTON -> Bar(modifier.width(112.dp), 40.dp, CircleShape)
            PlaceholderKind.CHART -> Bar(modifier.fillMaxWidth(), 168.dp, RoundedCornerShape(20.dp))
            else -> Bar(modifier.fillMaxWidth(), 48.dp)
        }
    }

    /** One shimmering bar. */
    @Composable
    fun Bar(modifier: Modifier, height: Dp, shape: Shape = RoundedCornerShape(6.dp)) {
        val t = rememberInfiniteTransition(label = "shimmer")
        val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "shimmer-alpha")
        Box(modifier.height(height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = a)))
    }
}
