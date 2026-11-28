package com.echoflow.weave.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.has
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.Coercion
import com.echoflow.weave.core.eval.Place
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.truthy
import com.echoflow.weave.core.eval.weaveEquals
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Inputs, 04 §7. Every input writes its bound place through the controller (one transaction). */
object Inputs {

    /** Form-wide "show validation errors" (04 §7.1): set once a Form submit was attempted. */
    val LocalShowRuleErrors = compositionLocalOf { false }

    private fun place(node: WNode): Place? = node.places["value"] ?: node.places["index"] ?: node.places["deck"]

    fun write(node: WNode, ctx: RenderCtx, value: WValue, drag: Boolean = false) {
        val p = place(node) ?: return
        ctx.controller.input(p, value, node.args["onChange"], drag)
    }

    /** 04 §7.1: null = valid. */
    fun ruleError(node: WNode): String? {
        if (!node.has("rule")) return null
        return when (val r = node.args["rule"]) {
            is WStr -> r.v
            null -> null
            else -> if (r.truthy()) null else "Invalid value"
        }
    }

    @Composable
    private fun Help(node: WNode, shown: Boolean) {
        val err = ruleError(node).takeIf { shown }
        when {
            err != null -> Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            node.str("help") != null -> Text(node.str("help") ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ---- buttons --------------------------------------------------------------------------------

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun ButtonR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val variant = node.word("variant") ?: "filled"
        val size = node.word("size") ?: "s"
        val (height, textStyle, iconSize) = when (size) {
            "xs" -> Triple(32.dp, MaterialTheme.typography.labelLarge, 18.dp)
            "m" -> Triple(56.dp, MaterialTheme.typography.titleMedium, 24.dp)
            "l" -> Triple(96.dp, MaterialTheme.typography.headlineSmall, 32.dp)
            "xl" -> Triple(136.dp, MaterialTheme.typography.headlineLarge, 40.dp)
            else -> Triple(40.dp, MaterialTheme.typography.labelLarge, 20.dp)
        }
        val emphasis = when (variant) { "filled" -> "high"; "tonal" -> "medium"; else -> "low" }
        val pair = TokenResolver.tone(node.word("tone"), emphasis, scheme, ctx.dark)
        val loading = node.bool("loading")
        val enabled = !node.bool("disabled") && !loading
        val onClick = { ctx.fire(node.args["onTap"]) }
        val m = modifier.heightIn(min = height).then(if (node.bool("wide")) Modifier.fillMaxWidth() else Modifier)
        val shape = node.args["shape"]?.takeIf { it != WNull }?.let { TokenResolver.shape(it) } ?: ButtonDefaults.shape
        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            if (loading) CircularWavyProgressIndicator(Modifier.size(20.dp))
            else {
                node.str("icon")?.let { Icon(TokenResolver.icon(it), null, Modifier.size(iconSize).padding(end = 0.dp)); androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp)) }
                Text(ctx.fmt(node.args["label"]), style = textStyle)
                node.str("trailIcon")?.let { androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp)); Icon(TokenResolver.icon(it), null, Modifier.size(iconSize)) }
            }
        }
        val button = @Composable {
            when (variant) {
                "tonal" -> FilledTonalButton(onClick, m, enabled, shape = shape, colors = pair?.let { ButtonDefaults.filledTonalButtonColors(containerColor = it.container, contentColor = it.content) } ?: ButtonDefaults.filledTonalButtonColors(), content = content)
                "outlined" -> OutlinedButton(onClick, m, enabled, shape = shape, colors = pair?.let { ButtonDefaults.outlinedButtonColors(contentColor = it.content) } ?: ButtonDefaults.outlinedButtonColors(), content = content)
                "text" -> TextButton(onClick, m, enabled, shape = shape, colors = pair?.let { ButtonDefaults.textButtonColors(contentColor = it.content) } ?: ButtonDefaults.textButtonColors(), content = content)
                "elevated" -> ElevatedButton(onClick, m, enabled, shape = shape, content = content)
                else -> Button(onClick, m, enabled, shape = shape, colors = pair?.let { ButtonDefaults.buttonColors(containerColor = it.container, contentColor = it.content) } ?: ButtonDefaults.buttonColors(), content = content)
            }
        }
        val sayText = ctx.controller.sayPreview(node.args["onTap"])
        if (sayText != null) {
            TooltipBox(TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(sayText) } }, state = rememberTooltipState()) { button() }
        } else button()
    }

    @Composable
    fun IconButtonR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val icon = TokenResolver.icon(node.str("icon") ?: node.word("icon"))
        val label = node.str("label")
        val size = when (node.word("size")) { "xs" -> 32.dp; "m" -> 56.dp; "l" -> 96.dp; "xl" -> 136.dp; else -> 40.dp }
        val iconSize = size * 0.5f
        val onClick = { ctx.fire(node.args["onTap"]) }
        val enabled = !node.bool("disabled")
        val m = modifier.size(size)
        if (node.has("selected")) {
            IconToggleButton(node.bool("selected"), { onClick() }, m, enabled) { Icon(icon, label, Modifier.size(iconSize)) }
            return
        }
        when (node.word("variant")) {
            "filled" -> FilledIconButton(onClick, m, enabled) { Icon(icon, label, Modifier.size(iconSize)) }
            "tonal" -> FilledTonalIconButton(onClick, m, enabled) { Icon(icon, label, Modifier.size(iconSize)) }
            "outlined" -> OutlinedIconButton(onClick, m, enabled) { Icon(icon, label, Modifier.size(iconSize)) }
            else -> IconButton(onClick, m, enabled) { Icon(icon, label, Modifier.size(iconSize)) }
        }
    }

    // ---- toggles --------------------------------------------------------------------------------

    @Composable
    fun ToggleR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val on = node.args["value"]?.truthy() == true
        val label = node.str("label")
        val sw = @Composable {
            Switch(on, null, thumbContent = if (on && node.bool("icon", true)) ({ Icon(TokenResolver.icon("check"), null, Modifier.size(16.dp)) }) else null, enabled = !node.bool("disabled"))
        }
        if (label == null) Row(modifier.toggleable(on, role = Role.Switch) { write(node, ctx, WBool.of(it)) }) { sw() }
        else Row(
            modifier.fillMaxWidth().toggleable(on, enabled = !node.bool("disabled"), role = Role.Switch) { write(node, ctx, WBool.of(it)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyLarge); Help(node, true) }
            sw()
        }
    }

    @Composable
    fun CheckR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val on = node.args["value"]?.truthy() == true
        Row(
            modifier.fillMaxWidth().toggleable(on, enabled = !node.bool("disabled"), role = Role.Checkbox) { write(node, ctx, WBool.of(it)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(on, null)
            Text(ctx.fmt(node.args["label"]), style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    fun RadioR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options") ?: emptyList()
        val value = node.args["value"] ?: WNull
        val render = node.args["render"] as? com.echoflow.weave.core.eval.WFn
        Column(modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { opt ->
                val selected = weaveEquals(opt, value)
                Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { write(node, ctx, opt) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, null)
                    val custom = render?.let { ctx.controller.callFn(it, listOf(opt)) } as? WNode
                    if (custom != null) com.echoflow.weave.compose.render.RenderNode(custom, ctx.child()) else Text(ctx.fmt(opt), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // ---- choices --------------------------------------------------------------------------------

    private fun optionLabel(node: WNode, ctx: RenderCtx, i: Int, opt: WValue): String =
        node.list("labels")?.getOrNull(i)?.let { ctx.fmt(it) } ?: ((node.str("prefix") ?: "") + ctx.fmt(opt) + (node.str("suffix") ?: ""))

    private fun selectedOf(node: WNode): List<WValue> = when (val v = node.args["value"]) {
        is WList -> v.items
        null, WNull -> emptyList()
        else -> listOf(v)
    }

    private fun toggleMulti(node: WNode, ctx: RenderCtx, opt: WValue) {
        val options = node.list("options") ?: return
        val sel = selectedOf(node)
        val next = if (sel.any { weaveEquals(it, opt) }) sel.filterNot { weaveEquals(it, opt) } else sel + opt
        write(node, ctx, WList(options.filter { o -> next.any { weaveEquals(it, o) } }))
    }

    @Composable
    fun Labelled(node: WNode, modifier: Modifier, content: @Composable () -> Unit) {
        val label = node.str("label")
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            content()
            Help(node, LocalShowRuleErrors.current)
        }
    }

    @Composable
    fun SegmentedR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options") ?: emptyList()
        val labels = options.mapIndexed { i, o -> optionLabel(node, ctx, i, o) }
        if (options.size > 5 || labels.sumOf { it.length } > 36) { ChipsR(node, ctx, modifier); return }
        val multi = node.bool("multi")
        val sel = selectedOf(node)
        val icons = node.list("icons")
        Labelled(node, modifier) {
            if (multi) MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, opt ->
                    SegmentedButton(sel.any { weaveEquals(it, opt) }, { toggleMulti(node, ctx, opt) }, SegmentedButtonDefaults.itemShape(i, options.size), enabled = !node.bool("disabled")) { Text(labels[i], maxLines = 1) }
                }
            } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, opt ->
                    SegmentedButton(
                        sel.any { weaveEquals(it, opt) }, { write(node, ctx, opt) }, SegmentedButtonDefaults.itemShape(i, options.size),
                        enabled = !node.bool("disabled"),
                        icon = { icons?.getOrNull(i)?.let { ic -> Icon(TokenResolver.icon(ctx.fmt(ic)), null, Modifier.size(18.dp)) } ?: SegmentedButtonDefaults.Icon(sel.any { weaveEquals(it, opt) }) },
                    ) { Text(labels[i], maxLines = 1) }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChipsR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options") ?: emptyList()
        val multi = node.bool("multi")
        val sel = selectedOf(node)
        val chips = @Composable {
            options.forEachIndexed { i, opt ->
                val on = sel.any { weaveEquals(it, opt) }
                FilterChip(on, { if (multi) toggleMulti(node, ctx, opt) else if (!on) write(node, ctx, opt) }, { Text(optionLabel(node, ctx, i, opt)) }, enabled = !node.bool("disabled"))
            }
        }
        Labelled(node, modifier) {
            if (node.bool("scroll")) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        }
    }

    // ---- slider and stepper ---------------------------------------------------------------------

    /** Rounds to the step, or 4 significant decimals with no step (04 §7 Slider). */
    fun snap(v: Double, min: Double, step: Double?): Double =
        if (step != null && step > 0) min + Math.round((v - min) / step) * step
        else BigDecimal(v).round(MathContext(maxOf(1, BigDecimal(v).precision() - BigDecimal(v).scale() + 4), RoundingMode.HALF_UP)).toDouble()

    @Composable
    fun SliderR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val min = node.num("min") ?: 0.0
        val max = (node.num("max") ?: 100.0).let { if (it <= min) min + 1 else it }
        val step = node.num("step")
        val current = ((node.args["value"] as? WNum)?.v ?: min).coerceIn(min, max)
        var dragging by remember { mutableStateOf(false) }
        var local by remember { mutableFloatStateOf(current.toFloat()) }
        LaunchedEffect(current) { if (!dragging) local = current.toFloat() }
        val steps = step?.let { s -> (Math.round((max - min) / s) - 1).toInt().takeIf { it in 1..100 } } ?: 0
        val shown = snap(local.toDouble(), min, step)
        val valueText = (node.str("prefix") ?: "") + ctx.fmt(WNum(shown), node.str("format")) + (node.str("suffix") ?: "")
        val inline = node.bool("inline")
        Column(modifier.then(if (inline) Modifier.widthIn(max = 120.dp) else Modifier.fillMaxWidth())) {
            if (!inline && (node.str("label") != null || node.bool("showValue", true))) Row(Modifier.fillMaxWidth()) {
                Text(node.str("label") ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (node.bool("showValue", true)) Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            var lastStep by remember { mutableStateOf(shown) }
            Slider(
                value = local,
                onValueChange = { v ->
                    dragging = true; local = v
                    val s = snap(v.toDouble(), min, step)
                    if (s != lastStep) {
                        lastStep = s
                        if (step != null) ctx.controller.hostEffect("haptic", listOf(WStr("tick")))
                        write(node, ctx, WNum.of(s), drag = true)
                    }
                },
                onValueChangeFinished = { dragging = false; ctx.controller.endDrag() },
                valueRange = min.toFloat()..max.toFloat(),
                steps = steps,
                enabled = !node.bool("disabled"),
                modifier = Modifier.semantics { stateDescription = valueText },
            )
            Help(node, LocalShowRuleErrors.current)
        }
    }

    @Composable
    fun StepperR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val v = (node.args["value"] as? WNum)?.v ?: 0.0
        val step = node.num("step") ?: 1.0
        val min = node.num("min"); val max = node.num("max")
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            node.str("label")?.let { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge) }
            FilledTonalIconButton({ write(node, ctx, WNum.of(snap(v - step, min ?: 0.0, step).let { if (min != null) maxOf(min, it) else it })) }, enabled = min == null || v > min) {
                Icon(TokenResolver.icon("remove"), "Decrease")
            }
            Text(ctx.fmt(WNum(v), node.str("format")) + (node.str("suffix") ?: ""), Modifier.widthIn(min = 48.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton({ write(node, ctx, WNum.of(snap(v + step, min ?: 0.0, step).let { if (max != null) minOf(max, it) else it })) }, enabled = max == null || v < max) {
                Icon(TokenResolver.icon("add"), "Increase")
            }
        }
    }

    // ---- text and select ------------------------------------------------------------------------

    private val SECRET = Regex(
        """\b(password|passcode|otp|one[- ]time|cvv|cvc|card number|account number|routing|ifsc|ssn|aadhaar|pan number)\b|\b(atm|card|debit|credit|upi|bank|mpin|secret|security|login)\s+pin\b|\bpin\b(?!\s*code)""",
        RegexOption.IGNORE_CASE,
    )

    /** T-400: secret-shaped fields are refused. */
    fun isSecretShaped(label: String?, placeholder: String?) = listOfNotNull(label, placeholder).any { SECRET.containsMatchIn(it) }

    @Composable
    fun TextFieldR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val label = ctx.fmt(node.args["label"])
        val placeholder = node.str("placeholder")
        val kind = node.word("kind") ?: "text"
        val secret = isSecretShaped(label, placeholder)
        val bound = node.args["value"]
        val external = when (bound) { null, WNull -> ""; else -> ctx.fmt(bound) }
        var text by remember { mutableStateOf(external) }
        var focused by remember { mutableStateOf(false) }
        var touched by remember { mutableStateOf(false) }
        var numberError by remember { mutableStateOf(false) }
        LaunchedEffect(external) { if (!focused) text = external }
        val maxLen = node.num("maxLen")?.toInt()
        val showErr = (touched || LocalShowRuleErrors.current)
        val err = if (secret) "EchoFlow blocks can't ask for secrets" else ruleError(node).takeIf { showErr } ?: if (numberError) "Enter a number" else null
        val onChange: (String) -> Unit = { raw ->
            val t = if (maxLen != null) raw.take(maxLen) else raw
            text = t
            if (kind == "number") {
                val n = Coercion.parseNumber(t)
                numberError = n == null && t.isNotBlank()
                if (n != null) write(node, ctx, WNum.of(n)) else if (t.isBlank()) write(node, ctx, WNull)
            } else write(node, ctx, WStr(t))
        }
        val keyboard = when (kind) {
            "number" -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            "email" -> KeyboardOptions(keyboardType = KeyboardType.Email)
            "phone" -> KeyboardOptions(keyboardType = KeyboardType.Phone)
            "url" -> KeyboardOptions(keyboardType = KeyboardType.Uri)
            "search" -> KeyboardOptions(imeAction = ImeAction.Search)
            else -> KeyboardOptions.Default
        }
        val leading = (node.str("icon") ?: if (kind == "search") "search" else null)?.let { ic -> @Composable { Icon(TokenResolver.icon(ic), null) } }
        val supporting: (@Composable () -> Unit)? = when {
            err != null -> ({ Text(err) })
            maxLen != null -> ({ Text("${text.length} / $maxLen") })
            node.str("help") != null -> ({ Text(node.str("help") ?: "") })
            else -> null
        }
        val m = modifier.fillMaxWidth().onFocusChanged { f -> if (focused && !f.isFocused) touched = true; focused = f.isFocused }
        val multiline = kind == "multiline"
        val prefix = node.str("prefix")?.let { p -> @Composable { Text(p) } }
        val suffix = node.str("suffix")?.let { p -> @Composable { Text(p) } }
        if ((node.word("style") ?: "outlined") == "filled") {
            TextField(text, onChange, m, enabled = !secret && !node.bool("disabled"), label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } }, leadingIcon = leading, prefix = prefix, suffix = suffix, supportingText = supporting, isError = err != null, keyboardOptions = keyboard, singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 8 else 1)
        } else {
            OutlinedTextField(text, onChange, m, enabled = !secret && !node.bool("disabled"), label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } }, leadingIcon = leading, prefix = prefix, suffix = suffix, supportingText = supporting, isError = err != null, keyboardOptions = keyboard, singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 8 else 1)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SelectR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val options = node.list("options") ?: emptyList()
        val value = node.args["value"] ?: WNull
        val i = options.indexOfFirst { weaveEquals(it, value) }
        var open by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(open, { open = it && !node.bool("disabled") }, modifier.fillMaxWidth()) {
            OutlinedTextField(
                if (i >= 0) optionLabel(node, ctx, i, options[i]) else if (value == WNull) "" else ctx.fmt(value), {}, readOnly = true,
                label = { Text(ctx.fmt(node.args["label"])) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                enabled = !node.bool("disabled"),
            )
            ExposedDropdownMenu(open, { open = false }) {
                options.forEachIndexed { k, opt ->
                    DropdownMenuItem({ Text(optionLabel(node, ctx, k, opt)) }, { open = false; write(node, ctx, opt) })
                }
            }
        }
    }

    // ---- form -----------------------------------------------------------------------------------

    @Composable
    fun FormR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var submitted by remember { mutableStateOf(false) }
        val gap = TokenResolver.space(node.args["gap"], 12.dp)
        CompositionLocalProvider(LocalShowRuleErrors provides submitted) {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                RenderChildren(node.children, ctx)
                // a form whose action hasn't arrived (still streaming) can't be submitted yet
                val action = node.args["onSubmit"]
                Button({
                    submitted = true
                    if (firstInvalid(node.children) != null) ctx.controller.hostEffect("haptic", listOf(WStr("reject")))
                    else ctx.fire(action)
                }, Modifier.fillMaxWidth(), enabled = action != null && action != com.echoflow.weave.core.eval.WNull) { Text(node.str("submitLabel") ?: "Submit") }
            }
        }
    }

    private fun firstInvalid(nodes: List<WNode>): WNode? {
        for (n in nodes) {
            if (ruleError(n) != null) return n
            firstInvalid(n.children)?.let { return it }
        }
        return null
    }
}
