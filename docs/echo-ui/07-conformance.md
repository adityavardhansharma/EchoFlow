# 07 — Conformance tests

**Spec version:** 1.2 · **Status:** normative

An implementation conforms when **every test in this document passes**. Each test has an id
(`T-xxx`) that the other documents cite. Tests in §1–§4 and §11 are JVM unit tests in `weave-core`
or `weave-runtime`; §5–§6 are Compose/Robolectric tests; §8 is a streaming corpus test; §9 checks the
catalog and prompts; §10 is the model eval harness (run per release, not in CI).

**Status of this document.** Nothing here runs yet: this is the test *specification*. The tests are
written alongside the code they cover, in the same PR. The streaming corpus (§8) does not exist
until it is collected; the eval harness (§10) does not exist until it is built. A test
**applies to a build only when everything it exercises ships in that build**: every component, mark
and action it names has a renderer or handler in the build's registry (05 §3.3), and every feature
it relies on (`!ask`, `lazy`, `query`, `frame`, Weave-JSON, the per-chat override, …) is in a phase
the build includes (01 §29). For example, `T-454`–`T-458` apply from phase 2 and `T-452` from phase
3. "Every component" in `T-401`, `T-408` and `T-500`–`T-504` means every component the build
registers. A build is conformant when every test that applies to it passes; skipping an applicable
test is a failure. §4.1 is marked **(phase 2)** as a reminder; the rule above is what decides.

Notation: `⇒` means "produces". Unless stated, locale is `en-IN`, time zone `Asia/Kolkata`, light
theme, font scale 1.0. "Tokens" lists token kinds; "AST" shows a canonical print (02 §13). "Diag"
lists diagnostic codes in order.

---

## 1. Source, lexing and splitting

### 1.1 Fences and normalisation

| Id | Input | Expected |
|---|---|---|
| T-001 | Fence info `Weave` | Not a block; rendered as a code fence |
| T-001b | Fence info `weave edit loan extra` | Edit of block `loan` |
| T-002 | Body `a = 1\n```\nb = 2` | Statements `a = 1`, `b = 2` (the ``` line is removed) |
| T-003 | Body with `\r\n` line endings and a BOM | Same result as with `\n` and no BOM |
| T-004 | A top-level `~~~weave` … `~~~` fence | A block, same as the backtick form; `~~~weave` closed by ```` ``` ```` is not closed |

### 1.2 Numbers, percent, durations, units

| Id | Input expression | Expected |
|---|---|---|
| T-010 | `1_000_000` | 1000000 |
| T-010b | `1__0`, `1_` | one `E104` each (one malformed number token, not a cascade) |
| T-011 | `1..3` | `[1, 2, 3]` |
| T-012a | `45%` | 0.45 |
| T-012b | `10%3` | 1 |
| T-012c | `10 % 3` | 1 |
| T-012d | `[45%, 5%]` | `[0.45, 0.05]` |
| T-013a | `300ms` | dur 300 ms |
| T-013b | `2m` | dur 120,000 ms |
| T-013c | `1.5s` | dur 1,500 ms |
| T-013d | `5min` | dur 300,000 ms with `W403` (A7) |
| T-014 | `Card(gap: 12dp)` | `Card(gap: m)` with `W403` (A6) and no `W402` (12 is exactly `m`) |
| T-014b | `Card(gap: 13px)` | `Card(gap: m)` with `W403`, `W402` |

### 1.3 Strings

| Id | Input | Expected |
|---|---|---|
| T-015a | `"Hi {name}"` with `name = "Asha"` | `"Hi Asha"` |
| T-015b | `"{x \| num(2)}"` with `x = 3.14159` | `"3.14"` |
| T-015c | `"{{literal}}"` | `"{literal}"` |
| T-015d | `"{x \| nope}"` with `x = 2` | `"2"` and `E202` |
| T-015e | `"{a \|> f()}"` | the `\|>` is a pipe, not a format separator |
| T-015f | `'single'` | `"single"` with `W403` (A1) |
| T-015g | `“curly”` | `"curly"` with `W403` |
| T-016 | `"""\n    line1\n      line2\n    """` | `"line1\n  line2\n"` (first newline removed, common indent removed) — trailing newline before the closing quotes is kept. Kotlin's `trimIndent()` returns `"line1\n  line2"` and MUST NOT be used (02 §2.9) |
| T-017a | `Text("x", color: #FF0000)` | `color: error` with `W403` (S5) |
| T-017b | `a = 1 # #FF0000 is red` | `a = 1` (comment: previous token is `NUMBER`, not `:`/`(`/`,`/`=`/`[`) |

### 1.4 Logical lines

| Id | Body | Statements |
|---|---|---|
| T-020 | `a = 1\nb = 2` | 2 |
| T-021 | `root = Card [\n  a,\n  b\n]` | 1 |
| T-022 | `x = 1 +\n  2` | 1 (`x = 1 + 2`) |
| T-023 | `xs = items\n  \|> filter(i => i > 0)` | 1 (leading `\|>`) |
| T-024 | `t = $a\n  ? "yes"\n  : "no"` | 1 |
| T-025 | `a = (1\n\n# note\n+ 2)` | 1 (blank and comment lines skipped inside brackets) |
| T-026 | `a = 1)\nb = 2` | `a` dropped with `E103`; `b = 2` kept |

---

## 2. Parsing and auto-fix

| Id | Input | Expected AST / result | Diag |
|---|---|---|---|
| T-030a | `f = x => x * 2` | lambda, 1 param | — |
| T-030b | `g = (a, b) => a + b` | lambda, 2 params | — |
| T-030c | `h = (a + b) * 2` | parenthesised expression, not a lambda | — |
| T-031 | `Text("x", tone: $bad ? error : primary)` | named arg `tone` = ternary | — |
| T-032 | `ok = $a = 3` | `ok = $a == 3` | `W403` |
| T-040a | `Header = Title("x")`; `root = Column [Header]` | renamed `header`, reference rewritten | `W403` |
| T-040b | `Header = Title("x")` where `header` also exists | statement dropped | `E107` |
| T-041 | `a = 1\na = 2` | `a = 2` | `W404` |
| T-042 | `on change($x) -> !inc($a)\non change($x) -> !inc($b)` | one effect: `on change($x) -> [!inc($a), !inc($b)]` | `W403` (S12) |
| T-042b | `on change($x) when $x > 10 -> !toast("high")\non change($x) when $x < 0 -> !toast("low")` | one effect: `on change($x) -> [!if($x > 10, !toast("high")), !if($x < 0, !toast("low"))]`; setting `$x` to 11 toasts "high" only, to -1 toasts "low" only | `W403` (S12) |
| T-042c | `on change($x, debounce: 300ms) -> !a()\non change($x) -> !b()` | two effects (different trigger keys) | — |
| T-042d | `$x = 0`, `$n = 0`, `on change($x) when $x > 10 -> !set($x, 0)`, `on change($x) when $x < 5 -> !inc($n)`; set `$x` to 11 | merged effect: first branch resets `$x` to 0, second branch sees 0 and runs → `$n == 1`; counts as **one** firing toward the 8-firing cap (plus one more for the `change` the reset causes); one history entry | `W403` (S12) |
| T-043a | No `root`; `card = Card [Text("x")]` | root = `card` | `W405` |
| T-043b | Only `x = 1` | renders as code fence with chip | `W405` |
| T-044 | `export emi, $nope` | exports `emi` only | `W407` |
| T-050 | `Slider($r, 0, 10, 0.5)` | `Slider($r, min: 0, max: 10, step: 0.5)` | `W403` |
| T-050b | `Slider($r, 0, 10, 0.5, "x", 9)` | `Slider($r, min: 0, max: 10, step: 0.5)`: Slider's positional order is only `min, max, step`, so `"x"` and `9` are dropped | `W403`, `E109`, `E109` |
| T-051 | `Text(style: body, "hi")` | `"hi"` dropped → `Text` has no content → error chip | `E108`, `E204` |
| T-052a | `Button("Go", onClick: !toast("x"))` | `onTap: !toast("x")` | `W403` |
| T-052b | `Button("Go", onTap: !toast("x"), sparkle: true)` | `sparkle` dropped | `W401` |
| T-053a | `x = list[0]` | index | — |
| T-053b | `root = Card [a]` | children | — |
| T-053c | `y = split(s, ",")[0]` | index on a function result | — |
| T-054 | `root = Column [Divider, Text("x")]` | `Divider` with no args | — |
| T-055 | `ys = xs \|> filter(x => x > 1) \|> take(2)` | `take(filter(xs, x => x > 1), 2)` | — |
| T-056 | `Button("Done", onTap: !confetti)` | `!confetti()` | — |
| T-060a | `@weave 2` | block renders as code, chip "Needs a newer EchoFlow" | — |
| T-060b | `@weave 1 id: Loan-1` | id generated (`b1`) | `W403` |
| T-060c | Two blocks in a thread with `id: loan` | second becomes `loan_2` | `W404` |

### 2.1 Auto-fix rules

| Id | Input | Output |
|---|---|---|
| T-070 | `a = $x && $y \|\| $z` | `a = $x and $y or $z` |
| T-071 | `a = !$x` | `a = not $x` |
| T-072 | `a = !(1 == 2)` | `a = not (1 == 2)` |
| T-073 | `a = $x === 1` | `a = $x == 1` |
| T-074 | `a = .5 + 1` | `a = 0.5 + 1` |
| T-075 | `a = True` | `a = true` |
| T-076 | `a = 1;` | `a = 1` |
| T-077 | `Card(gap: 14)` | `Card(gap: m)` (12 and 16 tie → smaller) |
| T-078 | `Card(gap: 20)` | `Card(gap: l)` (16 and 24 tie → smaller) |
| T-078b | `Card(shape: 24)` | `Card(shape: m)` (default `Shapes`: 20 and 28 tie → smaller) |
| T-079 | `Card(gap: 21)` | `Card(gap: xl)` |
| T-080 | `Card(gap: "m")` | `Card(gap: m)` (S8) |
| T-081 | `a = if($x > 1, "big", "small")` | `a = $x > 1 ? "big" : "small"` |
| T-082 | `root = card [Text("x")]` | `root = Card [Text("x")]` |
| T-083 | `root = LINECHART(d, x: "a", y: "b")` | `root = LineChart(…)` |
| T-084 | `root = Column(children: [a, b])` | `root = Column [a, b]` |
| T-085 | colour slot hex → role: `#FF0000`, `#4CAF50`, `#2196F3`, `#FFC107`, `#9E9E9E`, `#000000` | `error`, `success`, `primary`, `warning`, `neutral`, `neutral` |
| T-086 | `r = {name = "a"}` | `r = {name: "a"}` |
| T-087 | `a = 2 elif` | `E105` (not fixed) |

---

## 3. Values and evaluation

| Id | Expression | Result |
|---|---|---|
| T-200a | `1 / 0` | `null` |
| T-200b | `sqrt(-1)` | `null` |
| T-200c | `pow(10, 400)` | `null` |
| T-201 | `Text("x", tone: primary)` | no diagnostic (symbol in a token slot) |
| T-201b | `a = primray` | `"primray"` with `W407` |
| T-210 | `num("1,234.5")` via `TextField(kind: number)` coercion; `"12%"`; `"₹500"` | 1234.5; 0.12; parse fails (previous value kept) |
| T-211 | `"1h 30m"` in a `dur` slot | 5,400,000 ms |
| T-220 | Truthiness of `0`, `""`, `[]`, `{}`, `"0"`, `0ms`, `"false"` | false, false, false, false, true, false, true |
| T-221 | `sortBy([3, null, 1, 2])`; `sortBy([3, null, 1], desc: true)` | `[1, 2, 3, null]`; `[3, 1, null]` |
| T-230 | Block B reads `loan::emi` where block `loan` exports `emi = 100` | 100; after `loan` is deleted: `null` |
| T-240 | `a = b + 1`, `b = a + 1` | both `null`, one `E302` naming `a, b` |
| T-241a | `fn f(n) = f(n + 1)`; `x = f(0)` | `null`, `E303` |
| T-241b | `range(30000)` | `null`, `E304` (over `RANGE_ITEMS`) |
| T-241c | `sum(map(range(10000), i => map(range(10), j => j)))` | `null`, `E303` (step budget) |

### 3.1 Operators

| Id | Expression | Result |
|---|---|---|
| T-250 | `0.1 + 0.2 == 0.3` | `false` |
| T-251 | `round(0.1 + 0.2, 2) == 0.3` | `true` |
| T-252 | `1 == "1"` | `false` |
| T-253 | `"a" + 1` | `"a1"` |
| T-254 | `[1] + [2]` | `[1, 2]` |
| T-255 | `{a: 1} + {a: 2, b: 3}` | `{a: 2, b: 3}` |
| T-256 | `-1 % 3` | `2` |
| T-257 | `10 % 0` | `null` |
| T-258 | `null ?? 1`; `false ?? 1`; `0 or 5`; `3 and 4`; `0 and 4` | `1`; `false`; `5`; `4`; `0` |
| T-259 | `date("2026-12-03") - date("2026-11-30")` | dur 3 days (259,200,000 ms) |
| T-260 | `"b" < "a"`; `"a" < "ab"`; `null < 1` | `false`; `true`; `false` |
| T-261 | `3 in [1, 2, 3]`; `"k" in {k: 1}`; `"ell" in "hello"` | `true`; `true`; `true` |
| T-262 | `5..2` | `[5, 4, 3, 2]` |

### 3.2 Actions and effects

| Id | Setup | Action | Expected |
|---|---|---|---|
| T-270 | `$n = 0` | tap `onTap: [!set($n, $n + 1), !set($n, $n + 1)]` | `$n == 2`, one history entry |
| T-271 | `$todos = [{t: "a", done: false}]` | `!set($todos[0].done, true)` | `$todos == [{t: "a", done: true}]` |
| T-272 | `$l = [1, 2]` | `!set($l[5], 9)` | unchanged, `E205` |
| T-273 | `$l = [1, 2]` | `!set($l[2], 3)` | `[1, 2, 3]` (index = len appends) |
| T-274 | `$x: num = 0` | `!set($x, "abc")` | unchanged, `E205` |
| T-275 | `on appear -> [!url("https://example.com"), !toast("hi")]` | block appears | URL not opened (logged), toast shown |
| T-276 | `$a = 1`, no history | `[!inc($a), !undo]` in one tap | `$a == 2` and one history entry: `!undo` runs inside the same transaction, before the `!inc` entry is committed, so there is nothing to undo yet |
| T-277 | slider drag over 10 values | — | one history entry; `!undo` restores the value before the drag |
| T-278 | streamed in order: `$rate = base(10)`, `root = Text($rate)`, `fn base(n) = n * 2`, fence close | — | `$rate == 20` (initialised when `base` arrives); before that `root` shows its placeholder, never `—` |
| T-278b | streamed: `$rate = base(10)`, `emi = $rate * 3`, `root = Card [Title("Loan"), Metric("EMI", emi)]`, then `fn base(n) = n * 2` | — | before `base` arrives: `Card` and `Title` render, `Metric` shows its placeholder (its argument `emi` read the waiting `$rate`), never `—`; after: `Metric` shows `60` |
| T-279 | streamed: `$rate = nope(10)`, `root = Text($rate)`, fence close | — | at fence close `$rate` is initialised to `null` with `E202`; it is **not** re-evaluated later |
| T-280 | `$a = 0`, `$b = 0`, `on change($a) -> !inc($b)`, `on change($b) -> !inc($a)` | tap `!inc($a)` | `$a == 5`, `$b == 4`, one `E306` |
| T-281 | `on change($q, debounce: 400ms) -> !inc($n)` | 5 writes to `$q` 100 ms apart | `$n` increments once, 400 ms after the last write |
| T-282 | `on every(100ms) -> !inc($n)` | visible for 1 s | interval raised to 250 ms (`W402`); `$n` ∈ {3, 4} |
| T-283 | `on appear -> !inc($n)` | show, scroll away, scroll back, restart app | `$n == 1` |

### 3.3 Built-ins

| Id | Expression | Result |
|---|---|---|
| T-284 | `emi(5000000, 0.085, 20)` | 43391.16 ± 0.01 |
| T-285 | `pmt(0, 12, 1200)` | 100 |
| T-286 | `irr([-100, 110])`; `npv(0.1, [-100, 110])` | 0.1 (± 1e-7); 0 (± 1e-9) |
| T-287 | `compound(1000, 0.1, 2)` | 1210 (± 1e-9) |
| T-288 | `amortize(120000, 0, 1)` | `[{year: 1, principal: 120000, interest: 0, balance: 0}]` |
| T-288b | `last(amortize(5000000, 0.085, 20)).balance`; `sum(amortize(5000000, 0.085, 20), "principal")` | `0`; `5000000` (± 0.01) |
| T-289 | `round(2.5)`, `round(-2.5)`, `round(1234, -2)` | 3, −3, 1200 |
| T-290 | `rand("x", 0)` twice; `rand("x", 0) != rand("x", 1)`; `0 <= rand(1, 99) < 1` | equal; true; true. Exact vectors: `RandomVectorsTest` freezes the outputs of the reference implementation for seeds `0`, `1`, `"x"` and i = 0…9 on first commit; any change fails |
| T-291 | `addMonths(date("2026-01-31"), 1)` | `2026-02-28` |
| T-292 | `weekday(date("2026-12-01"))`; `startOfWeek(date("2026-12-03"))` | 2; `2026-11-30` |
| T-293 | `convert(1, "mi", "km")`; `convert(100, "C", "F")`; `convert(1, "kg", "m")` | 1.609344; 212; `null` |
| T-294 | `groupBy([{c: "a"}, {c: "b"}, {c: "a"}], "c")` | `{a: [{c: "a"}, {c: "a"}], b: [{c: "b"}]}` with keys in order `a`, `b` |
| T-295 | `take([1,2,3,4], -2)`; `slice("héllo", 1, 3)` | `[3, 4]`; `"él"` |
| T-296 | `isEmail("a@b.co")`, `isEmail("a@b")`, `isPhone("+91 98765-43210")` | true, false, true |

### 3.4 Formats

| Id | Value \| format | Locale | Output |
|---|---|---|---|
| T-300 | `1234567.8 \| int` | en-IN / en-US | `12,34,568` / `1,234,568` |
| T-301 | `3 \| num(2)`; `3.10 \| dec(2)` | | `3.00`; `3.1` |
| T-302 | `25000000 \| compact`, `340000 \| compact`, `5600 \| compact`, `999 \| compact` | en-IN | `2.5 Cr`, `3.4 L`, `5.6K`, `999` |
| T-303 | `1200000 \| compact`, `-1500 \| compact` | en-US | `1.2M`, `-1.5K` |
| T-304 | `0.085 \| percent`; `0.5 \| percent`; `8.5 \| pct` | | `8.5%`; `50%`; `8.5%` |
| T-305 | `43391.2 \| inr`; `12.5 \| usd` | en-IN; en-US | `₹43,391`; `$12.50` |
| T-306 | `25000000 \| inrCompact` | any | `₹2.5 Cr` |
| T-307 | `12 \| signed`; `-3 \| signed` | | `+12`; `−3` |
| T-308 | `3 \| ordinal` | en | `3rd` |
| T-309 | `8100000 \| duration`, `45000 \| duration`, `350 \| duration`, `97200000 \| duration` | | `2h 15m`, `45s`, `350ms`, `1d 3h` |
| T-310 | `125000 \| clock`, `3725000 \| clock` | | `02:05`, `1:02:05` |
| T-311 | `date("2026-12-01") \| date(full)` | en-IN | `Tuesday, 1 December 2026` |
| T-312 | `null` in text | | `—` |
| T-313 | `2 \| plural(item, items)`, `1 \| plural(item, items)` | | `items`, `item` |

---

## 4. Edits and versions

| Id | Block | Edit | Expected |
|---|---|---|---|
| T-100 | `a = 1`, `root = Text(a)` | `a = 2` | `root` shows `2`, version 2 |
| T-101 | `root = Column [a]`, `a = Text("x")` | `b = Text("y")`, `root = Column [a, b]` | both shown |
| T-102 | `$r = 5` (user moved it to 9) | `$r = 1` | `$r == 9` |
| T-103 | `$r = 5` (user moved it to 9) | `$r = 1 reset` | `$r == 1` |
| T-104 | `on change($r) -> !haptic(tick)` | `-on change($r)` | no effect |
| T-104b | `on change($r) -> !haptic(tick)` | `on change($r) -> !a()`, `-on change($r)`, `on change($r) -> !b()` | one effect: `on change($r) -> !b()` |
| T-105 | `root = Text("x")` | `-root` | `E107`, ignored |
| T-106 | `root = Text(a)`, `a = "x"` | `-a` | `root` shows `—` (a is `null`), `E301` |
| T-106b | `root = Text("x")` | `root = Nope()` | edit applies, but `root` has no renderable node → **edit rejected**, block stays at version 1 |
| T-107 | thread has no block `zz` | ```` ```weave edit zz```` with `root = Text("x")` | renders as a new block with id `zz` |
| T-108 | a block at 399 statements | an `!ask` edit adding 2 new helpers | edit rejected, block stays at version n, handled as a failed `!ask` (Retry chip); same for a result over 48 KB |
| T-110 | `root=Card(gap:m)[Text( "a" ),Text("b")]` | canonical print | `root = Card(gap: m) [Text("a"), Text("b")]`; `print(parse(print(p))) == print(p)` for every corpus program |

### 4.1 Weave-JSON (phase 2)

| Id | Input | Expected |
|---|---|---|
| T-120 | The 02 §12.1 example | Same IR as the equivalent source block (compare canonical prints) |
| T-121 | `"a": {"label": "=1+1"}` | `label: 1 + 1` |
| T-122 | `"a": {"label": "'=1+1"}` | `label: "=1+1"` |
| T-123 | `"c": ["kpi", "Hello"]` where `kpi` is a node and `Hello` is not | `[kpi, Text("Hello")]` |
| T-124 | Truncated JSON at fence close: `{"weave":1,"nodes":{"root":{"t":"Text","a":{"content":"hi"}}` | renders `Text("hi")` |

---

## 5. Rendering (Compose tests)

| Id | Test |
|---|---|
| T-400 | `TextField($p, label: …)` with `"Password"`, `"Enter OTP"`, `"Card number"`, `"PIN"`, `"ATM PIN"`, `"Aadhaar"` → field disabled with "EchoFlow blocks can't ask for secrets". |
| T-400b | Labels `"Pincode"`, `"PIN code"`, `"Postal code"`, `"Spinner speed"`, `"Pinned notes"` → field enabled (not refused). |
| T-400c | Labels `"ATM PIN code"`, `"UPI PIN"`, `"Card PIN"` → refused. |
| T-401 | Every component in 04 renders without crashing for 200 random argument sets (fuzz: random types, nulls, huge strings, negative and huge numbers, empty lists). |
| T-402 | Modifier order (04 §2.2): a `Card(bg: primary, shape: xl, border: true, pad: l, onTap: …)` produces a ripple clipped to the xl shape and padding inside the border (semantic and pixel assertions). |
| T-403 | `Row` overflow: a `Row` with 4 `Button`s of 20-character labels at 360 dp width lays out as a column. The `Row` is implemented with `Layout`, not `SubcomposeLayout` (lint). |
| T-403b | `Row [Text("a"), Carousel(items)]` renders without an exception and lays out as a column; with `wrap: never` the carousel gets the width left after `Text`. No intrinsic query reaches the carousel. |
| T-404 | `Metric` animation: value 100 → 200 shows intermediate values during `motion: default` and ends exactly at `200`. |
| T-405 | `Slider(step: 0.05)` writes values with no float noise (`8.55`, never `8.549999`). |
| T-406 | `Tabs` composes only the selected page (a counter in an unselected page's binding is never evaluated). |
| T-407 | Placeholders never shrink during streaming (§8). At fence close, a held extra height animates down with the `fast` spatial spec when the block is visible (intermediate heights observed), and changes in one step under reduced motion or when the block is off-screen. |
| T-408 | Theme matrix: every non-popup component screenshot-tested (Roborazzi) in all 6 palettes + dynamic × light/dark × font scale 1.0 and 1.5. Golden images reviewed once, then frozen. |
| T-409 | No hardcoded colours: a lint check fails the build on `Color(0x`, `Color.Red` etc. in `weave-compose` except in `tokens/Semantic.kt`. |
| T-410 | Popups — `Sheet`, `Dialog`, `Popover`, `Menu`, `Select`'s dropdown, the date/time pickers, the confirm sheet (05 §13.1) and the per-chat Intelligent UI submenu (05 §11.2) — verified on the emulator; screenshots in the PR (CLAUDE.md). Roborazzi can't capture them. |
| T-414 | Tokens resolve through the theme: with `Shape.kt` / `Spacing.kt` temporarily changed in a test theme, `Card(shape: m, pad: l)` uses the new `shapes.medium` and `Spacing.base`. |
| T-411 | Charts: `LineChart` with data `[{x: 1, y: 10}, {x: 2, y: 30}]` at 300 × 168 dp has y ticks `0, 10, 20, 30` and the line's last point at the top of the plot area. |
| T-412 | Chart select: tapping a bar writes its record to the `select` place and dims others to 40 %. |
| T-413 | Accessibility: TalkBack labels for `Metric` (`"Monthly EMI: ₹43,391"`), `Slider`, `Chart` summary; every clickable ≥ 48 dp. |

---

## 6. Runtime behaviour

| Id | Test |
|---|---|
| T-450 | State persists: change a slider, kill the process, reopen the chat → same value. Transient state resets. |
| T-451 | Off-screen: an `every(1s)` effect doesn't fire while the block is scrolled out; resumes after. |
| T-452 | Only one block runs `frame` effects (the most visible). |
| T-453 | Frame budget: an action list that takes > 2 ms for 3 frames pauses with the "Paused — tap to resume" scrim. |
| T-454 | `!ask` latest-wins: two `!ask` calls 100 ms apart → only the second result is applied. |
| T-455 | `!ask` restricted: a response that also redefines a name not in `update` → that statement discarded. |
| T-456 | `!ask` cost guard: the 21st request in an hour shows the banner and doesn't send. |
| T-457 | `lazy` cache: opening a lazy tab twice makes one request; changing its context shows the "Update" chip and makes no request until tapped. |
| T-458 | Consent: `query("web.search", …)` shows the consent row and runs nothing until "Allow once"; "Always for this block" persists across restarts. |
| T-459 | Confirm sheet: `!url("https://evil.example.com/login")` shows `example.com` in bold. Cancel → nothing opens. |
| T-460 | Effects can't do outbound actions (T-275). |
| T-461 | Max blocks: Balanced level, reply with 2 blocks → second renders as a code fence. |
| T-462 | Existing blocks still render and work after switching the level to Off. |
| T-463 | Room migration N → N + 1 (05 §9.2; N = `AppDatabase.version` when shipped) passes `MigrationTestHelper` (like the existing migration tests). |
| T-464 | History to model: a block whose `$rate` was moved to 9.1 is sent as its current canonical source followed by `# state {"rate":9.1}` inside the fence. No `<weave-state>` tag appears anywhere. |
| T-464b | 250 changed states named `s000`…`s249`: the state line is ≤ 2,000 characters, keeps a prefix of keys in declaration order with `"…"` values, and ends with `"…":"+<n> more"` where `<n>` is the number dropped. |
| T-465 | Debug build: reading a binding from `Dispatchers.Default` throws; chart downsampling receives a `WValue` list and never calls the evaluator. |
| T-466 | Block menu → "Show as plain text": the block is replaced by its plain text, "Show interactive" restores it with state intact, the choice survives a restart, and history sends the plain text. |
| T-467 | A write that would push `stateJson` past `STATE_BYTES` rolls back the whole transaction, keeps the previous value and shows "This block is full"; `historyJson` is trimmed oldest-first. Initialisation too: `$big = map(range(1000), i => "x" * 1000)` then `$n = 1` → `$big` is `null` with `E304`, `$n == 1`, the chip shows once, and the stored `stateJson` is under `STATE_BYTES`. |

---

## 7. Streaming parser

| Id | Tail (no newline at end) | Speculative statements |
|---|---|---|
| T-090 | `root = Column [a, b` | `root = Column [a]` (partial `b` removed, then `,`) |
| T-091 | `x = "hel` | `x = "hel"` |
| T-092 | `s = Slider($r, min: ` | `s = Slider($r)` |
| T-093 | `t = Text("a" +` | `t = Text("a")` |
| T-094 | `root = Sli` | none |
| T-095 | Body `a = 1\n` | `a = 1` is speculative, **not** frozen, until the next line's first non-space character arrives or the fence closes |
| T-096 | Body `xs = items\n` then `  \|> take(2)` | after the second chunk: one statement `xs = take(items, 2)` (never two) |

---

## 8. Streaming corpus test

The corpus is `weave-core/src/test/resources/corpus/*.weave` (at least 300 programs: all examples in
01, 04, 06; every test input above; plus model outputs collected by the eval harness).

For each program `p`:

1. Feed `p` to `IncrementalParser` one character at a time, then one "token-sized" chunk
   (1–6 characters, seeded random) at a time.
2. After the final chunk with `complete = true`, the program MUST equal `parse(p)` (canonical prints
   equal, diagnostics equal).
3. At no point may a frozen statement change.
4. (Compose, a sample of 50) The rendered block height sequence MUST be non-decreasing until fence
   close (T-407).

---

## 9. Catalog and prompt checks

| Id | Test |
|---|---|
| T-500 | Every component, mark, action and function heading in 03/04 has a catalog entry, and every catalog entry is documented (parses the Markdown headings and tables). |
| T-501 | Every catalog parameter has a type and either a default or `required`. |
| T-502 | Prompt snapshots: the output of `EchoUiPrompt.section` for every (tier 1–4) × (Balanced, Immersive) × (source; json from phase 2) is snapshot-tested against `weave-core/src/test/resources/prompts/*.txt`. The test also records each snapshot's token count (any tokenizer, stated in the file) so the prompt cost in 05 §12 is a measured number. |
| T-503 | Every example block in 06 §3.8 parses with zero errors and zero warnings, and renders. |
| T-504 | The prompt for tier n mentions no component, function or action whose tier > n or phase > the build's phase. |
| T-505 | `SystemPrompts.build` (and every other builder that ends with `freshnessGate`) with a non-zero tier: the Weave section is immediately before the freshness gate, and the freshness gate is still the last section. Tier 0: the output is byte-identical to today's. |

---

## 10. Model eval harness (per release)

**This harness is not a prerequisite for building phase 1.** It is built after the language core
and renderer exist and runs before a phase leaves its settings flag (01 §29), then once per release.
Until it has run, the tier table uses the defaults in 05 §10.1. It covers cloud models only (05 §10.1).

### 10.1 Tasks

60 tasks in 6 categories (10 each): **calculators** (EMI, tip split, unit converter, BMI, compound
interest…), **data** (chart + table from given numbers), **planning** (itinerary, weekly plan,
checklist), **learning** (quiz, flashcards, fill-in-the-blanks), **comparison** (products, plans),
**play** (simple games, simulations — tier 4 only). Each task has a user prompt and a programmatic
check (e.g. "the Metric labelled EMI shows ₹43,391 after setting rate to 8.5").

### 10.2 Metrics per model × tier

| Metric | Definition |
|---|---|
| Parse success | Blocks with no `E1xx` errors / blocks |
| Dropped statements | Average statements removed by errors per block |
| Render success | Blocks whose root renders / blocks |
| Task completion | Programmatic check passes |
| Token cost | Average output tokens per block |
| Over-use | (Balanced only) blocks produced for 20 control prompts that should get plain text |

### 10.3 Bars for the tier table

A model gets automatic tier `n` only if, at tier `n`: parse success ≥ 95 %, dropped statements ≤ 0.5,
render success ≥ 97 %, task completion ≥ 85 %, and (Balanced) over-use ≤ 10 %.

---

## 11. Settings logic

| Id | Input | Expected |
|---|---|---|
| T-600 | No `intelligent_ui_level` key | `BALANCED` |
| T-601 | Key value `"loud"` | `BALANCED` |
| T-602 | Global `OFF`, chat override `"immersive"` | effective level `IMMERSIVE` |
| T-603 | `effectiveTier(BALANCED, modelTier = 4, false, false, shippedTier = 4)` | 3 |
| T-604 | `effectiveTier(IMMERSIVE, 1, false, false, 4)` | 1 |
| T-605 | `effectiveTier(OFF, 3, explicitUiRequest = true, false, 4)` | 3, prompt level `BALANCED` |
| T-605b | Level `OFF`, explicit UI request, reply with 2 `weave` fences | first renders as a block, second as code (max 1 new block, from prompt level `BALANCED`) |
| T-606 | `effectiveTier(OFF, 3, false, makeInteractive = true, 4)` | 3 |
| T-606b | Level `OFF`, "Make interactive" reply with 1 `weave` fence | renders as a block. A normal reply in the same chat with 1 fence renders it as code |
| T-607 | `effectiveTier(IMMERSIVE, 4, false, false, shippedTier = 2)` | 2 |
| T-608 | Explicit-request regex (05 §11.5), true: "make a loan calculator", "show it as a table", "plot my expenses for March", "draw a chart of rainfall", "turn this into a checklist", "make this interactive", "build me a budget tracker", "show me how the table of contents works" (accepted false positive), "give me a chart of my spending", "put this in a table" | all true |
| T-608b | Explicit-request regex, false: "what's the capital of France", "any good tabletop games?", "the plot of the novel", "explain the periodic table", "how do I set a timer on my phone?", "is this form valid HTML?", "give up on this game", "lay the table for dinner", "my money is stuck in a card" | all false |
| T-609 | Unknown model, tools + 128k context | tier 2; no tools, 16k → 1; 4k → 0 |
| T-610 | Quality record averages 2.3 errors over 10 blocks | automatic tier lowered by 1 for 7 days; a user override is unaffected |
| T-611 | Chat on a local model | effective tier 0; no Weave section; "Make interactive" and the per-chat Intelligent UI row hidden; existing blocks render, local actions work, `!ask` disabled with "Live updates need a cloud model" |
