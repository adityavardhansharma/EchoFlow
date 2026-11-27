package com.echoflow.weave.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.echoflow.ui.components.LocalMarkdownBlockExtensions
import com.echoflow.ui.components.LocalMarkdownExtensionOrdinal
import com.echoflow.ui.components.MarkdownBlockExtension
import com.echoflow.ui.components.MarkdownExtensionBlock
import com.echoflow.ui.components.MarkdownText
import com.echoflow.weave.compose.render.ErrorChip
import com.echoflow.weave.compose.render.Placeholders
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.catalog.PlaceholderKind
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.runtime.WeaveBlockController
import com.echoflow.weave.runtime.WeaveRuntime
import com.echoflow.weave.runtime.storage.WeaveVersionRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

/**
 * The `weave` fence extension (05 §5.1). Edit fences (`weave edit <id>`) render a chip and apply
 * the edit when they close (05 §5.2); blocks beyond the reply's limit render as code (05 §11.4).
 */
class WeaveBlockExtension(
    private val runtime: WeaveRuntime,
    private val messageId: String?,
    private val chatId: String?,
    private val maxBlocks: Int,
) : MarkdownBlockExtension {
    @Composable
    override fun Render(block: MarkdownExtensionBlock, modifier: Modifier) {
        val words = block.info.trim().split(Regex("\\s+"))
        if (words.getOrNull(1) == "edit") {
            EditChip(runtime, chatId, messageId, words.getOrNull(2) ?: "", block, modifier)
            return
        }
        if (LocalMarkdownExtensionOrdinal.current >= maxBlocks) {
            CodeFence(block.body, modifier)
            return
        }
        val controller = runtime.controller(messageId, chatId, block.key)
        WeaveBlock(controller, block, modifier)
    }
}

/** A fence shown as an ordinary code block (no nested extensions). */
@Composable
fun CodeFence(body: String, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalMarkdownBlockExtensions provides emptyMap()) {
        MarkdownText("```weave\n$body\n```", modifier.fillMaxWidth())
    }
}

@Composable
private fun evalContext(): EvalContext {
    val config = LocalConfiguration.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val locale = config.locales[0] ?: Locale.getDefault()
    return remember(locale, dark, config.screenWidthDp, config.fontScale) {
        EvalContext(
            formatter = Formatter(locale = locale, zone = ZoneId.systemDefault()),
            deviceWidth = when { config.screenWidthDp >= 840 -> "expanded"; config.screenWidthDp >= 600 -> "medium"; else -> "compact" },
            landscape = config.screenWidthDp > config.screenHeightDp,
            dark = dark,
            fontScale = config.fontScale.toDouble(),
        )
    }
}

@Composable
fun WeaveBlock(controller: WeaveBlockController, block: MarkdownExtensionBlock, modifier: Modifier = Modifier) {
    val context = evalContext()
    LaunchedEffect(controller, block.body, block.complete) { controller.update(block.body, block.complete, context) }
    val program = controller.program
    val version = controller.version
    if (program == null) {
        Placeholders.Of(PlaceholderKind.BAR, modifier.padding(vertical = 4.dp))
        return
    }
    if (program.needsNewerApp) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { CodeFence(block.body); ErrorChip("Needs a newer EchoFlow") }
        return
    }
    if (program.complete && !program.renderable) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) { CodeFence(block.body); ErrorChip("Couldn't render this block") }
        return
    }
    if (controller.plain) {
        Column(modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalMarkdownBlockExtensions provides emptyMap()) { MarkdownText(controller.plainText(), Modifier.fillMaxWidth()) }
            TextButton({ controller.showPlain(false) }) { Text("Show interactive") }
        }
        return
    }
    // real-time clock for every/debounce/!after while the app is in the foreground (05 §4.6)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(controller, program.complete) {
        if (program.complete) controller.appear()
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = System.currentTimeMillis()
            while (true) {
                delay(if (controller.hasTimers) 100 else 500)
                val now = System.currentTimeMillis()
                if (controller.hasTimers) controller.tick(now - last)
                last = now
            }
        }
    }
    LaunchedEffect(controller) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { kotlinx.coroutines.awaitCancellation() } finally { controller.flush() }
        }
    }
    // `frame` effects and `loop()` run per display frame while the app is in the foreground (03 §8, 04 §13.1)
    val reducedMotion = remember { com.echoflow.weave.compose.motion.Motion.reducedMotion() }
    LaunchedEffect(controller, program, reducedMotion) {
        if (!program.complete || reducedMotion || !controller.animated) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) androidx.compose.runtime.withFrameNanos { now -> controller.frame((now - last) / 1_000_000); last = now }
        }
    }
    com.echoflow.weave.compose.motion.MotionSensors(controller)
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val firstStreaming = remember { !block.complete }
    val ctx = remember(controller, context, dark, screenH) {
        RenderCtx(controller, controller.session?.ctx?.formatter ?: context.formatter, screenH, dark, animateEnter = firstStreaming)
    }
    val root = remember(version, program) { controller.root() }
    var expanded by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clipToBounds().animateContentSize()) {
            controller.consentRequest?.let { ConsentStrip(it) }
            if (controller.budgetBanner) BudgetBanner(controller)
            if (root != null) RenderNode(root, ctx.child()) else Placeholders.Of(PlaceholderKind.BAR)
            controller.chip?.let { Spacer(Modifier.size(6.dp)); ErrorChip(it) }
            if (!program.complete && root == null) Placeholders.Of(PlaceholderKind.TEXT)
        }
        com.echoflow.weave.compose.motion.ConfettiOverlay(controller.confetti, Modifier.matchParentSize())
        com.echoflow.weave.compose.components.Overlays.Host(ctx)
        }
    }
    BlockChrome(controller, program.header.size, program.header.title, program.header.icon, modifier, onExpand = { expanded = true }, content = content)
    val expandReq = controller.expandRequest
    LaunchedEffect(expandReq) { if (expandReq != null) { expanded = expandReq; controller.expandRequest = null } }
    if (expanded) FullScreen(controller, program.header.title, { expanded = false }, content)
}

/** 05 §7.1 chrome by `size`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockChrome(
    controller: WeaveBlockController,
    size: String,
    title: String?,
    icon: String?,
    modifier: Modifier,
    onExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    when (size) {
        "card", "full" -> OutlinedCard(
            modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TokenResolver.icon(icon ?: "widgets"), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onExpand) { Icon(TokenResolver.icon("open_in_full"), "Expand") }
                Box {
                    IconButton({ menu = true }) { Icon(TokenResolver.icon("more_vert"), "Block menu") }
                    BlockMenu(controller, menu) { menu = false }
                }
            }
            if (size == "full") {
                Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).clipToBounds().padding(horizontal = 16.dp)) {
                    content()
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.7f to androidx.compose.ui.graphics.Color.Transparent, 1f to MaterialTheme.colorScheme.surfaceContainerLow)))
                }
                Button(onExpand, Modifier.padding(16.dp)) { Text("Open") }
            } else Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
        else -> Box(modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }, indication = null, interactionSource = null)) {
            content()
            if (menu) Box(Modifier.align(Alignment.TopEnd)) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(TokenResolver.icon("more_horiz"), "Block menu", Modifier.size(24.dp))
                }
                BlockMenu(controller, true) { menu = false }
            }
        }
    }
}

/** 05 §8.7: "This block wants to …" with Allow once / Always for this block / No. */
@Composable
private fun ConsentStrip(req: WeaveBlockController.ConsentRequest) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(TokenResolver.icon("shield"), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("This block wants to ${req.text}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ req.answer(false, false) }) { Text("No") }
                TextButton({ req.answer(true, true) }) { Text("Always for this block") }
                TextButton({ req.answer(true, false) }) { Text("Allow once") }
            }
        }
    }
}

/** 05 §8.4: the hourly budget for hidden requests ran out. */
@Composable
private fun BudgetBanner(controller: WeaveBlockController) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(12.dp)) {
            Text("This block has updated 20 times this hour. Keep updating?", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ controller.modelStopped = true; controller.budgetBanner = false }) { Text("Stop") }
                TextButton({ controller.extraBudget += 20; controller.budgetBanner = false }) { Text("Keep updating") }
            }
        }
    }
}

/** 05 §7.2. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockMenu(controller: WeaveBlockController, open: Boolean, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<List<WeaveVersionRow>?>(null) }
    var remix by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loop = com.echoflow.weave.compose.LocalWeaveRuntime.current?.modelLoop as? com.echoflow.weave.runtime.ModelLoop
    DropdownMenu(open, onDismiss) {
        DropdownMenuItem({ Text("Undo") }, { controller.undo(); onDismiss() }, enabled = controller.canUndo, leadingIcon = { Icon(TokenResolver.icon("undo"), null) })
        if (controller.blockVersion > 1) DropdownMenuItem({ Text("Versions") }, { scope.launch { versions = controller.versions() }; onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("history"), null) })
        if (loop != null) {
            DropdownMenuItem({ Text("Change this block…") }, { remix = true; onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("auto_fix_high"), null) })
            DropdownMenuItem({ Text("Regenerate") }, { loop.regen(controller); onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("refresh"), null) })
        }
        DropdownMenuItem({ Text("Permissions") }, { controller.revokeConsents(); controller.flashChip("Permissions reset"); onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("shield"), null) })
        DropdownMenuItem({ Text("View source") }, { source = true; onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("code"), null) })
        DropdownMenuItem({ Text("Copy as text") }, { controller.hostEffect("copy", listOf(com.echoflow.weave.core.eval.WStr(controller.plainText()))); onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("content_copy"), null) })
        DropdownMenuItem({ Text("Show as plain text") }, { controller.showPlain(true); onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("notes"), null) })
        DropdownMenuItem({ Text("Reset") }, { confirmReset = true; onDismiss() }, leadingIcon = { Icon(TokenResolver.icon("restart_alt"), null) })
    }
    if (source) ModalBottomSheet({ source = false }) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Source", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            SelectionContainer { Text(controller.source(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            TextButton({ controller.hostEffect("copy", listOf(com.echoflow.weave.core.eval.WStr(controller.source()))) }) { Text("Copy") }
        }
    }
    if (remix && loop != null) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { remix = false },
            title = { Text("Change this block") },
            text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, placeholder = { Text("Add a chart of the yearly totals…") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ if (text.isNotBlank()) loop.remix(controller, text.trim()); remix = false }) { Text("Change") } },
            dismissButton = { TextButton({ remix = false }) { Text("Cancel") } },
        )
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset this block?") },
        text = { Text("Every value goes back to how it started.") },
        confirmButton = { TextButton({ controller.resetAll(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
    )
    versions?.let { list ->
        ModalBottomSheet({ versions = null }) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Versions", style = MaterialTheme.typography.titleMedium)
                list.asReversed().forEach { v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Version ${v.version}", style = MaterialTheme.typography.bodyLarge)
                            Text(v.cause, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (v.version != controller.blockVersion) TextButton({ scope.launch { controller.restoreVersion(v.source) }; versions = null }) { Text("Restore") }
                    }
                }
            }
        }
    }
}

/** 05 §7.3: the same session full screen, with the block title in the top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreen(controller: WeaveBlockController, title: String?, onClose: () -> Unit, content: @Composable () -> Unit) {
    val loop = com.echoflow.weave.compose.LocalWeaveRuntime.current?.modelLoop as? com.echoflow.weave.runtime.ModelLoop
    val say = com.echoflow.weave.compose.LocalWeaveSay.current
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(title ?: "Block") },
                navigationIcon = { IconButton(onClose) { Icon(TokenResolver.icon("arrow_back"), "Back") } },
            )
        }, bottomBar = {
            // 05 §7.4 co-pilot: "Change this…" remixes the block; "Send to chat" sends a message instead
            if (loop != null) {
                var text by remember { mutableStateOf("") }
                var toChat by remember { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).androidxImePadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.FilterChip(toChat, { toChat = !toChat }, { Text("Send to chat") })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(if (toChat) "Message" else "Change this…") }, maxLines = 4)
                            IconButton({
                                val t = text.trim()
                                if (t.isNotEmpty()) {
                                    if (toChat) { say?.invoke(t + "\n\n" + com.echoflow.weave.core.program.StateCodec.encode(com.echoflow.weave.core.eval.WRec(LinkedHashMap(controller.session?.state ?: emptyMap())))); onClose() }
                                    else loop.remix(controller, t)
                                    text = ""
                                }
                            }) { Icon(TokenResolver.icon("send"), "Send") }
                        }
                    }
                }
            }
        }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
        }
    }
}

private fun Modifier.androidxImePadding() = this.imePadding()

/** 05 §5.2: an edit fence renders as a chip and applies once when it closes. */
@Composable
private fun EditChip(runtime: WeaveRuntime, chatId: String?, messageId: String?, target: String, block: MarkdownExtensionBlock, modifier: Modifier) {
    var outcome by remember { mutableStateOf<WeaveRuntime.EditOutcome?>(null) }
    LaunchedEffect(block.complete, messageId) {
        if (block.complete && chatId != null && messageId != null && target.isNotEmpty()) {
            outcome = runtime.applyEdit(chatId, target, block.body, messageId, block.key)
        }
    }
    val text = when (val o = outcome) {
        is WeaveRuntime.EditOutcome.Applied -> "Updated ${o.title}"
        is WeaveRuntime.EditOutcome.Rejected -> "Couldn't update ${o.title}"
        WeaveRuntime.EditOutcome.NoTarget -> "Couldn't find the block to update"
        null -> if (block.complete) "Updating…" else "Updating $target…"
    }
    Surface(modifier.padding(vertical = 4.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TokenResolver.icon("edit"), null, Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
