package com.echoflow.weave.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppDatabase
import com.echoflow.ui.components.LocalMarkdownBlockExtensions
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.HostEffect
import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WDateTime
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.runtime.WeaveBlockController
import com.echoflow.weave.runtime.WeaveHost
import com.echoflow.weave.runtime.WeaveRuntime
import java.time.ZoneId

/** The chat screen's Echo UI runtime; null outside a chat (no blocks render there). */
val LocalWeaveRuntime = staticCompositionLocalOf<WeaveRuntime?> { null }

/**
 * "Make interactive" on a reply (05 §11.6): null when unavailable (local model). Takes the
 * reply's text.
 */
val LocalMakeInteractive = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** The user turn "Make interactive" sends starts with this; it renders as a divider, not a bubble. */
const val MAKE_INTERACTIVE_PREFIX = "Turn the answer below into one Weave block"

/** Sends a chat message as the user (co-pilot "Send to chat", 05 §7.4). */
val LocalWeaveSay = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** New blocks a reply may render, from the prompt level (05 §11.4). */
val LocalWeaveMaxBlocks = androidx.compose.runtime.compositionLocalOf { 1 }

/** An outbound action waiting for the user's confirmation (05 §13.1). */
data class ConfirmRequest(
    val block: WeaveBlockController,
    val verb: String,
    val title: String,
    val body: @Composable () -> Unit,
    val run: () -> Unit,
)

/**
 * Hosts Echo UI for one chat screen: owns the [WeaveRuntime], performs deferred and outbound
 * actions (03 §7.4) and shows the confirm sheet. [onSay] sends text as the user (`!say`, 03 §9.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaveHostLayer(
    onSay: (String) -> Unit,
    maxBlocks: Int = 1,
    onMakeInteractive: ((String) -> Unit)? = null,
    hiddenModel: com.echoflow.weave.runtime.HiddenModel? = null,
    tools: Map<String, com.echoflow.weave.runtime.WeaveTool> = emptyMap(),
    autoFix: () -> Boolean = { false },
    modelTier: () -> Int = { 3 },
    modelId: () -> String = { "" },
    qualityRead: (String) -> String? = { null },
    qualityWrite: (String, String?) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val say by rememberUpdatedState(onSay)
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    val runtime = remember { WeaveRuntime(AppDatabase.getDatabase(context.applicationContext), Registry.names) }
    val host = remember(runtime) {
        object : WeaveHost {
            override fun perform(block: WeaveBlockController, effect: HostEffect) {
                WeaveEffects.perform(context, view, block, effect, { say(it) }) { confirm = it }
            }
            override fun rejectHaptic() { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
        }
    }
    DisposableEffect(runtime, hiddenModel) {
        runtime.modelLoop = hiddenModel?.let { m ->
            com.echoflow.weave.runtime.ModelLoop(runtime, m, tools, autoFix, modelTier).also { loop ->
                loop.qualityStore = qualityRead; loop.qualityWrite = qualityWrite; loop.currentModelId = modelId
            }
        }
        onDispose { }
    }
    DisposableEffect(runtime) {
        runtime.host = host
        onDispose { runtime.close() }
    }
    CompositionLocalProvider(LocalWeaveRuntime provides runtime, LocalWeaveMaxBlocks provides maxBlocks, LocalMakeInteractive provides onMakeInteractive, LocalWeaveSay provides { t: String -> say(t) }) { content() }
    confirm?.let { req ->
        ModalBottomSheet({ confirm = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the block's identity is always shown so a block can't pose as system UI (05 §13.2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(TokenResolver.icon(req.block.program?.header?.icon ?: "widgets"), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(req.block.title ?: "Interactive block", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(req.title, style = MaterialTheme.typography.titleLarge)
                req.body()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ confirm = null }) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    Button({ confirm = null; req.run() }) { Text(req.verb) }
                }
            }
        }
    }
}

/**
 * Per-message provider: registers the `weave` fence extension for an assistant message (05 §5.1).
 * [messageId] is null while the reply streams.
 */
@Composable
fun ProvideWeave(messageId: String?, chatId: String?, content: @Composable () -> Unit) {
    val runtime = LocalWeaveRuntime.current
    val maxBlocks = LocalWeaveMaxBlocks.current
    if (runtime == null) { content(); return }
    val ext = remember(runtime, messageId, chatId, maxBlocks) { WeaveBlockExtension(runtime, messageId, chatId, maxBlocks) }
    CompositionLocalProvider(LocalMarkdownBlockExtensions provides mapOf("weave" to ext, "weave-json" to ext)) { content() }
}

/** Deferred and outbound actions (03 §7.4, 04 §13). Outbound ones always go through the confirm sheet. */
object WeaveEffects {

    private fun s(v: WValue?, block: WeaveBlockController): String = when (v) {
        null, WNull -> ""
        is WStr -> v.v
        is WNode -> block.session?.plainText(v) ?: ""
        else -> block.session?.ctx?.formatter?.auto(v) ?: v.toString()
    }

    fun perform(
        context: Context,
        view: View,
        block: WeaveBlockController,
        e: HostEffect,
        say: (String) -> Unit,
        confirm: (ConfirmRequest) -> Unit,
    ) {
        val a0 = e.args.getOrNull(0)
        fun arg(i: Int, name: String) = e.args.getOrNull(i) ?: e.named[name]
        when (e.kind) {
            "toast" -> Toast.makeText(context, s(a0, block).take(200), Toast.LENGTH_SHORT).show()
            "haptic" -> view.performHapticFeedback(
                when (s(a0, block)) {
                    "tick" -> HapticFeedbackConstants.CLOCK_TICK
                    "confirm" -> HapticFeedbackConstants.CONFIRM
                    "reject" -> HapticFeedbackConstants.REJECT
                    "heavy" -> HapticFeedbackConstants.LONG_PRESS
                    else -> HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
            "copy" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("EchoFlow", s(a0, block)))
            }
            "confetti" -> { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); WeaveConfetti.burst(block) }
            "say" -> {
                val text = s(arg(0, "text"), block)
                val attach = (e.named["attach"] as? com.echoflow.weave.core.eval.WList)?.items
                val extra = if (attach.isNullOrEmpty()) "" else "\n\n" + attach.joinToString("\n") { StateCodec.encode(it) }
                if (text.isNotBlank()) say(text + extra)
            }
            "url" -> {
                val href = s(arg(0, "href"), block)
                val uri = runCatching { Uri.parse(href) }.getOrNull()
                if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) return
                val domain = registrableDomain(uri.host ?: "")
                confirm(ConfirmRequest(block, "Open", "Open link", {
                    Text(buildAnnotatedString {
                        val i = href.indexOf(domain)
                        if (domain.isEmpty() || i < 0) append(href) else {
                            append(href.substring(0, i)); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(domain) }; append(href.substring(i + domain.length))
                        }
                    }, style = MaterialTheme.typography.bodyLarge)
                }) { start(context, Intent(Intent.ACTION_VIEW, uri)) })
            }
            "share" -> {
                val text = s(arg(0, "value"), block)
                confirm(ConfirmRequest(block, "Share", "Share", { Text(text.take(600), style = MaterialTheme.typography.bodyMedium) }) {
                    start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                })
            }
            "export" -> {
                val value = arg(0, "value")
                val kind = s(arg(1, "as"), block).ifEmpty { "json" }
                val text = when (kind) {
                    "csv" -> csv(value, block)
                    "weave" -> block.source()
                    "json" -> StateCodec.encode(value ?: WNull)
                    else -> s(value, block)
                }
                confirm(ConfirmRequest(block, "Save", "Save as .$kind", { Text("${(block.title ?: "block")}.$kind · ${text.length} characters", style = MaterialTheme.typography.bodyMedium) }) {
                    start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (kind == "csv") "text/csv" else "text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_TITLE, "${block.title ?: "block"}.$kind"), null))
                })
            }
            "calendar" -> {
                val title = s(arg(0, "title"), block)
                val start = millis(arg(1, "start")); val end = millis(arg(2, "end"))
                confirm(ConfirmRequest(block, "Add", "Add to calendar", { Text("$title\n${s(arg(1, "start"), block)}", style = MaterialTheme.typography.bodyLarge) }) {
                    val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title)
                    start?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                    end?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                    s(e.named["notes"], block).takeIf { it.isNotEmpty() }?.let { i.putExtra(CalendarContract.Events.DESCRIPTION, it) }
                    s(e.named["location"], block).takeIf { it.isNotEmpty() }?.let { i.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    start(context, i)
                })
            }
            "remind" -> {
                val text = s(arg(0, "text"), block); val at = millis(arg(1, "at"))
                confirm(ConfirmRequest(block, "Set", "Set a reminder", { Text("$text\n${s(arg(1, "at"), block)}", style = MaterialTheme.typography.bodyLarge) }) {
                    val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, text)
                        .putExtra(CalendarContract.Events.HAS_ALARM, true)
                    at?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                    start(context, i)
                })
            }
            "dial" -> { val n = s(a0, block); confirm(ConfirmRequest(block, "Call", "Open the dialer", { Text(n, style = MaterialTheme.typography.headlineSmall) }) { start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n)))) }) }
            "sms" -> {
                val n = s(a0, block); val body = s(arg(1, "body"), block)
                confirm(ConfirmRequest(block, "Compose", "Send message", { Text("$n\n$body", style = MaterialTheme.typography.bodyLarge) }) {
                    start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))).putExtra("sms_body", body))
                })
            }
            "email" -> {
                val to = s(a0, block); val subject = s(arg(1, "subject"), block); val body = s(arg(2, "body"), block)
                confirm(ConfirmRequest(block, "Compose", "Write email", { Text("$to\n$subject\n\n$body", style = MaterialTheme.typography.bodyMedium) }) {
                    start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to))).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body))
                })
            }
            "map", "directions" -> {
                val q = when (val v = a0) { is WRec -> listOfNotNull((v["lat"] as? com.echoflow.weave.core.eval.WNum)?.v, (v["lng"] as? com.echoflow.weave.core.eval.WNum)?.v).joinToString(","); else -> s(v, block) }
                val uri = if (e.kind == "map") Uri.parse("geo:0,0?q=" + Uri.encode(q)) else Uri.parse("google.navigation:q=" + Uri.encode(q))
                confirm(ConfirmRequest(block, "Open", if (e.kind == "map") "Show on a map" else "Get directions", { Text(q, style = MaterialTheme.typography.bodyLarge) }) {
                    start(context, Intent(Intent.ACTION_VIEW, uri))
                })
            }
            "branch" -> {
                val text = s(a0, block)
                confirm(ConfirmRequest(block, "Send", "Continue in chat", { Text(text, style = MaterialTheme.typography.bodyLarge) }) { say(text) })
            }
            "expand", "collapse" -> block.requestExpanded(e.kind == "expand")
            "open", "close", "go", "focus", "scrollTo" -> block.localNavigation(e.kind, e.args)
            "ask", "refresh", "regen", "tool" -> block.modelAction(e)
        }
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "No app can do that", Toast.LENGTH_SHORT).show() }
    }

    private fun millis(v: WValue?): Long? = when (v) {
        is WDateTime -> v.v.toEpochMilli()
        is WDate -> v.v.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else -> null
    }

    private fun csv(v: WValue?, block: WeaveBlockController): String {
        val rows = (v as? com.echoflow.weave.core.eval.WList)?.items?.filterIsInstance<WRec>() ?: return s(v, block)
        val keys = rows.flatMap { it.fields.keys }.distinct()
        fun cell(x: String) = if (x.any { it == ',' || it == '"' || it == '\n' }) "\"" + x.replace("\"", "\"\"") + "\"" else x
        return (listOf(keys.joinToString(",") { cell(it) }) + rows.map { r -> keys.joinToString(",") { cell(s(r[it], block)) } }).joinToString("\n")
    }

    /** The registrable domain shown in bold on the confirm sheet (05 §13.1), e.g. `bank.example.co.in`. */
    fun registrableDomain(host: String): String {
        val parts = host.lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (parts.size <= 2) return parts.joinToString(".")
        val twoLevel = setOf("co", "com", "org", "net", "gov", "ac", "edu", "nic", "res")
        return if (parts[parts.size - 2] in twoLevel && parts.last().length == 2) parts.takeLast(3).joinToString(".") else parts.takeLast(2).joinToString(".")
    }
}
