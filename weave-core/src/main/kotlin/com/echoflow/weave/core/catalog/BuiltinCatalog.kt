package com.echoflow.weave.core.catalog

import com.echoflow.weave.core.check.TypeSpec

/**
 * Catalog entries from 03 §7, §8.2, §10 and 04. Components are written from their 04 signatures
 * through [Sig]. This build carries every phase 1 component whose signature 04 gives in full;
 * the renderer registry ([PHASE_1A_COMPONENTS]) decides what is advertised and accepted.
 */
object BuiltinCatalog : Catalog {

    /** Common props, 04 §2.1. Valid on every component; never repeated in prompt lines. */
    val COMMON: List<ParamSpec> = Sig.params(
        "show: any = true, w: width, h: height, minH: height, pad: space, padX: space, padY: space, " +
            "align: start|center|end|stretch|top|bottom|baseline|topStart|topEnd|bottomStart|bottomEnd, tone: tone, emphasis: low|medium|high = medium, " +
            "bg: color, shape: shape, border: bool | color = false, elevation: num = 0, enter: fade|rise|scale|slide|pop|none, " +
            "onTap: action, onLongPress: action, onDoubleTap: action, onSwipe: fn, label: str, key: any, tip: str, " +
            "badge: num | str | bool, disabled: bool = false, delay: dur = 0ms, span: num",
    ).map { it.copy(required = false) }

    val COMMON_NAMES = COMMON.map { it.name }.toSet()

    /** The common-props line of 06 §3.3. */
    const val COMMON_LINE = "Every component also takes: show w h pad align tone emphasis bg shape border enter onTap onLongPress onSwipe label key badge disabled delay"

    private fun comp(
        name: String,
        sig: String,
        leads: Int,
        tier: Int,
        phase: Int,
        doc: String,
        group: String,
        children: ChildrenSpec = ChildrenSpec.None,
        positional: List<String>? = null,
        aliases: Map<String, String> = emptyMap(),
        placeholder: PlaceholderKind = PlaceholderKind.BAR,
        block: Boolean = true,
        intrinsics: Boolean = true,
    ): ComponentSpec {
        val all = Sig.params(sig)
        val ls = all.take(leads)
        val ps = all.drop(leads)
        return ComponentSpec(
            name, ls, ps, positional ?: ps.map { it.name }, aliases, children, tier, phase, doc,
            placeholder, block, intrinsics, group,
        )
    }

    private val INPUT_ALIASES = mapOf("bind" to "value")

    /** 04 §7 shared input arguments: every input takes `help`, `rule` and `onChange`. */
    private val INPUT_SHARED = Sig.params("help?: str, rule?: any, onChange?: any").map { it.copy(promptHidden = true) }

    internal fun withInputArgs(c: ComponentSpec): ComponentSpec {
        if (c.leads.firstOrNull()?.type != com.echoflow.weave.core.check.TypeSpec.STATE) return c
        val extra = INPUT_SHARED.filter { p -> c.param(p.name) == null }
        return if (extra.isEmpty()) c else c.copy(params = c.params + extra)
    }

    override val components: List<ComponentSpec> get() = allComponents

    private val baseComponents: List<ComponentSpec> = listOf(
        // ---- Layout (04 §4)
        comp("Column", "gap: space = s, align: align = stretch, scroll: bool = false", 0, 1, 1,
            "vertical stack of children", "Layout", ChildrenSpec.Any, positional = listOf("gap", "align"),
            aliases = mapOf("spacing" to "gap", "padding" to "pad"), placeholder = PlaceholderKind.NONE),
        comp("Row", "gap: space = s, align: valign = center, justify: justify = start, wrap: auto|never = auto", 0, 1, 1,
            "horizontal row of children; becomes a column when they don't fit", "Layout", ChildrenSpec.Any,
            positional = listOf("gap", "justify"), aliases = mapOf("spacing" to "gap", "alignment" to "align"), placeholder = PlaceholderKind.NONE),
        comp("Box", "contentAlign: topStart|top|topEnd|start|center|end|bottomStart|bottom|bottomEnd = topStart", 0, 1, 1,
            "children stacked on top of each other", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Grid", "cols: num | auto = 2, gap: space = s, rowGap: space, minCell: height = s", 0, 1, 1,
            "grid of equal columns", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Flow", "gap: space = s, rowGap: space = s, justify: justify = start", 0, 1, 1,
            "items that wrap onto new lines", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Spacer", "size: space = m", 0, 1, 1, "empty space", "Layout", placeholder = PlaceholderKind.NONE),
        comp("Divider", "inset: bool = false, vertical: bool = false", 0, 1, 1, "thin dividing line", "Layout", placeholder = PlaceholderKind.NONE),
        comp("Card", "style: filled|outlined|elevated|tonal = filled, pad: space = l, gap: space = s", 0, 1, 1,
            "grouped content surface", "Layout", ChildrenSpec.Any,
            aliases = mapOf("variant" to "style", "kind" to "style"), placeholder = PlaceholderKind.NONE),
        comp("Surface", "tone: tone = neutral, emphasis: low|medium|high = low", 0, 1, 1,
            "tinted background area", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Section", "title?: str, subtitle?: str, action?: node, collapsible: bool = false, open: bool = true", 0, 1, 1,
            "titled group of content, optionally collapsible", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),

        // ---- Text (04 §5)
        comp("Text", "content: any, style: token<text> = body, tone?: tone, color?: color, weight?: regular|medium|bold, maxLines?: num, " +
            "align: start|center|end = start, select: bool = false, italic: bool = false, strike: bool = false", 1, 1, 1,
            "a paragraph of text; **bold**, *italic* and `code` work inside", "Text",
            positional = listOf("style"),
            aliases = mapOf("text" to "content", "value" to "content", "variant" to "style", "size" to "style"),
            placeholder = PlaceholderKind.TEXT),
        comp("Title", "content: str, subtitle?: str, style: token<text> = titleMedium, icon?: icon, trail?: node", 1, 1, 1,
            "heading with optional subtitle and icon", "Text", placeholder = PlaceholderKind.TEXT),
        comp("Markdown", "source: str", 1, 1, 1, "markdown text", "Text", placeholder = PlaceholderKind.TEXT),
        comp("Icon", "name: icon, size: s|m|l|xl = m, tone?: tone, color?: color, container?: shape", 1, 1, 1,
            "a Material icon", "Text", placeholder = PlaceholderKind.NONE, block = false),
        comp("Callout", "content: str, kind: info|success|warning|error|tip = info, title?: str, action?: node", 1, 1, 1,
            "highlighted note with an icon", "Text", placeholder = PlaceholderKind.TEXT),

        // ---- Numbers (04 §6)
        comp("Metric", "label: str, value: any, format?: str, prefix?: str, suffix?: str, delta?: num, deltaFormat: str = percent, " +
            "deltaGood: up|down = up, trend?: list<num>, tone?: tone, animate: bool = true, size: s|m|l = m", 2, 1, 1,
            "a labelled number that animates when it changes", "Numbers",
            positional = listOf("format"), aliases = mapOf("title" to "label", "unit" to "suffix", "change" to "delta"),
            placeholder = PlaceholderKind.METRIC),
        comp("Stat", "", 0, 1, 1, "a row of metrics", "Numbers", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),

        // ---- Inputs (04 §7)
        comp("Button", "label: str, onTap: action, variant: filled|tonal|outlined|text|elevated = filled, icon?: icon, trailIcon?: icon, " +
            "size: xs|s|m|l|xl = s, tone?: tone, wide: bool = false, loading?: bool, shape?: shape", 1, 1, 1,
            "a button that runs an action", "Inputs",
            positional = listOf("onTap", "variant", "icon"),
            aliases = mapOf("text" to "label", "title" to "label", "onClick" to "onTap", "action" to "onTap", "style" to "variant", "type" to "variant", "color" to "tone"),
            placeholder = PlaceholderKind.BUTTON, block = false),
        comp("IconButton", "icon: icon, onTap: action, variant: standard|filled|tonal|outlined = standard, label: str, size: xs|s|m|l|xl = s, selected?: bool, tone?: tone", 1, 2, 1,
            "a round icon button", "Inputs", aliases = mapOf("name" to "icon", "onClick" to "onTap"),
            placeholder = PlaceholderKind.BUTTON, block = false),
        comp("Toggle", "value: place, label?: str, icon: bool = true", 1, 2, 1, "on/off switch", "Inputs",
            aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("Check", "value: place, label: str", 1, 2, 1, "checkbox with a label", "Inputs",
            aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("Radio", "value: place, options: list, render?: fn(option) → node", 1, 2, 1, "pick one from a vertical list", "Inputs",
            aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("Segmented", "value: place, options: list, multi: bool = false, labels?: list<str>, icons?: list<icon>, suffix?: str, prefix?: str", 1, 2, 1,
            "pick one of a few options", "Inputs", aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("Chips", "value: place, options: list, multi: bool = false, labels?: list<str>, scroll: bool = false", 1, 2, 1,
            "filter chips for options", "Inputs", aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("Slider", "value: place, min: num = 0, max: num = 100, step?: num, label?: str, format?: str, prefix?: str, suffix?: str, " +
            "ticks: bool = false, showValue: bool = true, inline: bool = false", 1, 2, 1,
            "continuous value picker", "Inputs",
            positional = listOf("min", "max", "step"),
            aliases = mapOf("from" to "min", "to" to "max", "minimum" to "min", "maximum" to "max", "unit" to "suffix", "bind" to "value"),
            placeholder = PlaceholderKind.INPUT),
        comp("Stepper", "value: place, min?: num, max?: num, step: num = 1, label?: str, format?: str, suffix?: str", 1, 2, 1,
            "minus/plus number picker", "Inputs", aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),
        comp("TextField", "value: place, label: str, placeholder?: str, kind: text|number|email|phone|url|multiline|search = text, " +
            "maxLen?: num, prefix?: str, suffix?: str, icon?: icon, style: filled|outlined = outlined", 1, 2, 1,
            "text input", "Inputs", aliases = mapOf("hint" to "placeholder", "type" to "kind", "bind" to "value"),
            placeholder = PlaceholderKind.INPUT),
        comp("Select", "value: place, options: list, label: str, labels?: list<str>", 1, 2, 1,
            "dropdown to pick one option", "Inputs", aliases = INPUT_ALIASES, placeholder = PlaceholderKind.INPUT),

        // ---- Data (04 §8)
        comp("List", "items: list, render: fn(item, index) → node = auto, divider: bool = true, empty?: node, lazy: bool | auto = auto, h?: height", 1, 1, 1,
            "list of items", "Data", placeholder = PlaceholderKind.TABLE),
        comp("Item", "title: str, subtitle?: str, overline?: str, lead?: node | icon, trail?: node, onTap?: action", 1, 1, 1,
            "one list row", "Data", placeholder = PlaceholderKind.TABLE),
        comp("Table", "rows: list<rec>, cols?: list<col>, sort: bool = true, filter: bool = false, sticky: num = 1, dense: bool = false, " +
            "totals?: list<str>, onRow?: fn(row) → action, maxRows: num = 50", 1, 1, 1,
            "sortable table of records", "Data", placeholder = PlaceholderKind.TABLE, intrinsics = false),

        // ---- Navigation (04 §9)
        comp("Tabs", "index: place, labels: list<str>, icons?: list<icon>, scrollable: bool | auto = auto", 1, 2, 1,
            "tabs that switch between pages", "Navigation", ChildrenSpec.Any, placeholder = PlaceholderKind.BAR, intrinsics = false),

        // ---- Charts (04 §12)
        comp("Chart", "data: list<rec>, h: height = m, polar: bool = false, x?: axis, y?: axis, y2?: axis, legend: auto|top|bottom|none = auto, " +
            "tooltip: bool = true, scrub: bool = true, zoom: bool = false, select?: place, animate: bool = true, inner?: num", 1, 1, 1,
            "chart built from marks", "Charts", ChildrenSpec.Marks("Chart"), placeholder = PlaceholderKind.CHART),
        comp("LineChart", "data: list<rec>, x: field, y: field | list, series?: field, h: height = m", 1, 1, 1,
            "line chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("AreaChart", "data: list<rec>, x: field, y: field | list, stack: bool = false, h: height = m", 1, 1, 1,
            "area chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("BarChart", "data: list<rec>, x: field, y: field | list, stack: bool = false, horizontal: bool = false, h: height = m", 1, 1, 1,
            "bar chart", "Charts", placeholder = PlaceholderKind.CHART),
    ) + CatalogExtras.components

    private val allComponents: List<ComponentSpec> by lazy { baseComponents.map { withInputArgs(it) } }

    // ---- Chart marks, 04 §12.3 (leads = every parameter before the first optional one) ----------

    private fun mark(name: String, sig: String, doc: String, phase: Int = 1, container: Set<String> = setOf("Chart")): MarkSpec {
        val all = Sig.params(sig)
        val leads = all.takeWhile { it.required }
        return MarkSpec(name, container, leads, all.drop(leads.size), acceptsChildren = name == "group", tier = 1, phase = phase, doc = doc)
    }

    override val marks: List<MarkSpec> = listOf(
        mark("line", "x: field, y: field, tone?: tone, curve: linear|smooth|step = smooth, width: s|m|l = m, dashed: bool = false, points: bool = false, series?: field", "line through points"),
        mark("area", "x: field, y: field, y0?: field, tone?: tone, stack: bool = false, opacity: num = 0.3, series?: field, curve: linear|smooth|step = smooth", "filled area under a line"),
        mark("bar", "x: field, y: field, tone?: tone, stack: bool = false, group?: field, horizontal: bool = false, round: shape = s, labels: bool = false", "bars"),
        mark("point", "x: field, y: field, size: field | num = 6, tone?: tone, shape: circle|square|diamond = circle", "dots"),
        mark("rule", "x?: any, y?: any, tone: tone = muted, label?: str, dashed: bool = true", "reference line at a value"),
        mark("band", "from: any, to: any, axis: x|y = x, tone: tone = neutral, label?: str", "shaded range"),
        mark("text", "x: field, y: field, content: any, tone?: tone", "text label at a point"),
        mark("arc", "value: field, label: field, tone?: tone", "pie slice (needs polar: true)"),
    ) + CatalogExtras.marks

    // ---- Actions, 03 §7.3–7.4 -------------------------------------------------------------------

    private fun act(
        name: String, sig: String, leads: Int, local: Boolean, tier: Int, doc: String, confirm: Boolean = false, phase: Int = 1,
        variadic: Boolean = false, nameLeads: Boolean = false,
    ): ActionSpec {
        val all = Sig.params(sig)
        return ActionSpec(name, all.take(leads), all.drop(leads), local, confirm, tier, phase, doc, variadic, nameLeads)
    }

    override val actions: List<ActionSpec> = listOf(
        act("set", "place: state, value: any", 2, true, 2, "write a value"),
        act("inc", "place: state, by: num = 1, min?: num, max?: num", 1, true, 2, "add"),
        act("dec", "place: state, by: num = 1, min?: num, max?: num", 1, true, 2, "subtract"),
        act("toggle", "place: state", 1, true, 2, "flip a bool"),
        act("reset", "place: state", 1, true, 2, "back to the initial value", variadic = true),
        act("resetAll", "", 0, true, 2, "every state back to its initial value"),
        act("push", "place: state, item: any", 2, true, 2, "append to a list"),
        act("insert", "place: state, index: num, item: any", 1, true, 2, "insert into a list"),
        act("remove", "place: state, index: num", 2, true, 2, "remove an item"),
        act("removeWhere", "place: state, pred: fn", 2, true, 2, "remove matching items"),
        act("move", "place: state, from: num, to: num", 1, true, 2, "move an item"),
        act("patch", "place: state, fields: rec", 2, true, 2, "merge fields into a record"),
        act("undo", "", 0, true, 2, "undo"),
        act("redo", "", 0, true, 2, "redo"),
        act("if", "cond: any, then: any, else?: any", 2, true, 2, "run one branch"),
        act("open", "name: name", 1, true, 2, "open an overlay", nameLeads = true),
        act("close", "name?: name", 1, true, 2, "close overlays", nameLeads = true),
        act("go", "name: name, index: num", 2, true, 2, "switch tab or page", nameLeads = true),
        act("focus", "name: name", 1, true, 2, "focus a text field", nameLeads = true),
        act("after", "delay: dur, action: action", 2, false, 2, "run later"),
        act("haptic", "kind: token<haptic> = click", 1, false, 2, "vibrate"),
        act("toast", "text: str", 1, false, 2, "show a short message"),
        act("copy", "value: str | node", 1, false, 1, "copy to the clipboard"),
        act("confetti", "", 0, false, 2, "celebrate"),
        act("scrollTo", "name: name", 1, false, 2, "scroll to a part", nameLeads = true),
        act("expand", "", 0, false, 2, "full screen"),
        act("collapse", "", 0, false, 2, "leave full screen"),
        act("say", "text: str, attach?: list", 1, false, 2, "send a message as the user", confirm = false),
        act("ask", "prompt: str, update: list, with?: list", 1, false, 3, "ask the model to rewrite parts", phase = 2),
        act("refresh", "name: name", 1, false, 3, "regenerate a lazy part", phase = 2, nameLeads = true),
        act("regen", "", 0, false, 3, "rewrite the block", phase = 2),
        act("branch", "text: str", 1, false, 2, "branch the chat", confirm = true),
        act("url", "href: str", 1, false, 1, "open a link", confirm = true),
        act("share", "value: str | node | image", 1, false, 1, "share", confirm = true),
        act("export", "value: any, as: token<export>", 1, false, 2, "save as a file", confirm = true),
        act("calendar", "title: str, start: date | datetime, end?: date | datetime, notes?: str, location?: str", 2, false, 2, "add to calendar", confirm = true),
        act("remind", "text: str, at: datetime", 2, false, 2, "set a reminder", confirm = true),
        act("dial", "number: str", 1, false, 2, "open the dialer", confirm = true),
        act("sms", "number: str, body?: str", 1, false, 2, "compose an SMS", confirm = true),
        act("email", "to: str, subject?: str, body?: str", 1, false, 2, "compose an email", confirm = true),
        act("map", "query: str | rec", 1, false, 2, "show on a map", confirm = true),
        act("directions", "to: str | rec", 1, false, 2, "directions", confirm = true),
        act("tool", "name: str, args: rec, into: state", 2, false, 3, "run a tool", confirm = true, phase = 2),
    )

    /** Actions an effect may never run (03 §7.4). */
    val OUTBOUND = setOf("url", "share", "export", "calendar", "remind", "dial", "sms", "email", "map", "directions", "tool", "say", "branch")

    // ---- Functions, 03 §10 ----------------------------------------------------------------------

    private fun fn(name: String, sig: String, tier: Int = 1, lazy: Set<Int> = emptySet(), returns: TypeSpec = TypeSpec.ANY, phase: Int = 1) =
        FunctionSpec(name, Sig.params(sig).map { if (it.default == null && it.name.endsWith("…")) it.copy(required = false) else it }, lazy, returns, tier, "", phase)

    override val functions: List<FunctionSpec> = listOf(
        // control
        fn("if", "cond, a, b = null", lazy = setOf(1, 2)), fn("when", "cond, value", lazy = setOf(1)),
        fn("switch", "value, cases: rec, default = null", lazy = setOf(1, 2)), fn("coalesce", "values?", lazy = setOf(0)),
        // math
        fn("abs", "x"), fn("sign", "x"), fn("sqrt", "x"), fn("exp", "x"), fn("log", "x, base?"), fn("log10", "x"),
        fn("pow", "x, y"), fn("floor", "x"), fn("ceil", "x"), fn("round", "x, digits = 0"), fn("trunc", "x"),
        fn("min", "values?"), fn("max", "values?"), fn("clamp", "x, lo, hi"), fn("lerp", "a, b, t"),
        fn("remap", "x, inLo, inHi, outLo, outHi"), fn("sin", "x"), fn("cos", "x"), fn("tan", "x"), fn("asin", "x"),
        fn("acos", "x"), fn("atan", "x"), fn("atan2", "y, x"), fn("deg", "rad"), fn("rad", "deg"), fn("hypot", "a, b"),
        fn("isNumber", "x"), fn("rand", "seed, i = 0"), fn("randInt", "seed, i, lo, hi"),
        // lists
        fn("len", "x"), fn("range", "a, b?, step = 1"), fn("map", "list, fn"), fn("each", "list, fn"), fn("filter", "list, fn"),
        fn("reject", "list, fn"), fn("find", "list, fn"), fn("findIndex", "list, fn"), fn("indexOf", "list, value"),
        fn("any", "list, fn?"), fn("all", "list, fn?"), fn("count", "list, fn?"), fn("reduce", "list, init, fn"),
        fn("sum", "list, field = null"), fn("avg", "list, field = null"), fn("median", "list, field = null"),
        fn("minOf", "list, field = null"), fn("maxOf", "list, field = null"), fn("minBy", "list, field"), fn("maxBy", "list, field"),
        fn("sortBy", "list, field?, desc: bool = false"), fn("sort", "list, desc: bool = false"), fn("groupBy", "list, field"),
        fn("countBy", "list, field"), fn("unique", "list, field?"), fn("take", "list, n"), fn("drop", "list, n"),
        fn("first", "list"), fn("last", "list"), fn("reverse", "list"), fn("flatten", "list"), fn("zip", "a, b"),
        fn("chunk", "list, n"), fn("window", "list, n"), fn("cumsum", "list, field = null"), fn("pluck", "list, key"),
        fn("shuffle", "list, seed"), fn("contains", "x, value"),
        // records
        fn("keys", "rec"), fn("values", "rec"), fn("entries", "rec"), fn("fromEntries", "list"), fn("get", "rec, key, default = null"),
        fn("has", "rec, key"), fn("merge", "values?"), fn("pick", "rec, keys: list"), fn("omit", "rec, keys: list"), fn("set", "rec, key, value"),
        // strings
        fn("str", "x"), fn("fmt", "x, format"), fn("upper", "s"), fn("lower", "s"), fn("trim", "s"), fn("titleCase", "s"),
        fn("split", "s, sep"), fn("join", "list, sep = \", \""), fn("replace", "s, find, with"), fn("startsWith", "s, p"),
        fn("endsWith", "s, p"), fn("slice", "s, start, end?"), fn("padStart", "s, n, ch = \" \""), fn("padEnd", "s, n, ch = \" \""),
        fn("repeat", "s, n"), fn("words", "s"), fn("isEmail", "s"), fn("isPhone", "s"), fn("isUrl", "s"), fn("plural", "n, one, many"),
        // dates
        fn("now", ""), fn("today", ""), fn("date", "s"), fn("time", "s"), fn("datetime", "d, t"), fn("addDays", "d, n"),
        fn("addMonths", "d, n"), fn("addYears", "d, n"), fn("addMinutes", "dt, n"), fn("addHours", "dt, n"), fn("diffDays", "a, b"),
        fn("diffMinutes", "a, b"), fn("weekday", "d"), fn("day", "d"), fn("month", "d"), fn("year", "d"), fn("hour", "dt"),
        fn("minute", "dt"), fn("startOfWeek", "d"), fn("startOfMonth", "d"), fn("endOfMonth", "d"), fn("daysIn", "d"),
        fn("dur", "ms"), fn("seconds", "n"), fn("minutes", "n"), fn("hours", "n"), fn("days", "n"), fn("ms", "d"),
        fn("secondsOf", "d"), fn("minutesOf", "d"),
        // finance
        fn("pmt", "rate, n, pv"), fn("fv", "rate, n, payment, pv = 0"), fn("npv", "rate, flows"), fn("irr", "flows"),
        fn("compound", "p, rate, years, perYear = 1"), fn("emi", "principal, annualRate, years"),
        fn("amortize", "principal, annualRate, years"), fn("percentOf", "part, whole"), fn("pctChange", "from, to"),
        // units
        fn("convert", "value, from, to"), fn("bmi", "weightKg, heightCm"), fn("bmr", "weightKg, heightCm, age, sex"),
        // node helpers
        fn("lazy", "prompt", tier = 3, phase = 2), fn("pending", "name", tier = 3, phase = 2),
        fn("loading", "q", tier = 3, phase = 2), fn("failed", "q", tier = 3, phase = 2),
        fn("query", "tool, args, every?: dur", tier = 3, phase = 2),
        fn("nodeText", "node"), fn("aspect", "r"), fn("weight", "n"),
        fn("col", "header, value, format?, align?, w?"),
        fn("axis", "title?, format?, min?, max?, ticks?, grid = true, log = false, hidden = false"),
        // motion helpers (04 §13.1)
        fn("spring", "v, kind = smooth", tier = 4, phase = 3), fn("tween", "v, motion = default", tier = 4, phase = 3),
        fn("loop", "from, to, period, ease = sine", tier = 4, phase = 3), fn("stagger", "i, base = 40ms", tier = 4, phase = 3),
    )

    /** Functions whose arguments are all positional and may repeat (`min(a, b, c)`). */
    val VARIADIC = setOf("coalesce", "min", "max", "merge")

    // ---- Effect triggers, 03 §8.2 ---------------------------------------------------------------

    override val triggers: List<TriggerSpec> = listOf(
        TriggerSpec("change", Sig.params("x: any, debounce?: dur"), 2, 1),
        TriggerSpec("appear", emptyList(), 2, 1),
        TriggerSpec("visible", emptyList(), 2, 1),
        TriggerSpec("hidden", emptyList(), 2, 1),
        TriggerSpec("every", Sig.params("interval: dur"), 2, 1),
        TriggerSpec("at", Sig.params("moment: date | datetime"), 2, 1),
        TriggerSpec("frame", emptyList(), 4, 3),
        TriggerSpec("tilt", emptyList(), 4, 3),
        TriggerSpec("shake", emptyList(), 4, 3),
    )

    private val byName = components.associateBy { it.name }
    private val actionsByName = actions.associateBy { it.name }
    private val fnByName = functions.associateBy { it.name }
    private val trigByName = triggers.associateBy { it.name }

    override fun component(name: String) = byName[name]
    override fun mark(container: String, name: String) = marks.firstOrNull { it.name == name && container in it.container }
    override fun action(name: String) = actionsByName[name]
    override fun function(name: String) = fnByName[name]
    override fun trigger(name: String) = trigByName[name]
    override fun restrictedTo(tier: Int, phase: Int): Catalog = FilteredCatalog(this, tier, phase, null)
    override fun registeredOnly(names: Set<String>): Catalog = FilteredCatalog(this, 4, 3, names)
    override val full: Catalog get() = this
    override val tier: Int get() = 4

    /** The catalog the phase 1a build uses for a tier: phase 1, renderer registry = the 1a slice. */
    fun forBuild(tier: Int): Catalog = restrictedTo(tier, 1).registeredOnly(PHASE_1A_COMPONENTS)
}
