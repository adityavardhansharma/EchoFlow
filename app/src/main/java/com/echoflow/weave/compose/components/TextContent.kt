package com.echoflow.weave.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.LocalMarkdownBlockExtensions
import com.echoflow.ui.components.MarkdownText
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.has
import com.echoflow.weave.compose.render.isAction
import com.echoflow.weave.compose.render.node
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WStr

/** Text and content components, 04 §5. */
object TextContent {

    private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|\[([^\]]+)]\((https?://[^)\s]+)\)""")

    /** `**bold**`, `*italic*`, `` `code` `` and `[label](https://…)` — nothing else (04 §5 Text). */
    fun inline(text: String, linkColor: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
        var i = 0
        for (m in INLINE.findAll(text)) {
            append(text.substring(i, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[2]) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[3]) }
                else -> withLink(LinkAnnotation.Clickable(g[5], TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) { onLink(g[5]) }) { append(g[4]) }
            }
            i = m.range.last + 1
        }
        append(text.substring(i))
    }

    @Composable
    fun TextR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val raw = ctx.fmt(node.args["content"])
        val style = when (val w = node.word("style")) {
            "s" -> MaterialTheme.typography.bodySmall; "m" -> MaterialTheme.typography.bodyMedium; "l" -> MaterialTheme.typography.bodyLarge
            else -> TokenResolver.text(node.args["style"].takeIf { w != null }, MaterialTheme.typography, "bodyLarge")
        }
        val color = TokenResolver.color(node.args["color"], scheme, ctx.dark)
            ?: TokenResolver.toneText(node.word("tone"), scheme, ctx.dark) ?: LocalContentColor.current
        val weight = when (node.word("weight")) { "bold" -> FontWeight.Bold; "medium" -> FontWeight.Medium; "regular" -> FontWeight.Normal; else -> null }
        val text = remember(raw, scheme.primary) { inline(raw, scheme.primary) { ctx.controller.requestUrl(it) } }
        val content = @Composable {
            Text(
                text, modifier,
                color = color, style = style, fontWeight = weight,
                fontStyle = if (node.bool("italic")) FontStyle.Italic else null,
                textDecoration = if (node.bool("strike")) TextDecoration.LineThrough else null,
                textAlign = when (node.word("align")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start },
                maxLines = node.num("maxLines")?.toInt()?.coerceAtLeast(1) ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (node.bool("select")) SelectionContainer { content() } else content()
    }

    @Composable
    fun TitleR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            node.str("icon")?.let { Icon(TokenResolver.icon(it), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) {
                Text(ctx.fmt(node.args["content"]), Modifier.semantics { heading() }, style = TokenResolver.text(node.args["style"], MaterialTheme.typography, "titleMedium"))
                node.str("subtitle")?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            node.node("trail")?.let { RenderNode(it, ctx.child()) }
        }
    }

    /** Rich: one paragraph of inline parts; Text parts flow word by word (04 §5). */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun RichR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            node.children.forEach { part ->
                if (part.type == "Text") ctx.fmt(part.args["content"]).split(' ').filter { it.isNotEmpty() }.forEach { w ->
                    Text(w, style = TokenResolver.text(part.args["style"], MaterialTheme.typography, "bodyLarge"))
                } else RenderNode(part, ctx.child())
            }
        }
    }

    @Composable
    fun MarkdownR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        // No nested Weave fences (04 §5 Markdown): render with no block extensions.
        CompositionLocalProvider(LocalMarkdownBlockExtensions provides emptyMap()) {
            // links go through the block's confirmation sheet, like Link and Text (05 §13.2)
            MarkdownText(ctx.fmt(node.args["source"]), modifier.fillMaxWidth(), onLinkClick = { ctx.controller.requestUrl(it) })
        }
    }

    fun iconSize(w: String?) = when (w) { "s" -> 18.dp; "l" -> 32.dp; "xl" -> 48.dp; else -> 24.dp }

    @Composable
    fun IconR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val size = iconSize(node.word("size"))
        val container = node.args["container"]
        val tone = node.word("tone")
        val tint = TokenResolver.color(node.args["color"], scheme, ctx.dark) ?: TokenResolver.toneText(tone, scheme, ctx.dark) ?: LocalContentColor.current
        val vec = TokenResolver.icon(node.str("name") ?: node.word("name"))
        if (container != null && container != WNull) {
            val pair = TokenResolver.tone(tone ?: "primary", "medium", scheme, ctx.dark)
            Box(modifier.size(size * 1.75f).clip(TokenResolver.shape(container)).background(pair?.container ?: scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(vec, node.str("label"), Modifier.size(size), tint = pair?.content ?: scheme.onPrimaryContainer)
            }
        } else Icon(vec, node.str("label"), modifier.size(size), tint = tint)
    }

    @Composable
    fun AvatarR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val size = when (node.word("size")) { "s" -> 32.dp; "l" -> 56.dp; else -> 40.dp }
        val pair = TokenResolver.tone(node.word("tone") ?: "primary", "medium", MaterialTheme.colorScheme, ctx.dark)
        val shape = TokenResolver.shape(node.args["shape"] ?: WStr("full"), CircleShape)
        val src = node.str("src")?.takeIf { it.startsWith("https://") }
        Box(modifier.size(size).clip(shape).background(pair?.container ?: MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (src != null) {
                coil.compose.AsyncImage(src, node.str("name"), Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                val initials = (node.str("name") ?: "").split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
                Text(initials, style = if (size > 50.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, color = pair?.content ?: MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    @Composable
    fun BadgeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val pair = TokenResolver.tone(node.word("tone") ?: "primary", "high", MaterialTheme.colorScheme, ctx.dark)
        Box(modifier.height(16.dp).clip(CircleShape).background(pair?.container ?: MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(ctx.fmt(node.args["content"]), style = MaterialTheme.typography.labelSmall, color = pair?.content ?: MaterialTheme.colorScheme.onPrimary)
        }
    }

    @Composable
    fun ChipR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val label = ctx.fmt(node.args["label"])
        val icon = node.str("icon")?.let { TokenResolver.icon(it) }
        val trail = node.str("trail")?.let { TokenResolver.icon(it) }
        val tap = node.args["onTap"]
        val lead: (@Composable () -> Unit)? = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } }
        val trailing: (@Composable () -> Unit)? = trail?.let { { Icon(it, null, Modifier.size(18.dp)) } }
        if (node.has("selected")) {
            FilterChip(selected = node.bool("selected"), onClick = { ctx.fire(tap) }, label = { Text(label) }, modifier = modifier, leadingIcon = lead, trailingIcon = trailing, enabled = !node.bool("disabled"))
        } else {
            AssistChip(onClick = { if (isAction(tap)) ctx.fire(tap) }, label = { Text(label) }, modifier = modifier, leadingIcon = lead, trailingIcon = trailing, enabled = !node.bool("disabled"))
        }
    }

    @Composable
    fun LinkR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val href = node.str("href") ?: ""
        val label = ctx.fmt(node.args["label"])
        val c = MaterialTheme.colorScheme.primary
        Text(
            remember(label, href, c) { buildAnnotatedString { withLink(LinkAnnotation.Clickable(href, TextLinkStyles(SpanStyle(color = c, textDecoration = TextDecoration.Underline))) { ctx.controller.requestUrl(href) }) { append(label) } } },
            modifier, style = MaterialTheme.typography.bodyLarge,
        )
    }

    @Composable
    fun QuoteR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(ctx.fmt(node.args["content"]), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                node.str("cite")?.let { Text("— $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    @Composable
    fun CalloutR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val kind = node.word("kind") ?: "info"
        val tone = when (kind) { "success" -> "success"; "warning" -> "warning"; "error" -> "error"; "tip" -> "tertiary"; else -> "primary" }
        val pair = TokenResolver.tone(tone, "medium", MaterialTheme.colorScheme, ctx.dark)
        val icon = when (kind) { "success" -> "check_circle"; "warning" -> "warning"; "error" -> "error"; "tip" -> "lightbulb"; else -> "info" }
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = pair?.container ?: MaterialTheme.colorScheme.primaryContainer, contentColor = pair?.content ?: MaterialTheme.colorScheme.onPrimaryContainer) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(TokenResolver.icon(icon), null, Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.str("title")?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    val c = MaterialTheme.colorScheme.primary
                    val raw = ctx.fmt(node.args["content"])
                    Text(remember(raw) { inline(raw, c) { ctx.controller.requestUrl(it) } }, style = MaterialTheme.typography.bodyMedium)
                    node.node("action")?.let { RenderNode(it, ctx.child()) }
                }
            }
        }
    }

    @Composable
    fun CodeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = ctx.fmt(node.args["source"])
        val lang = node.str("lang") ?: ""
        CompositionLocalProvider(LocalMarkdownBlockExtensions provides emptyMap()) {
            MarkdownText("```$lang\n$src\n```", modifier.fillMaxWidth())
        }
    }

    @Composable
    fun KbdR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val keys = when (val k = node.args["keys"]) { is WList -> k.items.map { ctx.fmt(it) }; else -> listOf(ctx.fmt(k)) }
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEachIndexed { i, key ->
                if (i > 0) Text("+", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    @Composable
    fun EmptyR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val pair = TokenResolver.tone("secondary", "medium", MaterialTheme.colorScheme, ctx.dark)
        Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(84.dp).clip(TokenResolver.shape(WStr("cookie"))).background(pair?.container ?: MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(TokenResolver.icon(node.str("icon") ?: "inbox"), null, Modifier.size(48.dp), tint = pair?.content ?: MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(ctx.fmt(node.args["title"]), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            node.str("body")?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            node.node("action")?.let { RenderNode(it, ctx.child()) }
        }
    }
}
