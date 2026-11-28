package com.echoflow.weave.core.prompt

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.catalog.ChildrenSpec
import com.echoflow.weave.core.catalog.ComponentSpec
import com.echoflow.weave.core.catalog.MarkSpec
import com.echoflow.weave.core.catalog.ParamSpec
import com.echoflow.weave.core.check.TypeSpec

/** The Intelligent UI level, 05 §11.1. */
enum class IntelligentUiLevel(val storageKey: String) {
    OFF("off"), BALANCED("balanced"), IMMERSIVE("immersive");

    companion object {
        /** Absent or unknown → BALANCED (T-600, T-601). */
        fun fromStorage(v: String?) = entries.firstOrNull { it.storageKey == v } ?: BALANCED
    }
}

enum class PromptForm { SOURCE, JSON }

/**
 * `EchoUiPrompt.section` (06 §2): the system-prompt section for a tier and level, generated from
 * the catalog so the model is never told about something the build can't render (06 §1).
 * All fixed text below is copied verbatim from 06; change it there first.
 */
object EchoUiPrompt {

    fun section(tier: Int, level: IntelligentUiLevel, form: PromptForm = PromptForm.SOURCE, catalog: Catalog = BuiltinCatalog.forBuild(tier)): String {
        if (tier <= 0 || level == IntelligentUiLevel.OFF) return ""
        val parts = listOf(
            intro(level),
            syntax(tier, catalog),
            TOKENS,
            components(catalog),
            functions(tier, catalog),
            actions(tier, catalog),
            effects(tier, catalog),
            rules(tier, level, catalog),
            examples(tier, catalog),
        )
        return parts.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    /** The hidden-request system prompt (06 §4): the section with the editor intro. */
    fun editorSection(tier: Int, catalog: Catalog = BuiltinCatalog.forBuild(tier)): String =
        section(tier, IntelligentUiLevel.BALANCED, PromptForm.SOURCE, catalog).replaceFirst(BALANCED_INTRO, EDITOR_INTRO)

    // ---- 06 §2.2 --------------------------------------------------------------------------------

    private const val BALANCED_INTRO = """## Live UI (Weave)
You can add interactive native UI to a reply by writing a Weave block: a fenced code block with the info string `weave`. EchoFlow renders it as real phone UI that the user can touch.
Use a Weave block only when interaction or a visual layout clearly helps more than text: calculations with inputs, comparisons, data and charts, plans and schedules, step-by-step tools, quizzes, anything the user will want to explore or adjust. Never use one for a plain answer, a short explanation, or casual chat.
Write at most one block per reply. Put a short sentence of text before it. Keep any further explanation outside the block."""

    private const val IMMERSIVE_INTRO = """## Live UI (Weave)
You can add interactive native UI to a reply by writing a Weave block: a fenced code block with the info string `weave`. EchoFlow renders it as real phone UI that the user can touch.
The user has asked for rich, interactive answers. Prefer a Weave block whenever an answer has structure: numbers, options, steps, comparisons, places, timelines, data. Give the user controls to explore (sliders, toggles, tabs) and use charts, motion and visuals freely. Do not use a block for casual chat or a one-line answer.
You may write up to three blocks in a reply. Put a short sentence of text before each one. For a large tool or game, use `size: full`."""

    const val EDITOR_INTRO = """## Weave block editor
You edit an existing Weave block in EchoFlow. Reply with exactly one fenced code block and nothing else."""

    private fun intro(level: IntelligentUiLevel) = if (level == IntelligentUiLevel.IMMERSIVE) IMMERSIVE_INTRO else BALANCED_INTRO

    // ---- 06 §3.1 --------------------------------------------------------------------------------

    private const val SYNTAX_1 = """### Syntax
One statement per line. `name = value` defines a named part. The part named `root` is what is shown.
Components start with a capital letter: `Card(pad: l) [child1, child2]`. The first one or two arguments may be unnamed (see each signature); all others must be named. Children go in square brackets after the call.
Values: numbers `12`, `3.5`, `45%`; strings `"text"` with `{expr}` inside to insert a value and `{expr | format}` to format it; lists `[a, b]`; records `{name: "Tea", price: 40}`; `true`, `false`, `null`.
Operators: `+ - * / %`, comparisons `== != < <= > >=`, `and`, `or`, `not`, `c ? a : b`, `a ?? b`, `x in list`, `a |> f(b)` means `f(a, b)`. Lambdas: `x => x * 2`.
Never write pixels, dp or hex colours. Use the tokens below."""

    private const val SYNTAX_2 = """State: `${'$'}name = initial` declares a value the user can change. Inputs bind to state by taking it as their first argument: `Slider(${'$'}rate, min: 6, max: 14)`. Every named part that uses state updates automatically.
Functions: `fn name(a, b) = expression`.
Actions start with `!` and run when something happens: `onTap: !set(${'$'}n, ${'$'}n + 1)`. A list runs in order: `onTap: [!set(${'$'}x, 0), !haptic(click)]`.
Effects: `on change(${'$'}x) -> !haptic(tick)`, `on every(1s) when ${'$'}running -> !dec(${'$'}left)`."""

    private const val SYNTAX_3 = """`!ask("prompt", update: [part1, part2], with: [${'$'}a, ${'$'}b])` asks you, silently, to rewrite only those parts of this block; the rest stays interactive. `lazy("prompt", key: value)` is a part that you will generate the first time it is shown (for tabs, details, deeper levels).
`query("tool", {args}, every: 30m)` fetches live data and returns `{value, loading, error, at}`.
To change an existing block in a later reply, write a fence with the info string `weave edit <id>` containing only the statements to add or replace; `-name` deletes one. An `on` effect in an edit replaces every effect the block has on that same trigger, so to change one guarded effect, restate all the others on that trigger too.
`export name, ${'$'}state` lets later blocks read `id::name`."""

    private const val SYNTAX_4 = """`on frame -> …` runs every frame with `dt` (seconds) for simulations and games. `on tilt -> …` gives `x`, `y`. `Canvas(view: [0, 0, 100, 60]) [circle(50, 30, 5), …]` draws in its own coordinates; marks animate with `spring(value)`. `js fn name(a) = ""${'"'}…""${'"'}` defines a pure JavaScript function (JSON in, JSON out) for heavy logic."""

    private fun syntax(tier: Int, c: Catalog): String {
        val out = StringBuilder(SYNTAX_1)
        if (tier >= 2) out.append('\n').append(SYNTAX_2)
        if (tier >= 3 && c.action("ask") != null && c.function("lazy") != null) out.append('\n').append(SYNTAX_3)
        if (tier >= 4 && c.component("Canvas") != null && c.trigger("frame") != null) out.append('\n').append(SYNTAX_4)
        return out.toString()
    }

    // ---- 06 §3.2 --------------------------------------------------------------------------------

    private const val TOKENS = """### Tokens
space: none xxs xs s m l xl xxl
height h: xs s m l xl screen wrap aspect(16/9)
width w: fill wrap 1/2 1/3 2/3 1/4 3/4 weight(n)
tone: primary secondary tertiary error neutral success warning info muted; emphasis: low medium high
color (single colour slots): any Material 3 colour role name, e.g. onSurfaceVariant, primaryContainer, outline
text style: displaySmall headlineLarge headlineMedium headlineSmall titleLarge titleMedium titleSmall bodyLarge bodyMedium bodySmall labelLarge labelMedium labelSmall
shape: none xs s m l xl full; expressive: cookie clover sunny gem pill arch burst flower puffy heart
icons: Material icon names in snake_case, e.g. "savings", "flight_takeoff"
formats: int num(2) dec(1) compact percent pct currency(USD) inr money inrCompact signed date date(short) time datetime relative duration clock ordinal upper"""

    // ---- 06 §3.3 --------------------------------------------------------------------------------

    private val GROUPS = listOf("Layout", "Text", "Numbers", "Inputs", "Data", "Navigation", "Media", "Overlays", "Charts", "Canvas", "Visual", "Play")

    private fun components(c: Catalog): String {
        if (c.components.isEmpty()) return ""
        val sb = StringBuilder("### Components\n").append(BuiltinCatalog.COMMON_LINE)
        for (g in GROUPS) {
            val comps = c.components.filter { it.group == g }
            if (comps.isEmpty()) continue
            sb.append("\n#### ").append(g)
            for (comp in comps) sb.append('\n').append(componentLine(comp))
            if (g == "Charts" && comps.any { it.children is ChildrenSpec.Marks }) {
                for (m in c.marks.filter { "Chart" in it.container }) sb.append('\n').append("  ").append(markLine(m))
            }
        }
        return sb.toString()
    }

    fun componentLine(s: ComponentSpec): String {
        val args = ArrayList<String>()
        for (p in s.leads) args += if (p.type == TypeSpec.STATE) "\$value" else p.name + if (p.required) "" else "?"
        for (p in s.params) if (p.deprecated == null && !p.promptHidden) args += param(p)
        val children = when (s.children) {
            ChildrenSpec.None -> ""
            is ChildrenSpec.Marks -> " [marks]"
            else -> " [children]"
        }
        return s.name + (if (args.isEmpty()) "" else "(" + args.joinToString(", ") + ")") + children + " — " + doc(s.doc)
    }

    fun markLine(m: MarkSpec): String {
        val args = m.leads.map { it.name } + m.params.map { param(it) }
        return m.name + "(" + args.joinToString(", ") + ") — " + doc(m.doc)
    }

    private fun param(p: ParamSpec): String {
        val enumValues = when (val t = p.type) {
            is TypeSpec.Enum -> t.values
            is TypeSpec.Union -> t.options.filterIsInstance<TypeSpec.Enum>().flatMap { it.values }.takeIf { t.options.all { o -> o is TypeSpec.Enum } }
            else -> null
        }
        return when {
            p.default != null && enumValues != null -> {
                val d = p.defaultText ?: ""
                p.name + "=" + (listOf(d) + enumValues.filter { it != d }).joinToString("|")
            }
            p.default != null -> p.name + "=" + (p.defaultText ?: "")
            p.required -> p.name
            else -> p.name + "?"
        }
    }

    private fun doc(d: String): String = if (d.length <= 80) d else d.take(79) + "…"

    // ---- 06 §3.4–3.6 ----------------------------------------------------------------------------

    private const val FUNCTIONS = """### Functions
math: abs round(x, digits) floor ceil min max clamp pow sqrt log remap lerp sin cos rand(seed, i)
lists: len range map each filter find count sum avg minOf maxOf sortBy(list, field, desc: true) groupBy take first last unique zip chunk cumsum
records: keys values get merge pick omit
text: str fmt upper lower split join replace contains slice isEmail
dates: now() today() date("2026-12-01") addDays addMonths diffDays weekday
finance (rates are fractions: 8.5% = 0.085): pmt(rate, n, pv) emi(principal, annualRate, years) amortize(principal, annualRate, years) -> [{year, principal, interest, balance}] compound irr npv
units: convert(value, "km", "mi") bmi
helpers: if(c, a, b) when(c, node) col(header, value) axis(title?, format?)"""

    private fun functions(tier: Int, c: Catalog): String {
        val sb = StringBuilder(FUNCTIONS)
        if (tier >= 3) {
            val t3 = listOf("lazy", "query", "pending", "loading").filter { c.function(it) != null }
            if (t3.size == 4) sb.append(' ').append(t3.joinToString(" "))
        }
        if (tier >= 4) {
            val t4 = listOf("spring", "tween", "loop", "stagger").filter { c.function(it) != null }
            if (t4.size == 4) sb.append(' ').append(t4.joinToString(" "))
        }
        return sb.toString()
    }

    private const val ACTIONS = """### Actions
local: !set(${'$'}x, v) !inc(${'$'}x, by: 1) !dec !toggle(${'$'}b) !reset(${'$'}x) !push(${'$'}list, item) !remove(${'$'}list, i) !patch(${'$'}rec, {k: v}) !undo !if(c, a, b) !open(name) !close !go(name, i)
feedback: !haptic(tick|click|confirm|reject|heavy) !toast("text") !copy(value) !confetti
conversation: !say("text the user sends", attach: [${'$'}data])
device (the user confirms each): !url("https://…") !share(value) !export(value, as: csv) !calendar(title, start, end?) !remind(text, at) !dial(number) !map(place)"""

    private fun actions(tier: Int, c: Catalog): String {
        if (tier == 1) return "### Actions\n!copy(value) !share(value) !url(\"https://…\")"
        val sb = StringBuilder(ACTIONS)
        if (tier >= 3 && c.action("ask") != null) sb.append("\nmodel: !ask(prompt, update: [names], with: [values]) !refresh(lazyName) !regen")
        if (tier >= 3 && c.action("tool") != null) sb.append("\ndata: !tool(name, args, into: \$place)")
        return sb.toString()
    }

    private const val EFFECTS = """### Effects
on change(${'$'}x) -> action        on change(${'$'}x, debounce: 600ms) -> action
on appear -> action            on every(5s) when ${'$'}running -> action"""

    private fun effects(tier: Int, c: Catalog): String {
        if (tier < 2) return ""
        val sb = StringBuilder(EFFECTS)
        if (tier >= 4 && c.trigger("frame") != null) sb.append("\non frame -> action (uses dt)        on tilt -> action (uses x, y)        on shake -> action")
        return sb.toString()
    }

    // ---- 06 §3.7 --------------------------------------------------------------------------------

    private const val RULES = """### Rules
- Write statements in this order so the block appears quickly: the header, any `fn` that a state's starting value uses, state, `root`, then root's children from top to bottom, then other helpers and functions, then effects.
- Start with a header: `@weave 1 id: short_id title: "Title"`. The id is lowercase letters, digits and underscores.
- The screen is a phone in portrait, about 400dp wide. Prefer Column layouts; use Row only for 2-3 small items; never rely on horizontal scrolling except Carousel and Table.
- Use tokens for every size, space and colour. Never write numbers for spacing or colours.
- Name every argument except the leads shown in the signatures.
- Keep the block under 120 lines. Reuse parts with `fn` instead of repeating them.
- Use real, correct numbers and facts. Compute values with expressions instead of hard-coding results.
- Button text for !say must say exactly what will be sent.
- Never ask for passwords, card numbers, OTPs or other secrets.
- If something needs a real website or a JavaScript library, do not use Weave."""

    private fun rules(tier: Int, level: IntelligentUiLevel, c: Catalog): String {
        val sb = StringBuilder(RULES)
        if (tier >= 3 && c.action("ask") != null && c.function("lazy") != null) {
            sb.append("\n- Use !ask only for changes that need you (new content, new search); compute everything else locally.")
            sb.append("\n- Use lazy for content the user may never open, such as extra tabs or details.")
        }
        if (level == IntelligentUiLevel.IMMERSIVE) sb.append("\n- Make it feel alive: use enter animations, animated metrics, and haptics on important actions.")
        return sb.toString()
    }

    // ---- 06 §3.8 --------------------------------------------------------------------------------

    const val EXAMPLE_1 = """@weave 1 id: phones title: "Battery life"
root = Card(gap: m) [
  Title("Battery life", subtitle: "Hours of mixed use"),
  BarChart(phones, x: "name", y: "hours", h: m),
  Table(phones, cols: [col("Phone", "name"), col("Hours", "hours", align: end), col("Price", "price", format: inr)])
]
phones = [
  {name: "Pixel 9", hours: 25, price: 79999},
  {name: "Galaxy S25", hours: 23, price: 80999},
  {name: "iPhone 16", hours: 22, price: 79900}
]"""

    const val EXAMPLE_2 = """@weave 1 id: loan title: "Home loan"
${'$'}principal = 5000000
${'$'}rate = 8.5
${'$'}years = 20
root = Card(gap: m) [
  Title("Home loan", subtitle: "{${'$'}principal | inr} borrowed"),
  Slider(${'$'}rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%"),
  Segmented(${'$'}years, options: [10, 15, 20, 30], suffix: " yrs"),
  Stat [
    Metric("Monthly EMI", monthly, format: inr, tone: primary),
    Metric("Total interest", monthly * ${'$'}years * 12 - ${'$'}principal, format: inrCompact, tone: tertiary)
  ],
  Chart(amortize(${'$'}principal, ${'$'}rate / 100, ${'$'}years), h: m) [
    area(x: "year", y: "principal", tone: primary, stack: true),
    area(x: "year", y: "interest", tone: tertiary, stack: true)
  ],
  Button("Compare banks", icon: "compare_arrows", onTap: !say("Compare banks for a {${'$'}principal | inr} loan at {${'$'}rate}% for {${'$'}years} years"))
]
monthly = emi(${'$'}principal, ${'$'}rate / 100, ${'$'}years)
on change(${'$'}years) -> !haptic(tick)"""

    const val EXAMPLE_3 = """@weave 1 id: trip title: "Jaipur weekend"
${'$'}budget = 30000
${'$'}tab = 0
root = Column(gap: m) [
  Slider(${'$'}budget, min: 10000, max: 100000, step: 5000, format: inr, label: "Budget"),
  Tabs(${'$'}tab, labels: ["Plan", "Food", "Stay"]) [plan, food, stay]
]
plan = Timeline([{t: "9:00", what: "Amber Fort"}, {t: "13:00", what: "Lunch at LMB"}, {t: "16:00", what: "Hawa Mahal"}], time: i => i.t, render: i => Text(i.what))
food = lazy("Food guide for this budget", budget: ${'$'}budget)
stay = lazy("Three places to stay for this budget", budget: ${'$'}budget)
on change(${'$'}budget, debounce: 800ms) -> !ask("Rebuild the day plan for this budget", update: [plan], with: [${'$'}budget])"""

    const val EXAMPLE_4 = """@weave 1 id: ball title: "Bouncing ball"
${'$'}y = 10
${'$'}v = 0
root = Canvas(view: [0, 0, 100, 100], h: l, label: "Bouncing ball, tap to drop", onTap: p => [!set(${'$'}y, 10), !set(${'$'}v, 0)]) [
  rect(0, 95, 100, 5, fill: surfaceVariant),
  circle(50, ${'$'}y, 5, fill: primary)
]
on frame -> [
  !set(${'$'}v, ${'$'}y >= 90 and ${'$'}v > 0 ? -${'$'}v * 0.8 : ${'$'}v + 160 * dt),
  !set(${'$'}y, min(90, ${'$'}y + ${'$'}v * dt))
]"""

    private fun fence(title: String, body: String) = "### $title\n```weave\n$body\n```"

    private fun examples(tier: Int, c: Catalog): String {
        val out = ArrayList<String>()
        out += if (tier == 1) fence("Example", EXAMPLE_1) else fence("Example", EXAMPLE_2)
        if (tier >= 3 && c.component("Timeline") != null && c.function("lazy") != null) out += fence("Example with live updates", EXAMPLE_3)
        if (tier >= 4 && c.component("Canvas") != null && c.trigger("frame") != null) out += fence("Example with simulation", EXAMPLE_4)
        return out.joinToString("\n\n")
    }

    // ---- 06 §2.3, §5 ----------------------------------------------------------------------------

    fun makeInteractive(assistantMessageText: String) = """Turn the answer below into one Weave block that lets me explore it interactively. Keep the facts the same. Reply with one short sentence and the block.

Answer:
""${'"'}
$assistantMessageText
""${'"'}"""

    fun repair(blockId: String, diagnosticMessages: List<String>, affectedStatements: String) = """This Weave block has problems:
${diagnosticMessages.joinToString("\n") { "- $it" }}

The statements involved:
```weave
$affectedStatements
```

Reply with one fence: ```weave edit $blockId``` that fixes only these statements. Use only components, arguments and tokens from the reference."""
}
