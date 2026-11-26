# Echo UI — critique

**Status:** discussion only. Not normative. Where this file disagrees with
[02](02-language.md)–[07](07-conformance.md), those documents still win until they are changed.

Two reviews of the v1.0 spec (`README` plus `01`–`07`), checked against the EchoFlow tree at the
time of writing. Nothing in `com.echoflow` implements Weave. `01` line 6 says so, and a search for
`weave` outside this folder only hits the markdown seam the spec wants to plug into.

1. **Code-checked review.** Read the docs against `MarkdownStream.kt`, `Theme.kt` / `Spacing.kt` /
   `Shape.kt` / `Type.kt`, `AppDatabase` (version 32), `ChatThread`, `SettingsRepository`,
   `SystemPrompts`, and `ChatPlusMenu`.
2. **The old `critique.html` audit.** A line-by-line reading of the eight docs. It did not open
   the app. Its scores and a few of its citations do not survive that check; those are marked
   **Correction** below. Overlapping points are stated once.

`critique.html` is gone. This file replaces it.

---

## 1. The good

### 1.1 What holds up against the code

The language design is the strongest part, and it is specified tightly enough that two people could
implement the core the same way.

- **Flat statements, shallow brackets.** `01` §4 and `02` §4–6: named bindings, 0–2 positional
  leads, everything else named, children in brackets, forward references allowed. Most lines sit at
  bracket depth 1, so a model is not balancing an eight-deep JSX or JSON tree. Because every part
  has a top-level name, `!ask(..., update: [plan])` and `weave edit loan` can replace one binding
  instead of regenerating the block. `06` §3.7 asks for header, then state, then `root`, then
  children top to bottom, so the outline can paint while the rest streams. (That order fights
  state initialisation; see §3.1.)
- **Leads plus names.** `02` §6.2 and `04` §16. Extra positionals are rewritten by auto-fix S2
  (`Slider($r, 0, 10, 0.5)` → named `min` / `max` / `step`, `T-050`) instead of being dropped.
  Adding an optional parameter later does not shift every call.
- **Auto-fix is specified, not "be lenient."** `02` §8, rules A1–A10 and S1–S11, covered by
  `T-070`–`T-087`: smart quotes, `&&` / `||`, `!x` → `not`, `===`, `.5`, `12dp`, `5min`,
  `True` / `None`, trailing `;`, `if(c, a, b)` → ternary, hex → role, `{k = v}` → `{k: v}`. Each
  rewrite emits `W403` and runs once, in order, before the checker.
- **Evaluation rules you can implement from the page.** `03`: never throws; NaN and ±∞ become
  `null` (`T-200`); floored modulo (`-1 % 3` is `2`, `T-256`); 20,000-step budget and recursion
  depth 64 (`03` §5.3, `T-241`); immutable path writes (`T-271`, `T-273`); a real splitmix64
  recipe at `03` §10.10. `03` §7.2 runs a list of actions inside one transaction, so
  `[!set($n, $n + 1), !set($n, $n + 1)]` adds 2 (`T-270`) and commits one history entry.
- **The markdown seam is real.** `MarkdownBlockExtension` and `LocalMarkdownBlockExtensions` in
  `MarkdownStream.kt`. The planner already isolates a top-level fence, takes the first word of the
  info string as the kind, and freezes the fence once a later block has started. `02` §1.1 and
  `05` §5.1 match that, including case-sensitive `weave` versus `Weave` (`T-001`). A reply with
  no `weave` fence keeps today's chunk freezing. `openingFence` in `MarkdownSourceScan.kt` accepts
  both `` ` `` and `~`, which `02` does not mention.
- **Several "already in the app" claims are true.** Media3, Room, Roborazzi, the secrets plugin,
  and `MaterialExpressiveTheme` with `MotionScheme.expressive()` (`Theme.kt`). The six palettes
  plus dynamic colour match `01` §2 principle 3. `minSdk` is 24, so the "AGSL needs API 33,
  `Shader` falls back below that" note is a real constraint. Reduced motion already exists as
  `rememberReducedMotion()` (`Motion.kt`), read from the animator duration scale.
- **Level versus tier.** Off / Balanced / Immersive is a user choice, separate from model tier
  0–4, with `effectiveTier` written out in `05` §11.3. Confirm sheets, "effects cannot fire
  outbound actions" (`03` §7.4, `T-275`), and refusing secret-shaped fields are the right
  instincts even where the regex is wrong (§2.7).
- **Prompt text is normative.** `06` says wording changes in the spec, then the snapshot, not in
  code. `05` §9.4 / `06` §2.4 collapse blocks older than ten turns to a header plus a state line,
  which is the right shape for history if the encoding is settled (§2.3).
- **`07` is a real conformance doc.** About 212 numbered cases with inputs and expected
  diagnostics, plus a streaming-equivalence *requirement*. It is not a test suite (§2.8).
- **The README is honest about precedence.** `01` is informative; `02`–`07` win. `01` line 6 says
  nothing here is built.

### 1.2 From the HTML audit, still fair as spec design

These were scored "excellent" or "good" without opening the app. As *design* they hold. As
*built* they do not: there is no `BuiltinCatalog`, no `WeaveSession`, no Room v33.

- **Silent model loop, as specified.** `03` §9, `05` §8, `06` §4. `!ask` shimmers only the
  bindings in `update` (`04` §2.6), latest-wins, and drops statements outside `update` plus new
  helpers (`T-454`, `T-455`). `lazy` is SHA-256 of `{"p", "c"}` (`05` §8.5), persisted in
  `lazyJson`, with an "Update" chip instead of a surprise spend. Cap is one in-flight request per
  block and 20 hidden requests per hour (`T-456`).
- **One catalog drives checker, prompt, schema, and renderer.** `04` §16, `05` §3.3. `T-500`
  would parse the headings in `03` and `04` and require 1:1 parity. `restrictedTo(tier, phase)`
  is what stops the prompt advertising a component the build cannot draw (`T-504`).
- **HCT semantic colours, as specified.** `04` §1.4.2: success hue 145, warning hue 75, chroma
  `max(36, primaryChroma)`, so `success` / `warning` retint per palette. `tone` + `emphasis`
  resolve to a container/content pair. `T-409` is the lint that would ban hardcoded `Color(...)`
  outside `tokens/Semantic.kt`. The app does not do this today: additions use a fixed green,
  `ColorScheme.diffAdded` in `Color.kt`.
- **Coarse time and off-screen stop.** `now()` ticks once a minute (`03` §10.6). `every`,
  `frame`, and sensors run only while visible and in the foreground (`05` §4.6). Video, audio,
  `Model3D`, and `Shader` release after 5 s off-screen (`05` §5.6).
- **Modifier order and chart drawing, as specified.** `04` §2.2 writes a twelve-step `Modifier`
  chain so two implementations clip and pad the same way. `04` §12 draws a chart in one pass and
  downsamples with LTTB. The 120 Hz budget that sits on top of this is not yet testable (§3.4).
- **Accessibility defaults, as specified.** 48 dp targets, icon name → label
  (`"flight_takeoff"` → `"Flight takeoff"`), chart summary plus "View as table" (`04` §12.6),
  `snap()` under reduced motion (`T-413`).
- **Versions do not rewrite the message.** `05` §9 keeps the streamed `ChatMessage` text and
  stores the live source in `weave_blocks`, with a 500 ms debounce and an `ON_STOP` flush. The
  "migration 32 → 33" text matches the database version *today* and will rot (§2.4).
- **A cheaper model for hidden calls, and a downgrade.** `05` §8.8 and §10.4: optional UI model
  for `!ask` / `lazy` / remix / repair; if the last ten blocks average more than 2.0 errors, drop
  the automatic tier by one for seven days (`T-610`). User overrides are not downgraded.

---

## 2. The bad

Inaccuracies, gaps, and drift. Each one is a doc bug or an unimplemented claim, not a design
argument (those are §3).

### 2.1 Design tokens do not match EchoFlow

`01` §2 principle 10 says a block should be indistinguishable from hand-built EchoFlow UI. The
token tables are a different design system.

| | Spec | App |
|---|---|---|
| Spacing | `01` §11.1, `04` §1.1: `none 0, xxs 2, xs 4, s 8, m 12, l 16, xl 24, xxl 32` | `Spacing.kt`: `xs 4, s 8, m 12, base 16, l 20, xl 24, xxl 32, huge 48` |
| Corners | `04` §1.5: `xs 4, s 8, m 12, l 16, xl 28` | `Shape.kt`: `8, 14, 20, 28, 36` |
| Type | `04` §1.6: the 15 Material roles, then clamp | `Type.kt`: Space Grotesk Black 44 sp on `displayLarge`; Hanken Grotesk on reading styles |

Weave `l` is the app's `base`. The app's `l` (20 dp) does not exist in Weave. `shape: m` (12 dp)
is tighter than every card already on screen (20 dp).

The clamp disagrees with itself. `04` §1.6 replaces `displayLarge` and `displayMedium` with
`displaySmall` in both `inline` and `card` blocks. `01` §11.4 caps every `display*` to
`headlineLarge`, and only when the block is `inline`.

### 2.2 Token snapping, tests, and the spacing scale are one knot

`02` §8.2 S4: nearest token, ties to the smaller. Worked example: `gap: 14` ties 12 and 16, so
`m`. `T-077` expects `gap: 14` → `m`. `T-078` expects `gap: 20` → `l` "because 16 and 24 tie."
`T-014` expects `12dp` → `m` with `W403` and no `W402`, which is only true if `m` is exactly 12.

That arithmetic assumes `l` = 16. If spacing is later aligned to `Spacing.kt` (`l` = 20), `gap: 20`
is an exact `l` and `T-078`'s stated reason is false. Any spacing fix has to edit `02` §8.2 and
these three tests together.

### 2.3 Two history encodings, two truncation limits

- `01` §22 and the intro of `05` §8.6 show
  `<weave-state id="loan" v="3">{"rate":9.1,…}</weave-state>`, and `01` §22 cuts a value at 400
  characters (`"cart": "list(12)"`).
- `06` §2.4 appends `# state {{json}}` as a comment inside the fence. `05` §8.6's own table cuts
  strings at 200 characters, lists at 20 items, and the whole JSON at 2,000 characters.
- `05` §9.4 says history uses "the state line from `06` §2.4," so the normative docs already
  disagree with `01` and with the `<weave-state>` mention at the top of `05` §8.6.

The HTML audit's "good" item 3 treats `# state` plus the ten-turn collapse as settled (`T-464`).
It is settled only if `01` §22 and the `<weave-state>` sentence are deleted. Until then an
implementer can follow either.

### 2.4 Storage: two schemas, and a version number that is a race

`01` §23.1 shows `weave_block(message_id, block_key, …)` in snake_case, with no `chatId`,
`firedJson`, `askLogJson`, or `uiStateJson`. `05` §9.1 requires `weave_blocks`, a UUID primary
key, a foreign key to `ChatMessage.id`, and those columns. `01` is informative, but it is the
document a new reader hits first, and it does not say "ignore this schema."

`05` §9.2's migration is 32 → 33. That matches today: `AppDatabase` is `version = 32` ("v32:
quote & reply on user messages"). The same section says to take the next free version if 32 has
moved on. That sentence is the useful one; "33" will be wrong as soon as another feature ships.
`ChatThread` has no `intelligentUi` column.

Settings do live in `SettingsRepository` over encrypted SharedPreferences, so the key
`intelligent_ui_level` (`05` §11.1) is a plausible addition. There is no chat section on
`SettingsScreen` to drop the row into, and the `+` menu (`ChatPlusMenu.kt`) is a feature-toggle
list, not a per-chat level picker. `user.firstName` (`01` §6.6, `03` §4.3) "only if the user has
set one in settings" has nowhere to be set.

### 2.5 Prompt assembly ignores how prompts are built

`05` §12 appends the Echo UI section at the end of `SystemPrompts.build` and
`buildCustomProvider`. `SystemPrompts.build` deliberately ends with `freshnessGate`, documented
there as the recency slot: the last thing the model reads before it answers. A multi-thousand-token
Weave section after it buries that gate.

On-device models already have a separate, short prompt path because context is scarce. `05`
§10.1 assigns every local model tier 0, so they get an empty section (`05` §11.3). "Make this
interactive" is hidden unless the model tier is at least 1 (`05` §11.6). Live UI on the on-device
path is therefore specified to no-op, and the spec never says that in product language.

The HTML audit's reading of `effectiveTier` is slightly off and worth correcting. It says an
Immersive user on a small model "still only gets safe Tier 1 display blocks," and that "Make
interactive" gives Off users UI "at the model's full tier." The function is
`min(levelCap, modelTier, shippedTier)`. A small model at tier 1 stays at 1. "Make interactive"
sets the level cap to 4, but the model tier and `shippedTier` still clamp it, and a tier-0 model
stays at 0.

### 2.6 Chart colours and shape lists disagree across docs

- Series palette. `01` §11.3: `primary, tertiary, secondary, error`, then those at "low", then
  `neutral`. `04` §1.4.1: the same four, then the same four at 60% alpha, then `onSurfaceVariant`.
- Shapes. `01` §11.5 lists about ten expressive shapes. `04` §1.5 lists 26, including `cookie4`,
  `cookie12`, `clover8`, `verySunny`, and `ghost` → `Ghostish`. `06` §3.2's token card is a third,
  shorter set (`cookie clover sunny gem pill arch burst flower puffy heart`).

### 2.7 The secret-field regex blocks Indian addresses

`04` §7 matches the substring `pin` (with `password`, `otp`, `cvv`, `aadhaar`, …). `T-400`
explicitly accepts `"Pincode"` as a refused false positive. The spec's own default locale is
`en-IN` (`07` preamble; `inr`, lakh/crore, the Jaipur example). A delivery or address form that
asks for a pincode renders disabled with "EchoFlow blocks can't ask for secrets."

The HTML audit's suggested patch, `\bpin\b(?!\s*code)`, plus allowing `pincode` / `pin code` /
`postal`, is the right direction. `PIN` and `ATM PIN` should stay blocked.

### 2.8 Conformance is a document, not a suite

`07`'s first line says an implementation is correct when every test passes. There is no
`weave-core` module, no test class, and no corpus. `01` §28 asks for at least 300 golden programs
and a fuzz suite. `07` §8 describes streaming each corpus program character by character; it does
not contain the corpus.

**Correction of the HTML audit.** Its overview scores the spec 9/10 and marks conformance
"Excellent," including "char-by-char streaming corpus equivalence" and a Roborazzi matrix as if
they exist. `07` specifies `T-001`–`T-610` and the ≥300-program procedure. None of it runs. The
dossier counts (15 `WValue`s, ~70 components, ~90 builtins, ~212 test ids) are fair as *page
counts*. The score is not.

Rules with no row, or a row that does not match the rule:

- `E106` reserved names (`dt`, `device`, `block`, `user`, `item`, `index`).
- `js fn` below tier 4 → `E209` (`02` §5.4).
- Effect chains past the single `T-280` scenario. The cap is 8 (`03` §8.5).
- An edit that pushes a block past 48 KB or 400 statements (§3.7).
- `query` consent beyond the action-level tests.
- `T-010b` says `1__0` is `E104`. `02` §2.4 only says it is not a number.
- `02` §2.9 says raw strings strip indent "like Kotlin `trimIndent()`." `T-016` keeps the trailing
  newline before the closing quotes. Kotlin's `trimIndent()` drops a final blank line, so calling
  it directly fails `T-016`. The spec should say what to strip, and drop the Kotlin analogy.
- Diagnostic codes jump from `E304` to `E306`. There is no `E305`. `03` prints §10.11 (motion
  helpers) before §10.10 (randomness).

**Correction.** The HTML audit says `05` §5.6 links to a missing `05` §6.6. There is no such
link. `05` §5.6 is "Off-screen behaviour" and does not cite §6.6. Drop that finding.

### 2.9 Smaller drift

- `02` §1.1 says the fence is found by `MarkdownStreamPlanner`. True for top-level fences. The
  planner only promotes a fence whose kind is registered, and `openingFence` also accepts `~`.
  Neither is in the spec.
- `01` §26.7 says add kotlinx.serialization "if not already used." The app uses Moshi
  (`gradle/libs.versions.toml`). `05` §1 then says `weave-core` must not take a new JSON library
  and must ship `TolerantJson` instead. Those two should point at each other.
- `04` §1.9 wants `material-icons-extended` and a generated `WeaveIcons.kt`. The icon dependency
  is not what the app uses everywhere today; treat the registry as new surface, not as a free
  call into an existing font.
- `W406` is skipped (warnings go `W405`, `W407`). Same class of hole as `E305`.

---

## 3. The ugly

Design problems that get more expensive if the spec stays normative while they are unresolved.

### 3.1 Streaming state init versus the prompted order

`06` §3.7 tells the model to write state first and helper functions last. `03` §7.1 initialises a
state when its declaration freezes. `05` §4.2 adds states during streaming and never removes
them. `$rate = defaultRate(10)` therefore runs before `fn defaultRate` exists, evaluates to
`null` with `E202`, and the key is already in `StateStore`, so fence-close `sync` keeps the
`null`. The prompt requests the pattern that produces a permanent empty state.

Fix in the spec, not later in code: re-evaluate a state whose initialiser saw an unresolved name,
once, at fence close, unless the user has already written it. And tell the model to put functions
used by initialisers *before* state.

### 3.2 The catalog is a product, not a phase

`04` specifies on the order of 70 components, 11 chart marks, 15 chart sugars, 9 canvas marks,
`Map`, `Model3D`, `Shader`, `Lottie`, `Quiz`, `Board`, kanban, and `js fn`. `01` §23.6 adds a
Glance widget profile. `01` §29 calls phase 1 "the engine," and phase 1 still includes the full
parser, checker, JSON form, prompt generator, persistence, versions, and a large renderer.

`05` §1 makes three new Gradle modules and their package names required. The app is a single
`:app` module (`settings.gradle.kts`, `AGENTS.md`). A spec that says MUST for components nobody
will ship for a year is how the spec rots: every later change has to edit seventy signatures to
stay "complete."

The HTML audit's thin slice is the right counter-proposal, slightly larger than "layout plus one
input": `Column`, `Row`, `Card`, `Title`, `Text`, `Metric`, `Slider`, `Segmented`, `Button`,
`TextField`, one chart, `Table`. Ship that behind a developer flag. Mark everything else in `04`
and `05` as deferred, not MUST, until a model eval says it is needed.

### 3.3 Weave-JSON before any model has been measured

`02` §12 is a second grammar: its own streaming repair, its own edit document, a generated JSON
Schema, and `print(parse(print(p))) == print(p)` across both forms. `05` §12 says the user cannot
pick it in phase 1, and it is only used when a tier-table flag says the model supports
`response_format`. That table does not exist.

This is worth keeping as a *plan*. It is not worth normative parity tests (`T-120`–`T-124`)
before `07` §10 has shown that source Weave actually fails for a model that constrained JSON
would save. The HTML audit's cold-start point is the same concern from the other side: no model
has seen Weave, `and` / `or` / `not` and `[]`-for-children fight Python and JS habits, and the
60-task harness should run against Gemini, Claude, GPT, and Qwen on the JVM core before the
Compose catalog is built. Auto-fix and "default this model to JSON" should be decisions from that
run, not guesses in `05` §12.

### 3.4 Default prompt versus the app's prompt, and `weave_docs`

Balanced is the default (`README`, `05` §11.1) and it sends a syntax card, every tier-1–3
component line, functions, actions, effects, rules, and examples on every cloud turn (`06` §2),
including custom providers whose prompt is already search-heavy. The HTML audit's ~1.2k–4k token
estimate is plausible and **not measured anywhere in the spec**. Treat the number as a hypothesis.

For tier ≥ 3 with tools, `06` §2.1 and §8 replace the component list with an index plus a
`weave_docs` tool. The first interactive answer can then wait on a tool round-trip before any UI
streams. The HTML audit's mitigation is right if tier 3 ships at all: keep the common components
inline (`Card`, `Column`, `Row`, `Title`, `Text`, `Metric`, `Slider`, `Segmented`, `Button`,
`Chart`, `Table`, `Tabs`) and leave `weave_docs` for the long tail (`Sort`, `Canvas`, `Shader`,
`Quiz`).

Local models are tier 0 (§2.5). "Live UI is cloud-only" should be an explicit product sentence or
it should be false. It should not be an accident of `min(cap, modelTier, shippedTier)`.

### 3.5 Reactivity-through-Compose is a threading trap

`05` §4.3 makes each binding a `derivedStateOf` and says the dependency graph *is* Compose's
snapshot graph, evaluated on the main thread. `05` §4.4 parses on `Dispatchers.Default`. Those
fit only if no binding is ever read off the main thread. `snapshotFlow` for `change` (`05` §4.6)
is main-thread. Chart downsampling and audio decode are specified on `Dispatchers.Default`
(`05` §4.4) and will read bindings if anyone writes them that way. The spec never states
"bindings are main-thread only" as a rule with a test.

The HTML audit called this "zero custom reactive engine" and scored it excellent. The *idea*
(don't write a second reactivity system) is good. The missing sentence is the bug.

### 3.6 Budgets that cannot be budgeted yet

- `01` §27: slider → chart in the same frame at 120 Hz for ≤ 500 points, parse < 0.3 ms median,
  on a Pixel 8-class device. There is no CI for that device.
- `04` §3.3 / the `Row` section: `wrap: auto` is the default and uses `SubcomposeLayout` to fall
  back to a `Column`. Several of those inside a chat `LazyColumn` (`ChatMessages.kt`) is a known
  jank source on mid-range flings. The HTML audit's alternative is a single custom `Layout` that
  reads `maxIntrinsicWidth()` once, then places a row or a column. Prefer that, or default
  `wrap` to `never`, before asserting "no jank beyond the app's baseline" with 20 blocks.
- `04` §2.9: placeholders never shrink while streaming, then snap shorter at fence close. That
  jump happens exactly when the user reaches for the block (`T-407` encodes the snap). Animate
  the shrink with `motion: fast` when the block is visible, or the chat list jumps.
- Roborazzi cannot see `Popup`, `DropdownMenu`, or bottom sheets (`CLAUDE.md`; `01` §13.8 repeats
  it). `07` already splits the work: `T-408` screenshot-tests non-popup components, and `T-410`
  requires `Sheet`, `Dialog`, `Popover`, and `Menu` on an emulator with screenshots in the PR.
  That split is right. What this environment still cannot do is run `T-410`. `Select` and the
  confirm sheet are popups too, and `T-410` does not name them.

### 3.7 Edits can grow a block without a stop

`05` §13.3 stops a *stream* at `SOURCE_BYTES` (48 KB) or `STATEMENTS` (400). `02` §11.2 rejects an
edit only when the result has no renderable root. An `!ask` that adds helpers can cross both
limits with no stated outcome: reject, or truncate, or keep going. Combined with persisted state
(`03` §7.1) and 50 history entries of full state maps (`03` §7.5), one chatty block is a Room row
that only grows.

The HTML audit's fix belongs in `02` §11.2: an edit whose result exceeds either limit is rejected
and version `n` stays, same as a rootless edit.

### 3.8 One effect per trigger key drops the guard

`02` §5.5: the trigger key is the canonical print of the trigger, and the `when` condition is not
part of it. `T-042` then requires a second `on change($x)` to replace the first (`W404`). So

```weave
on change($x) when $x > 10 -> !toast("high")
on change($x) when $x < 0 -> !toast("low")
```

keeps only the second effect. The spec's own workaround is "use a list of actions," which cannot
express two different guards. Put the canonical `when` expression in the key, or say clearly that
two guards on one trigger are illegal and that the model must write `!if`.

### 3.9 Off has no escape hatch, and the Off-exception is a blunt regex

Users can lift a prose answer into a block ("Make this interactive", `05` §11.6). When Balanced
emits a block nobody asked for, nothing in `05` §7.2's block menu collapses it back to text. The
HTML audit's "Show as plain text" action is a product gap, not a spec contradiction. `07` §10.2
defines over-use (Balanced blocks on 20 plain-text control prompts) and §10.3 requires it to stay
at or below 10% before a model earns its tier. That is a required bar, not a result: nothing has
been measured, so the HTML audit's "~10% over-use" is the limit restated as if it had already
happened.

The other direction is specified, and it is jumpy. `05` §11.5's explicit-request regex, used when
the level is Off, matches bare `\bplot\b`, `\bform\b`, `\btable\b`, `\bgame\b`. `T-608` accepts
"the plot of the novel" as a hit. That silently attaches the Balanced prompt (the same unmeasured
1–4k tokens) to a turn the user asked to keep plain. Narrow it to an action plus an object
(`draw|make|show|build` a `chart|table|calculator`), and add the negative cases next to `T-608`.

### 3.10 "Any two implementers" is not true yet

The README says that if a choice exists, the spec makes it, and guessing is a bug. These choices
are still open: which history encoding (§2.3), which truncation limits, which spacing scale
(§2.1–2.2), whether fence-close re-inits streaming state (§3.1), whether an over-limit edit is
rejected or truncated (§3.7), whether two `when` guards coexist (§3.8), and what `1__0` diagnoses
(§2.8). Those are the ones not to guess.

---

## 4. What to change, in order

Doc fixes. None of these change runtime behaviour, because there is no runtime yet.

1. **Pick one scale.** Either Weave tokens name EchoFlow's `Spacing` / `Shapes` / `Typography`,
   or the spec says blocks are allowed to look different inside a bubble. Do not claim both.
   Update `01` §11, `04` §1, `02` §8.2 S4, `T-014`, `T-077`, and `T-078` in the same edit. The
   recommendation from the code-checked review: name the app's values, otherwise principle 10 is
   false on day one.
2. **One history encoding.** Delete `<weave-state>`. `06` §2.4 is the only form. Point `01` §22
   and `05` §8.6 at it, including the 200-character / 20-item / 2,000-character truncation.
3. **State init.** In `03` §7.1, re-evaluate at fence close a state whose initialiser saw an
   unresolved name, unless the user has written it. In `06` §3.7, functions used by initialisers
   come before state.
4. **Secret regex.** Word-boundary `pin`, allow `pincode` / `pin code`. Update `T-400`.
5. **Edit limits.** In `02` §11.2, reject an edit that exceeds `SOURCE_BYTES` or `STATEMENTS`
   and keep version `n`.
6. **Trigger keys.** Include `when` in the key, or ban two guards and say so next to `T-042`.
7. **Schema humility.** Mark `01` §23's table non-normative and point at `05` §9. Say the Room
   version is "current `AppDatabase.version` + 1," not "33."
8. **Prompt position.** In `05` §12, say whether the Weave section is before or after
   `freshnessGate`. Do not append it blindly.
9. **Off regex.** Narrow `05` §11.5 and extend `T-608` with the false positives it currently
   blesses ("the plot of the novel").
10. **Raw strings.** `02` §2.9 should match `T-016` and stop citing `trimIndent()`.
11. **Display clamp, chart palette, shape list.** Make `01` §11.3–11.5 match `04` §1.4–1.6, and
    make `06` §3.2 match whichever list is real.
12. **Number the holes.** Either define `E305` and `W406` or stop skipping them. Swap `03`
    §10.10 and §10.11.
13. **`07` honesty.** Say which tests are the suite and which are a harness that does not exist
    yet. `T-408` / `T-410` already keep popups out of Roborazzi; extend `T-410` to `Select` and
    the confirm sheet, which it does not name. Do not describe the streaming corpus as present.

### Code, discussion only

Not a proposal to start. Phase 1, if it happens, should be the language core plus the thin
component slice in §3.2, local actions, and the fence registration in `05` §5.1. Weave-JSON,
`!ask`, canvas, maps, shaders, QuickJS, and widgets wait on `07` §10. Until then, those sections
of `04` and `05` should be labelled deferred.

The HTML audit's extra implementation notes, kept here so they are not lost:

- `Row(wrap: auto)` should not be a `SubcomposeLayout` inside the chat list (§3.6).
- Placeholder height should ease down at fence close, not snap (`04` §2.9).
- A "Show as plain text" action belongs on the block menu (`05` §7.2) if Balanced can over-fire.
- Run the 60-task harness on the JVM core before writing the renderer (§3.3).

---

## 5. Open questions

From the code-checked review:

1. Should Weave tokens be a subset of `Spacing`, `Shapes`, and `Typography`, or a separate scale
   that is allowed to look different inside a bubble?
2. Is Balanced-as-default acceptable if it adds a full catalog to every cloud turn, including
   custom providers? The token cost has not been measured.
3. On-device models are tier 0. Is "live UI is cloud-only" a decision, or should local models get
   a small tier-1 card?
4. Where does the Weave section sit relative to `freshnessGate`?

From `01` §31, still open there and not answered by this critique: whether the fence stays
`weave` or becomes `echoui`; whether flagship models start at tier 2 or tier 3; whether `!ask`
spend is shown per block or only monthly; Google Maps versus a keyless map; QuickJS in the APK
or on demand; library sync versus the no-backend principle; whether Glance widgets are phase 3
or later.

---

## 6. What the HTML audit got wrong

Kept short so the findings above stay the document.

| Claim in `critique.html` | What is actually true |
|---|---|
| Spec rigor 9/10; conformance "Excellent" | The *writing* is tight. The suite does not exist, and several normative rules have no test (§2.8). |
| `05` §5.6 links to a missing `05` §6.6 | No. That link is not in the file. |
| "Make interactive" gives Off users the model's full tier | `05` §11.3 still clamps by `modelTier` and `shippedTier`. Tier 0 stays 0. |
| Immersive on a small model "gets safe Tier 1" | Only if that model is tier 1. The function does not round up. |
| ~1.2k–4k tokens, ~10% over-use | Not measured in `01`–`07`. Useful as a hypothesis, not as a result. |
| Streaming corpus equivalence, as a strength that is done | Specified in `07` §8. No corpus, no `IncrementalParser`. |
| Room v33 as a property of the system | Specified against database version 32. Not migrated. |

The HTML audit's real catches, all kept above: streaming state init (§3.1), the pincode regex
(§2.7), `trimIndent` versus `T-016` (§2.8), `<weave-state>` versus `# state` (§2.3), the
`display*` clamp (§2.1), one-effect-per-key (§3.8), the Off regex (§3.9), edit limits (§3.7),
placeholder snap and `SubcomposeLayout` (§3.6), prompt tax and `weave_docs` latency (§3.4), and
v1 scope (§3.2).
