# Echo UI — specification set

Echo UI lets a model put **interactive, native UI** inside a chat reply: cards, sliders, charts,
tables, wizards, games. The model writes it in a small language called **Weave**. Users control how
much of it they get with the **Intelligent UI** setting: **Off**, **Balanced** (default) or
**Immersive**.

This folder is the complete specification. The goal is that **any two engineers, junior or senior,
who implement it independently produce the same behaviour**. Where a choice exists, the spec
makes it. If something is still ambiguous, that is a bug in the spec: raise it and get the spec
changed. Don't guess.

---

## 1. The documents

| # | Document | What it covers | Normative? |
|---|---|---|---|
| 01 | [Overview](01-overview.md) | The product idea, principles, the whole system at a glance, worked examples, phasing | Informative. Where it conflicts with 02–07, **02–07 win** |
| 02 | [Language](02-language.md) | Lexer, full grammar (EBNF), statements, calls, line continuation, header, auto-fix rules, diagnostics | **Normative** |
| 03 | [Semantics](03-semantics.md) | Values, types, coercion, operators, evaluation, state, actions, effects, every built-in function, every format | **Normative** |
| 04 | [Components](04-components.md) | Every component and mark: parameters, defaults, Compose mapping, layout, interaction, accessibility, streaming placeholder | **Normative** |
| 05 | [Runtime](05-runtime.md) | Kotlin packages and classes, threading, streaming, rendering, model loop protocol, storage schema, settings, tiers, limits | **Normative** |
| 06 | [Prompts](06-prompts.md) | The exact text sent to models for each Intelligent UI level and tier, and for hidden requests | **Normative** |
| 07 | [Conformance](07-conformance.md) | Numbered test cases with exact inputs and expected outputs. An implementation is correct when all pass | **Normative** |

A separate [critique](critique.md) reviewed v1.0 against the app. It is not normative. Its §7 records
what v1.1 accepted, changed or rejected.

## 2. Reading order by role

- **New to the project (junior):** README → 01 → 02 §1–5 → 03 §1–6 → 04 §1–3 → the component you
  are building in 04 → its tests in 07. Build one component end to end before reading the rest.
- **Building the language core (mid-level):** 02 completely → 03 completely → 07 §1–4.
- **Building the renderer:** 04 completely → 05 §5–7 → 03 §7–8 (actions and effects) → 07 §5–6.
- **Building the model loop, storage or settings:** 05 completely → 06 completely → 03 §9.
- **Reviewing or owning the whole thing (senior):** everything, in order. Check 07 covers each
  MUST in 02–06.

## 3. Requirement words

These words have exact meanings everywhere in this folder:

| Word | Meaning |
|---|---|
| **MUST** / **MUST NOT** | Required. An implementation that doesn't do this is wrong. |
| **SHOULD** / **SHOULD NOT** | Do this unless there is a written, reviewed reason not to. Record the reason in the PR. |
| **MAY** | Optional. If you do it, do it exactly as described. |

## 4. Glossary

| Term | Meaning |
|---|---|
| **Echo UI** | The whole system: language, runtime, renderer, settings. |
| **Weave** | The language the model writes. Also the info string of the fence: ```` ```weave ````. |
| **Weave-JSON** | The JSON form of the same language (fence `weave-json`), for models that do better with JSON. |
| **Block** | One Weave program inside one reply: one fenced section. A reply can contain several blocks. |
| **Statement** | One logical line of a block: a state declaration, binding, function, effect, export, delete or header. |
| **Binding** | A named, read-only, reactive value: `chart = Chart(...)`. |
| **State** | A named mutable value owned by a block: `$rate = 8.5`. |
| **Node** | A value that renders as UI: the result of calling a component. |
| **Component** | A capitalised UI building block from the catalog: `Card`, `Slider`, `Chart`. |
| **Mark** | A lowercase drawing element inside `Chart` or `Canvas`: `line(...)`, `circle(...)`. |
| **Action** | A value starting with `!` that describes something to do when triggered: `!set($x, 1)`. |
| **Effect** | A statement that runs an action when something happens: `on change($x) -> !haptic(tick)`. |
| **Token** | A named design value from a fixed set: `m` (12 dp spacing), `primary` (colour role). |
| **Catalog** | The Kotlin definition of every component, mark, token set, function and action. The single source of truth. |
| **Tier** | What a model is allowed to use, 0–4 (05 §10). |
| **Level** | The user's Intelligent UI choice: Off / Balanced / Immersive (05 §11). |
| **Effective tier** | The tier actually used for a request, from the model's tier and the level (05 §11.3). |
| **Edit** | A Weave fragment that changes an existing block: ```` ```weave edit <id> ````. |
| **IR** | Intermediate representation: the checked program (`WeaveProgram`) that both forms lower to. |
| **Diagnostic** | An error or warning with a code from 02 §9. |

## 5. Versioning

- The language version is the number after `@weave` (currently `1`).
- The spec version is in each document's header. A change to any normative document bumps the minor
  version (`1.0` → `1.1`) and adds a line to §6.
- Additions (new components, new functions, new optional parameters) are minor. Renames, removals or
  changed defaults are major and require a new language version.
- **Pre-release exemption.** Until the first EchoFlow release that ships Echo UI to users, every
  change is a minor bump, including renames, removals and changed defaults, and the language
  version stays `1`. No block exists in the wild that a change could break. The major-version rule
  above starts with that release; record it in §6 when it happens.
- The overview (01) summarises and links; it does not copy tables from 02–07. When a summary in 01
  disagrees with 02–07, fix 01.

## 6. Change log

| Spec version | Date | Change |
|---|---|---|
| 1.0 | 2026-11-26 | First complete set: 01–07. |
| 1.1 | 2026-11-26 | Resolves the critique (`critique.md` §7). Shape tokens resolve through `MaterialTheme.shapes`; spacing tokens through `Spacing` (Weave `l` = `Spacing.base`). States wait for the names their initialiser needs (03 §7.1). Same-trigger effects merge into `!if` branches (S12) instead of replacing. Over-limit edits and over-size state are rejected. One history encoding (`# state`). Weave section sits before `freshnessGate`. Narrower Off regex; pincode allowed. "Show as plain text". Live UI is cloud-only. Weave-JSON moves to phase 2; phase 1 ships in two steps (1a slice, 1b rest); the eval harness follows the build. Placeholder shrink eases; `Row` uses `Layout`. Reserved `E305`/`W406`. New and changed tests in 07. |
| 1.2 | 2026-11-26 | Nodes whose arguments read a waiting state show placeholders (03 §7.1). Off + explicit request / Make interactive may render 1 block. Off regex drops `lay`, narrows `give` and `in a …`. Merged effects: honest semantics (one transaction, one firing). Remix and edit prompts warn that an effect replaces every effect on its trigger. 01 operator and limits tables replaced by summaries; pipe precedence fixed. Pre-release versioning exemption. |
| 1.3 | 2026-11-26 | From the first eval-harness run (07 §10): `when(` in expression position is the built-in function, not the keyword (02 §2.3); `item` and `index` are allowed as `fn` / lambda parameters (02 §2.3); new auto-fix S13 turns a format written as a call in a format slot (`format: dec(1)`) into its text (02 §8.2); a `List` of records with the default `render` also shows the second string field as the row's subtitle (04 §8). Expressions that build a string or a list by concatenation stop at `STRING_CHARS` (100,000) and `LIST_ITEMS` (100,000) (05 §13.3). |
