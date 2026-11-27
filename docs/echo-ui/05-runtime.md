# 05 — Runtime, storage, model loop and settings

**Spec version:** 1.3 · **Status:** normative

This document says how Echo UI is built inside EchoFlow: modules, classes, threading, streaming
integration, rendering, the hidden model requests, storage, tiers, the **Intelligent UI** setting,
consent and limits. Names given here (modules, packages, classes, columns, preference keys) are
**required**, so that code written by different people fits together.

---

## 1. Modules and packages

```
weave-core/        Kotlin/JVM library. No Android imports. Package com.echoflow.weave.core
  lex/             Token.kt, Lexer.kt, Normalize.kt
  parse/           Ast.kt, Splitter.kt, Parser.kt, IncrementalParser.kt, AutoFix.kt
  check/           Checker.kt, Diagnostics.kt, Types.kt
  catalog/         Catalog.kt, ComponentSpec.kt, BuiltinCatalog.kt (all specs from 04), Tokens.kt
  eval/            Values.kt (WValue), Evaluator.kt, Builtins*.kt, Formats.kt, Places.kt, Random.kt
  program/         WeaveProgram.kt, Edits.kt, CanonicalPrinter.kt
  json/            TolerantJson.kt, JsonLowering.kt
  prompt/          PromptGenerator.kt, SchemaGenerator.kt
weave-compose/     Android library. Package com.echoflow.weave.compose
  render/          WeaveSurface.kt, NodeRenderer.kt, Registry.kt, Modifiers.kt (04 §2.2), Placeholders.kt
  components/      one file per component group: Layout.kt, TextContent.kt, Numbers.kt, Inputs.kt,
                   Collections.kt, Navigation.kt, Media.kt, Overlays.kt, Visual.kt, Play.kt
  chart/           Chart.kt, Scales.kt, Marks.kt, Lttb.kt, ChartA11y.kt
  canvas/          WeaveCanvas.kt, SvgPath.kt
  tokens/          TokenResolver.kt (04 §1), Semantic.kt (success/warning), WeaveIcons.kt (generated)
  motion/          Motion.kt
weave-runtime/     Android library. Package com.echoflow.weave.runtime
  WeaveRuntime.kt, WeaveSession.kt, StateStore.kt, BindingCache.kt, ActionBus.kt, Effects.kt,
  History.kt, ModelLoop.kt, LazyCache.kt, Queries.kt, Consent.kt, ConfirmSheet.kt, Limits.kt,
  storage/ (Room entities and DAOs, §9), tiers/ (EchoUiTiers.kt, §10), widget/ (phase 3)
weave-js/          Optional (tier 4). Package com.echoflow.weave.js
app/               Integration only: registration, settings UI, SystemPrompts section, Room migration.
```

Dependency direction: `app → weave-runtime → weave-compose → weave-core`. `weave-core` depends only
on the Kotlin standard library and `kotlinx-coroutines-core` (already in the project). Room entities
live in `weave-runtime` but are registered in the app's `AppDatabase` (§9).

JSON in `weave-core` uses the module's own `TolerantJson` (needed for streaming anyway); no new JSON
library is added. The app itself uses Moshi (`gradle/libs.versions.toml`); `weave-runtime` and `app`
use Moshi for anything they serialise (Room JSON columns), not kotlinx.serialization. (01 §26.7 is
informative and defers to this paragraph.)

**Why separate modules, when EchoFlow is a single `:app` module today.** `weave-core` is a plain
Kotlin/JVM module so the language core — parser, checker, evaluator, prompt generator — runs and is
tested on the JVM with no emulator, Robolectric or Android SDK. That is what makes 07 §1–4 and §7–9
fast enough to run on every build and lets the model eval harness (07 §10) drive the core directly.
The module boundary is the enforcement: an Android import in `weave-core` fails the build. Phase 1
MUST create `weave-core`. `weave-compose` and `weave-runtime` MAY start as packages inside `:app`
(`com.echoflow.weave.compose`, `com.echoflow.weave.runtime`) and be split out later; the package
names above are normative either way.

---

## 2. The pipeline

```
MarkdownExtensionBlock(body, complete)            (from MarkdownStreamPlanner, per recomposition)
  → WeaveSession.update(body, complete)           (main thread; cheap: compares lengths)
      → parse job (Dispatchers.Default, conflated) : IncrementalParser.update → AutoFix → Checker
      → publishes WeaveProgram (StateFlow)          (main thread receives)
  → StateStore.sync(program)                        (creates/keeps/drops state, §4.2)
  → WeaveSurface(program, session)                  (Compose)
      → BindingCache: derivedStateOf per binding    (evaluation on main thread, snapshot reads)
      → NodeRenderer draws WNode trees
  → ActionBus (events) → transactions → deferred queue → ModelLoop / device / confirm
  → Persistence (debounced, Dispatchers.IO)
```

---

## 3. Core types (weave-core)

### 3.1 Syntax tree

```kotlin
data class Span(val line: Int, val start: Int, val end: Int)        // offsets into the normalised body

sealed interface Stmt { val span: Span }
data class HeaderStmt(val version: Int, val attrs: Map<String, Expr>, override val span: Span) : Stmt
data class StateDecl(val name: String, val type: WType?, val init: Expr,
                     val transient: Boolean, val reset: Boolean, override val span: Span) : Stmt
data class Binding(val name: String, val expr: Expr, override val span: Span) : Stmt
data class FnDecl(val name: String, val params: List<String>, val body: Expr?, val js: String?,
                  override val span: Span) : Stmt
data class Effect(val trigger: Call, val condition: Expr?, val action: Expr, val key: String,
                  override val span: Span) : Stmt
data class Export(val names: List<String>, override val span: Span) : Stmt   // "$x" for state
data class Delete(val target: String, override val span: Span) : Stmt          // "x", "$x" or "on <key>"

sealed interface Expr { val span: Span }
data class Lit(val value: WValue, override val span: Span) : Expr
data class Str(val parts: List<StrPart>, override val span: Span) : Expr      // text and interpolations
data class Name(val name: String, override val span: Span) : Expr
data class StateRef(val name: String, override val span: Span) : Expr
data class Qualified(val block: String, val name: String, override val span: Span) : Expr
data class Member(val target: Expr, val name: String, override val span: Span) : Expr
data class Index(val target: Expr, val index: Expr, override val span: Span) : Expr
data class Unary(val op: String, val operand: Expr, override val span: Span) : Expr
data class BinaryOp(val op: String, val left: Expr, val right: Expr, override val span: Span) : Expr
data class Ternary(val cond: Expr, val then: Expr, val otherwise: Expr, override val span: Span) : Expr
data class ListLit(val items: List<Expr>, override val span: Span) : Expr
data class RecLit(val fields: List<Pair<String, Expr>>, override val span: Span) : Expr
data class Lambda(val params: List<String>, val body: Expr, override val span: Span) : Expr
data class Call(val kind: CallKind, val callee: String, val leads: List<Expr>,
                val named: List<Pair<String, Expr>>, val children: List<Expr>?,
                override val span: Span) : Expr
enum class CallKind { COMPONENT, ACTION, MARK, FUNCTION }
```

### 3.2 Values

As 03 §1.1. Additionally:

- `WNode(type: String, args: Map<String, WValue>, children: List<WNode>, key: String,
  sourceStmt: String)` — `args` hold **evaluated** values (actions stay as `WAction`).
- `WAction(kind: String, args: List<Expr>, named: List<Pair<String, Expr>>, scope: Scope,
  origin: Span)` — equality: `kind`, `origin` and the identity of `scope`'s values read so far
  (implementations MAY simply use `origin` + `kind` equality and re-evaluate at execution, which is
  what 03 §7.2 requires anyway).
- `WFn` equality: by `origin` span.

### 3.3 Catalog

```kotlin
data class ParamSpec(val name: String, val type: TypeSpec, val default: WValue?, val required: Boolean,
                     val doc: String, val deprecated: String? = null)
data class ComponentSpec(
    val name: String, val leads: List<ParamSpec>, val params: List<ParamSpec>,
    val positionalOrder: List<String>, val aliases: Map<String, String>,
    val children: ChildrenSpec, val tier: Int, val phase: Int, val doc: String,
    val placeholder: PlaceholderKind, val blockLevel: Boolean,
    val intrinsics: Boolean,   // false if it can't answer intrinsic queries (04 §3.3)
)
data class MarkSpec(val name: String, val container: Set<String> /* "Chart", "Canvas" */,
                    val leads: List<ParamSpec>, val params: List<ParamSpec>,
                    val acceptsChildren: Boolean, val tier: Int, val phase: Int, val doc: String)
data class ActionSpec(val name: String, val leads: List<ParamSpec>, val params: List<ParamSpec>,
                      val local: Boolean, val needsConfirm: Boolean, val tier: Int, val phase: Int,
                      val doc: String)
data class FunctionSpec(val name: String, val params: List<ParamSpec>, val lazyArgs: Set<Int>,
                        val returns: TypeSpec, val tier: Int, val doc: String)
interface Catalog {
    fun component(name: String): ComponentSpec?
    fun mark(container: String, name: String): MarkSpec?
    fun action(name: String): ActionSpec?
    fun function(name: String): FunctionSpec?
    fun tokens(set: TokenSet): List<String>
    fun restrictedTo(tier: Int, phase: Int): Catalog
}
```

`BuiltinCatalog` contains every entry in 03 §7, 03 §10, 04 §4–§15. A unit test asserts that every
heading in 04 has a spec and vice versa (07 §9).

The catalog the checker and prompt generator actually use is
`BuiltinCatalog.restrictedTo(tier, buildPhase)` **further restricted to components the renderer
registry (`weave-compose` `Registry.kt`) has an entry for**. A component the build can't draw is
never advertised and is checked as `E209`, whatever its phase tag says. This is what lets phase 1
ship in steps (01 §29) without retagging 04.

### 3.4 Program

```kotlin
class WeaveProgram(
    val header: Header,                         // id, title, size, icon, keep, version
    val states: LinkedHashMap<String, StateDecl>,
    val bindings: LinkedHashMap<String, Binding>,
    val functions: LinkedHashMap<String, FnDecl>,
    val effects: LinkedHashMap<String, Effect>, // by trigger key
    val exports: Set<String>,
    val root: String?,
    val diagnostics: List<Diagnostic>,
    val complete: Boolean,                      // fence closed
    val frozenCount: Int,                       // statements confirmed (streaming)
)
```

`WeaveProgram` is immutable. `Edits.apply(program, edit): WeaveProgram` implements 02 §11.

---

## 4. Runtime (weave-runtime)

### 4.1 Sessions

One `WeaveSession` per block instance, keyed by `(messageId, blockKey)` where `blockKey` =
`MarkdownExtensionBlock.key`. `WeaveRuntime.rememberSession(messageId, block)` returns the same
session across recompositions and while the message stays in the chat list's composition; sessions
are held in an LRU of 64 per chat screen and closed (effects stopped, state flushed) when evicted
or when the screen is disposed.

### 4.2 StateStore

- Holds `SnapshotStateMap<String, WValue>`.
- `sync(program)`: for each declared state not present → initialise it **when it is ready** under
  03 §7.1 (from storage if a stored value exists; otherwise only once every name its initial
  expression reaches has arrived, or at fence close). A state that is not ready yet stays absent
  and is retried on every later `sync`. For states no longer declared after fence close → remove.
  During streaming, states are only added, never removed.
- All reads happen through the map, so Compose tracks them.

### 4.3 BindingCache

- `derivedStateOf { evaluator.eval(binding) }` per binding, created lazily on first read, dropped
  when the program version changes for bindings whose statement changed.
- The evaluator reads state through `StateStore`, other bindings through `BindingCache` (so the
  dependency graph is Compose's snapshot graph), queries through `Queries`, and context values
  through a `ContextValues` object whose fields are `State`s updated from the Compose environment
  (`LocalConfiguration`, theme, visibility).
- Cycle detection (03 §5.2) uses a thread-local in-progress set.
- Each cached result is `Evaluated(value: WValue, waiting: Boolean)`. `waiting` is true when
  evaluation read a state absent from `StateStore` (03 §7.1), directly or through another cached
  binding. `NodeRenderer` draws the placeholder for a node whose arguments evaluated with
  `waiting = true`. Because the read of the absent key is itself tracked by Compose, the node
  recomposes when the state is added.

### 4.4 Threading

| Work | Thread |
|---|---|
| Normalise, lex, parse, auto-fix, check | `Dispatchers.Default`, one conflated job per session (latest body wins) |
| Evaluation of bindings and actions | Main thread (inside composition or the ActionBus transaction) |
| Frame effects | Main thread, `withFrameNanos` |
| Model loop requests, queries, tools | `Dispatchers.IO`, results posted to main |
| Persistence | `Dispatchers.IO`, debounced |
| Chart downsampling and Audio waveform decode | `Dispatchers.Default` |

Evaluation MUST NOT block: budgets (03 §5.3) guarantee bounded work.

**Bindings are main-thread only.** `BindingCache`, `StateStore` and `ContextValues` MUST be read only
on the main thread (they are Compose snapshot state, and binding evaluation is the snapshot graph).
Work on `Dispatchers.Default` — chart downsampling, waveform decode — receives already-evaluated,
immutable `WValue`s captured on the main thread and MUST NOT call the evaluator or read a binding.
Debug builds assert this (`check(Looper.myLooper() == Looper.getMainLooper())` in
`BindingCache.read`). `[T-465]`

### 4.5 ActionBus

Implements 03 §7.2 exactly: `fire(action: WValue, eventData: WValue?, origin: EventOrigin)`.
Transactions use `Snapshot.withMutableSnapshot { … }`. The deferred queue is a coroutine `Channel`
consumed in order on the main thread. `EventOrigin` is `GESTURE`, `EFFECT` or `ASYNC`; outbound
actions are rejected unless `GESTURE` (03 §7.4).

### 4.6 Effects

`Effects.install(program, session)` launches one coroutine per effect in the session's scope:

- `change`: `snapshotFlow { read(x) }.drop(1).distinctUntilChanged()` with optional `debounce`.
- `every`: `while (true) { delay(interval); if (visible && foreground) fire() }` — the first delay
  starts when the block becomes visible (restart on each visibility change).
- `appear`/`at`: check the persisted "fired" flags (§9.1 `firedJson`).
- `frame`: `withFrameNanos` loop guarded by the global frame lease (03 §8.6).
- `tilt`/`shake`: `SensorManager` listener registered only while visible and foreground.

### 4.7 Visibility

The chat `LazyColumn` item hosting the message reports the block's visible fraction via
`Modifier.onGloballyPositioned` + the list's viewport bounds. `block.visible` = visible area > 0.
App foreground = `ProcessLifecycleOwner` at least `STARTED`.

---

## 5. Rendering (weave-compose)

### 5.1 Registration

In the app, the chat screen provides:

```kotlin
CompositionLocalProvider(
    LocalMarkdownBlockExtensions provides mapOf(
        "weave" to WeaveBlockExtension(runtime),
        // "weave-json" to WeaveBlockExtension(runtime),   // phase 2 (02 §12)
    )
) { … MarkdownText(…) … }
```

`MarkdownStreamPlanner` must be constructed with `extensionKinds = setOf("weave")` in phase 1, and
`setOf("weave", "weave-json")` from phase 2. An unregistered kind renders as an ordinary code fence.
Edit fences (`weave edit <id>`) use kind `weave` and are dispatched by the info string.

When the effective tier for the chat is 0 **and** the message has no blocks, nothing changes. Blocks
already in a thread always render regardless of the current level (05 §11.4).

### 5.2 Edit fences in replies

An edit fence (`weave edit loan`) in a later reply renders as a compact, non-interactive chip in
that reply — `edit` icon, "Updated <block title>" — tapping it scrolls to the block. The edit is
applied to the target block when the fence closes (not while streaming).

### 5.3 No exceptions in composition

Compose can't catch exceptions thrown inside composables. Therefore:

- All evaluation happens in `BindingCache`/`Evaluator`, which catch everything and return `null`
  plus a diagnostic.
- Component renderers receive already-evaluated, already-coerced arguments (`ResolvedArgs`) and
  MUST be total: no `!!`, no unchecked casts, no indexing without bounds checks.
- A renderer test (07 §6) fuzzes every component with random argument values; any crash fails the
  build.

### 5.4 Node rendering

`NodeRenderer.Render(node: WNode)` looks up `Registry[node.type]` → a `@Composable (ResolvedArgs,
children: List<WNode>, ctx: RenderCtx) -> Unit`. The common modifier chain (04 §2.2) is built once
by `Modifiers.common(args, ctx)`. Children are rendered with `key(child.key) { Render(child) }`.

### 5.5 Placeholders

`Placeholders.forSlot(slotType, partialCall)` implements 04 §2.9. A reference to a binding that is
not yet frozen or speculative renders the placeholder.

### 5.6 Off-screen behaviour

- When a block has been fully off-screen for 5 s: stop `every`/`frame`/sensor effects (already
  visibility-gated), pause and release `Video`, `Audio` and `Model3D` players, stop `Shader` time.
  `Map` (when implemented) is replaced by a bitmap snapshot taken before release.
- When it returns, everything resumes; players restore position.

---

## 6. Streaming

### 6.1 Update rule

`WeaveSession.update(body, complete)` is called on every recomposition of the extension block. It
does nothing if `body.length` and `complete` are unchanged. Otherwise it posts the body to the
conflated parse job.

### 6.2 Program publication

The parse job publishes a new `WeaveProgram` when **either** the set of frozen statements grew,
**or** the speculative tail's parse result changed, **or** `complete` changed. Publications are
throttled to at most one per 50 ms during streaming (the last one is always delivered).

### 6.3 First paint

The block frame (and placeholder for `root`) appears as soon as the fence opens. If `root` has not
arrived after the first 3 statements, the renderer shows the placeholder for an unknown node so the
user sees something is coming.

### 6.4 Fence close

On `complete = true`: full re-parse from scratch (02 §10.3), `StateStore.sync`, effects installed,
missing-ref placeholders removed, layout height settles (04 §2.9).

---

## 7. Block chrome, sizes and full-screen

### 7.1 Chrome

| `size` | Chrome |
|---|---|
| `inline` | None. The root renders directly in the reply column. A 24 dp `more_horiz` icon button appears at the block's top-end corner only on long-press of the block (menu below). |
| `card` | `OutlinedCard` (shape `l`, `surfaceContainerLow`) with a header row: icon (from header, default `widgets`), title (`titleSmall`), spacer, `open_in_full` `IconButton` (expand), `more_vert` menu. Content padding 16 dp. |
| `wide` | Like `inline` but the block extends to the screen's horizontal edges (negative horizontal padding equal to the reply column's padding); content gets 16 dp side padding back except for `Carousel`, `Map`, `Table`, `Gallery`, `Image` direct children of root. |
| `full` | A `card` preview of the block (max 240 dp tall, gradient fade at the bottom) with a filled `Button("Open")`. Opening enters full-screen mode with co-pilot (§7.4). |

### 7.2 Block menu (⋯)

Items, in order: **Undo** (enabled when history has entries) · **Versions** (when > 1 version) ·
**Change this block…** (remix, tier 3) · **Keep in Library** · **Share** (as image / as `.weave` /
data) · **View source** (canonical print, monospace, copyable) · **Show as plain text** · **Reset**
(`!resetAll` with a confirm dialog) · **Diagnostics** (debug builds only).

**Show as plain text** is the inverse of "Make interactive" (§11.6), for a block the user didn't
want. It replaces the block in place with its plain-text rendering (04 §2.4) as ordinary Markdown,
and shows a one-line "Show interactive" text button under it to switch back. The choice is stored
per block in `uiStateJson` (`"plain": true`) and survives restarts. Nothing is sent to the model and
the block's state is kept. When a message's blocks are shown as plain text, history (§9.4) sends
that plain text instead of the source. `[T-466]`

### 7.3 Expand and collapse

`!expand` (or the expand button) shows the block in a full-screen route `weave/<messageId>/<blockKey>`
using a shared-element transition (`SharedTransitionLayout`, `sharedBounds` keyed by
`"weave-<messageId>-<blockKey>"`). The same `WeaveSession` is reused (state continues).
`block.expanded = true`. Top app bar: back arrow, title, block menu. Back or `!collapse` returns.

Between **versions** (edits), nodes whose key (04 §2.5) exists in both versions animate their bounds
with `animateContentSize`/`sharedElement` inside the block; others cross-fade (`motion: default`).

### 7.4 Co-pilot (full-screen, `size: full` or any expanded block at tier 3)

The full-screen route shows the chat composer docked at the bottom with the placeholder
"Change this…". Sending text makes a remix request (§8.3) instead of a chat message. A "Send to
chat" toggle in the composer switches to sending a normal chat message with the block attached
(`!say` with `attach: [all state]`).

---

## 8. The model loop

All hidden requests go through the existing `LlmGateway.stream(LlmStreamRequest)` with:
`model` = the UI model (§8.8) or the chat's model; `chatId` = the thread id; `history` = synthetic,
**never persisted** `ChatMessage`s built as below; `systemPrompt` from 06; `params` = the cloud or
local inference params with `temperature` forced to `0.4` and `maxTokens` to `4096`. Requests are
tagged so they never create chat messages, never update the thread's `updatedAt` or title, and are
excluded from reply notifications. Usage is recorded in the usage ledger under the feature name
`echo_ui`.

### 8.1 Response parsing (shared)

1. Collect the streamed text (no partial rendering for hidden requests, except `lazy`, §8.5).
2. Take the **first** fence whose info string starts with `weave` or `weave-json`. If none, treat the
   whole text as a Weave body.
3. Parse as an edit (02 §11) for `!ask`/remix/repair, as a fragment for `lazy`, as a block for
   `!regen`.
4. Timeout: 60 s from request start (90 s for `!regen`). Timeout = failure.

### 8.2 `!ask`

Request `history` = one user message whose content is the template in 06 §4.1 filled with: block
source (canonical print of the current version), current state (§8.6), the `update` names, the
`with` values, the prompt text, and the last 5 `!ask` exchanges for this block (prompt + names
updated, no source). Apply per 03 §9.2. Record the exchange in `weave_block.askLogJson` (max 5).

### 8.3 Remix (Change this block…, co-pilot)

Same as `!ask` with `update` = **all** bindings and the user's text as the prompt, template 06 §4.2.
States may be re-declared (with or without `reset`), effects may be added/removed. Becomes a new
version with cause `remix`.

### 8.4 Cost guards

- At most 1 in-flight hidden request per block (any kind). A new `!ask` cancels an older `!ask`;
  a remix or `!regen` while another request is in flight is queued (max 1 queued, latest wins).
- `!ask` + `lazy` + repair requests per block per rolling hour: max **20**. The 21st shows an inline
  banner on the block: "This block has updated 20 times this hour. Keep updating?" with
  "Keep updating" (allows 20 more) and "Stop" (disables `!ask`/`lazy` for this block until the app
  restarts).
- On metered providers (all except local models), repair rounds (§8.9) are off unless enabled in
  Settings → Intelligent UI → "Auto-fix broken blocks".

### 8.5 `lazy`

- Cache key = SHA-256 hex of the canonical JSON `{"p": prompt, "c": {context sorted by key}}`
  (values encoded with §8.6 rules, no truncation).
- Request: template 06 §4.3 with block source, state, the prompt and context.
- The fragment streams: the lazy node renders it progressively (same incremental parser), so a
  generated tab fills in as it arrives.
- Stored in `weave_block.lazyJson` as `{bindingName: {key, source}}`.

### 8.6 Serialising values for the model

Used in the history state line (06 §2.4, the only history encoding), `!ask`, `lazy`, `!say(attach:)`:

| Value | JSON |
|---|---|
| null, bool, num, str | as JSON (numbers: shortest round-trip) |
| dur | `"<n>ms"` string |
| date / time / datetime | ISO-8601 string (`"2026-12-01"`, `"06:40"`, `"2026-12-01T06:40:00+05:30"`) |
| list, rec | JSON arrays / objects |
| sym | its name as a string |
| image | `"[image]"` (the image itself is attached as an image part only for `!say(attach:)` and only if the model supports images) |
| node / action / fn | `"[node]"`, `"[action]"`, `"[function]"` |

Truncation for history context (not for `!ask`), applied in this order until the **complete
serialised line** (keys, values, punctuation) is at most 2,000 characters:

1. Any JSON string value over 200 characters is cut to 200 + `…`; any list over 20 items becomes
   `"list(<n>)"`.
2. If still over: every first-level value becomes `"…"`, keys kept.
3. If still over: keep first-level keys in source order of their state declarations while the line,
   including a final `"…":"+<n> more"` entry, fits; drop the rest and set `<n>` to the number
   dropped.

Step 3 always terminates: an object holding only the `"…"` entry is under 2,000 characters.
`[T-464b]`

### 8.7 Tools and consent

- Block-safe tools are registered in `WeaveToolRegistry` with: `name`, `description`, `argsSchema`,
  `consentText(args) → String` (e.g. `search the web for "USD to INR rate today"`), and
  `run(args) → WValue`. Phase 2 registers `web.search` (via the user's configured search provider),
  `web.read`, `model.extract`; `sandbox.python`, `image.generate` and `connector.*` are registered
  only when the corresponding feature is configured.
- Consent UI: in place of the query's dependent node (or as a strip at the top of the block for
  `!tool`), a `surfaceContainerHigh` row: `shield` icon, "This block wants to <consentText>",
  buttons **Allow once**, **Always for this block**, **No**. Grants are stored in
  `weave_block.consentJson` as `{toolName: "always"}`; "Allow once" is not stored. "No" leaves the
  query in `error: "Not allowed"`.
- Consent can be revoked in the block menu → "Permissions".

### 8.8 The UI model

Settings → Intelligent UI → "Model for live updates": **Same as chat** (default) or any configured
chat model. Used for `!ask`, `lazy`, remix and repair — never for the main reply.

### 8.9 Repair round

After fence close, if the block has errors that removed at least one statement **or** produced an
error chip, and repairs are enabled (§8.4), make one request with template 06 §5 (the diagnostics as
their exact messages, 02 §9, and the canonical print of the affected statements). Apply the edit only
if the number of errors decreases; else discard. At most one repair per block version.

---

## 9. Storage

### 9.1 Room entities (database version N + 1)

```kotlin
@Entity(
    tableName = "weave_blocks",
    foreignKeys = [ForeignKey(entity = ChatMessage::class, parentColumns = ["id"],
                              childColumns = ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["messageId", "blockKey"], unique = true), Index("chatId"), Index("blockId")],
)
data class WeaveBlockRow(
    @PrimaryKey val id: String,              // UUID
    val chatId: String,
    val messageId: String,
    val blockKey: Int,                       // MarkdownExtensionBlock.key
    val blockId: String,                     // header id (unique per chat)
    val version: Int,                        // current version number, starts at 1
    val form: String,                        // "source" | "json"
    val source: String,                      // canonical print of the current version
    val stateJson: String,                   // §8.6 encoding of non-transient state, no truncation
    val historyJson: String,                 // 03 §7.5 entries
    val lazyJson: String,                    // §8.5
    val queryJson: String,                   // last query values by binding name
    val consentJson: String,                 // §8.7
    val firedJson: String,                   // {"appear": true, "at:<key>": true}
    val askLogJson: String,                  // §8.2
    val uiStateJson: String,                 // per-node UI state by node key (sections, sort, tabs scroll)
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "weave_versions",
    foreignKeys = [ForeignKey(entity = WeaveBlockRow::class, parentColumns = ["id"],
                              childColumns = ["blockRowId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["blockRowId", "version"], unique = true)],
)
data class WeaveVersionRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val blockRowId: String,
    val version: Int,
    val source: String,
    val cause: String,                       // "stream" | "edit" | "ask" | "remix" | "repair" | "regen" | "restore"
    val createdAt: Long,
)

@Entity(tableName = "weave_library")
data class WeaveLibraryRow(
    @PrimaryKey val id: String,
    val title: String,
    val icon: String,
    val source: String,
    val stateJson: String,
    val lazyJson: String,
    val pinnedWidgetId: Int?,                // phase 3
    val createdAt: Long,
    val updatedAt: Long,
)
```

`chat_threads` gains one column: `intelligentUi TEXT` (nullable; §11.2).

### 9.2 Migration N → N + 1

```sql
CREATE TABLE IF NOT EXISTS `weave_blocks` (
  `id` TEXT NOT NULL, `chatId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `blockKey` INTEGER NOT NULL,
  `blockId` TEXT NOT NULL, `version` INTEGER NOT NULL, `form` TEXT NOT NULL, `source` TEXT NOT NULL,
  `stateJson` TEXT NOT NULL, `historyJson` TEXT NOT NULL, `lazyJson` TEXT NOT NULL,
  `queryJson` TEXT NOT NULL, `consentJson` TEXT NOT NULL, `firedJson` TEXT NOT NULL,
  `askLogJson` TEXT NOT NULL, `uiStateJson` TEXT NOT NULL,
  `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
  FOREIGN KEY(`messageId`) REFERENCES `chat_messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE UNIQUE INDEX IF NOT EXISTS `index_weave_blocks_messageId_blockKey` ON `weave_blocks` (`messageId`, `blockKey`);
CREATE INDEX IF NOT EXISTS `index_weave_blocks_chatId` ON `weave_blocks` (`chatId`);
CREATE INDEX IF NOT EXISTS `index_weave_blocks_blockId` ON `weave_blocks` (`blockId`);
CREATE TABLE IF NOT EXISTS `weave_versions` (
  `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `blockRowId` TEXT NOT NULL, `version` INTEGER NOT NULL,
  `source` TEXT NOT NULL, `cause` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
  FOREIGN KEY(`blockRowId`) REFERENCES `weave_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE UNIQUE INDEX IF NOT EXISTS `index_weave_versions_blockRowId_version` ON `weave_versions` (`blockRowId`, `version`);
CREATE TABLE IF NOT EXISTS `weave_library` (
  `id` TEXT NOT NULL, `title` TEXT NOT NULL, `icon` TEXT NOT NULL, `source` TEXT NOT NULL,
  `stateJson` TEXT NOT NULL, `lazyJson` TEXT NOT NULL, `pinnedWidgetId` INTEGER,
  `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
ALTER TABLE `chat_threads` ADD COLUMN `intelligentUi` TEXT DEFAULT NULL;
```

The exact SQL MUST match what Room generates for the entities (verify with the exported schema
JSON and a `MigrationTestHelper` test, as the existing `*MigrationTest`s do). If the database
`N` is `AppDatabase.version` at the time this ships (32 when this spec was written). Never hard-code
33: take whatever the next free version is, and name the migration after it.

### 9.3 When rows are written

| Event | Write |
|---|---|
| Fence closes for a new block | Insert `weave_blocks` (version 1) and `weave_versions` (cause `stream`) |
| State transaction committed | Update `stateJson`, `historyJson`, `updatedAt` — debounced 500 ms per block, flushed immediately on `ON_STOP` |
| Edit / ask / remix / repair / regen / restore applied | Increment `version`, update `source`, insert `weave_versions` |
| Lazy result, query result, consent, fired flag, UI state change | Update the matching JSON column (debounced 500 ms) |
| Message deleted | Cascade |
| Message regenerated (the reply is replaced) | The old message's rows are deleted with it; the new reply's blocks start fresh |

The **original streamed text** of the message is never modified; the current version lives in
`weave_blocks.source`. When a chat is rendered, a block whose row exists renders from the row (the
fence text in the message is only used if there is no row).

### 9.4 Sending history to the model

When building `history` for a normal chat request, each assistant message containing blocks is sent
with each block fence's body replaced by the row's current canonical source, followed by the state
line from 06 §2.4. Blocks more than 10 assistant turns back are reduced to their header line plus the
state line. Edit fences are dropped from history (their effect is in the current source).

### 9.5 Rendering a node to an image

For `!share`/`!export(png|pdf)`: render the node off-screen in a `ComposeView` attached to the
activity window at the current width, with the current theme, using `GraphicsLayer.toImageBitmap()`
at 2× density; PDF via `PdfDocument` with one page sized to the content (A4 width scaling if wider).
Popups/overlays are not included. Animated content renders at its current frame.

---

## 10. Tiers

### 10.1 Tier table

`EchoUiTiers.kt` holds a curated `Map<String, Int>` from model-id **prefix** to tier, longest prefix
wins, updated each release from the eval harness (07 §10). Initial rules until evals run:

| Model | Default tier |
|---|---|
| Flagship cloud models (curated list: large frontier models from the providers EchoFlow supports) | 3 |
| Mid-size cloud models with tool use | 2 |
| Small cloud models (< 20B parameters where known) | 1 |
| On-device (local) models | 0 |

**Live UI is cloud-only in this version. This is a product decision, not a side effect.** On-device
models get tier 0, so they receive no Weave prompt section (§11.3), never stream blocks, and the
"Make interactive" action (§11.6) is hidden for them. Blocks already in a chat still render and run
when the user switches that chat to a local model — rendering and local actions need no model —
but hidden requests (`!ask`, `lazy`, remix, repair) are disabled with the tooltip "Live updates need
a cloud model". The Intelligent UI settings page says this in one line under the level cards:
"Live UI works with cloud models." The user override (§10.3) is not offered for local models.
Revisit only with a measured eval (07 §10) of a local model.

Tier 4 is never a default in Phase 1–2. In Phase 3, curated flagship models may be raised to 4 after
passing the tier-4 eval suite.

### 10.2 Unknown models

If a model id matches no prefix: tier **2** when the provider reports tool-use support and context
≥ 32k tokens; tier **1** when context ≥ 16k; else **0**.

### 10.3 User override

Settings → Models → (a model) → "Live UI capability": **Automatic** (default; uses §10.1–10.2),
**Off** (0), **Display** (1), **Interactive** (2), **Agentic** (3), **Extreme** (4). Stored in
SharedPreferences key `echoui_tier_override_<modelId>` as an int, absent = automatic.

### 10.4 Auto-downgrade

For each model, keep a rolling record (SharedPreferences `echoui_quality_<modelId>`, a JSON array of
the last 10 blocks' error counts, counting each error that removed a statement or produced an error
chip). If the average exceeds 2.0, the automatic tier is lowered by one for 7 days
(`echoui_downgrade_until_<modelId>` = epoch ms). User overrides are never downgraded.

---

## 11. The Intelligent UI setting

### 11.1 Global setting

- Model: `enum class IntelligentUiLevel(val storageKey: String) { OFF("off"), BALANCED("balanced"), IMMERSIVE("immersive") }`.
- Stored in the app's settings SharedPreferences (the one `SettingsRepository` uses) under key
  **`intelligent_ui_level`**; absent or unknown value → `BALANCED`. Exposed by `SettingsRepository`
  as `intelligentUiLevel: StateFlow<IntelligentUiLevel>` and `setIntelligentUiLevel(level)`.
- UI: Settings → new top-level row **Intelligent UI** (icon `auto_awesome`, summary = the level's
  short description, 01 §20.5) in the main list of `SettingsScreen`, directly after **Web search**
  (the main list is a flat list of category rows; there is no "chat" section). It opens a
  `SettingsPageScaffold` page with:
  - three selectable cards (radio semantics), in order **Off**, **Balanced** (with a "Default"
    badge), **Immersive**; each card shows its name (`titleMedium`), short description
    (`bodyMedium`) and a static preview image of the same answer at that level (phase 2; phase 1
    shows the description only);
  - "Model for live updates" (§8.8);
  - "Auto-fix broken blocks" switch (§8.4), default off;
  - a link "What is Intelligent UI?" opening a short explainer sheet.

### 11.2 Per-chat override

- `chat_threads.intelligentUi`: `NULL` (follow the global setting), `"off"`, `"balanced"` or
  `"immersive"`.
- UI: the chat's `+` menu (`ChatPlusMenu.kt`, today a list of feature toggles) gains one row,
  **Intelligent UI**, whose trailing text is the current level. Unlike the toggles, tapping it opens
  a small submenu: "Use default (<global level>)", "Off", "Balanced", "Immersive". It is hidden when
  the chat's model is local (§10.1). Verify on the emulator (it is a popup; 07 `T-410`).

### 11.3 Effective tier

For each chat request, compute:

```kotlin
fun effectiveTier(
    level: IntelligentUiLevel,          // chat override ?: global
    modelTier: Int,                     // §10 after override and downgrade
    explicitUiRequest: Boolean,         // §11.5
    makeInteractive: Boolean,           // "Make this interactive" action
    shippedTier: Int,                   // highest tier implemented in this build (phase)
): Int {
    val cap = when {
        makeInteractive -> 4
        level == OFF && explicitUiRequest -> 3          // Balanced rules for this one request
        level == OFF -> 0
        level == BALANCED -> 3
        else -> 4                                       // IMMERSIVE
    }
    return minOf(cap, modelTier, shippedTier)
}

fun promptLevel(level: IntelligentUiLevel, explicitUiRequest: Boolean, makeInteractive: Boolean) = when {
    makeInteractive -> IntelligentUiLevel.BALANCED      // with the "make interactive" instruction, 06 §2.3
    level == OFF && explicitUiRequest -> IntelligentUiLevel.BALANCED
    else -> level
}
```

If `effectiveTier == 0`, no Echo UI text is added to the system prompt.

### 11.4 What each level changes

| | Off | Balanced | Immersive |
|---|---|---|---|
| Echo UI prompt sent | No (except §11.5 / Make interactive) | Yes, Balanced text (06 §2.2) | Yes, Immersive text (06 §2.2) |
| Tier cap | 0 | 3 | 4 |
| Max blocks rendered per reply | 0 new (existing still render) — except §11.5 / Make interactive: 1 | 1 (extra blocks render as code) | 3 (extra render as code) |
| `lazy`, `!ask` allowed | — | Yes (cost guards) | Yes (cost guards) |
| Repair round | never | if enabled | if enabled |
| Suggest full-screen | — | no | yes (prompt allows `size: full`) |

"Max blocks per reply" counts `weave`/`weave-json` fences that are not edits, in order; the excess
fences render as plain code blocks with no chip. The limit comes from the **prompt level**
(`promptLevel`, §11.3), not the raw level: a reply to an explicit UI request (§11.5) or to "Make
interactive" (§11.6) is generated under Balanced rules, so it may render **1** new block even
though the chat is Off. Every other reply in an Off chat renders 0 new blocks. `[T-605b]`
`[T-606b]`

Blocks already in a thread **always render and work** whatever the level (a level change applies to
new replies only).

### 11.5 Explicit UI requests (when the level is Off)

The latest user message is an explicit UI request if, lower-cased, it matches this regular
expression:

```
\b(make|build|create|draw|show|generate|turn|put|give (me|us))\b[^.?!\n]{0,40}\b(calculator|chart|graph|plot|table|quiz|flashcards?|slider|dashboard|tracker|timer|countdown|planner|checklist|kanban|widget|form|wizard|game|simulat\w*|ui)s?\b
|\b(plot|chart|graph|visuali[sz]e|tabulate|simulate)\s+(it|this|that|these|those|my|the|our|how)\b
|\b(as|into)\s+(a |an )?(card|chart|graph|table|ui|calculator|checklist)\b
|\bin\s+(a |an )?(chart|graph|table)\b
|\bmake\s+(it|this|that)\s+interactive\b
```

(whitespace and newlines between the alternatives above are not part of it). The rule is an
**action plus an object**: a bare noun (`plot`, `table`, `game`, `form`) is never enough, because the
cost of a false positive is a Balanced-sized prompt on a turn the user asked to keep plain. It is
deterministic and tested in 07 §11, including the false positives it must not produce ("the plot of
the novel", "any good tabletop games?", "explain the periodic table", "give up on this game", "lay
the table for dinner", "my money is stuck in a card"). `give` counts only as `give me` / `give us`,
and `in a …` only before `chart`, `graph` or `table`. Known accepted false
positive: "show me how the table of contents works". `[T-608]` `[T-608b]`

### 11.6 "Make this interactive"

An action in the assistant message's action bar (icon `auto_awesome`, label "Make interactive"),
shown when the chat's model tier ≥ 1 and the message has no blocks. It sends a hidden-from-history
user turn — the 06 §2.3 instruction plus the message text — and the reply replaces nothing: it is a
new assistant message containing a block, rendered below, with the user turn shown as a small
"Made interactive" divider instead of a bubble.

---

## 12. Prompt assembly

- `SystemPrompts.build(...)` (and the custom-provider variant) gains a section produced by
  `EchoUiPrompt.section(effectiveTier, promptLevel, form, catalog)` (06 §2). It is inserted
  **immediately before `freshnessGate`**, which stays the last section: `SystemPrompts` documents
  the freshness gate as the recency slot, the last thing the model reads before answering, and a
  multi-thousand-token Weave section after it would bury it. Every builder that ends with
  `freshnessGate` follows the same rule. When `effectiveTier == 0` the section is the empty string
  and nothing is inserted. `[T-505]`
- Token cost is accepted. Balanced adds roughly 1–4k tokens to each cloud turn (measure the real
  number in the prompt snapshot test, 07 §9, and record it there). Cloud models EchoFlow supports
  have context windows of hundreds of thousands of tokens, so this is not a reason to change the
  default; it is a number to watch.
- The section is cached by `(effectiveTier, promptLevel, form, catalogVersion)`.
- `form` is always `source` in phase 1 (Weave-JSON is phase 2, 02 §12). From phase 2, `form` is
  `json` when the model's tier ≤ 2 **and** the tier table marks that model as measured to do better
  with schema-constrained output (07 §10), else `source`. The user can't change it.
- The JSON Schema (02 §12.4) is passed to providers that support `response_format` only for hidden
  requests (§8), never for normal replies (a normal reply is prose with an embedded fence).

---

## 13. Safety

### 13.1 Confirm sheet

All `needsConfirm` actions (03 §7.4) show one `ModalBottomSheet`:

- Header: the block's icon and title in `labelLarge` `onSurfaceVariant` — always shown, so a block
  can't pretend to be system UI.
- Title (`titleLarge`): the action in plain words ("Open link", "Add to calendar", "Share", "Send
  message").
- Body: the exact payload — full URL with the **registrable domain in bold**, event title/time,
  number, the file name and type, the text to be sent.
- Buttons: `TextButton("Cancel")`, `Button(<verb>)`.

### 13.2 Anti-spoofing rules

- `Dialog` always shows the block title (04 §11).
- Full-screen mode always shows the top app bar with the block title.
- `TextField` refuses secret-shaped labels (04 §7, `[T-400]`).
- A block can't draw over other messages: block content is clipped to its own bounds (`clipToBounds`
  on the block frame), and `Popover`/`Menu` popups are dismissed when the block scrolls off-screen.

### 13.3 Limits

`Limits.kt` constants, used by the checker and runtime:

```kotlin
object Limits {
    const val SOURCE_BYTES = 48 * 1024
    const val STATEMENTS = 400
    const val NODES_PER_BLOCK = 600
    const val NODES_VISIBLE_TOTAL = 3_000
    const val DEPTH = 24
    const val EVAL_STEPS = 20_000
    const val RECURSION = 64
    const val STATE_BYTES = 256 * 1024
    const val EAGER_LIST_ITEMS = 30
    const val RANGE_ITEMS = 10_000
    const val STRING_CHARS = 100_000
    const val LIST_ITEMS = 100_000
    const val TIMERS_PER_BLOCK = 4
    const val MIN_TIMER_MS = 250L
    const val IMAGES_PER_BLOCK = 24
    const val IMAGE_BYTES = 4 * 1024 * 1024
    const val QUERIES_PER_BLOCK = 4
    const val MIN_QUERY_INTERVAL_MS = 60_000L
    const val ASKS_PER_HOUR = 20
    const val EFFECT_CHAIN = 8
    const val HISTORY_ENTRIES = 50
    const val JS_TIME_MS = 50L
    const val JS_MEMORY_BYTES = 16 * 1024 * 1024
    const val FRAME_BUDGET_NANOS = 2_000_000L
}
```

Exceeding `SOURCE_BYTES` or `STATEMENTS` while streaming: stop parsing further statements and show
"This block is too large" at the bottom (`E304`). An **edit** whose result would exceed either is
rejected whole and the block stays at its current version (02 §11.2).

`STATE_BYTES` caps the persisted size of one block's `stateJson` (§8.6 encoding, UTF-8, before any
history truncation). A write (03 §7.2) whose committed result would exceed it is **rejected**: the
whole transaction rolls back, the state keeps its previous value, the block shows a 4 s inline
chip "This block is full", and `E304` is logged.

The cap also applies to **initialisation** (03 §7.1), which is not an action write. States are
initialised in the order 03 §7.1 gives; after each one, the encoded size of all states initialised
so far is checked. A state whose initial value would take the total past `STATE_BYTES` is
initialised to `null` instead, `E304` (`This block is too large (state).`) is logged, and the block
shows the same "This block is full" chip once. Later states are still tried in order, so small
states after a huge one still initialise. The same check applies to values restored from storage
and to new states added by an edit (02 §11.3).

`historyJson` keeps at most `HISTORY_ENTRIES` entries (03 §7.5) and is dropped oldest-first until it
is also under `STATE_BYTES`. Together these bound a block's Room row at roughly
`SOURCE_BYTES + 2 × STATE_BYTES` plus the small JSON columns. `[T-467]`

Exceeding `NODES_VISIBLE_TOTAL`: blocks furthest from the viewport render their placeholder-sized snapshot instead of live UI.

---

## 14. Privacy

- No telemetry. Diagnostics, quality records (§10.4) and usage stay on the device.
- Block state is sent to a model only as described in §8 and 06; nothing else leaves the device
  except through confirmed actions and consented tools.
- `.weave` exports contain source and state only (no chat content).

---

## 15. Build flags

`BuildConfig.ECHO_UI_PHASE` (1, 2 or 3) sets `shippedTier` (1 → 2, 2 → 3, 3 → 4) and filters the
catalog by `phase` (`Catalog.restrictedTo(tier, phase)`). A developer setting (debug builds) can
lower it for testing.
