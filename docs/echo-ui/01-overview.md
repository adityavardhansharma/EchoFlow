# Weave — Generative, Interactive UI in Chat

**Name:** **Echo UI** (the engine) · language codename **Weave** (the fenced `weave` syntax)
**User-facing setting:** **Intelligent UI**: Off · Balanced (default) · Immersive (§20.5)
**Version:** 1.0 draft spec · November 2026
**Status:** design — nothing here is built yet. This is the **overview**; the exact rules are in
[02–07](README.md), which win wherever they differ from this document.

---

## 0. Contents

1. What this is, and what it is not
2. Design principles
3. How a Weave block reaches the screen (architecture at a glance)
4. The Weave language
5. Types, values and the expression language
6. State
7. Actions
8. Effects and time
9. The model loop: `!ask`, `lazy`, and edits
10. Data: `query` and tools
11. Design tokens — the mobile contract
12. Common node properties
13. Component catalog
14. The Chart grammar
15. Canvas, motion and simulation
16. Block sizes, expand and full-screen mode
17. Streaming
18. Validation, repair and fallback
19. Weave-JSON (the constrained-decoding form)
20. Capability tiers, model fit and the Intelligent UI setting
21. Prompt generation
22. What the model sees back (state context)
23. Persistence, versions, remix, library and widgets
24. Safety, consent and limits
25. Accessibility
26. Kotlin architecture
27. Performance budgets
28. Testing and evaluation
29. Phasing
30. Worked examples
31. Open questions

---

## 1. What this is, and what it is not

A model writes a **Weave block** inside its reply: a fenced section of a small reactive language.
EchoFlow renders it as **native Jetpack Compose UI** inside the chat — cards, sliders, charts,
tables, wizards, canvases, games — that the user can touch, and that can **talk back to the model**
without leaving the conversation.

Each block is a **tiny app**: a tree of components, its own state, its own logic, and a set of
actions. A mortgage calculator recomputes as the slider moves with no model call. A trip planner
asks the model to rebuild just its itinerary when the budget changes. A tab can generate its own
content the first time it is opened. A physics toy runs at 60 fps inside a bubble.

**What Weave is NOT:**

- **Not HTML in a WebView.** Everything is native Compose, themed by EchoFlow's palettes. (Artifacts
  remain the escape hatch for web content; see §24.6.)
- **Not code execution with device access.** Block logic is a sandboxed, budgeted expression
  language. It cannot touch the network, files, or Android APIs. Everything outward goes through a
  fixed set of actions, many behind consent.
- **Not a fixed template set.** The vocabulary is general-purpose building blocks plus a grammar of
  graphics, so a capable model can produce layouts nobody designed in advance.
- **Not a copy of an existing format.** Weave is its own language, designed for mobile, for
  reactivity, and for a model in the loop.

**Fence:** a Weave block is a top-level fenced block with the info string `weave`:

````
```weave
root = Card [Title("Hello"), Text("I'm a live block.")]
```
````

It plugs into the existing seam: `MarkdownBlockExtension` keyed by `"weave"` in
`LocalMarkdownBlockExtensions` (`ui/components/MarkdownStream.kt`). Text and blocks interleave
freely in one reply.

---

## 2. Design principles

1. **Constrain values, free the structure.** The model chooses any nesting, combination, content
   and behaviour. It may not choose raw pixels, raw colours, raw fonts or raw durations beyond the
   token sets. Structure is creativity; values are the design system.
2. **Mobile is the only target.** Every token, cap and default assumes a 360–412 dp wide phone in
   portrait, with landscape and foldables as graceful extensions.
3. **Every theme, always.** Colours are roles from `MaterialTheme.colorScheme`. A block written
   once looks native in Monochrome, Ocean, Forest, Sunset, Lavender, Rose, dynamic colour, light
   and dark.
4. **Stream first.** Every statement renders the moment it is complete. Nothing waits for the end of
   the reply. The finished block is identical to what streamed.
5. **Never crash, never blank.** Any error degrades the smallest possible part of the block. The
   worst case is the source shown as code.
6. **Local first, model second.** Interactions that can be computed locally are; the model is called
   only when a block explicitly asks.
7. **Tokens are money.** The syntax is terse, line-oriented and reference-based, so a rich block
   costs hundreds of tokens, not thousands.
8. **Hard for the model to get wrong.** Named arguments, one obvious way to do each thing, and
   errors that are local and repairable.
9. **The user is always in control.** Anything leaving the app is confirmed. The user sees exactly
   what a button will send.
10. **Native feel.** Material 3 Expressive motion, haptics, shapes and type. A Weave block should be
    indistinguishable from hand-built EchoFlow UI.

---

## 3. Architecture at a glance

```
 model stream ──▶ MarkdownStreamPlanner ──▶ MarkdownExtensionBlock(kind="weave", body grows)
                                                     │
                                                     ▼
                                     WeaveIncrementalParser (per statement)
                                                     │  AST
                                                     ▼
                                      Checker (catalog types, tokens, limits)
                                                     │  IR (WeaveProgram)
                     Weave-JSON ──▶ JsonLowering ───▶│
                                                     ▼
                         Runtime: StateStore + reactive graph + Evaluator + ActionBus
                                                     │
                                                     ▼
                          WeaveRenderer (Compose) ◀── Catalog (Kotlin) ──▶ PromptGenerator
                                                     │
                       ┌───────────────┬─────────────┴─────────┬─────────────────┐
                    Room (state,     ActionBus ──▶ Chat      ModelLoop (!ask,   Consent
                    versions)        (!say, confirm sheet)   lazy, edits)       (tools, device)
```

- **One source of truth:** the Kotlin **Catalog** defines every component, its parameters, types
  and docs. It drives the checker, the renderer mapping, the prompt and the JSON Schema.
- **Two input forms, one IR:** Weave source (default) and Weave-JSON (for schema-constrained
  decoding) both lower to the same `WeaveProgram`.

---

## 4. The Weave language

### 4.1 Shape of a program

A program is a list of **statements**, one per line. Order does not matter for meaning; it only
matters for streaming (what can appear first). Forward references are allowed.

```weave
@weave 1 id: loan title: "Home loan" size: card

$rate = 8.5
$years = 20
$principal = 5000000

emi = monthly($principal, $rate, $years)
fn monthly(p, r, y) = p * (r/1200) * pow(1 + r/1200, y*12) / (pow(1 + r/1200, y*12) - 1)

root = Card(pad: l, gap: m) [head, rate, years, kpis, chart, cta]
head = Title("Home loan", style: titleLarge)
rate = Slider($rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%")
years = Segmented($years, options: [10, 15, 20, 30], suffix: " yrs")
kpis = Row(gap: s) [
  Metric("Monthly EMI", emi, format: inr, tone: primary),
  Metric("Total interest", emi * $years * 12 - $principal, format: inrCompact, tone: tertiary)
]
chart = Chart(amortize($principal, $rate / 100, $years), h: m) [
  area(x: "year", y: "principal", tone: primary),
  area(x: "year", y: "interest", tone: tertiary, stack: true)
]
cta = Row(gap: s) [
  Button("Compare 3 banks", onTap: !say("Compare 3 banks for a {$principal | inr} loan at {$rate}%")),
  Button("Share", variant: tonal, icon: "share", onTap: !share(root))
]
```

### 4.2 Statement kinds

| Kind | Form | Meaning |
|---|---|---|
| Header | `@weave 1 key: value …` | Optional first line. Version and block metadata (§4.7). |
| State | `$name = expr` / `$name: type = expr` | A mutable variable owned by the block (§6). |
| Binding | `name = expr` | A named, reactive, read-only value: a node, a number, a list… |
| Function | `fn name(a, b) = expr` | A pure helper (§5.8). |
| Effect | `on trigger [when cond] -> action` | Runs an action when something happens (§8). |
| Delete | `-name` | Removes a statement. Only meaningful in edits (§9.4). |
| Comment | `# text` | Ignored. Models may use it for planning; it costs tokens, so prompts discourage it. |

The entry point is the binding named `root`. If there is no `root`, the first binding whose value
is a node is used, with a `no-root` warning.

### 4.3 Lexical rules

- **Identifiers:** `[a-zA-Z_][a-zA-Z0-9_]*`. Case carries meaning:
  - `Capitalized(...)` — a **component** (from the catalog).
  - `lowercase(...)` — a **function** (built-in or `fn`), or a mark inside `Chart`/`Canvas`.
  - `$name` — **state**.
  - `!name(...)` — an **action** value.
  - `blockId::name` — a value exported by another block in the same thread (§6.5).
- **Numbers:** `12`, `-3.5`, `1_000_000`, `1e6`, `45%` (= `0.45`).
- **Durations:** `300ms`, `2s`, `5m`, `1h`, `1d`.
- **Strings:** `"double quoted"`, with escapes `\n \" \\`. Interpolation: `"EMI {emi | inr}"`.
  A literal brace is `{{` or `}}`. Triple-quoted `"""…"""` strings may span lines.
- **Booleans / null:** `true`, `false`, `null`.
- **Lists:** `[a, b, c]`. **Records:** `{name: "Tea", price: 40}`. Trailing commas are allowed.
- **Comments:** `#` to end of line, outside strings.
- **Line continuation:** a statement continues onto the next line while any `(`, `[`, `{` is open,
  or the line ends with an operator, `,`, `|>`, `=>` or `->`.

### 4.4 Calls: lead arguments, named arguments, children

```
Component(lead1, lead2, name: value, name: value) [child, child, …]
```

- Each catalog entry declares **0–2 lead parameters** (e.g. `Text(content)`, `Slider($bind)`,
  `Button(label)`). Leads may be passed positionally. **Every other argument must be named.** This
  keeps the common case short and makes the rest order-proof and future-proof.
- Leads may also be named (`Text(content: "x")`).
- **Children** follow the call in brackets. Parentheses may be dropped when there are no arguments:
  `Row [a, b]`. Children are any expression evaluating to a node or list of nodes (lists are
  flattened; `null` is skipped), so `Column [head, each(items, i => Item(i.name)), footer]` works.
- Unknown named arguments are **dropped with a warning**, not an error (§18).

### 4.5 Values inside token-typed slots

Where a parameter is typed as a token set (spacing, tone, text style, shape…), a bare identifier is
read as a token: `gap: m`, `tone: primary`, `style: titleLarge`. A token may also be computed:
`tone: $over ? error : primary`. A bare identifier that is neither a token of that slot nor a
binding is an error for that argument only.

### 4.6 The pipe and lambdas

```weave
cheap = $items |> filter(i => i.price <= $max) |> sortBy(i => i.rating, desc: true) |> take(5)
```

`a |> f(b)` means `f(a, b)`. Lambdas are `x => expr` or `(a, b) => expr`. Lambdas are values; they
can be stored, passed to components (`render:`), and returned from functions.

### 4.7 Header

`@weave 1` (language version) followed by optional `key: value` pairs:

| Key | Values | Meaning |
|---|---|---|
| `id` | identifier | Stable block id within the thread. Required for edits and cross-block reads. Default: `b<n>`. |
| `title` | string | Shown in the expand sheet, library and share sheet. |
| `size` | `inline` (default), `card`, `wide`, `full` | §16. |
| `icon` | Material Symbols name | Library and widget icon. |
| `tier` | 1–4 | The tier the model wrote for (diagnostics only). |
| `keep` | `true` | Suggests offering "Keep in Library" (§23). |

---

## 5. Types, values and the expression language

### 5.1 Types

`num`, `str`, `bool`, `null`, `list<T>`, `rec` (record), `dur`, `date`, `color` (a colour role),
`token<Set>`, `node`, `action`, `fn`. Types are checked against catalog parameters; values are
**coerced** where safe (`"12"` → `12` in a `num` slot, `12` → `"12"` in a `str` slot, a single node
→ a one-element list for children). Unsafe coercions are an error for that argument.

### 5.2 Operators (lowest to highest precedence)

| Operator | Meaning |
|---|---|
| `c ? a : b` | Conditional |
| `a ?? b` | `a` unless `null`, else `b` |
| `or` | Logical or |
| `and` | Logical and |
| `not` | Logical not (prefix) |
| `== != < <= > >=` | Comparison (deep equality for lists/records) |
| `in` | Membership: `x in list`, `"k" in record`, `x in 1..10` |
| `..` | Inclusive range of integers: `1..12` |
| `+ -` | Add / subtract; `+` concatenates strings and lists, merges records |
| `* / %` | Multiply, divide, remainder |
| `-x` | Negation |
| `a.b`, `a[i]`, `f(x)` | Member, index (negative from end), call |
| `|>` | Pipe (§4.6) |

Words (`and`, `or`, `not`) are used instead of `&&`/`||`/`!` because `!` marks actions.
Division by zero yields `null` (displayed as "—"), never an exception.

### 5.3 String interpolation and formats

`"{expr}"` interpolates; `"{expr | format}"` formats. Formats (also usable as `format:` arguments):

| Format | Example output |
|---|---|
| `int` | `1,234` (locale grouping) |
| `num(d)` | `1,234.57` with `d` decimals |
| `compact` | `1.2K`, `3.4M`, `5.6 Cr` (Indian locales use lakh/crore) |
| `percent`, `percent(d)` | value is a fraction: 0.085 → `8.5%` (`pct` takes 8.5 → `8.5%`) |
| `currency(CODE)` | `₹43,391`, `$1,200.00` |
| `inr`, `usd`, `eur`, `gbp`, `inrCompact` | Shortcuts |
| `date`, `date(short\|medium\|long)`, `time`, `datetime`, `relative` | Locale aware; `relative` = "in 3 days" |
| `duration` | `2h 15m` |
| `ordinal` | `3rd` |
| `upper`, `lower`, `title` | Case |
| `plural(one, many)` | `"{n} {n \| plural(item, items)}"` |

All formatting uses the device locale.

### 5.4 Built-in functions

Math: `abs ceil floor round(x, d) min max clamp(x, lo, hi) pow sqrt log exp sin cos tan atan2 pi
lerp(a, b, t) remap(x, a1, a2, b1, b2) rand(seed, i)` — `rand` is **seeded and deterministic**, so re-renders never flicker.

Lists: `len sum avg median minOf maxOf count(list, pred) map filter reduce(list, init, fn) sortBy
groupBy(list, keyFn) → rec of lists, take drop first last reverse unique flatten zip range(n)
indexOf find any all chunk(list, n) window(list, n) cumsum`.

Records: `keys values entries get(rec, key, default) merge pick omit`.

Strings: `len upper lower trim split join replace contains startsWith slice pad(s, n) fmt(value,
format)`.

Dates: `now()` (reactive, see §8.3), `today()`, `date("2026-12-01")`, `addDays addMonths diffDays
weekday month year startOfWeek`.

Finance and common domains (prompt-cheap, frequently needed): `pmt(rate, n, pv)`, `fv`, `npv`,
`irr`, `amortize(principal, annualRate, years) → list<{year, principal, interest, balance}>`,
`compound(p, rate, years, perYear)`, `bmi`, `convert(value, from, to)` (units: length, mass,
temperature, volume, speed, data, time).

Validation: `isEmail isPhone isUrl isNumber`.

Node helpers: `each(list, (item, i) => node)`, `when(cond, node)` (= `cond ? node : null`),
`pending(name)` (§9.1), `loading(query)`.

### 5.5 Records and lists in state

Lists and records in state are **immutable values**. Actions replace them (§7.2); there is no
in-place mutation. This makes the reactive graph trivially correct and makes undo cheap.

### 5.6 Null safety

Member access on `null` yields `null`; calling a function with a `null` number yields `null`;
rendering `null` renders nothing; a `null` in text renders "—". The model does not need defensive
code for streaming gaps.

### 5.7 Determinism

Given the same source and state, evaluation is identical on every run. No wall-clock reads except
`now()`/`today()`, no unseeded randomness. This is what makes snapshots, persistence, undo and
replay tests reliable.

### 5.8 User functions

```weave
fn tip(bill, pct) = round(bill * pct / 100, 2)
fn grade(score) = score >= 90 ? "A" : score >= 75 ? "B" : "C"
fn card(p) = Card(pad: m) [Title(p.name), Text(p.blurb, style: bodyMedium)]
```

Functions are pure. Recursion is allowed but bounded (depth 64) and every evaluation has a step
budget (§24.4). Functions may return nodes, which makes them the component-composition mechanism.

---

## 6. State

### 6.1 Declaration

```weave
$count = 0
$tab: num = 0
$query = ""
$cart: list = []
$draft = "" transient
```

- The initial value is any expression evaluated once, at block creation. It may reference other
  state (`$end = $start + 7`), evaluated in dependency order.
- State is **persisted by default** (§23.1): scroll away for a week, come back, the numbers are as
  you left them. `transient` opts out (resets each time the block is shown).
- Re-declaring a state in an edit **keeps the user's current value** unless the edit writes
  `$name = expr reset`.

### 6.2 Binding inputs

Inputs take state as their lead: `Slider($rate, …)`, `TextField($query, …)`, `Toggle($dark, …)`.
That is a **two-way binding**: the input shows the value and writes it on change. A bound input can
also take `onChange:` for extra actions.

Binding into structure is allowed with a path: `Toggle($todos[i].done)`, `TextField($form.email)`.
The write produces a new value for the whole state variable.

### 6.3 Derived values

Every binding is reactive. `emi = monthly($principal, $rate, $years)` recomputes only when one of
those three changes, and only nodes reading `emi` recompose. Nothing more to learn.

### 6.4 History

Each block keeps the last 50 state snapshots. `!undo` / `!redo` are actions; a long-press on a
block offers "Undo last change". Snapshots are taken per user gesture, not per frame (a slider drag
is one entry).

### 6.5 Thread scope: blocks reading each other

A block may export values: `export emi, $rate`. A later block in the same thread reads them as
`loan::emi`, `loan::$rate` (read-only). If the source block is missing, the read is `null`.

This makes conversations compose: "now compare that with a 15-year loan" produces a block that
reads the first one, and stays live if the user moves the first slider.

### 6.6 Global context values

Read-only, reactive: `device.width` (`compact` | `medium` | `expanded` window class),
`device.dark`, `device.locale`, `device.currency`, `device.reducedMotion`, `device.fontScale`,
`device.landscape`, `block.expanded`, `block.visible`, `user.firstName` (only if the user has set
one in settings; otherwise `null`).

---

## 7. Actions

### 7.1 Action values

An action is a value created by a `!call`. It **does nothing until triggered** by an event
(`onTap:`, `onChange:`, an effect). A list of actions runs in order:
`onTap: [!set($step, 2), !haptic(confirm)]`. A lambda returning an action receives event data:
`onDrag: p => !set($x, p.x)`.

### 7.2 Local actions (instant, no model, no consent)

| Action | Effect |
|---|---|
| `!set($x, value)` | Assign. Paths allowed: `!set($form.email, "")`. |
| `!inc($x, by: 1)` / `!dec` | Numeric step, optional `min:`/`max:` clamp. |
| `!toggle($b)` | Flip a boolean. |
| `!reset($x, …)` / `!resetAll` | Back to the declared initial value. |
| `!push($list, item)` / `!insert($list, i, item)` / `!remove($list, i)` / `!move($list, from, to)` | List edits. |
| `!patch($rec, {k: v})` | Merge into a record. |
| `!undo` / `!redo` | §6.4. |
| `!if(cond, then, else?)` | Conditional action. |
| `!after(dur, action)` | Delayed; cancelled if the block leaves composition. |
| `!open(name)` / `!close(name?)` | Show/hide a `Sheet`, `Dialog` or `Popover` binding (§13.8). |
| `!go(name, index)` | Switch a `Tabs`/`Pager`/`Wizard` to a page. |
| `!scrollTo(name)` | Scroll the chat so a node is visible. |
| `!expand` / `!collapse` | Full-screen mode (§16). |
| `!haptic(tick \| click \| confirm \| reject \| heavy)` | Haptic feedback (respects system setting). |
| `!toast(text)` | Snackbar. |
| `!copy(text \| node)` | Clipboard (node → its plain-text rendering). |
| `!focus(name)` | Focus a text field. |
| `!confetti` | A short celebration burst over the block (honours reduced motion). |

### 7.3 Conversation actions

| Action | Effect |
|---|---|
| `!say(text, attach: [values])` | Sends `text` as a **user message**. The sent bubble carries a small "from <block title>" chip. `attach:` adds state snapshots as context (§22). |
| `!ask(prompt, update: [names], with: [values])` | Silent model call that **edits this block in place** (§9.1). No new message. |
| `!refresh(name)` | Regenerates a `lazy` binding (§9.2). |
| `!regen` | Asks the model to rewrite the whole block with the same intent. |
| `!branch(text)` | Starts a new branch of the conversation from this message with `text` as the user turn. |

Before the first `!say` from a block runs, the button shows the exact text it will send on
long-press, and `!say` text is always visible in the sent bubble. A block can never send a message
the user did not see.

### 7.4 Device actions (always confirmed)

These open a confirmation sheet showing exactly what will happen; nothing runs without a tap.

| Action | Effect |
|---|---|
| `!url(href)` | Open in browser (https only). |
| `!share(text \| node \| file)` | Android share sheet; a node is rendered to PNG (§23.4). |
| `!export(node, as: png \| pdf \| csv \| json \| weave)` | Save a file via the system picker. `csv`/`json` require a list or table value. |
| `!calendar(title, start, end?, notes?, location?)` | Pre-filled calendar insert intent. |
| `!remind(text, at)` | Schedules a local notification (needs notification permission). |
| `!dial(number)` / `!sms(number, body)` / `!email(to, subject, body)` | Opens the app with fields pre-filled; never sends by itself. |
| `!map(query \| {lat, lng})` / `!directions(to)` | Opens maps. |
| `!tool(name, args, into: $var)` | Runs an app tool (§10). |

### 7.5 Custom actions are not possible

There is no way to define a new action type. Composition (`[…]`, `!if`, `!after`, functions that
return actions) covers every need without opening a hole.

---

## 8. Effects and time

### 8.1 Form

```
on trigger [when condition] -> action
```

### 8.2 Triggers

| Trigger | Fires |
|---|---|
| `change($x)` / `change(name)` | After the value changes (not on first render). Debounce: `change($q, debounce: 400ms)`. |
| `appear` | First time the block is shown (once per block lifetime, not per scroll). |
| `visible` / `hidden` | Each time the block enters/leaves the screen. |
| `every(dur)` | Repeating timer, min `250ms`. Runs only while visible. |
| `at(date)` | Once, when the time passes, while visible. |
| `frame` | Every frame; the action can read `dt` (seconds since the last frame) — tier 4 only, visible only, max 60 fps (§15.4). |
| `tilt` | Device tilt `{x, y}` in −1…1 — tier 4, visible only, no permission needed. |
| `shake` | Device shake. |

### 8.3 Time values

`now()` is reactive but **coarse by default**: it ticks each minute. A block that needs seconds
declares `on every(1s) -> !set($t, now())` and reads `$t`. This keeps idle blocks free of
recomposition.

### 8.4 Examples

```weave
on change($city, debounce: 600ms) -> !ask("Weather card for {$city}", update: [forecast])
on every(1s) when $running -> !set($left, max(0, $left - 1))
on change($left) when $left == 0 -> [!set($running, false), !haptic(heavy), !confetti]
```

---

## 9. The model loop

This is the part nobody else does well: blocks that keep the model in the loop *without* turning
every interaction into a chat message.

### 9.1 `!ask` — silent, scoped, in-place updates

```weave
$budget = 40000
$style = "relaxed"
plan = Column [Text("Planning…")]
controls = Column(gap: m) [
  Slider($budget, min: 10000, max: 200000, step: 5000, format: inr, label: "Budget"),
  Segmented($style, options: ["relaxed", "packed", "foodie"])
]
on change($budget, debounce: 700ms) -> !ask("Rebuild the 3-day Jaipur plan for this budget and style", update: [plan], with: [$budget, $style])
on change($style) -> !ask("Rebuild the 3-day Jaipur plan for this budget and style", update: [plan], with: [$budget, $style])
```

Semantics:

1. The app sends a **hidden request** to the same model (or a cheaper "UI model" chosen in settings,
   §20.4) containing: the block source, current state (`with:` values plus anything referenced),
   the prompt, and the list of statements it may change.
2. The model answers with a Weave **edit** (§9.4) containing only those statements. Anything else in
   its answer is discarded. New helper statements are allowed if their names are new.
3. While waiting, `pending(plan)` is `true` and the renderer shows a shimmer over the `plan` node
   (built-in; no code needed). The rest of the block stays interactive.
4. A newer `!ask` from the same trigger **cancels** the older one (latest wins).
5. The exchange is stored with the message (not shown as chat turns) so the block can be replayed
   and the model sees a compact log of updates later (§22).
6. Cost guard: max 1 in-flight `!ask` per block; max 20 per block per hour unless the user taps
   "Keep updating"; each `!ask` counts against the user's provider usage as normal.

### 9.2 `lazy` — UI that generates itself on demand

```weave
tabs = Tabs($tab, labels: ["Overview", "Day 1", "Day 2", "Day 3"]) [
  overview,
  lazy("Hour-by-hour plan for day 1 in Jaipur, with a map and costs", budget: $budget),
  lazy("Hour-by-hour plan for day 2", budget: $budget),
  lazy("Hour-by-hour plan for day 3", budget: $budget)
]
```

A `lazy(prompt, …context)` value is a node that is generated **the first time it is shown**. The
model returns a Weave fragment (statements whose `root` replaces the lazy node). The result is
cached with the block; `!refresh(name)` regenerates it. Context arguments become part of the
cache key, so changing `$budget` marks lazy nodes stale (a small "Update" chip appears; the user
decides — no surprise spend).

This gives effectively **infinite UI**: a reply can be a small shell whose depth unfolds as the user
explores, paying only for what they open.

### 9.3 `!regen` and `!branch`

`!regen` re-asks for the whole block (keeps state values whose names survive). `!branch` is a
normal EchoFlow branch with a prefilled user turn.

### 9.4 Edits

An edit is a fence with `edit` and the target block id:

````
```weave edit loan
$rate = 9.1 reset
chart = Chart(amortize($principal, $rate / 100, $years), h: l) [bar(x: "year", y: "interest", tone: error)]
-cta
```
````

Rules:

- A statement with an existing name **replaces** it; a new name **adds** it; `-name` **deletes** it.
- State values are kept unless `reset` is written (§6.1).
- Each applied edit creates a new **version** of the block (§23.2). The block animates between
  versions (shared-element on nodes with the same name, fade otherwise).
- Edits may appear in any later assistant message (the model says "I've switched the chart to bars"
  and includes the edit), from `!ask`, or from remix (§23.3).
- An edit to an unknown block id renders as a normal block if it contains `root`, otherwise as code.

---

## 10. Data: `query` and tools

### 10.1 `query`

```weave
fx = query("web.search", {q: "USD to INR rate today"}, every: 30m)
rate = fx.value.rate ?? 83.2
status = loading(fx) ? Text("Updating…", style: labelSmall) : Text("as of {fx.at | relative}", style: labelSmall)
```

A query is a reactive value `{value, loading, error, at}`. It runs when the block appears, again
when any `$var` in its arguments changes (debounced 400 ms), and on `every:` while visible.

### 10.2 What tools exist

Only tools in EchoFlow's tool registry that are marked **block-safe**, e.g.:

| Tool | Returns | Notes |
|---|---|---|
| `web.search` | `{results: [{title, url, snippet}]}` plus a model-extracted `value` when the query asks for a fact | Uses the user's configured search provider. |
| `web.read` | `{title, text}` of a URL | Only URLs present in the block source or in a prior result. |
| `model.extract` | Structured `value` from text, by schema | A small model call. |
| `sandbox.python` | `{stdout, files, value}` | Only if the user has configured the sandbox (see `docs/sandbox-feature.md`). |
| `image.generate` | `{url}` | Uses the user's image provider. |
| `connector.*` | Per connector | Only connectors the user has enabled. |

### 10.3 Consent

The first time a block wants to run a tool, the tool's node area shows a compact consent row:
"This block wants to **search the web** for *USD to INR rate today* — Allow once · Always for this
block · No". Nothing runs before a choice. "Always" is per block, per tool, revocable from the
block's menu. `!tool(...)` actions use the same consent.

### 10.4 Treat results as data

Tool results are values. They are never parsed as Weave, never executed, and when sent back to the
model (§22) they are wrapped as quoted data.

---

## 11. Design tokens — the mobile contract

The model never writes dp, sp, hex or ms (except `dur` literals for timers). It writes tokens.

### 11.1 Spacing (`gap`, `pad`, `padX`, `padY`, `pad: [top, end, bottom, start]`)

| Token | dp |
|---|---|
| `none` | 0 |
| `xxs` | 2 |
| `xs` | 4 |
| `s` | 8 |
| `m` | 12 |
| `l` | 16 |
| `xl` | 24 |
| `xxl` | 32 |

### 11.2 Heights (`h`) and widths (`w`)

| Token | Height dp | Typical use |
|---|---|---|
| `xs` | 48 | Sparkline, progress strip |
| `s` | 96 | Small chart, thumbnail row |
| `m` | 168 | Standard chart |
| `l` | 240 | Large chart, map, canvas |
| `xl` | 320 | Hero media |
| `screen` | 60% of viewport (expanded: 100%) | Games, full canvases |
| `aspect(w/h)` | from width | `aspect(16/9)`, `aspect(1)` |
| `wrap` | content | default |

Widths: `fill` (default for block-level), `wrap`, a fraction `1/2`, `1/3`, `2/3`, `1/4`, `3/4`, or
`weight(n)` inside a `Row`.

### 11.3 Colour roles (`tone`, `color`, `bg`)

`primary`, `secondary`, `tertiary`, `error`, `neutral` (surface family), `inverse`, plus
**semantic** roles mapped onto them: `success`, `warning`, `info`, `positive` (= success),
`negative` (= error), `muted` (onSurfaceVariant).

A `tone` on a container automatically uses the right **container/on-container pair**
(`primaryContainer` + `onPrimaryContainer`), so contrast is guaranteed. `emphasis: low | medium |
high` picks container vs. solid. Advanced: `color: onSurfaceVariant` and other exact M3 role names
are accepted where a single colour is needed.

`success`/`warning`/`info` are harmonised to the active palette (hue fixed, tone and chroma from the
scheme), so they never clash with Lavender or Sunset.

**Chart series** get colours in this order when not specified: `primary, tertiary, secondary,
error, primary(low), tertiary(low), secondary(low), neutral` — all scheme-derived.

### 11.4 Typography (`style`)

The 15 M3 roles: `displayLarge … labelSmall` (display/headline/title/body/label × large/medium/
small). Shortcuts: `hero` = displaySmall, `h1` = headlineSmall, `h2` = titleLarge, `h3` =
titleMedium, `body` = bodyLarge, `caption` = bodySmall, `overline` = labelSmall + caps, `mono` =
the code font at bodyMedium. Weight: `weight: regular | medium | bold`. `display*` is capped to
`headlineLarge` when the block is `inline` (it would overflow a bubble).

### 11.5 Shape (`shape`)

`none` 0, `xs` 4, `s` 8, `m` 12, `l` 16, `xl` 28, `full` (pill/circle), plus Material 3 Expressive
shapes for decorative containers and icons: `cookie`, `clover`, `sunny`, `pill`, `gem`, `arch`,
`burst`, `flower`, `puffy`, `slanted` (the `MaterialShapes` set). Expressive shapes morph when
their value changes.

### 11.6 Elevation (`elevation`)

`0`–`3`, mapped to tonal elevation (not shadows) to match EchoFlow.

### 11.7 Motion

Durations and easings are the M3 Expressive motion scheme: `motion: fast | default | slow` and
`spring: bouncy | smooth | stiff`. Raw ms exist only for timers and `!after`.

### 11.8 Icons

Material Symbols Rounded names (`"savings"`, `"flight_takeoff"`). Unknown names render a neutral dot
and a warning. Emoji are allowed in text.

### 11.9 Images

`Image(src)` with `https` URLs (loaded through the app's image loader, cached, max 4 MB, no
redirects to non-https). `data:` URIs up to 64 KB. Generated images via `!tool("image.generate")`.

---

## 12. Common node properties

Every component accepts these (they are not repeated in §13):

| Prop | Type | Meaning |
|---|---|---|
| `show` | bool | Render only when true (animated in/out). |
| `w`, `h` | token | §11.2 |
| `pad`, `padX`, `padY` | spacing | Inner padding |
| `align` | `start \| center \| end \| stretch` | Self alignment in parent |
| `tone`, `emphasis` | §11.3 | Colour |
| `bg` | colour role | Background |
| `shape` | §11.5 | Clip/shape |
| `border` | `true \| tone` | Outline in `outlineVariant` or the tone |
| `enter` | `fade \| rise \| scale \| slide \| pop \| none` | Entrance animation (staggered among siblings) |
| `onTap`, `onLongPress`, `onDoubleTap` | action | Gestures (adds ripple and a11y click) |
| `onSwipe` | `dir => action` | `left \| right \| up \| down` |
| `label` | str | Accessibility label override |
| `key` | expr | Identity for list animations (default: index) |
| `tip` | str | Tooltip on long-press |
| `badge` | num \| str | Small badge in the top-end corner |
| `disabled` | bool | Disabled look, no interaction |

---

## 13. Component catalog

Signatures: `Name(lead, lead?, param: type = default, …) [children]`. `?` = optional.

### 13.1 Layout

```
Column(gap: spacing = s, align: start|center|end|stretch = stretch, scroll: bool = false) [children]
Row(gap: spacing = s, align: top|center|bottom|baseline = center, justify: start|center|end|between|around|evenly = start, wrap: auto|never = auto) [children]   # auto: becomes a Column when children overflow
Box(contentAlign: topStart|top|…|center|…|bottomEnd = topStart) [children]      # layers children
Grid(cols: num | auto = 2, gap: spacing = s, minCell: token = s) [children]     # auto = as many as fit
Flow(gap: spacing = s, rowGap: spacing = s) [children]                          # wrapping row (chips, tags)
Carousel(peek: bool = true, snap: bool = true, itemW: 1/2|2/3|3/4|4/5 = 3/4) [children]   # horizontal; the only horizontal scroller
Spacer(size: spacing | weight(n))
Divider(inset: bool = false)
Card(style: filled|outlined|elevated|tonal = filled, pad: spacing = l, gap: spacing = s) [children]
Surface(tone: color = neutral, emphasis: low|medium|high = low) [children]
Section(title?, subtitle?, action?: node, collapsible: bool = false, open: bool = true) [children]
Split(ratio: 1/2|1/3|2/3 = 1/2, breakpoint: medium) [left, right]              # side-by-side when wide, stacked when compact
Sticky [header, body]                                                           # header pins while body scrolls (needs h)
```

### 13.2 Text and content

```
Text(content, style: textStyle = body, tone?, maxLines?: num, align: start|center|end = start, select: bool = false)
Title(content, style = titleMedium, subtitle?: str)
Rich [parts]                       # inline run of Text/Chip/Icon/Metric/Slider(inline:true)/Link for "sentences with live values"
Markdown(source)                   # EchoFlow's markdown renderer, for long prose inside blocks
Icon(name, size: s|m|l = m, tone?)
Avatar(name | src, size: s|m|l = m)            # initials from name when no src
Badge(content, tone = primary)
Chip(label, icon?, selected?: bool, onTap?)    # display chip
Link(label, href)                              # = Text + !url, confirmed
Quote(content, cite?)
Callout(content, kind: info|success|warning|error|tip = info, title?)
Code(source, lang?, run?: bool = false)        # run = offer "Run in sandbox" if configured
Kbd(keys)                                      # keyboard shortcut chips
Empty(title, body?, icon?, action?)            # empty-state illustration
```

### 13.3 Numbers and status

```
Metric(label, value, format?, prefix?: str, suffix?: str, delta?: num, deltaFormat = percent, trend?: list<num>, tone?, animate: bool = true, size: s|m|l = m)
Progress(value 0..1, kind: linear|circular|wavy = wavy, label?, tone = primary)
Gauge(value, min = 0, max = 100, bands?: list<{to, tone}>, label?, format?)
Rating(value, max = 5, readOnly = true)
Stat [Metric…]                              # equal-width KPI strip, wraps on compact
Steps(current, labels: list<str>)            # horizontal stepper indicator
Timer(seconds, running: bool, onDone?)       # big countdown display
Clock(time, style: digital|analog = digital)
```

### 13.4 Inputs (all bindable; all take `label`, `help?`, `rule?`, `onChange?`, `disabled?`)

```
Button(label, onTap, variant: filled|tonal|outlined|text|elevated = filled, icon?, size: s|m|l = m, tone?, wide: bool = false)
IconButton(icon, onTap, variant: standard|filled|tonal|outlined = standard, label: str)   # label required for a11y
Fab(icon, onTap, label?, expanded: bool = false)            # floats at the block's bottom-end
SplitButton(label, onTap, menu: list<{label, onTap}>)
ButtonGroup($bind?, options) [buttons]                       # M3 Expressive connected buttons
Toggle($bool, label?)                                        # switch
Check($bool, label)
Radio($value, options: list)                                 # vertical list
Segmented($value, options: list, multi: bool = false, suffix?: str, icons?: list)
Chips($value, options: list, multi: bool = false)            # filter/choice chips, wraps
Slider($num, min = 0, max = 100, step?, label?, format?, suffix?, ticks: bool = false, inline: bool = false)
RangeSlider($range, min, max, step?, label?, format?)        # $range = [lo, hi]
Stepper($num, min?, max?, step = 1, label?)                  # − 3 +
TextField($str, label, placeholder?, kind: text|number|email|phone|url|multiline|search = text, maxLen?, prefix?, suffix?)
Select($value, options: list, label)                         # exposed dropdown
DatePicker($date, label, min?, max?)                         # opens M3 date picker sheet
DateRange($range, label)
TimePicker($time, label)
ColorPick($role, options?: list<color>)                      # picks a colour ROLE, not a hex
RatingInput($num, max = 5)
Pad2D($point, x: {min, max, label}, y: {min, max, label})    # 2-D picker; $point = {x, y}
Sketch($image, h = l, tools: pen|marker|eraser)              # freehand; value is a PNG usable in !say(attach:)
Swipe($deck, render: item => node, onLeft: item => action, onRight: item => action)  # card stack
Rank($list, render: item => node)                            # drag to reorder
Sort($board, columns: list<str>, render: item => node)       # kanban: $board = {col: [items]}
Form(onSubmit, submitLabel = "Submit") [fields]              # validates all `rule`s before running onSubmit
```

`rule:` is an expression that is `true` when valid, or a string error message:
`TextField($email, label: "Email", kind: email, rule: isEmail($email) or "Enter a valid email")`.

**Not offered, on purpose:** password fields, card-number fields, OTP fields, file pickers. Blocks
cannot be used to collect secrets (§24.3).

### 13.5 Collections and data

```
List(items, render: (item, i) => node, divider: bool = true, empty?: node, lazy: bool = auto)      # lazy for > 30 items, needs h
Item(title, subtitle?, overline?, lead?: node|icon, trail?: node, onTap?)                          # M3 list item
Table(rows, cols: list<col>, sort: bool = true, filter: bool = false, sticky: num = 1, dense: bool = false, totals?: list<str>, onRow?: row => action)
col(header, value: row => any, format?, align?, w?)                                               # helper for Table
Tree(root, children: n => list, render: n => node, open: num = 1)                                  # collapsible hierarchy, org charts
Timeline(items, render: item => node, time: item => date|str, dir: vertical|horizontal = vertical)
Calendar(events, month?: date, view: month|week|agenda = month, onDay?: d => action)
Heatcal(values: list<{date, value}>, tone = primary)                                               # GitHub-style year/quarter heatmap
Diff(before, after, mode: inline|split = inline)
Json(value, open: num = 1)                                                                         # explorable JSON
```

### 13.6 Navigation inside a block

```
Tabs($index, labels: list<str>, icons?: list, scrollable: bool = auto) [pages]
Pager($index, dots: bool = true, h?) [pages]
Wizard($step, labels: list<str>, next?: list<bool>, onFinish) [steps]    # next[i] gates Next on step i
Accordion($open?: num, single: bool = true) [Section…]
Reveal(label = "Show more", lines: num = 3) [content]                     # clamp + expand
Flip($side?, back: node) [front]                                          # card flip (flashcards)
```

### 13.7 Media and spatial

```
Image(src, alt: str, fit: cover|contain = cover, h?, shape?, zoom: bool = false)
Gallery(srcs, alts?, cols = 3, viewer: bool = true)
Video(src, poster?, autoplay: bool = false, loop: bool = false)          # Media3
Audio(src, title?, waveform: bool = true)
Map(center?: {lat, lng} | str, zoom = 12, markers?: list<{lat, lng, title, tone?, onTap?}>, route?: list<{lat, lng}>, h = l)
Model3D(src, autoRotate: bool = true, h = l)                             # glTF via SceneView (tier 4)
Lottie(src, loop: bool = true, h?)
Qr(content, size = m)
```

### 13.8 Overlays (defined as bindings, opened with `!open(name)`)

```
Sheet(title?, full: bool = false) [content]
Dialog(title, confirm?: {label, onTap}, dismiss?: {label}) [content]
Popover(anchor: name) [content]
Menu(anchor: name, items: list<{label, icon?, onTap}>)
```

Overlays live in their own window (as with all Compose popups); they are verified on device, not in
Roborazzi (see CLAUDE.md).

### 13.9 Visual

```
Gradient(tones: list<color>, angle = 135, h?, shape?) [children]          # scheme-derived gradients only
Shader(kind: aurora|mesh|grain|ripple|liquid, tones?, speed = 1, h?) [children]   # AGSL, API 33+; falls back to Gradient
Shape(kind: expressive shape, size = m, tone = primary, morphTo?: shape, rotate?: num)
Blob(seed, tone, h = m)                                                   # organic decorative shape
Skeleton(kind: text|card|chart|list = card)                               # explicit placeholder
Canvas(view: [x, y, w, h], h = l, onTap?: p => action, onDrag?: p => action, onPinch?: s => action) [marks]   # §15
```

### 13.10 Learning and play

```
Quiz($answers, questions: list<{q, options, answer, why?}>, shuffle: bool = false, onDone?: score => action)
Flashcards($state, cards: list<{front, back}>, spaced: bool = true)
Blanks($values, text: str)                         # "The capital of France is [[Paris]]"
Match($pairs, left: list, right: list)             # drag/tap to connect
Board($cells, rows, cols, render: (cell, r, c) => node, onCell: (r, c) => action)   # grid games: tic-tac-toe, sudoku, minesweeper
Dice($values, count = 1, onRoll?)
Spinner($choice, options: list)                    # decision wheel
Scratch(reveal: node, cover?: tone)                # scratch-to-reveal
```

### 13.11 Charts

`Chart` (§14) plus sugar that expands to it: `LineChart`, `AreaChart`, `BarChart`, `PieChart`,
`DonutChart`, `ScatterChart`, `Sparkline`, `Radar`, `Funnel`, `Treemap`, `Sankey`, `Candles`,
`Histogram`, `BoxPlot`, `Heatmap`, `Waterfall`. Each sugar takes `(data, x:, y:, …)`.

### 13.12 Catalog growth rules

- New components are added to the Kotlin catalog only; the prompt and schema update automatically.
- Parameters are never renamed; deprecated ones keep working and drop out of the prompt.
- A component the renderer does not know (newer model output, older app) renders its children, or
  its `label`/first string argument as text, with a warning (§18).

---

## 14. The Chart grammar

A grammar of graphics, so models can build charts no fixed list anticipates.

```
Chart(data, h = m, polar: bool = false, x?: axis, y?: axis, y2?: axis, legend: auto|top|bottom|none = auto,
      tooltip: bool = true, scrub: bool = true, zoom: bool = false, select?: $bind,
      animate: bool = true) [marks]
```

### 14.1 Marks

```
line(x, y, tone?, curve: linear|smooth|step = smooth, width: s|m|l = m, dashed?: bool, points?: bool, series?: field)
area(x, y, y0?, tone?, stack?: bool, opacity = 0.3, series?)
bar(x, y, tone?, stack?: bool, group?: field, horizontal?: bool, round = s, labels?: bool)
point(x, y, size?: field|num, tone?, shape: circle|square|diamond = circle)
rule(x? | y?, tone = muted, label?, dashed = true)        # reference / threshold lines
band(from, to, axis: x|y, tone, label?)                    # shaded ranges (night, recession, target zone)
text(x, y, content, tone?)                                 # annotations
arc(value, label, tone?)                                   # pie/donut slices; Chart(…, polar: true)
range(x, lo, hi, tone?)                                    # error bars, candles' wicks
cell(x, y, value)                                          # heatmap cells
link(from, to, value)                                      # sankey
```

Field arguments are strings naming record fields (`x: "year"`) or lambdas (`y: r => r.a + r.b`).

### 14.2 Axes

```
axis(title?, format?, min?, max?, ticks?: num, grid: bool = true, log: bool = false, hidden: bool = false)
```

### 14.3 Interaction

- **Scrub:** drag across the plot; a vertical cursor snaps to the nearest x with a haptic tick per
  step and a tooltip card listing each series' value.
- **Tap:** selects a point/bar; with `select: $picked` the selection is bound to state so the rest
  of the block can react ("tap a month to see its transactions").
- **Legend:** tap to toggle series.
- **Zoom:** pinch on x when `zoom: true`; double-tap resets.
- **Animation:** when data changes (e.g. a slider moves), marks interpolate from old to new values
  with the default spring. Driven every frame during a drag, so slider ↔ chart feels physical.

### 14.4 Rendering

Charts are drawn with Compose `Canvas` in a single draw pass per chart (no per-point composables),
text measured once and cached. Large data is downsampled (LTTB) to ≤ 2 points per pixel. Colours
come from §11.3.

---

## 15. Canvas, motion and simulation

### 15.1 Canvas

`Canvas(view: [0, 0, 100, 60], h: l) [marks]` draws vector marks in a **virtual coordinate
space** that is scaled to the block width — the model never thinks in pixels.

Marks:

```
circle(x, y, r, fill?, stroke?, width?)
rect(x, y, w, h, fill?, stroke?, radius?)
line(x1, y1, x2, y2, stroke, width?, dashed?)
path(d, fill?, stroke?, width?)                    # SVG path syntax
poly(points: list<[x, y]>, fill?, stroke?, closed?)
label(x, y, content, style?, tone?, anchor?)
icon(x, y, name, size, tone?)
image(x, y, w, h, src)
group(translate?: [x, y], rotate?: deg, scale?: num, opacity?) [marks]
```

Colours are roles (`fill: primary`, `stroke: outline`). Each mark takes `onTap:`; `Canvas` takes
`onTap`/`onDrag`/`onPinch` with points in view coordinates.

### 15.2 Motion functions

Any numeric, colour or shape value can be animated by wrapping it:

| Function | Meaning |
|---|---|
| `spring(v, kind: bouncy\|smooth\|stiff = smooth)` | Springs to the new value when `v` changes |
| `tween(v, motion: fast\|default\|slow)` | Eased transition |
| `loop(from, to, period: dur, ease: linear\|sine = sine)` | Continuous oscillation while visible |
| `stagger(i, base: dur = 40ms)` | Delay helper for list entrances |

```weave
needle = group(rotate: spring(remap(emi, 0, 100000, -120, 120), kind: bouncy)) [line(50, 50, 50, 12, stroke: primary, width: l)]
```

`device.reducedMotion` turns springs into instant changes and stops `loop`.

### 15.3 Gestures on canvas

```weave
$p = {x: 50, y: 30}
pad = Canvas(view: [0, 0, 100, 60], onDrag: d => !set($p, {x: d.x, y: d.y})) [
  circle($p.x, $p.y, 6, fill: primary),
  label(4, 8, "x {round($p.x)} · y {round($p.y)}", style: labelSmall)
]
```

### 15.4 Simulation (tier 4)

`on frame -> action` runs each frame with `dt`. Combined with state and canvas this is enough for
games and physics:

```weave
$angle = 0.6
$vel = 0
$len = 1.5
on frame -> [
  !set($vel, $vel - 9.81 / $len * sin($angle) * dt),
  !set($angle, $angle + $vel * dt)
]
```

Rules: runs only while the block is visible and the app is foreground; state writes inside one
frame are batched into one recomposition; history (§6.4) does not record frame writes; a frame
callback over its budget (2 ms) three times in a row pauses the simulation with a "Paused —
tap to resume" overlay.

### 15.5 Script functions (tier 4, optional)

For logic beyond expressions (path-finding, a chess engine, a sort visualiser's steps):

```weave
js fn solve(board) = """
  // pure: JSON in, JSON out. No I/O, no globals, no timers.
  ...
  return next;
"""
```

Runs in **QuickJS** in an isolated runtime per block: no `fetch`, no `require`, no host objects,
50 ms wall time and 16 MB memory per call, results must be JSON-serialisable. Exceeding a limit
returns `null` and logs a warning. The JS tier is off for models below tier 4 and can be disabled
in settings.

---

## 16. Block sizes, expand and full-screen mode

| `size` | Behaviour |
|---|---|
| `inline` | Inside the reply column like text. Max height 1.2 × viewport; beyond that the block clamps with "Expand". |
| `card` | Same width, rendered as a distinct elevated surface with title bar and a ⋯ menu (version, undo, remix, keep, share, view source). |
| `wide` | Breaks out of the reply's horizontal padding to the screen edges (galleries, maps, tables). |
| `full` | Shows as a card preview; tapping opens a **full-screen mini-app** with the chat composer docked at the bottom — "co-pilot mode" — where typing sends `!ask`-style edits to this block instead of new chat messages. |

`!expand` opens any block in full-screen with a shared-element transition from the bubble;
`block.expanded` lets the block adapt (`h: block.expanded ? screen : m`). Back collapses it.

Landscape and foldables: `Split` and `Grid(cols: auto)` reflow on `device.width`; full-screen
blocks get a two-pane layout on `expanded` widths.

---

## 17. Streaming

### 17.1 Statement-level rendering

The parser consumes the growing fence body. A statement is **complete** when its line ends with no
open brackets and no trailing continuation token. Complete statements are parsed once and frozen.
The last, incomplete statement is parsed **speculatively** with auto-closing (strings, brackets) so
partial children can appear early; its result is discarded and replaced as more text arrives.

### 17.2 Placeholders

A reference to a name that has not arrived yet renders a **skeleton** sized by the referring slot
(a chart slot gets a chart skeleton of height `h`, a text slot a text line). When the statement
arrives, the skeleton cross-fades into it. Layout height only grows during streaming (skeletons are
sized to the eventual default), which prevents jumps.

### 17.3 Prompted order

The prompt asks for: header → state → `root` → top-level children in visual order → helpers and
functions → effects. This makes the block's outline appear immediately and fill top-down.

### 17.4 Interactivity during streaming

Inputs become interactive once their bound state is declared. Actions referencing names that have
not arrived are disabled until they do. Effects do not run until the fence closes.

### 17.5 Equivalence

When the fence closes, the program is re-checked as a whole. The result must equal what was
streamed; this is asserted in tests by streaming every corpus program char-by-char (§28).

---

## 18. Validation, repair and fallback

### 18.1 Error classes

| Class | Example | Handling |
|---|---|---|
| `syntax` | Unbalanced bracket in one statement | That statement is dropped; references to it render a quiet "missing" chip in debug builds and nothing in release. |
| `unknown-component` | `Swiper(...)` | Render children, else first string arg as `Text`; warning. Suggest the closest name in repair. |
| `unknown-arg` | `Slider($x, colour: …)` | Arg dropped; warning. |
| `bad-token` | `gap: 13` | Snap to nearest token (`m`); warning. |
| `bad-type` | `Slider("x")` | Argument dropped; if a required lead, the node renders an inline error chip "Slider needs a value". |
| `missing-ref` | `root` references `chart` that never arrives | Placeholder removed at fence close. |
| `cycle` | `a = b + 1`, `b = a + 1` | Both `null`; warning. |
| `budget` | Runaway recursion | `null`; warning. |
| `limit` | > 600 nodes | Truncate after limit with a "Block too large" footer. |

### 18.2 Auto-fix pass (before checking)

Common model slips are fixed silently: positional args where named are required (mapped by the
catalog's documented order, then warned), `&&`/`||` → `and`/`or`, `!x` on a boolean → `not x`,
hex colours → nearest role, dp/px numbers in spacing → nearest token, JSON-style `"key": value` in
records, smart quotes, a trailing ``` inside the body, `color` → `tone` where only `tone` exists,
single `=` in conditions (`if a = b`) → `==`.

### 18.3 Repair round (tier 3+)

If the closed block has errors that removed visible content, EchoFlow may make **one** hidden
repair call: the errors (as compact lines) + the broken statements → an edit. Applied only if it
reduces errors. Off by default for metered providers; on for flat-rate ones; user-settable.

### 18.4 Fallback ladder

1. Render everything that checks; drop the rest.
2. If nothing renders, show the block as a code fence with a "Couldn't render this block" chip and
   a "Try again" button (`!regen`).
3. Never throw out of the renderer. Every node renders inside an error boundary.

---

## 19. Weave-JSON

For models that are better at JSON, and for providers that support schema-constrained decoding
(OpenAI-compatible `response_format: json_schema`, Mistral, others), Weave has an exact JSON
equivalent. The catalog generates the JSON Schema.

````
```weave-json
{
  "weave": 1, "id": "loan", "title": "Home loan",
  "state": { "rate": 8.5, "years": 20, "principal": 5000000 },
  "let": {
    "emi": "monthly($principal, $rate, $years)"
  },
  "fn": { "monthly(p, r, y)": "p * (r/1200) * pow(1 + r/1200, y*12) / (pow(1 + r/1200, y*12) - 1)" },
  "nodes": {
    "root": { "t": "Card", "a": { "pad": "l", "gap": "m" }, "c": ["rate", "kpi"] },
    "rate": { "t": "Slider", "a": { "bind": "$rate", "min": 6, "max": 14, "step": 0.05, "label": "Interest rate" } },
    "kpi":  { "t": "Metric", "a": { "label": "Monthly EMI", "value": "=emi", "format": "inr" } }
  },
  "on": [ { "trigger": "change($rate)", "do": "!haptic(tick)" } ]
}
```
````

- Strings starting with `=` are expressions; strings starting with `!` are actions; `$name` in a
  `bind` is a state binding; everything else is a literal.
- `nodes` is a flat map; `c` lists child names or inline node objects.
- Streaming: the JSON is parsed with a tolerant incremental JSON parser (the same repair approach as
  `MarkdownStreamingRepair`); each completed entry in `nodes` renders as it arrives.
- Edits in JSON: `{"edit": "loan", "set": {"nodes": {...}}, "delete": ["cta"]}`.

Both forms lower to the same IR. The renderer never knows which was used.

---

## 20. Capability tiers, model fit and the Intelligent UI setting

EchoFlow talks to many providers and model sizes. A small model that writes broken UI is worse than
plain markdown, so each model gets a tier.

| Tier | Name | Allowed | Prompt budget |
|---|---|---|---|
| 0 | Off | Markdown only | 0 |
| 1 | Display | Layout, text, metrics, charts, tables, media. No state, no actions except `!copy`/`!share`. | ~1.2k tokens |
| 2 | Interactive | + state, expressions, functions, inputs, local actions, `!say`, effects (no `frame`) | ~2.8k |
| 3 | Agentic | + `!ask`, `lazy`, `query`, `!tool`, edits, device actions | ~4k |
| 4 | Extreme | + `frame`, `tilt`, `Canvas` gestures, `Model3D`, `Shader`, `js fn` | ~5.5k |

### 20.1 How a model's tier is chosen

1. A curated table of known model ids → default tier (updated with app releases).
2. Unknown models: tier from context window and whether the provider reports tool use / structured
   output support (≥ 32k and tools → 2; otherwise 1).
3. User override per model in model settings ("Live UI: Off / Display / Interactive / Agentic /
   Extreme").
4. **Auto-downgrade:** if a model's last 10 blocks average more than 2 dropped statements, its tier
   drops by one for 7 days and a subtle note appears in model settings.

### 20.2 Form selection

Per model: `source` (default), or `json` when the model is tier ≤ 2 and the provider supports
schema-constrained output, or when the user picks it.

### 20.3 When to make UI at all

The prompt tells the model to use a block **only when interaction or visual structure clearly beats
prose**: calculations with parameters, comparisons, data, plans, quizzes, step-by-step tools,
anything the user will explore. Never for plain answers. How eagerly this rule is applied depends on
the **Intelligent UI** level (§20.5). A "Make this interactive" action on any reply asks for a block
version.

### 20.4 A separate UI model (optional)

Settings may name a cheaper/faster model for `!ask`, `lazy` and repair rounds, keeping
interactions snappy and cheap while the main model handles conversation.

### 20.5 The "Intelligent UI" setting

Users control how much interactive UI they get through one setting, **Settings → Intelligent UI**,
powered by **Echo UI** (this engine). It has three levels:

| Level | Short description shown in Settings | What the model is told | Limits |
|---|---|---|---|
| **Off** | "Replies stay as text." | Nothing: the Echo UI prompt is not sent (0 extra tokens). | No new blocks. Blocks already in older chats still render and work. |
| **Balanced** *(default)* | "Adds interactive UI when it clearly helps." | Use a block only when interaction or visual structure clearly beats prose (§20.3). | At most 1 block per reply. Capped at tier 3: no `frame`, `tilt`, `Shader`, `Model3D` or `js fn` unless the user asks for them. |
| **Immersive** | "Leans into rich, interactive answers." | Prefer a block whenever an answer has structure: numbers, options, steps, comparisons, places, timelines. Add controls that let the user explore, and use motion and visuals freely. | Up to 3 blocks per reply. The model's full tier, up to 4. Suggests full-screen mode for larger blocks. |

Rules:

- **Balanced is the default** for new installs and for users upgrading from a version without Echo UI.
- **The setting never raises a model above its tier** (§20.1). The effective tier is the lower of
  the model's tier and the level's cap. On Off, the effective tier is always 0. A tier-1 model on
  Immersive still only gets display blocks, just more often.
- **Per-chat override:** the `+` menu has an "Intelligent UI" chip with the same three levels. It
  applies only to that chat, overrides the global setting, and is saved with the chat.
- **Per-reply override:** "Make this interactive" on any reply always works, even on Off, because
  it is an explicit request. It produces one block at the model's full tier.
- **Explicit requests win.** If the user's message asks for UI ("give me a calculator", "show it as
  a chart"), the model may add a block even on Off, as long as the model's tier allows it. On Off,
  the Echo UI prompt is attached only to that one request.
- **Changing the level** affects new replies only. Existing blocks are never removed or reduced.
- The settings row shows a small live preview of each level: the same answer as text, as a
  Balanced block, and as an Immersive block.
- The model-settings tier override (§20.1) is an advanced option for capability. Intelligent UI
  is the everyday option for preference. The UI keeps them separate: tier sits under each model,
  and Intelligent UI sits in the main settings.

---

## 21. Prompt generation

The prompt is generated from the Kotlin catalog, per tier and form, and cached.

### 21.1 Structure

1. **Purpose** (3 lines): what a Weave block is and when to use one (§20.3).
2. **Syntax card**: statement kinds, call rules (leads, named args, children), operators,
   interpolation — about 25 lines.
3. **Tokens**: one line per token set.
4. **Components**: one signature line each, grouped, filtered by tier. Format:
   `Slider($num, min=0, max=100, step?, label?, format?, suffix?) — continuous value picker`.
5. **Actions and effects**: filtered by tier.
6. **Rules**: streaming order (§17.3), no raw dp/hex, mobile width, ≤ 1 block per reply unless
   asked, keep `!say` text explicit, prefer existing components to Canvas.
7. **Examples**: 2–3 short complete blocks for the tier, chosen to cover state, a chart and an
   action.

### 21.2 On-demand docs (tool-capable models)

Instead of all signatures, tier 3–4 models with tool use may get a compact **index** (names + one
line) and a `weave_docs(names: list)` tool returning full signatures and an example for each.
This keeps the base prompt small while giving access to the whole catalog.

### 21.3 Prompt tests

The generated prompt is snapshot-tested; a catalog change that alters it must update the snapshot
deliberately.

---

## 22. What the model sees back

The model must know what the user did, without blowing the context.

- Each assistant message containing blocks is sent back in history with its **source** (latest
  version) and a compact **state line**:
  `<weave-state id="loan" v="3">{"rate":9.1,"years":15,"principal":5000000}</weave-state>`
  Only state that differs from the declared initial value is included. Large values (> 400 chars)
  are summarised (`"cart": "list(12)"`).
- `!say(attach: […])` adds the named values to that user turn in the same format.
- `!ask` calls include the full current state of the block and a log of the last 5 `!ask`s.
- Lazy fragments are included only if they were opened.
- Older history: blocks more than 10 turns back are reduced to header + state line.

---

## 23. Persistence, versions, remix, library and widgets

### 23.1 Storage (Room)

```
weave_block(
  message_id, block_key,             -- block_key = fence offset from MarkdownStreamPlanner
  block_id,                          -- from header, unique per thread
  version, source, form,             -- form = source|json
  state_json, history_json,          -- §6.4, capped
  lazy_json,                         -- generated fragments by name + cache key
  consent_json,                      -- per-tool "always" grants
  created_at, updated_at
)
weave_version(block_row_id, version, source, cause, created_at)   -- cause = stream|edit|ask|remix|repair
weave_library(id, title, icon, source, state_json, pinned_widget, created_at)
```

State saves are debounced (500 ms) and flushed on background.

### 23.2 Versions

Every edit, `!ask` result, remix or repair is a version. The block's ⋯ menu shows a version strip;
tapping one previews it, "Restore" makes it current (as a new version).

### 23.3 Remix

Long-press → "Change this block…" opens a small composer. The request is sent with the block source
and state; the reply is an edit applied in place (no new chat turn unless the user expands it).

### 23.4 Share and export

- **Image:** render the block to a bitmap at 2× and share as PNG (with an optional "Made with
  EchoFlow" footer, off by default).
- **Source:** `.weave` file (source + state). EchoFlow registers an intent filter; opening a
  `.weave` file shows it as a block in a new chat. No backend involved.
- **Data:** `!export` for tables/lists as CSV/JSON.

### 23.5 Library ("Made for you")

"Keep" saves a block (source + state) to a library screen. Kept blocks run standalone in
full-screen mode with co-pilot editing (§16). `!ask`/`query` still work there.

### 23.6 Home-screen widgets (Glance)

A kept block can be pinned as a widget. Glance supports a subset; the widget renderer uses the same
IR with a **widget profile**:

- Allowed: `Column`, `Row`, `Box`, `Text`, `Title`, `Metric`, `Progress`, `Icon`, `Image`,
  `Button` (with `!set`, `!inc`, `!dec`, `!toggle`, or "open in EchoFlow"), `List` (≤ 10 items),
  `Sparkline` (pre-rendered bitmap).
- Effects: `every(≥ 15m)` via WorkManager; `query` refresh at the same cadence.
- Anything else renders as "Open in EchoFlow".

A water tracker, a countdown, a currency rate or a habit streak becomes a real widget in two taps.

---

## 24. Safety, consent and limits

### 24.1 Capability boundary

Block code can only: read/write its own state, read exported values of other blocks, evaluate
expressions, and emit actions from the fixed list. No network, files, intents, clipboard reads,
contacts, location or sensors except as listed (§6.6, §8.2).

### 24.2 Outbound is confirmed

Every §7.4 action shows a sheet with the exact payload ("Add to calendar: *Flight to Jaipur*, Fri 12
Dec 06:40–08:15") and Cancel / Confirm. `!say` content is visible before and after sending (§7.3).

### 24.3 Anti-phishing

- No secret-shaped inputs (§13.4). A `TextField` whose label matches password/OTP/PIN/CVV/card/
  account-number patterns renders disabled with "EchoFlow blocks can't ask for secrets".
- Blocks cannot imitate system UI: no full-screen overlays outside `!expand` (which always shows the
  block chrome and title), no fake permission dialogs (`Dialog` always shows the block's title).
- `Link`/`!url` show the real domain in the confirmation sheet.

### 24.4 Limits

| Limit | Value |
|---|---|
| Source size | 48 KB per block |
| Statements | 400 |
| Rendered nodes | 600 per block; 3,000 across visible blocks |
| Nesting depth | 24 |
| Expression evaluation | 20,000 steps per evaluation; recursion depth 64 |
| State size | 256 KB per block |
| List length rendered eagerly | 30 (then lazy, requires `h`) |
| Timers | min 250 ms; max 4 per block |
| `frame` effects | 1 per block; only 1 block simulating at a time (the most visible) |
| Images | 24 per block, 4 MB each |
| `!ask` | 1 in flight per block; 20/hour per block without confirmation |
| `query` | 4 per block; min interval 1 min (`every:`); consent required |
| JS | 50 ms / 16 MB per call |

### 24.5 Prompt injection

Tool results and web content are data (§10.4). A block's own text is model output and is treated as
such: nothing a block displays can trigger an action; only user gestures and declared effects can.
Effects cannot fire outbound (§7.4) actions — those require a user gesture.

### 24.6 Artifacts remain the web escape hatch

When a request needs real web technology (an existing JS library, a full HTML document), the model
should produce an EchoFlow artifact instead. Weave does not embed WebViews.

---

## 25. Accessibility

- Every catalog component carries correct semantics (roles, state descriptions, ranges for sliders,
  headings for `Title`/`Section`).
- `IconButton` requires `label`; images require `alt`; charts expose a generated summary ("Line
  chart, 20 points, interest falls from ₹4.1L to ₹12K") and a "View as table" action.
- Touch targets ≥ 48 dp regardless of visual size.
- Contrast is guaranteed by role pairs (§11.3).
- Font scale: token sizes scale with `fontScale` for text-bearing heights; `Row`s that overflow
  at large font scales wrap to `Column`s automatically (`Row(wrap: auto)` default).
- Reduced motion: springs become snaps, `loop` and `Shader` stop, `frame` simulations pause by
  default with a play button.
- Canvas content must declare `label:`; `Board` cells expose row/column semantics.

---

## 26. Kotlin architecture

### 26.1 Modules

```
:weave-core        pure Kotlin/JVM — no Android. Lexer, incremental parser, AST, auto-fix, checker,
                   IR, evaluator, reactive graph interfaces, Weave-JSON lowering, catalog model,
                   prompt + JSON Schema generator. 100% unit-testable on JVM.
:weave-compose     Android. Renderer, components, tokens, motion, charts, canvas, gestures,
                   overlays, consent UI, error boundaries.
:weave-runtime     Android. StateStore on Compose snapshots, ActionBus, ModelLoop (!ask, lazy,
                   repair), ToolBridge, Room entities/DAOs, persistence, Glance widget profile.
:weave-js          Optional. QuickJS binding for tier 4 `js fn`.
app                Registers WeaveBlockExtension under "weave"/"weave-json" in
                   LocalMarkdownBlockExtensions; wires ModelLoop to ChatRepository; settings UI.
```

### 26.2 Core types (sketch)

```kotlin
// :weave-core
sealed interface Stmt { val name: String?; val span: IntRange }
data class StateDecl(override val name: String, val type: WType?, val init: Expr, val transient: Boolean, val reset: Boolean, …) : Stmt
data class Binding(override val name: String, val expr: Expr, …) : Stmt
data class FnDecl(override val name: String, val params: List<String>, val body: Expr, val js: String? = null, …) : Stmt
data class Effect(val trigger: Trigger, val condition: Expr?, val action: Expr, …) : Stmt { override val name = null }
data class Delete(override val name: String, …) : Stmt

sealed interface Expr
data class Call(val callee: String, val kind: CallKind, val leads: List<Expr>, val named: Map<String, Expr>, val children: Expr?) : Expr
// CallKind = COMPONENT | FUNCTION | ACTION | MARK

class WeaveProgram(val header: Header, val statements: Map<String, Stmt>, val effects: List<Effect>, val diagnostics: List<Diagnostic>)

class IncrementalParser(private val catalog: Catalog) {
    /** Feed the whole growing body; returns frozen statements + speculative tail. */
    fun update(body: String, complete: Boolean): ParseResult
}

interface Catalog {
    fun component(name: String): ComponentSpec?
    fun tokens(set: TokenSet): List<String>
    fun forTier(tier: Int): Catalog
}
data class ComponentSpec(val name: String, val leads: List<Param>, val params: List<Param>, val children: ChildSpec, val tier: Int, val doc: String, val since: Int)
```

### 26.3 Reactive model on Compose snapshots

- Each state variable is a `MutableState<WValue>` inside a `WeaveStateStore`.
- Each binding is lazily wrapped in `derivedStateOf { evaluator.eval(expr, scope) }`. Because the
  evaluator reads state through snapshot-observable getters, dependency tracking is automatic and
  exact: moving `$rate` invalidates only bindings that read `$rate`, and only the composables reading
  those bindings recompose.
- Node bindings evaluate to an immutable `WNode` tree; `WeaveRenderer` maps `WNode` → composables
  via the catalog's renderer table with stable keys (statement name + path).
- `WValue` is a sealed, `@Immutable` hierarchy with structural equality, so unchanged derived values
  short-circuit recomposition.

### 26.4 Evaluator

A tree-walking interpreter with a per-call step budget, depth guard and a pure built-in table.
Hot paths (arithmetic, member access) avoid allocation by using primitive `WNum` caches. Lambdas
close over an immutable scope. No reflection.

### 26.5 Renderer

```kotlin
class WeaveBlockExtension(private val runtime: WeaveRuntime) : MarkdownBlockExtension {
    @Composable
    override fun Render(block: MarkdownExtensionBlock, modifier: Modifier) {
        val session = runtime.rememberSession(block)          // parser + store + Room row
        val program by session.program.collectAsState()        // updates per completed statement
        WeaveSurface(program, session, modifier)               // error boundaries, size mode, chrome
    }
}
```

- Each component composable reads only its own arguments' `State`s.
- Charts and Canvas draw in `Modifier.drawWithCache`; animated values use `Animatable` keyed by mark
  identity.
- Off-screen: blocks inside the chat `LazyColumn` stop timers/frames/queries via a visibility
  signal; heavy nodes (`Map`, `Video`, `Model3D`, `Shader`) are replaced by a cached snapshot
  bitmap when off-screen for > 5 s.

### 26.6 Model loop

`ModelLoop` builds hidden requests (§9.1, §9.2, §18.3) through the existing chat transport
(`ChatRepository` / stream transports) with a dedicated system prompt, parses the returned edit with
the same parser, and applies it as a version. Requests are cancellable and tagged so they never
appear as chat turns or affect conversation titles.

### 26.7 Dependencies

- Already present: Compose BOM, Material 3, Media3.
- New: kotlinx.serialization (if not already used), Maps Compose (tier 3+, behind the existing
  secrets plugin key), SceneView (tier 4, optional, dynamic feature or skipped), Lottie Compose,
  QuickJS binding (optional `:weave-js`).
- AGSL shaders need API 33; `minSdk` is 24, so `Shader` falls back to `Gradient` below 33.

---

## 27. Performance budgets

| Metric | Budget |
|---|---|
| Parse one statement | < 0.3 ms median, < 2 ms p99 on a mid-range device |
| First pixel of a block after its `root` line completes | ≤ 1 frame |
| Slider drag → dependent chart redraw | within the same frame (no dropped frames at 120 Hz on a Pixel 8-class device for ≤ 500 chart points) |
| Recomposition scope | only nodes reading changed values (verified with composition tracing in tests) |
| Idle block cost | zero recomposition, zero timers while off-screen |
| Memory per typical block | < 2 MB (excluding images/media) |
| Chat scroll with 20 blocks | no jank beyond the app's existing baseline |

---

## 28. Testing and evaluation

1. **Parser golden tests:** a corpus of ≥ 300 programs (hand-written + model-generated) with
   expected AST/diagnostics.
2. **Streaming equivalence:** every corpus program fed char-by-char and token-by-token; final
   program must equal the one-shot parse; skeleton heights must never shrink (no layout jumps).
3. **Fuzzing:** random mutations of corpus programs must never throw out of parser, checker,
   evaluator or renderer.
4. **Evaluator property tests:** determinism, budget enforcement, null-safety rules.
5. **Weave-JSON round trip:** source → IR → JSON → IR equality.
6. **Screenshots:** Roborazzi for every non-popup component in every palette × light/dark × font
   scale 1.0/1.5. Popups/sheets/dialogs verified on the emulator per CLAUDE.md.
7. **Interaction tests:** Compose UI tests drive sliders, forms, wizards, swipe decks, boards, and
   assert state and rendered output.
8. **Prompt snapshot tests** (§21.3).
9. **Model eval harness** (outside CI, run per release): 60 tasks × each curated model × each tier.
   Scores: parse success, dropped statements, render success, task completion (does the calculator
   compute correctly?), token cost, and a screenshot gallery for human review. Results update the
   curated tier table (§20.1).

---

## 29. Phasing

**Phase 1 — the engine (tier 1–2).**
`:weave-core` complete (parser, auto-fix, checker, evaluator, IR, JSON form, prompt generator).
Renderer with layout, text, numbers, all §13.4 inputs except `Sketch`/`Swipe`/`Rank`/`Sort`,
`Chart` with line/area/bar/point/rule/arc, `Table`, `List`, `Tabs`, `Wizard`, `Sheet`. Local
actions, `!say`, effects (`change`, `appear`, `every`). Persistence and versions. Tiers 1–2,
curated table for the top models. The **Intelligent UI** setting (§20.5) with all three levels, the
per-chat chip and "Make this interactive". Every level is capped at the highest tier shipped so far.

**Phase 2 — the model loop (tier 3).**
`!ask`, `lazy`, edits from later replies, remix, repair round, `query` + consent, device actions,
cross-block exports, full-screen and co-pilot mode, share as image / `.weave`, Library.

**Phase 3 — extreme (tier 4).**
`frame`, `tilt`, Canvas gestures, simulation, `Shader`, `Model3D`, `Map`, `Sketch`, `Swipe`,
`Rank`, `Sort`, learning/play components, `js fn`, Glance widgets, on-demand docs tool, UI model
setting, auto-downgrade.

Each phase ships behind a settings flag until its eval scores meet the bar.

---

## 30. Worked examples

### 30.1 Tip splitter (tier 2)

```weave
@weave 1 id: tip title: "Split the bill"
$bill = 2400
$pct = 10
$people = 4
perHead = ($bill + $bill * $pct / 100) / $people
root = Card(gap: m) [
  TextField($bill, label: "Bill", kind: number, prefix: "₹"),
  Segmented($pct, options: [0, 5, 10, 15, 20], suffix: "%"),
  Stepper($people, min: 1, max: 30, label: "People"),
  Metric("Each pays", perHead, format: inr, size: l, tone: primary),
  Button("Send to group", icon: "send", onTap: !say("Each of us pays {perHead | inr} ({$people} people, {$pct}% tip)"))
]
```

### 30.2 Quiz (tier 2)

```weave
@weave 1 id: quiz title: "Photosynthesis check"
$answers = []
root = Quiz($answers, questions: [
  {q: "Where does photosynthesis happen?", options: ["Mitochondria", "Chloroplast", "Nucleus"], answer: 1, why: "Chlorophyll lives in chloroplasts."},
  {q: "Which gas is released?", options: ["CO₂", "O₂", "N₂"], answer: 1},
  {q: "The main sugar produced is…", options: ["Glucose", "Sucrose", "Lactose"], answer: 0}
], onDone: s => [!confetti, !say("I scored {s}/3 — explain what I missed")])
```

### 30.3 Trip planner with in-place updates and lazy days (tier 3)

```weave
@weave 1 id: trip title: "3 days in Jaipur" size: card
$budget = 40000
$style = "relaxed"
$tab = 0
root = Column(gap: m) [controls, Tabs($tab, labels: ["Overview", "Day 1", "Day 2", "Day 3"]) [overview, d1, d2, d3]]
controls = Card(style: tonal, gap: s) [
  Slider($budget, min: 15000, max: 150000, step: 5000, format: inr, label: "Budget"),
  Segmented($style, options: ["relaxed", "packed", "foodie"])
]
overview = Column(gap: s) [
  Map("Jaipur", markers: [{lat: 26.9855, lng: 75.8513, title: "Amber Fort"}, {lat: 26.9239, lng: 75.8267, title: "Hawa Mahal"}], h: m),
  Stat [Metric("Est. spend", $budget * 0.86, format: inr), Metric("Walking", 18, suffix: " km")]
]
d1 = lazy("Day 1 timeline with costs", budget: $budget, style: $style)
d2 = lazy("Day 2 timeline with costs", budget: $budget, style: $style)
d3 = lazy("Day 3 timeline with costs", budget: $budget, style: $style)
on change($budget, debounce: 800ms) -> !ask("Rebalance the overview for the new budget", update: [overview], with: [$budget, $style])
```

### 30.4 Expense explorer with a selectable chart (tier 2)

```weave
@weave 1 id: spend title: "October spending"
$picked = null
tx = [
  {cat: "Food", amt: 8200}, {cat: "Rent", amt: 22000}, {cat: "Travel", amt: 5400},
  {cat: "Bills", amt: 3100}, {cat: "Fun", amt: 2600}
]
total = sum(map(tx, t => t.amt))
root = Card(gap: m) [
  Metric("Total", total, format: inr, size: l),
  Chart(tx, polar: true, h: l, select: $picked) [arc(value: "amt", label: "cat")],
  when($picked != null, Callout("{$picked.cat}: {$picked.amt | inr} — {round($picked.amt / total * 100)}% of the month", kind: info)),
  Button("How do I cut this?", variant: tonal, onTap: !say("Help me reduce my {$picked.cat ?? \"top\"} spending", attach: [tx]))
]
```

### 30.5 Pendulum lab (tier 4)

```weave
@weave 1 id: pend title: "Pendulum lab" size: card
$len = 1.5
$angle = 0.8
$vel = 0
$run = true
on frame when $run -> [
  !set($vel, ($vel - 9.81 / $len * sin($angle) * dt) * 0.999),
  !set($angle, $angle + $vel * dt)
]
bx = 50 + 40 * $len / 3 * sin($angle)
by = 8 + 40 * $len / 3 * cos($angle)
root = Card(gap: m) [
  Canvas(view: [0, 0, 100, 60], h: l, onTap: _ => !toggle($run), label: "Pendulum, tap to pause") [
    line(50, 8, bx, by, stroke: outline, width: s),
    circle(50, 8, 1.5, fill: muted),
    circle(bx, by, 5, fill: primary)
  ],
  Slider($len, min: 0.5, max: 3, step: 0.1, label: "Length", suffix: " m"),
  Metric("Period", 2 * pi * sqrt($len / 9.81), format: num(2), suffix: " s")
]
```

### 30.6 Kanban from a planning chat (tier 3)

```weave
@weave 1 id: week title: "This week" keep: true
$board = {Todo: [{t: "Draft deck"}, {t: "Call bank"}], Doing: [{t: "Tax forms"}], Done: []}
root = Column(gap: s) [
  Sort($board, columns: ["Todo", "Doing", "Done"], render: i => Item(i.t)),
  Row(gap: s) [
    Button("Plan my days", onTap: !ask("Turn the board into a day-by-day plan below", update: [plan], with: [$board])),
    IconButton("calendar_add_on", label: "Add to calendar", onTap: !calendar("Weekly review", addDays(today(), 6)))
  ],
  plan
]
plan = Empty("No plan yet", body: "Tap Plan my days", icon: "event_note")
export $board
```

---

## 31. Open questions

1. **Names.** Settled: the engine is **Echo UI**, the setting is **Intelligent UI**, and the levels
   are Off / Balanced / Immersive. Open: should the `weave` fence and language name stay as an
   internal codename, or become `echoui`?
2. **Default tier for flagship models** — start every top model at tier 2 and let evals promote to
   3/4, or trust tier 3 from day one?
3. **`!ask` cost UX** — show a per-block spend indicator, or only a monthly summary?
4. **Maps provider** — Google Maps (needs key, secrets plugin present) vs. an OSM-based renderer to
   stay keyless and consistent with "no account" identity.
5. **QuickJS** — ship in the main APK (~1 MB) or as an on-demand download?
6. **Library sync** — kept blocks are local-only today; export/import via `.weave` covers backup.
   Anything more needs a decision about EchoFlow's no-backend principle.
7. **Widget scope** — is the Glance subset worth Phase 3, or a later phase?
