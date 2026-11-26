# 06 — Prompts

**Spec version:** 1.1 · **Status:** normative

This document gives the **exact text** sent to models. Text in fenced `text` blocks is sent verbatim,
with `{{placeholders}}` substituted. Do not paraphrase, re-order or "improve" it in code; change it
here (with a spec version bump) and regenerate the prompt snapshot test (07 §9).

Prompt quality is measured by the eval harness (07 §10). Any wording change must be evaluated before
it ships.

---

## 1. Rules for all prompt text

- Plain ASCII except where the output must show a symbol (`₹` in examples is allowed).
- Second person, imperative, short sentences.
- No mention of internal names other than "Weave" and the fence info strings.
- Generated parts (§3) are produced from the catalog filtered by tier and phase
  (`Catalog.restrictedTo`), so the model is never told about something the app can't render.

---

## 2. The chat system-prompt section

### 2.1 Assembly

`EchoUiPrompt.section(tier, level, form, catalog)` returns, joined with one blank line:

1. **Intro** (§2.2) for the level.
2. **Syntax card** (§3.1) for the tier — or the JSON card (§7) when `form == json`.
3. **Tokens** (§3.2).
4. **Components** (§3.3) for the tier.
5. **Functions, actions, effects** (§3.4–3.6) for the tier.
6. **Rules** (§3.7) for the tier and level.
7. **Examples** (§3.8) for the tier.

If the model supports tool use and `tier ≥ 3`, parts 4–5 are **trimmed**, not replaced: the
**common set** keeps its full component lines (§3.3) and all functions, actions and effects stay
inline; only the long tail moves to the **index** (§8) behind the `weave_docs` tool. The common set
is `Column Row Card Title Text Metric Callout Button Slider Segmented TextField Select Chart Table
Tabs List Item`, plus every component in the phase 1 slice (01 §29). This way the first interactive
answer never waits on a tool round-trip; `weave_docs` is only needed for things like `Sort`,
`Canvas`, `Shader` or `Quiz`.

### 2.2 Intro text

**Balanced:**

```text
## Live UI (Weave)
You can add interactive native UI to a reply by writing a Weave block: a fenced code block with the info string `weave`. EchoFlow renders it as real phone UI that the user can touch.
Use a Weave block only when interaction or a visual layout clearly helps more than text: calculations with inputs, comparisons, data and charts, plans and schedules, step-by-step tools, quizzes, anything the user will want to explore or adjust. Never use one for a plain answer, a short explanation, or casual chat.
Write at most one block per reply. Put a short sentence of text before it. Keep any further explanation outside the block.
```

**Immersive:**

```text
## Live UI (Weave)
You can add interactive native UI to a reply by writing a Weave block: a fenced code block with the info string `weave`. EchoFlow renders it as real phone UI that the user can touch.
The user has asked for rich, interactive answers. Prefer a Weave block whenever an answer has structure: numbers, options, steps, comparisons, places, timelines, data. Give the user controls to explore (sliders, toggles, tabs) and use charts, motion and visuals freely. Do not use a block for casual chat or a one-line answer.
You may write up to three blocks in a reply. Put a short sentence of text before each one. For a large tool or game, use `size: full`.
```

### 2.3 "Make this interactive" instruction

Sent as the hidden user turn for 05 §11.6, followed by the original message text in a quote:

```text
Turn the answer below into one Weave block that lets me explore it interactively. Keep the facts the same. Reply with one short sentence and the block.

Answer:
"""
{{assistantMessageText}}
"""
```

### 2.4 State line in history

After each block fence body in history (05 §9.4), on its own line **inside** the fence, the app
appends:

```text
# state {{json}}
```

`{{json}}` is the §8.6-encoded (05) map of states whose current value differs from the initial
value, truncated per 05 §8.6. Because it is a comment, the model can read it but it never changes the
program. If no state differs, nothing is appended.

---

## 3. Generated parts

### 3.1 Syntax card

Tier 1:

```text
### Syntax
One statement per line. `name = value` defines a named part. The part named `root` is what is shown.
Components start with a capital letter: `Card(pad: l) [child1, child2]`. The first one or two arguments may be unnamed (see each signature); all others must be named. Children go in square brackets after the call.
Values: numbers `12`, `3.5`, `45%`; strings `"text"` with `{expr}` inside to insert a value and `{expr | format}` to format it; lists `[a, b]`; records `{name: "Tea", price: 40}`; `true`, `false`, `null`.
Operators: `+ - * / %`, comparisons `== != < <= > >=`, `and`, `or`, `not`, `c ? a : b`, `a ?? b`, `x in list`, `a |> f(b)` means `f(a, b)`. Lambdas: `x => x * 2`.
Never write pixels, dp or hex colours. Use the tokens below.
```

Tier 2 adds:

```text
State: `$name = initial` declares a value the user can change. Inputs bind to state by taking it as their first argument: `Slider($rate, min: 6, max: 14)`. Every named part that uses state updates automatically.
Functions: `fn name(a, b) = expression`.
Actions start with `!` and run when something happens: `onTap: !set($n, $n + 1)`. A list runs in order: `onTap: [!set($x, 0), !haptic(click)]`.
Effects: `on change($x) -> !haptic(tick)`, `on every(1s) when $running -> !dec($left)`.
```

Tier 3 adds:

```text
`!ask("prompt", update: [part1, part2], with: [$a, $b])` asks you, silently, to rewrite only those parts of this block; the rest stays interactive. `lazy("prompt", key: value)` is a part that you will generate the first time it is shown (for tabs, details, deeper levels).
`query("tool", {args}, every: 30m)` fetches live data and returns `{value, loading, error, at}`.
To change an existing block in a later reply, write a fence with the info string `weave edit <id>` containing only the statements to add or replace; `-name` deletes one.
`export name, $state` lets later blocks read `id::name`.
```

Tier 4 adds:

```text
`on frame -> …` runs every frame with `dt` (seconds) for simulations and games. `on tilt -> …` gives `x`, `y`. `Canvas(view: [0, 0, 100, 60]) [circle(50, 30, 5), …]` draws in its own coordinates; marks animate with `spring(value)`. `js fn name(a) = """…"""` defines a pure JavaScript function (JSON in, JSON out) for heavy logic.
```

### 3.2 Tokens

```text
### Tokens
space: none xxs xs s m l xl xxl
height h: xs s m l xl screen wrap aspect(16/9)
width w: fill wrap 1/2 1/3 2/3 1/4 3/4 weight(n)
tone: primary secondary tertiary error neutral success warning info muted; emphasis: low medium high
color (single colour slots): any Material 3 colour role name, e.g. onSurfaceVariant, primaryContainer, outline
text style: displaySmall headlineLarge headlineMedium headlineSmall titleLarge titleMedium titleSmall bodyLarge bodyMedium bodySmall labelLarge labelMedium labelSmall
shape: none xs s m l xl full; expressive: cookie clover sunny gem pill arch burst flower puffy heart
icons: Material icon names in snake_case, e.g. "savings", "flight_takeoff"
formats: int num(2) dec(1) compact percent pct currency(USD) inr money inrCompact signed date date(short) time datetime relative duration clock ordinal upper
```

(The tokens line is identical for all tiers; `screen` and expressive shapes are removed below the
tier that allows their components.)

The expressive shapes listed here are a deliberate **subset** of 04 §1.5: the ten that read well at
avatar and icon sizes. The prompt advertises only these; the checker accepts all 26 names in 04
§1.5, so a model that writes `cookie12` still gets it. 01 §11.5 is informative and points here.

### 3.3 Component lines

Grouped under these headings, in this order, only groups with at least one allowed component:
`Layout`, `Text`, `Numbers`, `Inputs`, `Data`, `Navigation`, `Media`, `Overlays`, `Charts`,
`Canvas`, `Visual`, `Play`.

One line per component:

```text
Name(lead1, lead2?, param=default, param?, …) [children] — doc
```

Generation rules (from `ComponentSpec`, 05 §3.3):

- Leads first, without names; a lead that binds state is written `$value`.
- Then named params in catalog order: `name=default` when there is a default; `name?` when
  optional without default; `name` when required. Enum-typed params show their values with `|`:
  `variant=filled|tonal|outlined|text|elevated`.
- Common props (04 §2) are **not** repeated per line; one line after the heading `### Components`
  says: `Every component also takes: show w h pad align tone emphasis bg shape border enter onTap onLongPress onSwipe label key badge disabled delay`.
- `[children]` is written only when the component takes children.
- `doc` = the first sentence of its description in 04, max 80 characters.
- Deprecated params are omitted.

Example lines:

```text
Slider($value, min=0, max=100, step?, label?, format?, prefix?, suffix?, ticks=false) — continuous value picker
Card(style=filled|outlined|elevated|tonal, pad=l, gap=s) [children] — grouped content surface
Chart(data, h=m, polar=false, x?, y?, legend=auto, tooltip=true, select?) [marks] — chart built from marks
```

Chart marks get their own lines under `Charts`, prefixed with two spaces:
`  line(x, y, tone?, curve=smooth|linear|step, …) — line through points`.

### 3.4 Functions

One line per group, names only, with signatures for the ones the model most often gets wrong:

```text
### Functions
math: abs round(x, digits) floor ceil min max clamp pow sqrt log remap lerp sin cos rand(seed, i)
lists: len range map each filter find count sum avg minOf maxOf sortBy(list, field, desc: true) groupBy take first last unique zip chunk cumsum
records: keys values get merge pick omit
text: str fmt upper lower split join replace contains slice isEmail
dates: now() today() date("2026-12-01") addDays addMonths diffDays weekday
finance (rates are fractions: 8.5% = 0.085): pmt(rate, n, pv) emi(principal, annualRate, years) amortize(principal, annualRate, years) -> [{year, principal, interest, balance}] compound irr npv
units: convert(value, "km", "mi") bmi
helpers: if(c, a, b) when(c, node) col(header, value) axis(title?, format?)
```

Tier 3 adds `lazy query pending loading`; tier 4 adds `spring tween loop stagger`.

### 3.5 Actions

```text
### Actions
local: !set($x, v) !inc($x, by: 1) !dec !toggle($b) !reset($x) !push($list, item) !remove($list, i) !patch($rec, {k: v}) !undo !if(c, a, b) !open(name) !close !go(name, i)
feedback: !haptic(tick|click|confirm|reject|heavy) !toast("text") !copy(value) !confetti
conversation: !say("text the user sends", attach: [$data])
device (the user confirms each): !url("https://…") !share(value) !export(value, as: csv) !calendar(title, start, end?) !remind(text, at) !dial(number) !map(place)
```

Tier 3 adds `model: !ask(prompt, update: [names], with: [values]) !refresh(lazyName) !regen` and
`data: !tool(name, args, into: $place)`. Tier 1 keeps only `!copy !share !url`.

### 3.6 Effects

Tier 2+:

```text
### Effects
on change($x) -> action        on change($x, debounce: 600ms) -> action
on appear -> action            on every(5s) when $running -> action
```

Tier 4 adds `on frame -> action (uses dt)`, `on tilt -> action (uses x, y)`, `on shake -> action`.

### 3.7 Rules

```text
### Rules
- Write statements in this order so the block appears quickly: the header, any `fn` that a state's starting value uses, state, `root`, then root's children from top to bottom, then other helpers and functions, then effects.
- Start with a header: `@weave 1 id: short_id title: "Title"`. The id is lowercase letters, digits and underscores.
- The screen is a phone in portrait, about 400dp wide. Prefer Column layouts; use Row only for 2-3 small items; never rely on horizontal scrolling except Carousel and Table.
- Use tokens for every size, space and colour. Never write numbers for spacing or colours.
- Name every argument except the leads shown in the signatures.
- Keep the block under 120 lines. Reuse parts with `fn` instead of repeating them.
- Use real, correct numbers and facts. Compute values with expressions instead of hard-coding results.
- Button text for !say must say exactly what will be sent.
- Never ask for passwords, card numbers, OTPs or other secrets.
- If something needs a real website or a JavaScript library, do not use Weave.
```

Tier 3 adds:

```text
- Use !ask only for changes that need you (new content, new search); compute everything else locally.
- Use lazy for content the user may never open, such as extra tabs or details.
```

Immersive adds:

```text
- Make it feel alive: use enter animations, animated metrics, and haptics on important actions.
```

### 3.8 Examples

Tier 1:

````text
### Example
```weave
@weave 1 id: phones title: "Battery life"
root = Card(gap: m) [
  Title("Battery life", subtitle: "Hours of mixed use"),
  BarChart(phones, x: "name", y: "hours", h: m),
  Table(phones, cols: [col("Phone", "name"), col("Hours", "hours", align: end), col("Price", "price", format: inr)])
]
phones = [
  {name: "Pixel 9", hours: 25, price: 79999},
  {name: "Galaxy S25", hours: 23, price: 80999},
  {name: "iPhone 16", hours: 22, price: 79900}
]
```
````

Tier 2:

````text
### Example
```weave
@weave 1 id: loan title: "Home loan"
$principal = 5000000
$rate = 8.5
$years = 20
root = Card(gap: m) [
  Title("Home loan", subtitle: "{$principal | inr} borrowed"),
  Slider($rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%"),
  Segmented($years, options: [10, 15, 20, 30], suffix: " yrs"),
  Stat [
    Metric("Monthly EMI", monthly, format: inr, tone: primary),
    Metric("Total interest", monthly * $years * 12 - $principal, format: inrCompact, tone: tertiary)
  ],
  Chart(amortize($principal, $rate / 100, $years), h: m) [
    area(x: "year", y: "principal", tone: primary, stack: true),
    area(x: "year", y: "interest", tone: tertiary, stack: true)
  ],
  Button("Compare banks", icon: "compare_arrows", onTap: !say("Compare banks for a {$principal | inr} loan at {$rate}% for {$years} years"))
]
monthly = emi($principal, $rate / 100, $years)
on change($years) -> !haptic(tick)
```
````

Tier 3 (in addition to the tier 2 example):

````text
### Example with live updates
```weave
@weave 1 id: trip title: "Jaipur weekend"
$budget = 30000
$tab = 0
root = Column(gap: m) [
  Slider($budget, min: 10000, max: 100000, step: 5000, format: inr, label: "Budget"),
  Tabs($tab, labels: ["Plan", "Food", "Stay"]) [plan, food, stay]
]
plan = Timeline([{t: "9:00", what: "Amber Fort"}, {t: "13:00", what: "Lunch at LMB"}, {t: "16:00", what: "Hawa Mahal"}], time: i => i.t, render: i => Text(i.what))
food = lazy("Food guide for this budget", budget: $budget)
stay = lazy("Three places to stay for this budget", budget: $budget)
on change($budget, debounce: 800ms) -> !ask("Rebuild the day plan for this budget", update: [plan], with: [$budget])
```
````

Tier 4 (in addition):

````text
### Example with simulation
```weave
@weave 1 id: ball title: "Bouncing ball"
$y = 10
$v = 0
root = Canvas(view: [0, 0, 100, 100], h: l, label: "Bouncing ball, tap to drop", onTap: p => [!set($y, 10), !set($v, 0)]) [
  rect(0, 95, 100, 5, fill: surfaceVariant),
  circle(50, $y, 5, fill: primary)
]
on frame -> [
  !set($v, $y >= 90 and $v > 0 ? -$v * 0.8 : $v + 160 * dt),
  !set($y, min(90, $y + $v * dt))
]
```
````

---

## 4. Hidden request templates

The system prompt for every hidden request is the §2 section for the block's tier with the
**Balanced** intro replaced by:

```text
## Weave block editor
You edit an existing Weave block in EchoFlow. Reply with exactly one fenced code block and nothing else.
```

### 4.1 `!ask`

User turn:

````text
Block "{{blockId}}" ({{blockTitle}}):
```weave
{{canonicalSource}}
```
Current state: {{stateJson}}
Values sent with this request: {{withJson}}
Recent updates: {{askLog}}

Task: {{prompt}}

Reply with one fence: ```weave edit {{blockId}}``` containing new definitions for only these parts: {{updateNames}}. You may add new helper parts with new names. Do not change anything else. Do not declare state.
````

`{{askLog}}` = `none` or a list of `"<prompt>" -> <names>` separated by `; `.

### 4.2 Remix

````text
Block "{{blockId}}" ({{blockTitle}}):
```weave
{{canonicalSource}}
```
Current state: {{stateJson}}

The user wants this change: {{userText}}

Reply with one fence: ```weave edit {{blockId}}``` containing only the statements to add or replace, and `-name` lines for anything to remove. Keep state names the user already set unless the change requires otherwise; add ` reset` after a state declaration only if its value must start over.
````

### 4.3 `lazy`

````text
Block "{{blockId}}" ({{blockTitle}}):
```weave
{{canonicalSource}}
```
Current state: {{stateJson}}

Generate the part "{{bindingName}}": {{prompt}}
Context: {{contextJson}}

Reply with one fence: ```weave``` containing a `root` (the content to show in that place) and any helper parts. Do not declare state and do not write a header. You may read the block's state and parts by name.
````

### 4.4 `!regen`

````text
Rewrite this Weave block from scratch with the same purpose, fixing anything that does not work well. Keep the same id and state names.
```weave
{{canonicalSource}}
```
Current state: {{stateJson}}
Reply with one fence: ```weave``` containing the complete new block.
````

---

## 5. Repair template

````text
This Weave block has problems:
{{diagnosticLines}}

The statements involved:
```weave
{{affectedStatements}}
```

Reply with one fence: ```weave edit {{blockId}}``` that fixes only these statements. Use only components, arguments and tokens from the reference.
````

`{{diagnosticLines}}` = each diagnostic's exact message (02 §9), one per line, prefixed with `- `.
`{{affectedStatements}}` = canonical print of each statement that produced an error, plus `root`.

---

## 6. Language of the prompt

Prompts are always in English. The model replies in the user's language as usual; strings inside
blocks follow the conversation language.

---

## 7. JSON card (form = json)

Replaces §3.1 when the model uses Weave-JSON:

````text
### Format
Write the block as JSON in a fenced code block with the info string `weave-json`:
```weave-json
{"weave": 1, "id": "loan", "title": "Home loan",
 "state": {"rate": 8.5},
 "let": {"monthly": "emi(5000000, $rate / 100, 20)"},
 "nodes": {
   "root": {"t": "Card", "c": ["rate", "pay"]},
   "rate": {"t": "Slider", "a": {"value": "$rate", "min": 6, "max": 14, "label": "Interest rate"}},
   "pay":  {"t": "Metric", "a": {"label": "Monthly EMI", "value": "=monthly", "format": "inr"}}
 },
 "on": [{"trigger": "change($rate)", "do": "!haptic(tick)"}]}
```
In "a": a string starting with `=` is an expression, a string starting with `!` is an action, `"$name"` is a state binding; anything else is a plain value. "c" lists the names of child nodes. Arguments use the parameter names shown in the signatures (the first argument of an input is "value").
````

The tokens, components, functions, actions, effects, rules and examples sections are the same,
except examples are converted to JSON by the canonical JSON printer (05 §3; the inverse of 02 §12).

---

## 8. Index and `weave_docs` tool (tier ≥ 3, tool-capable models)

### 8.1 Index (the long tail only; §2.1)

```text
### Reference index
The components above are all you need for most blocks. These others also exist; call the weave_docs tool with their names to get exact signatures before using any of them.
Layout: Box Grid Flow Carousel Spacer Divider Surface Section Split Sticky
Text: Rich Markdown Icon Avatar Badge Chip Link Quote Code Kbd Empty
…one line per group, names only, excluding the common set (§2.1)…
```

### 8.2 Tool definition

```json
{
  "name": "weave_docs",
  "description": "Get exact signatures and one example for Weave components, marks, or the groups \"functions\", \"actions\", \"effects\".",
  "parameters": {
    "type": "object",
    "properties": { "names": { "type": "array", "items": { "type": "string" }, "maxItems": 30 } },
    "required": ["names"]
  }
}
```

The tool result is the §3.3 lines for the requested names (and the §3.4–3.6 text for group names),
each followed by one short example line from the catalog. Unknown names are listed as
`unknown: <name>`. The tool call and result are part of that reply's tool exchange and are not stored
in chat history.
