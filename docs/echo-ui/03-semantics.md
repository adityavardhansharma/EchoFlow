# 03 — Semantics

**Spec version:** 1.2 · **Status:** normative

This document defines what a parsed Weave program **means**: the values it computes, how state
changes, what actions do, when effects run, and the exact behaviour of every built-in function and
format. Syntax is in [02-language.md](02-language.md).

**The two golden rules of evaluation:**

1. **Evaluation never throws.** Any failure produces `null` (and, where listed, a diagnostic). The
   UI never crashes because of a block.
2. **Evaluation is deterministic.** The same program, state and context values always produce the
   same result. The only inputs that change over time are `now()`, `today()`, `device.*`,
   `block.*`, query results and `dt`. All of them are explicit.

---

## 1. Values

### 1.1 The value model

Every Weave value is one of these. The Kotlin names are required (05 §3).

| Weave type | Kotlin class | Holds | Literal |
|---|---|---|---|
| `null` | `WNull` (object) | nothing | `null` |
| `bool` | `WBool(v: Boolean)` | true/false | `true` |
| `num` | `WNum(v: Double)` | IEEE double; never NaN or ±∞ (those become `null`) | `12`, `45%` |
| `str` | `WStr(v: String)` | Unicode text | `"hi"` |
| `dur` | `WDur(ms: Long)` | a length of time | `300ms` |
| `date` | `WDate(v: LocalDate)` | a calendar day | `date("2026-12-01")` |
| `time` | `WTime(v: LocalTime)` | a time of day, minute precision | `time("06:40")` |
| `datetime` | `WDateTime(v: Instant)` | a moment; shown in the device time zone | `now()` |
| `list` | `WList(items: List<WValue>)` | ordered, immutable | `[1, 2]` |
| `rec` | `WRec(fields: LinkedHashMap<String, WValue>)` | string keys, insertion-ordered, immutable | `{a: 1}` |
| `node` | `WNode(type, args, children, key)` | a UI element (04) | `Card [...]` |
| `action` | `WAction(kind, argExprs, scope)` | something to do later | `!set($x, 1)` |
| `fn` | `WFn(params, body, scope)` or `WBuiltin(name)` | a callable | `x => x * 2` |
| `sym` | `WSym(name: String)` | a bare word that isn't a defined name | `primary` |
| `image` | `WImage(uri: String, w: Int, h: Int)` | a picture (from `Sketch`, `image.generate`) | — |

All values are immutable and compare **structurally** (`equals`), except `WAction`, `WFn` and
`WNode`, which compare by identity of their source expression plus their evaluated arguments (05
§3.2). Every `WValue` class MUST be annotated `@Immutable`.

### 1.2 Numbers

- Arithmetic is IEEE double. Any operation producing NaN or ±∞ produces `null`. `[T-200]`
- Integers are just doubles with no fraction. "Is an integer" means `v == floor(v)` and
  `|v| < 2^53`.
- There is no separate int type.

### 1.3 Symbols

An identifier that resolves to nothing (§4) evaluates to `WSym(name)`. Symbols exist so token
values can be computed (`tone: $bad ? error : primary`). A symbol:

- in a token-typed slot → the token of that name (or `E206` if not a member of the set);
- in a `str` slot → the string of its name;
- anywhere else → behaves like the string of its name, and the checker emits `W407 unresolved`
  **unless** the identifier appears only inside a token-typed slot's expression (directly, or as a
  branch of `?:`, `??`, or an item of a list in that slot). `[T-201]`

---

## 2. Types and coercion

### 2.1 Where types are checked

Types are checked **when a value reaches a typed slot**: a component or action parameter (types
in 04), a built-in function parameter (§10), a typed state declaration, or an operator. Untyped
contexts (bindings, list items, record fields) accept anything.

### 2.2 Slot types used in the catalog

`any`, `num`, `str`, `bool`, `dur`, `date`, `time`, `datetime`, `list`, `list<T>`, `rec`, `node`,
`nodes` (a node or a list of nodes), `action`, `fn`, `image`, `token<Set>` (Set is one of the token
sets in 04 §1), `state` (a state place, §5.4), `color` (a colour-role token), `field` (a string
field name or a `fn`), and unions `A|B`.

### 2.3 Coercion table

When value of type **from** reaches a slot of type **to**. "—" means no coercion: `E205 bad-type`,
the argument is dropped (it becomes `null`, so the default applies).

| from \ to | num | str | bool | dur | date/time/datetime | list | node / nodes | token |
|---|---|---|---|---|---|---|---|---|
| num | ✓ | `auto` format (§11.1) | `v != 0` | — | — | `[v]` | `Text(auto)` | §8.2 S4 rule (spacing/height/shape/elevation only) |
| str | parse (§2.4); fail → — | ✓ | `v != ""` | parse duration (§2.5); fail → — | ISO parse (§10.9); fail → — | `[v]` | `Text(v)` | if `v` is a member |
| bool | `1`/`0` | `"true"`/`"false"` | ✓ | — | — | `[v]` | `Text("true"/"false")` | — |
| null | null | null | `false` | null | null | `[]` | nothing | default |
| dur | ms | `duration` format | `ms != 0` | ✓ | — | `[v]` | `Text(duration)` | — |
| date etc. | — | `date`/`time`/`datetime` format | `true` | — | ✓ (date→datetime at 00:00 local; datetime→date in local zone) | `[v]` | `Text(fmt)` | — |
| list | — | items joined with `", "` (each with `auto`) | `len > 0` | — | — | ✓ | flattened, each item coerced to node | — |
| rec | — | JSON text | `size > 0` | — | — | `[v]` | — | — |
| sym | — | name | — | — | — | `[v]` | `Text(name)` | ✓ if member |
| node | — | plain text of the node (04 §2.4) | `true` | — | — | `[v]` | ✓ | — |
| image | — | — | `true` | — | — | `[v]` | `Image` | — |

`null` in a slot means **use the parameter's default** (04). For a required parameter with no
default, `null` is `E204 missing-required`.

### 2.4 Parsing strings as numbers

Trim whitespace. Remove `,` and `_` grouping characters. Accept an optional leading `+`/`-`, digits
with one optional `.`, an optional exponent, and an optional trailing `%` (divides by 100). Anything
else fails. `"1,234.5"` → 1234.5; `"12%"` → 0.12; `"₹500"` fails. `[T-210]`

### 2.5 Parsing strings as durations

Same syntax as a `DURATION` literal (02 §2.6), optionally with spaces between number and unit, and
several parts summed: `"1h 30m"` → 5,400,000 ms. `[T-211]`

---

## 3. Truthiness, equality and ordering

### 3.1 Truthiness

Used by `and`, `or`, `not`, `?:`, `when(...)`, `show:`, `!if`, effect `when`, and `rule:`.

Falsy: `false`, `null`, `0`, `""`, `[]`, `{}`. Everything else is truthy (including `"0"`,
`"false"` and every node, action, function, symbol, date and duration — `0ms` is falsy). `[T-220]`

### 3.2 Equality (`==`, `!=`, `in`, `unique`, change detection)

- Same type: structural equality. Numbers compare exactly (`0.1 + 0.2 == 0.3` is `false`; use
  `round`). Lists compare item by item; records compare key sets and values (order ignored).
- `num` vs `str`: **not** equal, no coercion (`1 == "1"` is `false`).
- `sym` vs `str`: equal if the symbol's name equals the string.
- `null == null` is `true`; `null` equals nothing else.
- `date` vs `datetime`: not equal.

### 3.3 Ordering (`<`, `<=`, `>`, `>=`, `sortBy`, `minOf`, `maxOf`)

| Types | Order |
|---|---|
| num, num | numeric |
| str, str | by Unicode code point, then length (no locale collation) |
| dur, dur | by ms |
| date/time/datetime of the same type | chronological |
| bool, bool | `false < true` |
| anything else | operators return `false`; sorting puts the value after all comparable ones, in original order |

`null` with an operator: result `false`. In sorting, `null`s go **last** in both ascending and
descending order. `[T-221]`

---

## 4. Names and scope

### 4.1 Resolution order

An `IDENT` is looked up in this order; the first match wins:

1. Lambda parameters (innermost lambda first).
2. Function parameters of the enclosing `fn`.
3. Event variables of the enclosing effect or handler (`dt` in `frame`; see §8).
4. Bindings and functions of this block.
5. Built-in constants: `pi`, `e`, `device`, `block`, `user`.
6. Otherwise a symbol (§1.3).

A `$name` is looked up only among this block's state. An undeclared state name is `E301
missing-ref` and evaluates to `null`. A write to it (`!set($nope, 1)`) is a no-op with `E301`.

`id::name` and `id::$name` read an **exported** value of an earlier block in the same thread. Not
found, not exported, or a later block → `null` (no diagnostic; blocks may be deleted). These reads
are reactive. `[T-230]`

### 4.2 Closures

A lambda captures the scope it was created in. Functions declared with `fn` see only their
parameters, the block's bindings, functions and state, and the built-in constants. They do **not**
see lambda parameters of their call site.

### 4.3 Context constants

| Name | Value | Reactive |
|---|---|---|
| `pi` | 3.141592653589793 | no |
| `e` | 2.718281828459045 | no |
| `device.width` | `compact` (< 600 dp), `medium` (600–839), `expanded` (≥ 840), as `sym` | yes |
| `device.landscape` | bool | yes |
| `device.dark` | bool — the **app's** effective dark mode | yes |
| `device.locale` | str, BCP-47 (`en-IN`) | yes |
| `device.currency` | str, ISO 4217 of the locale's country, or `USD` if none | yes |
| `device.reducedMotion` | bool — system animator scale is 0 or "Remove animations" is on | yes |
| `device.fontScale` | num | yes |
| `block.expanded` | bool — the block is in full-screen mode | yes |
| `block.visible` | bool — at least 1 px of the block is on screen | yes |
| `block.id` | str | no |
| `block.version` | num | yes |
| `user.firstName` | str or `null` — only if set in EchoFlow settings | yes |

---

## 5. Evaluation

### 5.1 Laziness and memoisation

- A binding is evaluated only when something reads it (the renderer, an action, an effect, or
  another binding). It is memoised until one of the state values, context values, query values or
  other bindings it read changes. (In Compose terms: each binding is a `derivedStateOf`; 05 §4.)
- Functions are not memoised; each call evaluates the body.
- Arguments to user functions and built-ins are evaluated **eagerly, left to right**, except for
  the lazy built-ins listed in §10.1 (`if`-like functions) and the right side of `and`, `or`,
  `??` and the branches of `?:`, which are only evaluated when needed.

### 5.2 Cycles

If evaluating binding `a` requires evaluating `a` again (directly or through other bindings), every
binding in the cycle evaluates to `null` and `E302 cycle` is reported once, listing the names in
source order. Detection: each evaluation context keeps a set of bindings in progress. `[T-240]`

A state initial value that reads itself or forms a cycle with other initial values: all states in
the cycle start as `null` with `E302`.

### 5.3 Budget

Every **top-level evaluation** (one binding, one action argument, one effect condition, one state
initial value) gets a budget of **20,000 steps**. One step is charged for:

- each expression node visited;
- each item processed by a list built-in (`map` over 100 items costs 100 plus the lambda's steps);
- each character beyond the first 1,000 produced by string concatenation, divided by 100.

Recursion depth (user function calls, nested) is capped at **64**. Exceeding either gives `null`
for that whole top-level evaluation and `E303 budget`. `[T-241]`

### 5.4 Places (assignable state paths)

A **place** is a state reference optionally followed by members and indexes: `$form`,
`$form.email`, `$todos[i].done`, `$grid[r][c]`. Places appear as the lead of input components and
as the first argument of state actions (§7.2).

**Writing a place** produces a new value for the root state variable, copying only along the path:

- Member on a record: replaces that field (adds it if missing). Member on a non-record: the write
  is ignored (`E205`).
- Index on a list: replaces the item. Negative index counts from the end. Out of range: ignored
  (`E205`). Index `len` (one past the end) appends.
- Index on a record with a string: same as member.

**Reading a place** is ordinary member/index access (missing → `null`).

### 5.5 Member and index access

| Expression | Rule |
|---|---|
| `rec.k`, `rec["k"]` | field or `null` |
| `list[i]` | item; `i` rounded half-up; negative from end; out of range → `null` |
| `list.k` | **pluck**: `[item.k for each item]` (useful for charts: `rows.amount`) |
| `str[i]` | the code point at `i` as a string |
| `node.k` / `query.value` etc. | see 04 / §13 |
| anything on `null` | `null` |
| `x.len` | shorthand for `len(x)` on str, list, rec |

---

## 6. Operators

| Operator | Operands → result | Notes |
|---|---|---|
| `a + b` | num+num → num; str+any or any+str → str (other side `auto` formatted); list+list → concatenation; rec+rec → merge (right wins); date+dur → date (whole days of the duration added, remainder ignored); datetime+dur → datetime; dur+dur → dur | else `null` |
| `a - b` | num → num; date−dur → date; datetime−dur → datetime; date−date → dur (whole days); datetime−datetime → dur; dur−dur → dur | else `null` |
| `a * b` | num*num; dur*num or num*dur → dur (ms rounded half-up); str*num → repeat (num ≤ 1,000, else `null`) | else `null` |
| `a / b` | num/num (b = 0 → `null`); dur/num → dur; dur/dur → num | else `null` |
| `a % b` | num: **floored** modulo `a - b * floor(a / b)` (b = 0 → `null`). `-1 % 3` is `2` | else `null` |
| `-a` | num → num; dur → dur | else `null` |
| `==`, `!=` | any → bool | §3.2 |
| `<`, `<=`, `>`, `>=` | → bool | §3.3 |
| `a in b` | b list: any item `== a`; b rec: `a` (as string) is a key; b str: `a` (as string) is a substring; b is a range value: same as list | else `false` |
| `a .. b` | num..num → list of integers from `round(a)` to `round(b)` inclusive, stepping +1 or −1. More than 10,000 items → `null` with `E304` | |
| `a and b` | if `a` is falsy → `a`, else `b` (`b` not evaluated when `a` is falsy) | returns an operand, not a bool |
| `a or b` | if `a` is truthy → `a`, else `b` | `isEmail($e) or "Enter a valid email"` |
| `not a` | → bool | |
| `a ?? b` | `a` if not `null`, else `b` | `false ?? 1` is `false` |
| `c ? a : b` | `a` if `c` truthy, else `b` | |

`[T-250]`–`[T-262]`

---

## 7. State and actions

### 7.1 State lifecycle

1. **Creation.** Each state without a stored value is initialised by evaluating its initial
   expression **once**, at the first moment both of these hold:
   - its declaration is frozen (02 §10.1), or the program is complete (fence closed, or loaded);
   - every name its initial expression references — states, bindings, `fn`s — is declared by a
     frozen statement. Names reached only through those references count too (if `$a = f(1)` and
     `fn f(x) = x * rate`, then `rate` must also have arrived).

   At fence close, every state still waiting is initialised regardless; a name that is still
   missing then evaluates to `null` with its usual diagnostic (`E301` / `E202`). States that become
   ready together are evaluated in dependency order (a topological sort; ties in source order).
   Initial expressions may **not** read bindings that depend on a state that isn't initialised yet:
   such reads see `null`.

   Until a state is initialised it is **absent** from the `StateStore` (05 §4.2) and is called
   **waiting**. Reading a waiting state gives `null` and marks the read as **waiting**. The mark
   propagates: any expression, binding or function call whose evaluation read a waiting value is
   itself waiting (like `pending`, §9.2, but automatic). A node whose own arguments — not its
   children, which are separate nodes — evaluate as waiting renders its streaming placeholder
   (04 §2.9) instead of showing `—`. So with `$rate` waiting, `Slider($rate)`, `Text($rate)` and
   `Metric("EMI", emi)` (where `emi` reads `$rate`) all show placeholders, while their parent
   `Card` renders normally. **Effects** never see waiting values: they install only at fence close
   (§8.1), and by then no state is waiting. **Actions from gestures** can run while the reply
   streams (01 §17.4), so a static `Button(onTap: !set($out, $rate))` can be tapped before `$rate`
   is ready. Rule: when an event's transaction (§7.2) reads a waiting value — in an action
   argument, a condition or a handler — the whole transaction is **discarded**: no writes, no
   deferred actions, no history entry, no diagnostic. The control gives the `reject` haptic and
   works normally once the value arrives. Discarding (not delaying) keeps a tap from firing seconds
   later with a value the user never saw. `[T-279b]` This rule exists because the prompt cannot guarantee order: a model that writes
   `$rate = defaultRate(10)` before `fn defaultRate` MUST still get the computed value, never a
   permanent `null`. `[T-278]` `[T-278b]` `[T-279]`
2. **Restoration.** If the block has a stored state (05 §9), stored values are used for every state
   whose name exists, coerced to the declared type; failed coercion → re-initialise.
3. **Transient** states are always initialised fresh when the block is shown again (a new
   composition of the block), never restored.
4. **Edits** follow 02 §11.3.

### 7.2 Execution of actions

Actions run **only** in response to:

- a user gesture on a component (`onTap`, `onChange`, …, and input bindings);
- an effect firing (§8);
- the completion callback of an async action (e.g. `!tool(..., into:)`, `Quiz`'s `onDone`).

**Algorithm.** When an event fires with an action value `A` and event data `d`:

1. If `A` is a `fn` (a handler lambda like `p => !set($x, p.x)`), call it with `d` to get the
   action value. If the result is not an action or a list of actions, stop.
2. Flatten nested lists into a sequence `[a1, a2, …]`. `null` items are skipped.
3. Open a **transaction** (a mutable Compose snapshot).
4. For each `ai` in order: evaluate its arguments **now**, inside the transaction, so they see
   writes by earlier actions in the same sequence. Then perform it:
   - **local** actions (§7.3) apply to the transaction immediately;
   - **deferred** actions (all others) are appended to a queue with their evaluated arguments.
5. Commit the transaction (one recomposition, one history entry — §7.5).
6. Run the deferred queue in order, on the main thread. Each deferred action that needs
   confirmation or consent shows its UI; the next deferred action waits until it completes or is
   cancelled.

So `[!set($n, $n + 1), !set($n, $n + 1)]` adds **2**. `[T-270]`

An action whose arguments fail type checks does nothing and reports the diagnostic. The remaining
actions still run.

### 7.3 Local actions (instant; no model, no consent)

| Action | Parameters (leads first) | Effect |
|---|---|---|
| `!set(place, value)` | place: `state`; value: `any` | Write `value` to the place (§5.4), coerced to the declared state type if any. |
| `!inc(place, by: num = 1, min?: num, max?: num)` | | `place = clamp(place + by, min, max)`. Non-number place → treated as 0. |
| `!dec(place, by: num = 1, min?: num, max?: num)` | | `place = clamp(place - by, min, max)`. |
| `!toggle(place)` | | `place = not place`. |
| `!reset(place…)` | one or more state names (not paths) | Re-evaluate their initial expressions. |
| `!resetAll` | | Every state re-initialised (including transient). |
| `!push(place, item)` | | Append. Non-list place → becomes `[item]`. |
| `!insert(place, index: num, item)` | | Insert at index (clamped to 0..len). |
| `!remove(place, index: num)` | | Remove the item at index (negative from end). Out of range: no-op. |
| `!removeWhere(place, pred: fn)` | | Remove every item where `pred(item)` is truthy. |
| `!move(place, from: num, to: num)` | | Move an item. Indices clamped. |
| `!patch(place, fields: rec)` | | Merge fields into a record place. |
| `!undo` / `!redo` | | §7.5. |
| `!if(cond, then, else?)` | cond: `any`; then/else: `action` or list | Evaluates `cond` at execution time and runs the chosen branch **inline** (inside the same transaction; deferred items in the branch join the queue). |
| `!open(name)` | name: the **name** of a binding holding a `Sheet`/`Dialog`/`Popover`/`Menu` | Shows it. Opening one overlay closes any other overlay of the same block. |
| `!close(name?)` | | Closes that overlay, or all of the block's overlays. |
| `!go(name, index: num)` | name of a `Tabs`/`Pager`/`Wizard` binding | Sets its index (writes the bound state). |
| `!focus(name)` | name of a `TextField` binding | Requests focus. |

`!open`, `!close`, `!go`, `!focus` take a **binding name** as their lead. It is written as a bare
identifier: `!open(details)`. The checker resolves it as a name, not a value.

### 7.4 Deferred actions

| Action | Parameters | Behaviour |
|---|---|---|
| `!after(delay: dur, action)` | | Waits, then runs `action` as a new event (own transaction). Cancelled if the block leaves composition or its version changes. Max delay 10 minutes. |
| `!haptic(kind: token<haptic> = click)` | `tick \| click \| confirm \| reject \| heavy` | `HapticFeedbackConstants`: `tick`=CLOCK_TICK, `click`=VIRTUAL_KEY, `confirm`=CONFIRM (API 30+, else VIRTUAL_KEY), `reject`=REJECT (API 30+, else LONG_PRESS), `heavy`=LONG_PRESS. Skipped if the system disables haptics. |
| `!toast(text: str)` | | Shows a snackbar in the chat screen's snackbar host (max 120 chars, truncated). |
| `!copy(value: str \| node)` | | Clipboard (node → plain text, 04 §2.4). Shows the system "Copied" UI on API 33+, else a snackbar "Copied". |
| `!confetti` | | 1.2 s particle burst over the block bounds using `primary`, `secondary`, `tertiary`. Skipped when reduced motion is on. |
| `!scrollTo(name)` | | Scrolls the chat list so the named node is fully visible. |
| `!expand` / `!collapse` | | Full-screen mode on/off (05 §7). |
| `!say(text: str, attach?: list)` | | §9.1. |
| `!ask(prompt: str, update: list<name>, with?: list)` | | §9.2. |
| `!refresh(name)` | name of a `lazy` binding | §9.3. |
| `!regen` | | §9.4. |
| `!branch(text: str)` | | Creates a branch at this message with `text` as the user turn. |
| `!url(href: str)` | | Confirm sheet → open in browser. Only `https:`; anything else: action does nothing, `E205`. |
| `!share(value: str \| node \| image)` | | Confirm sheet → system share sheet. Node → PNG (05 §9.5). |
| `!export(value, as: token<export>)` | `png \| pdf \| csv \| json \| weave` | Confirm → system "create document" picker. `csv` needs a list of records (columns = union of keys in first-seen order); `json` any value; `png`/`pdf` a node; `weave` the whole block. |
| `!calendar(title: str, start: date\|datetime, end?, notes?: str, location?: str)` | | Confirm → `Intent.ACTION_INSERT` on `CalendarContract.Events`. A `date` start makes an all-day event. |
| `!remind(text: str, at: datetime)` | | Confirm → schedules a local notification via the app's notification channel "Reminders from blocks". Needs `POST_NOTIFICATIONS` (asks once). `at` in the past → `E205`. |
| `!dial(number: str)` | | Confirm → `ACTION_DIAL` (never `ACTION_CALL`). |
| `!sms(number: str, body?: str)` | | Confirm → `ACTION_SENDTO smsto:`. Never sends. |
| `!email(to: str, subject?: str, body?: str)` | | Confirm → `ACTION_SENDTO mailto:`. Never sends. |
| `!map(query: str \| rec)` | rec = `{lat, lng}` | Confirm → `geo:` intent. |
| `!directions(to: str \| rec)` | | Confirm → Google Maps directions URL intent, falling back to `geo:`. |
| `!tool(name: str, args: rec, into: state)` | | §13.2. |

**Effects can never run actions that need confirmation** (`!url` through `!tool` in the table, and
`!say`, `!branch`). If an effect's action list contains one, that item is skipped with
`E209`-style log `outbound action from effect` and the rest run. `!ask` and `!refresh` **are**
allowed from effects (they are cost-guarded, 05 §8.4). `[T-275]`

### 7.5 History (undo/redo)

- Each committed transaction that changed at least one state is one history entry, **except**:
  transactions from `frame` effects (never recorded) and transactions from a continuous input
  gesture (slider drag, range drag, `Pad2D` drag, canvas drag), which are merged into one entry
  per gesture: the entry is opened at gesture start and closed at gesture end.
- An entry stores the full state map before and after. Max 50 entries per block (oldest dropped).
- `!undo` restores the "before" of the latest entry and moves it to the redo stack. `!redo`
  re-applies. Any new entry clears the redo stack.
- Undo/redo themselves are not recorded as entries. History is persisted with the block (05 §9).

### 7.6 State types on write

If a state has a declared type, every write is coerced (§2.3). A write that can't be coerced is
ignored with `E205`. Untyped states accept any value.

---

## 8. Effects

### 8.1 When effects are active

Effects are installed when the fence **closes** (never during streaming) and the block is in
composition. They are removed when the block leaves composition or a new version replaces them.

### 8.2 Triggers

| Trigger | Parameters | Fires |
|---|---|---|
| `change(x)` | `x`: a state, binding or `id::name`; `debounce?: dur` | After a committed transaction (or query update) in which `x`'s value changed (§3.2 equality). Not on first install. With `debounce`, fires once after `x` has stayed unchanged for that long; the action sees the latest values. |
| `appear` | — | Once per block **lifetime** (persisted: a block that has fired `appear` never fires it again, even after restart), on the first frame the block is visible. |
| `visible` | — | Each time `block.visible` goes false → true. |
| `hidden` | — | Each time `block.visible` goes true → false. |
| `every(interval: dur)` | min `250ms` (smaller values are raised to 250 ms with `W402`) | Repeatedly while `block.visible` and the app is in the foreground. The first fire is one interval after becoming visible. Missed fires are not caught up. |
| `at(moment: date \| datetime)` | | Once, the first time the block is visible at or after `moment`. Persisted like `appear`. |
| `frame` | — | Every display frame while visible and foreground. Tier 4. Provides `dt`. |
| `tilt` | — | Accelerometer, at most 30 Hz, while visible and foreground. Tier 4. Provides `x`, `y` in −1…1 (device tilt left/right, forward/back; gravity / 9.81, clamped). |
| `shake` | — | When acceleration magnitude minus gravity exceeds 12 m/s² twice within 500 ms; then at most once per second. Tier 4. |

Unknown trigger → `E203` (statement dropped).

### 8.3 Conditions

`on trigger when cond -> action`: `cond` is evaluated at fire time (after debounce); the action runs
only if it is truthy.

### 8.4 Event variables

Inside the effect's `when` and action expressions:

| Trigger | Variables |
|---|---|
| `frame` | `dt` — seconds since the previous frame, as `num`, clamped to `[0, 0.05]` |
| `tilt` | `x`, `y` |
| `change(x)` | `prev` — the value before the change |

Handlers on components receive event data as their lambda parameter instead (04 lists each
event's data).

### 8.5 Loops

An effect's action may change state that triggers another `change` effect. Chains are allowed up to
**8** effect firings caused (directly or indirectly) by one original event. An effect produced by
merging same-trigger effects (02 §5.5, S12) is one firing, whatever its branch count. The 9th is not run and
`E306 effect-loop` is reported once per block version. `frame` effects do not count toward chains
they start (their writes trigger `change` effects normally, but each frame starts a fresh count).
`[T-280]`

### 8.6 Frame effects in detail

- At most **one** block in the whole app runs `frame` effects at a time: the visible block with
  the largest visible area (ties: the lowest on screen). Others are paused.
- All writes in one frame's action list are one transaction.
- If the action evaluation for a frame takes more than 2 ms three frames in a row, the effect is
  paused and the block shows a "Paused — tap to resume" scrim over its `Canvas`es (or over the
  whole block if there is none). Tapping resumes and resets the counter.
- Reduced motion: frame effects start paused with a play button.

---

## 9. The model loop (semantics)

The protocol (request and response formats) is in 05 §8 and the prompts in 06 §4–5. This section
defines what the user and the block observe.

### 9.1 `!say(text, attach?)`

1. `text` is evaluated (interpolations resolved). Empty after trim → no-op.
2. A user message is added to the thread with content `text` and a source chip
   `from <block title or "block">`. If `attach` is given, each attached value is serialised as
   described in 05 §8.6 and included with the message for the model only (not shown in the bubble;
   the chip shows "+ data").
3. The message is sent exactly as if the user had typed it (same model, same settings).
4. A component whose handler contains `!say` shows the text it would send in its long-press tooltip.

### 9.2 `!ask(prompt, update, with?)`

- `update` is a list of **binding names** (bare identifiers, resolved as names). It is the set of
  statements the model may replace. It MUST NOT be empty; empty → no-op with `E205`.
- While the request is in flight, `pending(name)` is `true` for each name in `update`, and the
  renderer draws the built-in pending overlay on those nodes (04 §2.6).
- **Latest wins:** if `!ask` is called again for the same block while one is in flight, the earlier
  request is cancelled (its result is discarded even if it arrives).
- On success, the returned statements are applied as an edit (02 §11) restricted to: names in
  `update`, plus **new** names (helpers) that don't exist in the block. Any other statement in the
  response is discarded with a log line. State declarations in the response are discarded unless
  their name is new.
- On failure (network, model error, unparseable response), the nodes return to their previous
  content, `pending` becomes `false`, and a small inline error chip with "Retry" appears on the first
  name in `update` for 6 s.

### 9.3 `lazy(prompt, …context)`

`lazy` is a built-in function that returns a **lazy node**.

- Arguments: `prompt: str`, then any **named** arguments, which form the context.
- **Cache key** = canonical print of the prompt string value + the context values (05 §8.5).
- When a lazy node is **rendered** for the first time with a given cache key and no cached result,
  a hidden request is made. While it runs, the node shows a skeleton sized `h: m`.
- The result is a Weave fragment whose `root` replaces the lazy node. Fragment statements live in
  a private namespace (prefixed internally with the lazy binding's name), so they can't collide with
  the block's names; fragment state is **not** allowed (dropped with log).
- If the context values change, the cached result stays visible with an "Update" chip in its top-end
  corner. Tapping it regenerates with the new key. Nothing regenerates automatically.
- `!refresh(name)` regenerates with the current key.
- Results are persisted (05 §9).

### 9.4 `!regen`

Asks the model to rewrite the whole block with the same intent. The response is a full block. It
becomes version `n+1`; state follows edit rules (02 §11.3) as if every state were re-declared
without `reset`.

---

## 10. Built-in functions

All built-in parameters are positional, except those written `name: type = default`, which may be
given positionally (in order) or by name. Every function returns `null` on wrong argument types
(with `E205`) unless stated otherwise. "list<num>" accepts any list; non-number items are skipped.

### 10.1 Control

| Function | Returns | Notes |
|---|---|---|
| `if(cond, a, b = null)` | `a` if cond truthy else `b` | **Lazy:** only the chosen branch is evaluated. (Also produced by auto-fix S6.) |
| `when(cond, value)` | `value` or `null` | Lazy. Typical: `when($x > 0, Callout(...))`. |
| `switch(value, cases: rec, default = null)` | `cases[str(value)]` or `default` | Lazy in `cases` values. |
| `coalesce(a, b, …)` | first non-null | Lazy left to right. |

### 10.2 Math

| Function | Returns |
|---|---|
| `abs(x)` `sign(x)` `sqrt(x)` (x<0 → null) `exp(x)` | num |
| `log(x)` natural; `log(x, base)`; `log10(x)` (x ≤ 0 → null) | num |
| `pow(x, y)` | num (null if not finite) |
| `floor(x)` `ceil(x)` | num |
| `round(x, digits = 0)` | num. **Half away from zero**: `round(2.5) = 3`, `round(-2.5) = -3`. Negative digits round to tens etc. |
| `trunc(x)` | toward zero |
| `min(a, b, …)` `max(a, b, …)` | num. Ignores non-numbers. No numbers → null |
| `clamp(x, lo, hi)` | num. If `lo > hi`, they are swapped |
| `lerp(a, b, t)` | `a + (b - a) * t` (t not clamped) |
| `remap(x, inLo, inHi, outLo, outHi)` | linear map; `inLo == inHi` → `outLo` |
| `sin cos tan asin acos atan` (radians) `atan2(y, x)` | num |
| `deg(rad)` `rad(deg)` | num |
| `hypot(a, b)` | num |
| `isNumber(x)` | bool: is `num` |
| `rand(seed, i = 0)` | num in [0, 1). See §10.10 |
| `randInt(seed, i, lo, hi)` | integer in [lo, hi] inclusive |

### 10.3 Lists

`fn` arguments may take `(item)` or `(item, index)`; the index is passed only if the lambda declares
two parameters. `field` arguments are a field name string or a `fn`.

| Function | Returns | Notes |
|---|---|---|
| `len(x)` | num | str (code points), list, rec (keys); else 0 |
| `range(n)` / `range(a, b, step = 1)` | list | `range(4)` = `[0,1,2,3]`; end exclusive; step 0 → `[]`; > 10,000 items → null (`E304`) |
| `map(list, fn)` | list | |
| `each(list, fn)` | list | Same as `map`; reads better for nodes |
| `filter(list, fn)` | list | keep truthy |
| `reject(list, fn)` | list | keep falsy |
| `find(list, fn)` | item or null | first truthy |
| `findIndex(list, fn)` | num | −1 if none |
| `indexOf(list, value)` | num | equality §3.2; −1 if none |
| `any(list, fn = identity)` `all(list, fn = identity)` | bool | `all([])` is `true` |
| `count(list, fn = identity)` | num | truthy count |
| `reduce(list, init, fn)` | any | `fn(acc, item, index)` |
| `sum(list, field = null)` | num | 0 for empty |
| `avg(list, field = null)` | num | null for empty |
| `median(list, field = null)` | num | mean of the two middle values for even length |
| `minOf(list, field = null)` `maxOf(list, field = null)` | the item's value (num/str/date…) | ordering §3.3; null if empty |
| `minBy(list, field)` `maxBy(list, field)` | the **item** | first on ties |
| `sortBy(list, field = identity, desc: bool = false)` | list | **stable**; nulls last |
| `sort(list, desc: bool = false)` | list | `sortBy(list, identity)` |
| `groupBy(list, field)` | rec | keys = `str(key)` in first-seen order; values = lists |
| `countBy(list, field)` | rec | key → count |
| `unique(list, field = identity)` | list | keeps the first of each key |
| `take(list, n)` `drop(list, n)` | list | n < 0 counts from end (`take(l, -2)` = last two) |
| `first(list)` `last(list)` | item or null | |
| `reverse(list)` | list | also reverses strings |
| `flatten(list)` | list | one level |
| `zip(a, b)` | list of `[a_i, b_i]` | length = shorter |
| `chunk(list, n)` | list of lists | n ≤ 0 → null |
| `window(list, n)` | list of lists | sliding, length `len - n + 1` |
| `cumsum(list, field = null)` | list<num> | running total |
| `pluck(list, key)` | list | same as `list.key` |
| `shuffle(list, seed)` | list | Fisher–Yates using `rand(seed, i)` for i = 0,1,2… |
| `contains(list, value)` | bool | same as `in`. `contains` is one function: with a list first argument it tests membership, with a string first argument it tests for a substring (§10.5) |

### 10.4 Records

| Function | Returns |
|---|---|
| `keys(rec)` `values(rec)` | list (insertion order) |
| `entries(rec)` | list of `{key, value}` |
| `fromEntries(list)` | rec from `{key, value}` records or `[k, v]` pairs |
| `get(rec, key, default = null)` | value |
| `has(rec, key)` | bool |
| `merge(a, b, …)` | rec, later wins |
| `pick(rec, keys: list)` `omit(rec, keys: list)` | rec |
| `set(rec, key, value)` | new rec (pure; not a state write) |

### 10.5 Strings

| Function | Returns | Notes |
|---|---|---|
| `str(x)` | str | `auto` format |
| `fmt(x, format)` | str | `format` as a string, e.g. `fmt(x, "num(2)")` (§11) |
| `upper` `lower` `trim` | str | `upper`/`lower` use the device locale |
| `titleCase(s)` | str | first letter of each space-separated word upper-cased |
| `split(s, sep)` | list<str> | `sep == ""` splits into code points |
| `join(list, sep = ", ")` | str | items `auto` formatted |
| `replace(s, find, with)` | str | all occurrences, literal (no regex) |
| `contains(s, part)` `startsWith(s, p)` `endsWith(s, p)` | bool | case-sensitive |
| `slice(s, start, end = len)` | str | code points; negative from end |
| `padStart(s, n, ch = " ")` `padEnd(s, n, ch = " ")` | str | |
| `repeat(s, n)` | str | n ≤ 1,000 |
| `words(s)` | list<str> | split on runs of whitespace |
| `isEmail(s)` | bool | `^[^\s@]+@[^\s@]+\.[^\s@]{2,}$` |
| `isPhone(s)` | bool | after removing spaces, `-`, `(`, `)`: `^\+?[0-9]{7,15}$` |
| `isUrl(s)` | bool | starts with `https://` or `http://` and has a host with a dot |
| `plural(n, one, many)` | str | `n == 1` → one, else many |

### 10.6 Dates and times

| Function | Returns | Notes |
|---|---|---|
| `now()` | datetime | **Minute-precise and reactive per minute** (it changes value once per minute, on the minute). For seconds, use an `every` effect. |
| `today()` | date | reactive at local midnight |
| `date(s)` | date or datetime | ISO `YYYY-MM-DD` → date; `YYYY-MM-DDTHH:MM[:SS]` → datetime in the device zone; with `Z` or offset → that instant. Invalid → null |
| `time(s)` | time | `HH:MM` or `H:MM` (24 h) or `h:MM am/pm` |
| `datetime(d, t)` | datetime | date + time in the device zone |
| `addDays(d, n)` `addMonths(d, n)` `addYears(d, n)` | same type | month-end clamped (`Jan 31 + 1 month = Feb 28/29`) |
| `addMinutes(dt, n)` `addHours(dt, n)` | datetime | |
| `diffDays(a, b)` | num | whole days `b - a` (dates; datetimes converted to local dates) |
| `diffMinutes(a, b)` | num | |
| `weekday(d)` | num | 1 = Monday … 7 = Sunday (ISO) |
| `day(d)` `month(d)` `year(d)` `hour(dt)` `minute(dt)` | num | |
| `startOfWeek(d)` | date | Monday on or before `d` (ISO) |
| `startOfMonth(d)` `endOfMonth(d)` | date | |
| `daysIn(d)` | num | days in that month |
| `dur(ms)` / `seconds(n)` / `minutes(n)` / `hours(n)` / `days(n)` | dur | |
| `ms(d)` `secondsOf(d)` `minutesOf(d)` | num | from a dur |

### 10.7 Finance

All rates are **fractions per period** unless stated. To use a percentage, divide by 100 or use a
percent literal (`8.5%`).

| Function | Returns | Definition |
|---|---|---|
| `pmt(rate, n, pv)` | num (positive) | `rate == 0 → pv / n`; else `pv * rate / (1 - (1 + rate)^-n)` |
| `fv(rate, n, payment, pv = 0)` | num | `rate == 0 → pv + payment * n`; else `pv * (1+rate)^n + payment * ((1+rate)^n - 1) / rate` |
| `npv(rate, flows)` | num | `Σ flows[i] / (1 + rate)^i`, **i from 0** (first flow is today) |
| `irr(flows)` | num or null | Rate where `npv == 0`. Newton–Raphson from 0.1, max 50 iterations, tolerance 1e-7; if it fails, bisection on [−0.9999, 10] for max 200 iterations; if no sign change, null |
| `compound(p, rate, years, perYear = 1)` | num | `p * (1 + rate / perYear)^(perYear * years)` — `rate` is the **annual** fraction |
| `emi(principal, annualRate, years)` | num | `pmt(annualRate / 12, years * 12, principal)` |
| `amortize(principal, annualRate, years)` | list<rec> | One row per year `y = 1…ceil(years)`: `{year, principal, interest, balance}` where `principal`/`interest` are the amounts **paid during that year** and `balance` the balance at year end. Monthly schedule with `emi(...)`; the last payment is reduced so the balance reaches exactly 0. Balance values are rounded to 2 decimals per month. |
| `percentOf(part, whole)` | num | `part / whole` (fraction); `whole == 0 → null` |
| `pctChange(from, to)` | num | `(to - from) / abs(from)` (fraction); `from == 0 → null` |

### 10.8 Units and health

| Function | Returns | Notes |
|---|---|---|
| `convert(value, from, to)` | num or null | Units below. Different categories → null. |
| `bmi(weightKg, heightCm)` | num | `weightKg / (heightCm/100)^2` |
| `bmr(weightKg, heightCm, age, sex)` | num | Mifflin–St Jeor; sex `"m"` or `"f"` |

`convert` units (case-sensitive):

| Category | Units (base first) |
|---|---|
| length | `m`, `km`, `cm`, `mm`, `mi`, `yd`, `ft`, `in` |
| mass | `kg`, `g`, `mg`, `lb`, `oz`, `t` |
| volume | `l`, `ml`, `gal` (US), `qt`, `cup` (US, 236.588 ml), `tbsp`, `tsp`, `floz` |
| temperature | `C`, `F`, `K` |
| speed | `mps`, `kmh`, `mph`, `kn` |
| data | `B`, `KB` (1000), `MB`, `GB`, `TB`, `KiB` (1024), `MiB`, `GiB` |
| time | `ms`, `s`, `min`, `h`, `day`, `week` |
| area | `m2`, `km2`, `ft2`, `acre`, `ha` |

Conversion factors MUST be the exact SI definitions (e.g. `in` = 0.0254 m, `lb` = 0.45359237 kg).

### 10.9 Node helpers

| Function | Returns | Notes |
|---|---|---|
| `lazy(prompt, …named context)` | node | §9.3. Tier 3. |
| `pending(name)` | bool | name of a binding in an `!ask` `update` list in flight |
| `loading(q)` `failed(q)` | bool | for a query value (§13) |
| `nodeText(node)` | str | plain text of a node (04 §2.4) |
| `aspect(r)` | height token | `aspect(16/9)` — height = width ÷ r (04 §1.2) |
| `weight(n)` | width token | share of a `Row`'s remaining width (04 §1.3) |
| `col(header, value, format?, align?, w?)` | rec | a `Table` column description (04 §8) |
| `axis(title?, format?, min?, max?, ticks?, grid = true, log = false, hidden = false)` | rec | a `Chart` axis description (04 §12) |

### 10.10 Deterministic randomness

```
seedBits(seed) = if seed is num: Double.toRawBits(seed) else FNV-1a-64(UTF-8 bytes of str(seed))
splitmix64(z):
    z = z + 0x9E3779B97F4A7C15
    z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9
    z = (z xor (z ushr 27)) * 0x94D049BB133111EB
    return z xor (z ushr 31)
rand(seed, i)        = (splitmix64(seedBits(seed) + i * 0x9E3779B97F4A7C15) ushr 11) * 2^-53
randInt(seed,i,lo,hi)= lo + floor(rand(seed, i) * (hi - lo + 1))
```

All arithmetic is unsigned 64-bit wrapping. `i` is rounded to an integer. `[T-290]`

### 10.11 Motion helpers

`spring`, `tween`, `loop` and `stagger` are built-in functions defined in 04 §13.1. Outside a
rendering slot they return their target value, so they never change the result of a calculation.

---

## 11. Formats

Formats are used in interpolation (`{x | inr}`), `fmt(x, "inr")`, and `format:` parameters. A
format applied to a value of the wrong type falls back to `auto` without a diagnostic. All number
formatting uses `android.icu.text.NumberFormat` / `DecimalFormat` with the device locale unless
stated. Rounding is **half-up** (`RoundingMode.HALF_UP`) everywhere.

### 11.1 `auto` (the default)

| Value | Output |
|---|---|
| null | `—` (em dash) |
| bool | `true` / `false` |
| num, integer, \|x\| < 1e15 | `int` format |
| other num | up to 2 decimals, trailing zeros removed, locale grouping (`1,234.5`) |
| str / sym | as is |
| dur | `duration` |
| date / time / datetime | `date(medium)` / `time` / `datetime` |
| list | items `auto`, joined with `, ` |
| rec | compact JSON |
| node | its plain text |

### 11.2 Number formats

| Format | Input → output (locale en-IN unless noted) |
|---|---|
| `int` | 1234567.8 → `12,34,568` (en-IN grouping); en-US → `1,234,568` |
| `num(d)` | `num(2)`: 3.14159 → `3.14`; 3 → `3.00` (always `d` decimals) |
| `dec(d)` | like `num(d)` but trailing zeros removed: 3.10 → `3.1` |
| `compact` | Indian locales (country `IN`): ≥ 1e7 → `1.2 Cr`; ≥ 1e5 → `3.4 L`; ≥ 1e3 → `5.6K`. Others: K, M, B, T at 1e3, 1e6, 1e9, 1e12. One decimal, `.0` removed. Below 1000: `int`. Negative keeps the sign. |
| `percent` / `percent(d)` | value is a **fraction**: 0.085 → `8.5%`. Default: up to 1 decimal, trailing zero removed |
| `pct` / `pct(d)` | value is **already in percent**: 8.5 → `8.5%` |
| `currency(CODE)` | ICU currency format for `CODE`. Decimals: 0 if \|x\| ≥ 1000 or x is an integer; otherwise the currency's default (usually 2). 43391.2 INR → `₹43,391`; 12.5 USD → `$12.50` |
| `inr` | `currency(INR)`, **always** with en-IN grouping |
| `usd`, `eur`, `gbp`, `jpy` | `currency(USD)` etc. in the device locale |
| `money` | `currency(device.currency)` |
| `inrCompact` | `₹` + `compact` with Indian rules: 2,50,00,000 → `₹2.5 Cr` |
| `moneyCompact` | currency symbol + `compact` |
| `signed` | adds `+` to positive numbers: `+12`, `−3` (uses U+2212 minus) |
| `ordinal` | ICU `RuleBasedNumberFormat.ORDINAL`: 3 → `3rd` |
| `bytes` | binary-free SI: 1536 → `1.5 KB` |

### 11.3 Text formats

`upper`, `lower`, `title` (as `titleCase`), `trim`, `truncate(n)` (code points; adds `…`),
`plural(one, many)` (applied to a number: `{n | plural(item, items)}` → `item` or `items` — the
number itself is not printed).

### 11.4 Date and time formats

| Format | Output (en-IN) |
|---|---|
| `date` = `date(medium)` | `1 Dec 2026` (`DateTimeFormatter.ofLocalizedDate(MEDIUM)`) |
| `date(short)` | `01/12/26` |
| `date(long)` | `1 December 2026` |
| `date(full)` | `Tuesday, 1 December 2026` |
| `weekday` | `Tue` (`EEE`); `weekday(long)` → `Tuesday` |
| `monthYear` | `Dec 2026` |
| `time` | `6:40 am` (localised, short) |
| `datetime` | `1 Dec 2026, 6:40 am` |
| `relative` | `android.icu.text.RelativeDateTimeFormatter`: < 60 s → `now`; < 60 min → `in 5 min` / `5 min ago`; < 24 h → hours; < 7 days → days; < 5 weeks → weeks; else months, then years. Style SHORT. |
| `duration` | dur or num of ms: `2h 15m`, `45s`, `1d 3h`. Largest two non-zero units among d, h, m, s; ms only if < 1 s (`350ms`) |
| `clock` | dur as `mm:ss`, or `h:mm:ss` if ≥ 1 hour |

### 11.5 Chaining

`{x | num(1) | upper}` applies each format to the previous output; a number format applied to a
string falls back to `auto` (no-op).

---

## 12. Rendering values as text

Wherever a value is shown as text without an explicit format (a `Text` with a number, a list item,
an interpolation), `auto` (§11.1) is used.

---

## 13. Data: `query` and `!tool`

### 13.1 `query(tool, args, every?: dur)`

- Tier 3. Max 4 queries per block (`E304` beyond).
- Returns a **query value**, a record: `{value, loading: bool, error: str|null, at: datetime|null}`.
  `value` is `null` until the first success.
- Runs: when the block first becomes visible **and** consent is granted (05 §8.7); again when `args`
  changes (structural equality), debounced 400 ms; and every `every` (min 1 minute) while visible.
- While re-running, `loading` is `true` and `value` keeps the previous result.
- On error, `error` is a short message and `value` keeps the previous result.
- Results are persisted with the block and restored (so a block shows its last data immediately on
  reopen, then refreshes).

### 13.2 `!tool(name, args, into)`

Runs once, after consent, as a deferred action. Writes the query-shaped record
`{value, loading, error, at}` into the `into` state place: first with `loading: true`, then the
result. Max 1 in flight per `into` place; a new call cancels the old one.

### 13.3 Tool names

Only tools registered as block-safe exist (05 §8.7). Unknown name → the query value has
`error: "Unknown tool"` and does nothing. Tool results are treated as data: strings in results are
never parsed as Weave.
