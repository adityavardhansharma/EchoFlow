# 04 — Components, marks and tokens

**Spec version:** 1.2 · **Status:** normative

This is the reference for everything a block can draw. For each component it gives: the signature,
every parameter with type and default, positional order and aliases (for auto-fix), children,
exactly which Compose/Material 3 building blocks render it, its layout, events, accessibility,
streaming placeholder, tier and build phase.

Two implementations built from this document MUST look and behave the same. When this document
says "M3 `X`", use `androidx.compose.material3.X` from the version in `gradle/libs.versions.toml`
with its default parameters, except those listed.

**Never hardcode colours or sizes.** Every colour comes from `MaterialTheme.colorScheme`, every type
style from `MaterialTheme.typography`, every shape from §1.5 (CLAUDE.md rule).

---

## 1. Tokens

### 1.1 Spacing — `token<space>`

| Token | dp | Resolves to (`ui/theme/Spacing.kt`) |
|---|---|---|
| `none` | 0 | `0.dp` |
| `xxs` | 2 | `2.dp` (hairline; no app equivalent) |
| `xs` | 4 | `Spacing.xs` |
| `s` | 8 | `Spacing.s` |
| `m` | 12 | `Spacing.m` |
| `l` | 16 | `Spacing.base` |
| `xl` | 24 | `Spacing.xl` |
| `xxl` | 32 | `Spacing.xxl` |

Weave's names are its own on purpose: the model needs a short, evenly stepped scale, and the app's
scale has an extra step (`Spacing.l` = 20 dp, `Spacing.huge` = 48 dp) that Weave does not expose.
Every Weave value except `xxs` is an existing app value, so a block's spacing is indistinguishable
from hand-built EchoFlow UI. Note the one naming trap for implementers: **Weave `l` is
`Spacing.base`, not `Spacing.l`**. The renderer MUST read these through `Spacing`, not literals, so
a change to the app scale reaches blocks.

`pad` also accepts a list of 2 tokens `[vertical, horizontal]` or 4 tokens
`[top, end, bottom, start]`.

### 1.2 Heights — `token<height>`

| Token | dp | |
|---|---|---|
| `xs` | 48 | |
| `s` | 96 | |
| `m` | 168 | |
| `l` | 240 | |
| `xl` | 320 | |
| `screen` | `0.6 × viewport height` (inline), `1.0 × available height` (expanded) | |
| `wrap` | content height | default for most components |
| `aspect(r)` | width ÷ r, where `r` is a number such as `16/9` | `aspect` is a built-in that returns the token |

Inside an **inline** block, any fixed height above `0.8 × viewport height` is clamped to that.

### 1.3 Widths — `token<width>`

| Value | Meaning |
|---|---|
| `fill` | the parent's full content width (default for block-level components, §3.2) |
| `wrap` | the content's width (default for inline components, §3.2) |
| `1/2`, `1/3`, `2/3`, `1/4`, `3/4` | that fraction of the parent's content width (these are number expressions; `w: 1/2` is the number 0.5) |
| any number in (0, 1] | that fraction |
| `weight(n)` | inside a `Row` only: share of the remaining width (`Modifier.weight(n)`). Elsewhere: treated as `fill` |

### 1.4 Colour roles — `color`, and the `tone` system

**Single colour slots** (`color:`, mark `fill:`/`stroke:`, `bg:`) accept any of these and resolve to
exactly one `colorScheme` value:

| Token | Resolves to |
|---|---|
| `primary` `onPrimary` `primaryContainer` `onPrimaryContainer` | the same-named role |
| `secondary` … `onSecondaryContainer` | same |
| `tertiary` … `onTertiaryContainer` | same |
| `error` `onError` `errorContainer` `onErrorContainer` | same |
| `surface` `onSurface` `surfaceVariant` `onSurfaceVariant` | same |
| `surfaceContainerLowest` `surfaceContainerLow` `surfaceContainer` `surfaceContainerHigh` `surfaceContainerHighest` | same |
| `outline` `outlineVariant` `inverseSurface` `inverseOnSurface` `inversePrimary` | same |
| `neutral` | `onSurface` (in fills: `surfaceContainerHighest`) |
| `muted` | `onSurfaceVariant` |
| `success` `warning` `info` | §1.4.2 (solid colour) |
| `positive` = `success`, `negative` = `error` | |

**Tone slots** (`tone:` on components) take one of the 9 **tones** below and produce a *pair*
(container + content) depending on `emphasis`:

| Tone | `emphasis: high` (solid) | `emphasis: medium` (container) — **default** | `emphasis: low` (text only) |
|---|---|---|---|
| `primary` | `primary` / `onPrimary` | `primaryContainer` / `onPrimaryContainer` | no fill / `primary` |
| `secondary` | `secondary` / `onSecondary` | `secondaryContainer` / `onSecondaryContainer` | no fill / `secondary` |
| `tertiary` | `tertiary` / `onTertiary` | `tertiaryContainer` / `onTertiaryContainer` | no fill / `tertiary` |
| `error` (= `negative`) | `error` / `onError` | `errorContainer` / `onErrorContainer` | no fill / `error` |
| `neutral` | `inverseSurface` / `inverseOnSurface` | `surfaceContainerHighest` / `onSurface` | no fill / `onSurface` |
| `success` (= `positive`) | §1.4.2 | §1.4.2 | §1.4.2 |
| `warning` | §1.4.2 | §1.4.2 | §1.4.2 |
| `info` | = `primary` | = `primary` | = `primary` |
| `muted` | = `neutral` medium | `surfaceContainer` / `onSurfaceVariant` | no fill / `onSurfaceVariant` |

Each component says what its `tone` colours (e.g. `Metric` colours its value text with the
low-emphasis content colour; `Callout` uses the medium pair for its surface).

#### 1.4.1 Chart series palette

When a mark has no `tone`, series `i` (0-based, in mark/series order) gets:
`primary`, `tertiary`, `secondary`, `error`, then the same four at 60 % alpha, then `onSurfaceVariant`
for the rest.

#### 1.4.2 Harmonised semantic colours

`success` and `warning` are generated so they fit every palette:

- Source hues: success = HCT hue **145**, warning = HCT hue **75**.
- Using `com.google.android.material.color.utilities` (or the equivalent ported HCT code), build a
  `TonalPalette` from that hue with chroma `max(36, primaryChroma)` where `primaryChroma` is the
  HCT chroma of the current `colorScheme.primary`.
- Solid = tone 40 (light theme) / 80 (dark); onSolid = tone 100 / 20; container = tone 90 / 30;
  onContainer = tone 10 / 90.
- Computed once per (scheme, dark) and cached.

### 1.5 Shapes — `token<shape>`

Corner tokens resolve through the app theme, never to fixed dp, so a block's cards and images
match the cards EchoFlow already draws (principle 10, 01 §2):

| Token | Resolves to | EchoFlow default (`ui/theme/Shape.kt`) |
|---|---|---|
| `none` | `RectangleShape` | 0 dp |
| `xs` | `MaterialTheme.shapes.extraSmall` | 8 dp |
| `s` | `MaterialTheme.shapes.small` | 14 dp |
| `m` | `MaterialTheme.shapes.medium` | 20 dp — the M3 `Card` default |
| `l` | `MaterialTheme.shapes.large` | 28 dp |
| `xl` | `MaterialTheme.shapes.extraLarge` | 36 dp |
| `full` | `CircleShape` / pill (`RoundedCornerShape(50%)`) | — |

The dp column is informative, except that auto-fix S4 (02 §8.2) snaps raw numbers using it. If
`Shape.kt` changes, update this column and the S4 tests together.

Expressive shapes (`MaterialShapes`, converted with `.toShape()`), allowed only on `Shape`,
`Avatar`, `Icon(container: …)`, `Image`, `Surface`, `Card` (with `style: filled|tonal`), and
`Button(variant: filled|tonal, size: l)`:

| Token | `MaterialShapes.` |
|---|---|
| `circle` | `Circle` |
| `square` | `Square` |
| `slanted` | `Slanted` |
| `arch` | `Arch` |
| `fan` | `Fan` |
| `oval` | `Oval` |
| `pill` | `Pill` |
| `triangle` | `Triangle` |
| `diamond` | `Diamond` |
| `pentagon` | `Pentagon` |
| `gem` | `Gem` |
| `sunny` | `Sunny` |
| `verySunny` | `VerySunny` |
| `cookie` | `Cookie9Sided` |
| `cookie4` | `Cookie4Sided` |
| `cookie12` | `Cookie12Sided` |
| `clover` | `Clover4Leaf` |
| `clover8` | `Clover8Leaf` |
| `burst` | `Burst` |
| `softBurst` | `SoftBurst` |
| `boom` | `Boom` |
| `flower` | `Flower` |
| `puffy` | `Puffy` |
| `ghost` | `Ghostish` |
| `bun` | `Bun` |
| `heart` | `Heart` |

When a shape token **changes** at runtime, the shape **morphs** (`Morph(from, to)` animated with the
`default` spatial spring) if both are expressive or `circle`/`square`; otherwise it switches
instantly.

### 1.6 Text styles — `token<text>`

The 15 `MaterialTheme.typography` roles by name: `displayLarge`, `displayMedium`, `displaySmall`,
`headlineLarge`, `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`,
`bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelMedium`, `labelSmall`.

Aliases: `hero` = `displaySmall`; `h1` = `headlineSmall`; `h2` = `titleLarge`; `h3` =
`titleMedium`; `body` = `bodyLarge`; `caption` = `bodySmall`; `label` = `labelLarge`;
`overline` = `labelSmall` with all-caps and letter spacing +0.08 em; `mono` = `bodyMedium` with the
app's code font family (the one `MarkdownText` uses for code).

In **inline** and **card** blocks, `displayLarge` and `displayMedium` are replaced by
`displaySmall`. They are allowed unchanged in expanded/full-screen mode.

`weight: regular | medium | bold` → `FontWeight.Normal | Medium | Bold`, overriding the style's
weight.

### 1.7 Elevation — `token<elev>`

`0`–`3` → tonal elevation `0, 1, 3, 6 dp` (`Surface(tonalElevation = …)`), shadow elevation 0.

### 1.8 Motion

| Token | Compose spec |
|---|---|
| `motion: fast` | `MaterialTheme.motionScheme.fastSpatialSpec()` (layout/position) / `fastEffectsSpec()` (colour/alpha) |
| `motion: default` | `defaultSpatialSpec()` / `defaultEffectsSpec()` |
| `motion: slow` | `slowSpatialSpec()` / `slowEffectsSpec()` |
| `spring: bouncy` | `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)` |
| `spring: smooth` | `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)` |
| `spring: stiff` | `spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)` |

The app uses `MaterialExpressiveTheme`'s expressive motion scheme. With reduced motion
(03 §4.3), every animation in this document uses `snap()`, except progress indicators.

### 1.9 Icons — `token<icon>`

- Names are Material Icons in `snake_case`: `"flight_takeoff"`, `"savings"`, `"share"`.
- They resolve to `Icons.Rounded.<PascalCase>` from `material-icons-extended`, through a **generated
  registry** (`WeaveIcons.kt`, built by a Gradle task from `weave/icons.txt`: one name per line,
  ~600 curated names). **No reflection.** `material-icons-extended` is already an `:app`
  dependency, but most app screens draw their own vector drawables, so the registry is new surface:
  it is what keeps R8 from stripping the icons a block may name, and it is the only list the prompt
  may advertise.
- A name not in the registry renders a 6 dp dot in `onSurfaceVariant` and reports `W401`.
- Icon sizes: `s` 18 dp, `m` 24 dp, `l` 32 dp, `xl` 48 dp.

### 1.10 Other enumerations

| Set | Values |
|---|---|
| `align` | `start`, `center`, `end`, `stretch` |
| `valign` | `top`, `center`, `bottom`, `baseline` |
| `justify` | `start`, `center`, `end`, `between`, `around`, `evenly` |
| `haptic` | `tick`, `click`, `confirm`, `reject`, `heavy` |
| `enter` | `fade`, `rise`, `scale`, `slide`, `pop`, `none` |
| `export` | `png`, `pdf`, `csv`, `json`, `weave` |
| `size3` | `s`, `m`, `l` |

---

## 2. Common properties (every component)

### 2.1 The props

| Prop | Type | Default | Meaning |
|---|---|---|---|
| `show` | any (truthiness) | `true` | When falsy, the component is not laid out. Changes animate (§2.3). |
| `w` | `token<width>` | per §3.2 | Width |
| `h` | `token<height>` | per component | Height |
| `minH` | `token<height>` | none | Minimum height |
| `pad`, `padX`, `padY` | `token<space>` | per component | Inner padding. `padX`/`padY` override the matching sides of `pad`. |
| `align` | `align` | parent's | Self-alignment in the parent's cross axis |
| `tone` | tone | per component | §1.4 |
| `emphasis` | `low \| medium \| high` | `medium` | §1.4 |
| `bg` | `color` | none | Background fill. Overrides the fill that `tone` would give. |
| `shape` | `token<shape>` | per component | Clip and background shape |
| `border` | `bool \| color` | `false` | 1 dp border in `outlineVariant` (`true`) or the colour |
| `elevation` | `token<elev>` | 0 | §1.7 |
| `enter` | `enter` | `fade` for direct children of `root`, else `none` | §2.3 |
| `onTap`, `onLongPress`, `onDoubleTap` | action | none | Gestures. Event data: `{x, y}` in dp relative to the component |
| `onSwipe` | `fn(dir) → action` | none | `dir` is `"left" \| "right" \| "up" \| "down"` (threshold 56 dp, velocity > 400 dp/s) |
| `label` | str | derived | Accessibility label (§2.7) |
| `key` | any | position | Identity in lists for animations (§2.5) |
| `tip` | str | none | Tooltip on long-press (M3 `PlainTooltip`). Ignored if `onLongPress` is set. |
| `badge` | num \| str \| bool | none | M3 `Badge` at the top-end corner. `true` = dot. Numbers > 999 → `999+`. |
| `disabled` | bool | `false` | 38 % alpha content, no gestures, `semantics { disabled() }` |
| `delay` | dur | `0ms` | Delay before the `enter` animation starts (adds to the sibling stagger). Typical: `delay: stagger(i)` |

### 2.2 Modifier order

To make every implementation lay out the same, common props are applied as `Modifier`s in **exactly
this order**, outermost first:

1. `show` (via `AnimatedVisibility` wrapping the whole thing; §2.3)
2. `semantics` merge and `label` (§2.7)
3. width (`fillMaxWidth()`, `fillMaxWidth(f)`, `wrapContentWidth()`, `weight(n)`)
4. height (`height(dp)`, `heightIn(min)`, `aspectRatio(r)`)
5. `align` (via the parent scope's `align`)
6. elevation (tonal, via the `Surface` that also does 7–8 when any of 7–8 apply)
7. `clip(shape)`
8. background (`bg` or tone fill) and `border(1.dp, color, shape)`
9. gestures: `combinedClickable` (tap/long/double, with ripple bounded by the shape), then
   `pointerInput` for swipe
10. badge (`BadgedBox` wraps from outside 3 but is listed here: badge is drawn outside the clip)
11. `padding` (inner)
12. the component's own content

### 2.3 Enter and show animations

| `enter` | Animation (spec: `motion: default` effects for alpha, spatial for movement) |
|---|---|
| `fade` | alpha 0 → 1 |
| `rise` | alpha 0 → 1 and translationY +16 dp → 0 |
| `scale` | alpha 0 → 1 and scale 0.92 → 1 |
| `slide` | translationX +24 dp → 0 and alpha |
| `pop` | scale 0.6 → 1 with `spring: bouncy`, alpha |
| `none` | instant |

- `enter` runs once, when the node first appears (first render, or when it streams in).
  Re-composition doesn't replay it. A node restored from storage on reopening a chat does **not**
  animate.
- Siblings that appear in the same frame are **staggered** by 40 ms each, in order, up to 8
  siblings (the 9th onward start with the 8th).
- `show` changes use `expandVertically + fadeIn` / `shrinkVertically + fadeOut` (`motion: default`).

### 2.4 Plain text of a node

Used by `!copy`, `!share` (as text), `nodeText`, accessibility summaries and repair prompts. Defined
recursively:

- `Text`, `Title`, `Markdown`, `Quote`, `Callout`, `Code`: their text (Title: title, newline,
  subtitle).
- `Metric`: `label: formatted value`.
- Inputs: `label: current value as text`.
- `Table`: rows as tab-separated lines with a header line.
- `List`: one line per item.
- `Chart`: its accessibility summary (§12.6).
- Containers: children's plain text joined by `\n`; `Row` children joined by `  ` (two spaces).
- `Image`: its `alt`. Everything else: empty.

### 2.5 Identity and keys

Every rendered node has a stable key: `<statement name>/<child path>` (e.g. `root/2/0`). For items
produced by `each`/`map` inside a children list, the path segment is the item's `key` prop if given,
else its index. Keys drive `animateItem()` in lists, shared-element transitions between versions
(05 §7.3) and the persisted state of uncontrolled UI (scroll positions, expanded sections).

### 2.6 Pending overlay (`!ask` in flight)

On each node whose statement is in an in-flight `!ask`'s `update` list: draw the node at 60 % alpha
and over it a shimmer — a diagonal linear gradient band of `onSurface` at 8 % alpha, 120 dp wide,
moving left → right over 1,200 ms, repeating. Gestures on that node are disabled while pending. With
reduced motion: no shimmer, only the alpha.

### 2.7 Accessibility defaults

- Every component sets the correct semantics role (listed per component).
- `label` overrides the computed content description. If a component is clickable and has no
  visible text, `label` is **required**; when it is missing, the checker auto-derives it from the
  icon name (`"flight_takeoff"` → `"Flight takeoff"`) with `W403`.
- Minimum touch target 48 × 48 dp for anything clickable (`minimumInteractiveComponentSize()`).
- Text follows the user's font scale. Heights from §1.2 that contain text grow with font scale:
  `h` is treated as a **minimum** when the node contains text.

### 2.8 Error chip

When a node can't render because of an error (`E204` on a required parameter, `E205` on a lead),
it renders as an `AssistChip`-shaped, non-clickable chip with icon `error_outline`, label from the
diagnostic's short form (`Slider needs a value`), in `errorContainer` / `onErrorContainer`. In
release builds the chip is shown only if the node has **no** valid children to show instead.

### 2.9 Placeholders (streaming)

A placeholder is shown in place of a node when either:

- a binding it references has not arrived yet; or
- its own arguments read a **waiting** state, directly or through bindings and functions (03 §7.1).

The placeholder's form comes from the **slot** it is referenced in, then from the binding's call if
the statement has started but not finished, then from the node's own component:

| Slot / component | Placeholder |
|---|---|
| Any `Chart`/sugar, `Canvas`, `Map`, `Image`, `Video`, `Model3D`, `Shader` | Rounded rectangle (`shape: m`), height = the slot's `h` or `m` (168 dp), fill `surfaceContainerHigh`, shimmer as §2.6 |
| `Text`, `Title`, `Markdown` | 2 lines of 12 dp bars (100 % and 60 % width), 8 dp apart |
| `Metric` | 14 dp bar (40 %) above a 28 dp bar (60 %) |
| `Table`, `List` | 3 rows of 16 dp bars, 12 dp apart |
| Inputs | 48 dp tall pill (`shape: full`), full width |
| `Button` | 40 dp × 112 dp pill |
| Unknown (just a name) | 48 dp tall bar, full width |

Placeholders never shrink while streaming: when content arrives, the container height animates
from the placeholder height to the content height only if the content is **taller**; if it is
shorter, the extra height is kept until fence close. This prevents the chat from jumping while text
streams. At fence close, any held extra height **animates** down to the content height with the
`fast` spatial spec of the motion scheme (04 §1.8) — never a snap, because fence close is when the
user's eye and thumb arrive at the block. Under reduced motion it changes in one step. If the block
is entirely off-screen at fence close, it changes in one step and the list keeps the user's scroll
anchor. `[T-407]`

---

## 3. Layout model

### 3.1 The block frame

- Width available to an inline block = the assistant reply column's content width (the same width
  `MarkdownText` gets).
- The block's `root` is laid out in a `Column(Modifier.fillMaxWidth())`.
- `size: card` wraps the root in the block chrome (05 §7.1) with 16 dp padding.
- Nested depth above 24 → deeper nodes are not rendered (`E304`).

### 3.2 Default widths

- **Block-level** components default to `w: fill`: `Column`, `Row`, `Box`, `Grid`, `Flow`,
  `Carousel`, `Card`, `Surface`, `Section`, `Split`, `Sticky`, `Divider`, `Text` (when it is a
  direct child of a `Column`, `Card`, `Section`, `Surface`), `Callout`, `Quote`, `Code`, `Markdown`,
  every input except `Button`/`IconButton`/`Fab`/`Toggle`/`Check`/`Chip`, every chart, `Table`,
  `List`, `Tabs`, `Pager`, `Wizard`, `Accordion`, `Image`, `Gallery`, `Video`, `Audio`, `Map`,
  `Canvas`, `Progress(kind: linear|wavy)`, every learning/play component.
- **Inline** components default to `w: wrap`: `Button`, `IconButton`, `Chip`, `Badge`, `Icon`,
  `Avatar`, `Toggle`, `Check`, `Kbd`, `Link`, `Shape`, `Progress(kind: circular)`, `Metric` inside a
  `Row`, `Text` inside a `Row`.
- In a `Row`, children with `w: fill` are converted to `weight(1)` (so several "fill" children share
  the row).

### 3.3 Overflow

- Text never overflows horizontally: it wraps (unless `maxLines` is set, then ellipsis).
- A `Row` whose children don't fit in its width (measured with intrinsic widths) and whose `wrap`
  is `auto` (default) is laid out as a `Column` with the same `gap` instead. This is decided per
  layout pass by a single custom `Layout`: it reads each child's `maxIntrinsicWidth` once, compares
  the sum plus gaps with the incoming max width, then measures and places the children as a row or
  a column. It MUST NOT use `SubcomposeLayout`, which recomposes per pass and janks inside the chat
  `LazyColumn` on mid-range devices.
- Some children can't answer intrinsic queries: Compose throws when a subcomposed or lazy layout is
  asked for intrinsics. Each `ComponentSpec` (05 §3.3) has `intrinsics: Boolean`; it is `false` for
  every component whose implementation uses `SubcomposeLayout`, a lazy layout or a pager — at least
  `Carousel`, `Pager`, `Tabs`, `Wizard`, `Gallery`, `Table`, `List(lazy: true)`, `Calendar` — and
  for any node wrapping one. The `Row` never queries such a child. With `wrap: auto`, a `Row` that
  contains one is laid out as a `Column` (on a ~400 dp screen, those components need the full width
  anyway). With `wrap: never`, such a child is measured with the width left after its intrinsic
  siblings and gaps, or with `weight` if it has one. `[T-403b]`
- Horizontal scrolling exists only in `Carousel`, `Tabs(scrollable: true)`, `Table` and `Chips`
  with `scroll: true`. No other component scrolls horizontally.
- Vertical scrolling inside a block exists only where a fixed `h` is set on `Column(scroll: true)`,
  `List(lazy)`, `Sticky` and overlays. Otherwise the block grows and the chat scrolls.

### 3.4 Default spacing

- `root` direct children: if `root` is a `Column`/`Card` without `gap`, the gap is `m`.
- `Card` default `pad: l`, `gap: s`.
- Everything else has no implicit margins. Space comes only from `gap` and `pad`.

---

## 4. Layout components

### Column

`Column(gap: token<space> = s, align: align = stretch, scroll: bool = false) [children]` ·
tier 1 · phase 1

| Param | Type | Default | Notes |
|---|---|---|---|
| `gap` | space | `s` | `Arrangement.spacedBy` |
| `align` | align | `stretch` | `stretch` = children default to `fill`; else `Alignment.Start/CenterHorizontally/End` and children default to `wrap` |
| `scroll` | bool | `false` | `verticalScroll` — only effective with a fixed `h` |

Positional order: `gap`, `align`. Aliases: `spacing`→`gap`, `padding`→`pad`.
Renders: Compose `Column`. Semantics: none (container). Placeholder: n/a (children have their own).

### Row

`Row(gap = s, align: valign = center, justify: justify = start, wrap: auto|never = auto) [children]`
· tier 1 · phase 1

| Param | Type | Default | Notes |
|---|---|---|---|
| `gap` | space | `s` | |
| `align` | valign | `center` | vertical alignment of children; `baseline` = `alignByBaseline()` |
| `justify` | justify | `start` | `Arrangement.Start/Center/End/SpaceBetween/SpaceAround/SpaceEvenly`; with `gap`, `between/around/evenly` ignore `gap` |
| `wrap` | `auto \| never` | `auto` | §3.3 |

Positional order: `gap`, `justify`. Aliases: `spacing`→`gap`, `alignment`→`align`.

### Box

`Box(contentAlign = topStart) [children]` · tier 1 · phase 1

Children are stacked; later children draw on top. `contentAlign`: `topStart`, `top`, `topEnd`,
`start`, `center`, `end`, `bottomStart`, `bottom`, `bottomEnd` (mapped to `Alignment.*`). A child
may override with `align` using the same 9 values.

### Grid

`Grid(cols: num | auto = 2, gap = s, rowGap = gap, minCell: token<height> = s) [children]` · tier 1 · phase 1

- `cols: n` (1–6): equal columns. `cols: auto`: as many columns of at least `minCell` dp width as
  fit (`minCell` uses the height token table's dp values as widths: `s` = 96 dp), max 6.
- Implemented with a custom `Layout` (not `LazyVerticalGrid`, which can't nest in the chat list).
  Rows take the height of their tallest cell. Cells are filled left to right, top to bottom.
- A child may span columns with the extra prop `span: n` (only meaningful in `Grid`).

### Flow

`Flow(gap = s, rowGap = s, justify = start) [children]` · tier 1 · phase 1

Compose `FlowRow` with `horizontalArrangement = spacedBy(gap, justify alignment)` and
`verticalArrangement = spacedBy(rowGap)`.

### Carousel

`Carousel(itemW: 1/2|2/3|3/4|4/5 = 3/4, gap = s, snap = true, peek = true, h?) [children]` · tier 1 · phase 1

M3 `HorizontalMultiBrowseCarousel` when `peek: true` (`preferredItemWidth = itemW × width`,
`itemSpacing = gap`), else `HorizontalUncontainedCarousel` with `itemWidth = itemW × width`. Item
height = `h` or the tallest child's height measured once. Items are clipped with `shape: l` via
`maskClip`. Semantics: each item is a list item; the carousel announces "Carousel, n items".

### Spacer

`Spacer(size: token<space> | weight(n) = m)` · tier 1 · phase 1 — vertical in a `Column`, horizontal
in a `Row`. `weight(n)` in a `Row`/`Column` = `Modifier.weight(n)`.

### Divider

`Divider(inset: bool = false, vertical: bool = false)` · tier 1 · phase 1 — M3 `HorizontalDivider`
(or `VerticalDivider` in a `Row`, height = row height). `inset` adds 16 dp start padding.

### Card

`Card(style: filled|outlined|elevated|tonal = filled, pad = l, gap = s) [children]` · tier 1 · phase 1

| `style` | Renders |
|---|---|
| `filled` | M3 `Card` (container `surfaceContainerHighest`) |
| `outlined` | M3 `OutlinedCard` |
| `elevated` | M3 `ElevatedCard` |
| `tonal` | M3 `Card` with `containerColor` = tone's medium container (default tone `secondary`) |

Shape default `m` (`shapes.medium`, the same as every other card in the app). Content is a `Column(gap)` with `pad`. With `onTap`, uses the clickable
`Card(onClick)` overload. Aliases: `variant`→`style`, `kind`→`style`.

### Surface

`Surface(tone = neutral, emphasis = low) [children]` · tier 1 · phase 1 — M3 `Surface` with the tone
pair from §1.4 (`emphasis: low` here means `surfaceContainerLow` + `onSurface`, the exception to the
§1.4 table, because a surface with no fill would be pointless). Default shape `m`, pad `m`.

### Section

`Section(title?: str, subtitle?: str, action?: node, collapsible = false, open = true) [children]` · tier 1 · phase 1

A header row (`title` in `titleMedium`, `subtitle` in `bodySmall` `onSurfaceVariant`, `action`
at the end) above the children with `gap: s`. With `collapsible`, the header is clickable with a
rotating `expand_more` icon (180° over `motion: fast`), and `open` is the **initial** state, kept in
the node's UI state (persisted by key, §2.5). Semantics: header is `heading()`; collapsible header
has `stateDescription` "Expanded"/"Collapsed".

### Split

`Split(ratio: 1/2|1/3|2/3 = 1/2, breakpoint: medium|expanded = medium, gap = l) [left, right]` · tier 1 · phase 1

When `device.width` is at least `breakpoint` (or the block is expanded on a medium+ window), lays
out two children side by side with widths `ratio` and `1 - ratio`; otherwise stacks them with `gap`.
Exactly 2 children; extra children are dropped (`E208`).

### Sticky

`Sticky(h: token<height>) [header, body]` · tier 2 · phase 2 — a fixed-height area where `header`
stays at the top and `body` scrolls beneath it. Requires `h` (without it: renders header then body,
no scrolling). Exactly 2 children.

---

## 5. Text and content

### Text

`Text(content, style: token<text> = body, tone?, color?, weight?, maxLines?: num, align: start|center|end = start, select: bool = false, italic = false, strike = false)` · tier 1 · phase 1

- Lead: `content` (any; coerced to str via `auto`).
- Colour: `color` if given; else `tone` with `emphasis: low` (§1.4); else `LocalContentColor`.
- `maxLines` → ellipsis overflow. `select: true` wraps in `SelectionContainer`.
- **Inline styling inside `content`:** `**bold**`, `*italic*`, `` `code` `` and `[label](https://…)`
  are rendered (and nothing else of markdown). Links open with the `!url` confirm flow.
- Positional order: `style`. Aliases: `text`→`content`, `value`→`content`, `variant`→`style`,
  `size`→`style` (with `s`/`m`/`l` → `bodySmall`/`bodyMedium`/`bodyLarge`), `color` on a tone value
  → `tone`.
- Semantics: text. Placeholder: §2.9.

### Title

`Title(content, subtitle?: str, style = titleMedium, icon?: icon, trail?: node)` · tier 1 · phase 1

A row: optional leading `Icon(size: m, tone: primary)`, then a column of `content` (style) and
`subtitle` (`bodyMedium`, `onSurfaceVariant`), then `trail` at the end. `content` has
`semantics { heading() }`.

### Rich

`Rich [parts]` · tier 2 · phase 2

A single paragraph built from inline parts, laid out with `FlowRow` (baseline-aligned, gap 4 dp,
row gap 4 dp). Allowed parts: `Text` (its text flows as words: each word is a separate flow item so
lines break naturally), `Chip`, `Icon`, `Badge`, `Link`, `Metric(size: s)` (rendered inline as
bold value text), `Slider(inline: true)` (renders as a 120 dp slider), `Select`, `Segmented`
(rendered as an inline dropdown chip), `Stepper` (compact). Other parts render as their plain text.

### Markdown

`Markdown(source: str)` · tier 1 · phase 1 — renders with EchoFlow's `MarkdownText` (no nested
Weave fences: a ```` ```weave ```` fence inside is shown as code).

### Icon

`Icon(name: icon, size: s|m|l|xl = m, tone?, color?, container?: token<shape>)` · tier 1 · phase 1

M3 `Icon` with the registry vector. `container`: draws the icon centred in a shape 1.75 × the icon
size, filled with the tone's medium container. Decorative by default (`contentDescription = null`)
unless `label` is set or it is clickable.

### Avatar

`Avatar(name: str | src: str, size: s|m|l = m, shape = full, tone = primary)` · tier 1 · phase 1

Sizes 32/40/56 dp. With `src` (https URL): image cropped to the shape. Otherwise initials (first
letter of the first two words, upper-case) on the tone's medium container in `labelLarge`
(`titleMedium` for `l`).

### Badge

`Badge(content: str | num, tone = primary)` · tier 1 · phase 1 — standalone pill: tone high-emphasis
pair, `labelSmall`, height 16 dp, horizontal padding 4 dp, shape `full`.

### Chip

`Chip(label, icon?: icon, selected?: bool, onTap?: action, trail?: icon)` · tier 1 · phase 1

- No `onTap`, no `selected`: M3 `AssistChip` with `enabled = true` and no click (display only).
- `onTap` and no `selected`: M3 `AssistChip(onClick)`.
- `selected` given: M3 `FilterChip(selected)`; tapping runs `onTap`.
Semantics: role Button (clickable) / Checkbox (`selected`).

### Link

`Link(label, href: str)` · tier 1 · phase 1 — `Text` in `primary`, underlined, clickable → `!url(href)`
(confirm sheet). Semantics: role Button with `"Opens <domain>"` hint.

### Quote

`Quote(content, cite?: str)` · tier 1 · phase 1 — 4 dp wide rounded bar in `primary` at the start,
16 dp gap, `content` in `bodyLarge` italic, `cite` below in `labelMedium` `onSurfaceVariant`
prefixed with `— `.

### Callout

`Callout(content, kind: info|success|warning|error|tip = info, title?: str, action?: node)` · tier 1 · phase 1

Surface with shape `m`, pad `m`, the kind's medium pair (`tip` uses `tertiary`), a leading icon
(`info`→`info`, `success`→`check_circle`, `warning`→`warning`, `error`→`error`, `tip`→`lightbulb`),
`title` in `titleSmall`, `content` in `bodyMedium`, `action` below. Semantics: `liveRegion` polite
when it appears after first render.

### Code

`Code(source: str, lang?: str, run: bool = false, wrap: bool = false)` · tier 1 · phase 1 — rendered
like EchoFlow's markdown code block (same highlighter and copy button). `run: true` shows "Run in
sandbox" only when the sandbox feature is configured; it sends `!say("Run this code: …")`.

### Kbd

`Kbd(keys: str | list<str>)` · tier 1 · phase 2 — each key in a 1 dp `outlineVariant` bordered box,
`mono` style, shape `xs`, separated by `+`.

### Empty

`Empty(title, body?: str, icon: icon = "inbox", action?: node)` · tier 1 · phase 1 — centred column:
`Icon(size: xl, container: cookie, tone: secondary)`, `title` (`titleMedium`), `body`
(`bodyMedium`, `onSurfaceVariant`), `action`. Vertical padding `xl`.

---

## 6. Numbers and status

### Metric

`Metric(label, value, format?: str, prefix?: str, suffix?: str, delta?: num, deltaFormat = percent, deltaGood: up|down = up, trend?: list<num>, tone?, animate = true, size: s|m|l = m)` · tier 1 · phase 1

- Layout: column — `label` (`labelMedium`, `onSurfaceVariant`); value row: `prefix` + formatted
  value + `suffix` in `headlineSmall` (`s`: `titleMedium`, `l`: `displaySmall`, clamped per §1.6)
  with the tone's low-emphasis colour (default `onSurface`); optional delta chip; optional sparkline
  (24 dp tall, full width) for `trend`.
- **Delta chip:** arrow icon (`arrow_upward`/`arrow_downward`/`remove` for 0) + `abs(delta)`
  formatted with `deltaFormat`. Colour: `success` if the direction matches `deltaGood`, `error` if
  not, `muted` for 0.
- **Animate:** when the numeric value changes, the displayed number animates from the old to the
  new value over `motion: default` (`animateFloatAsState`), re-formatting each frame. Non-numeric
  values don't animate.
- Positional order: `format`. Aliases: `title`→`label`, `unit`→`suffix`, `change`→`delta`.
- Semantics: merged, `"<label>: <formatted value>, <up/down> <delta>"`.

### Progress

`Progress(value: num, kind: linear|circular|wavy = wavy, label?: str, showValue = false, tone = primary)` · tier 1 · phase 1

- `value` is a fraction 0–1 (clamped). `null` → indeterminate.
- `wavy` → M3 `LinearWavyProgressIndicator`; `linear` → `LinearProgressIndicator`; `circular` →
  `CircularWavyProgressIndicator` (48 dp). Colour = tone solid; track = `surfaceContainerHighest`.
- Value changes animate with `ProgressIndicatorDefaults.ProgressAnimationSpec`.
- `label` above (linear) or beside (circular); `showValue` adds `percent` text.
- Semantics: `progressBarRangeInfo`.

### Gauge

`Gauge(value: num, min = 0, max = 100, bands?: list<{to: num, tone}>, label?: str, format?: str, size: s|m|l = m)` · tier 1 · phase 2

A 240° arc (from 150° to 390°, clockwise), stroke 12 dp (`s`: 8, `l`: 16), round caps, diameter
120/160/200 dp. Track `surfaceContainerHighest`. Bands colour the track segments up to each `to`.
Value arc in `primary` (or the band's tone containing the value). Needle-free. Value text centred
(`headlineSmall`), label below. Value animates (`spring: smooth`).

### Rating

`Rating(value: num, max = 5)` · tier 1 · phase 1 — `max` star icons (`star`, `star_half`,
`star_outline`, rounded to the nearest 0.5) in `tertiary`, 18 dp. Read-only. Semantics:
`"Rated 4.5 out of 5"`.

### Stat

`Stat [Metric…]` · tier 1 · phase 1 — a row of equal-width metrics (`weight(1)`, gap `s`); with more
than 3 children, or when they don't fit (§3.3), a 2-column `Grid`. Non-`Metric` children are allowed.

### Steps

`Steps(current: num, labels: list<str>)` · tier 1 · phase 2 — horizontal stepper: circles 24 dp
(done: `primary` with `check` icon; current: `primary` outline 2 dp with number; future:
`outlineVariant` outline), connected by 2 dp lines, labels below in `labelSmall`. More than 5
steps → only the number of the current step "Step 3 of 7" and a `Progress(kind: linear)`.

### Timer

`Timer(seconds: num, running: bool, onDone?: action, style: ring|digits = ring)` · tier 2 · phase 2

Displays a countdown from `seconds`. `ring`: `CircularWavyProgressIndicator` 160 dp with `clock`
format text inside; `digits`: `displaySmall` text. The component keeps its own remaining time
(persisted) while `running` is truthy; when it reaches 0 it runs `onDone` once, with `!haptic(heavy)`.
Changing `seconds` resets it.

### Clock

`Clock(time: datetime | time = now(), style: digital|analog = digital, zone?: str)` · tier 1 · phase 2 —
digital: `time` format in `headlineMedium`; analog: 120 dp face drawn on Canvas with hour ticks in
`outlineVariant`, hands in `onSurface`/`primary`. Updates per minute (per second if bound to a state
updated by `every`).

---

## 7. Inputs

**Shared rules for all inputs:**

- The **lead** of an input is a **place** (03 §5.4) — the two-way binding. If the lead is not a
  place, the input renders read-only showing the value, with `E210`.
- Shared params: `label?: str`, `help?: str` (supporting text below), `rule?: any` (validation,
  §7.1), `onChange?: fn(value) → action | action` (runs **after** the bound write, in the same
  transaction), `disabled?: bool`.
- Inputs write on every change, except `TextField` (writes on each keystroke too, but `change`
  effects with debounce are the way to react) and continuous drags (history merging, 03 §7.5).

### 7.1 Validation (`rule`)

`rule` is evaluated on every change of the bound value, **but errors are shown only after** the
field has lost focus once, or a `Form` submit was attempted:

- truthy and not a string → valid;
- a string → invalid, with that string as the error text (`isError = true`, `supportingText`);
- falsy → invalid, error text `"Invalid value"` (localised).

### Button

`Button(label: str, onTap: action, variant: filled|tonal|outlined|text|elevated = filled, icon?: icon, trailIcon?: icon, size: xs|s|m|l|xl = s, tone?, wide = false, loading?: bool, shape?: token<shape>)` · tier 1 (with `!copy`/`!share`/`!url` only) / tier 2 · phase 1

- Variants → M3 `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `ElevatedButton`.
- Sizes → M3 Expressive `ButtonDefaults` size sets: `xs` = `ExtraSmallContainerHeight` (32 dp),
  `s` = `MinHeight` (40 dp), `m` = `MediumContainerHeight` (56), `l` = `LargeContainerHeight` (96),
  `xl` = `ExtraLargeContainerHeight` (136), with matching content padding and icon sizes from the
  same `ButtonDefaults` functions, and text styles `labelLarge` (xs, s), `titleMedium` (m),
  `headlineSmall` (l), `headlineLarge` (xl).
- `tone` overrides container/content colours with the tone's pair (high for `filled`, medium for
  `tonal`, low for `outlined`/`text`).
- `shape` default: `ButtonDefaults.shapes()` (round, morphing to square-ish when pressed —
  M3 Expressive). An expressive shape token disables press morphing.
- `wide: true` → `fillMaxWidth()`.
- `loading: true` → content replaced by a 20 dp `CircularWavyProgressIndicator`, button disabled.
- **Long-press** shows a tooltip with the text that `!say` would send, when the handler contains
  `!say` (03 §9.1).
- Positional order: `onTap`, `variant`, `icon`. Aliases: `text`→`label`, `title`→`label`,
  `onClick`→`onTap`, `action`→`onTap`, `style`→`variant`, `type`→`variant`, `color`→`tone`.
- Semantics: role Button.

### IconButton

`IconButton(icon: icon, onTap: action, variant: standard|filled|tonal|outlined = standard, label: str, size: xs|s|m|l|xl = s, selected?: bool, tone?)` · tier 2 · phase 1

M3 `IconButton` / `FilledIconButton` / `FilledTonalIconButton` / `OutlinedIconButton`, sizes from
`IconButtonDefaults` (`ExtraSmallContainerSize` …). `selected` given → the `Icon*ToggleButton`
variant. `label` required (§2.7). Aliases: `name`→`icon`, `onClick`→`onTap`.

### Fab

`Fab(icon: icon, onTap: action, label?: str, expanded = false, size: s|m|l = m, tone = primary)` · tier 2 · phase 2

Rendered at the **block's** bottom-end corner (16 dp inset), floating over the block content, not in
the flow. `expanded` with `label` → `ExtendedFloatingActionButton`. Sizes → `SmallFloatingActionButton`,
`FloatingActionButton`, `LargeFloatingActionButton`. Only one `Fab` per block; extras are dropped.

### SplitButton

`SplitButton(label: str, onTap: action, menu: list<{label, onTap, icon?}>, variant: filled|tonal|outlined = filled)` · tier 2 · phase 2 — M3 `SplitButtonLayout` with leading button and trailing menu toggle opening a `DropdownMenu` of `menu`.

### ButtonGroup

`ButtonGroup(value?: place, options?: list, multi = false) [buttons]` · tier 2 · phase 2

M3 Expressive `ButtonGroup` (connected). Two modes:
- With children: a group of the given `Button`/`IconButton`s (press animation expands the pressed one).
- With `value` and `options`: connected toggle buttons (`ToggleButton`), one per option; single
  select writes the option, `multi` writes a list.

### Toggle

`Toggle(value: place, label?: str, icon: bool = true)` · tier 2 · phase 1 — M3 `Switch`. With
`label`, a full-width row: label (`bodyLarge`) at start, switch at end; the whole row is
`toggleable`. `icon: true` shows `check` in the thumb when on. Semantics: role Switch.

### Check

`Check(value: place, label: str)` · tier 2 · phase 1 — M3 `Checkbox` + label row, whole row
`toggleable`. Semantics: role Checkbox.

### Radio

`Radio(value: place, options: list, render?: fn(option) → node)` · tier 2 · phase 1 — vertical list of
M3 `RadioButton` rows (`selectableGroup`), each row `selectable`. Option text = `render(option)` or
`auto` text of the option. Writes the selected option value.

### Segmented

`Segmented(value: place, options: list, multi = false, labels?: list<str>, icons?: list<icon>, suffix?: str, prefix?: str)` · tier 2 · phase 1

M3 `SingleChoiceSegmentedButtonRow` / `MultiChoiceSegmentedButtonRow` with `SegmentedButton` per
option. Text = `labels[i]` or `prefix + auto(option) + suffix`. More than 5 options, or labels that
don't fit, → falls back to `Chips` with the same arguments. `multi` writes a list (in options order).

### Chips

`Chips(value: place, options: list, multi = false, labels?: list<str>, scroll = false)` · tier 2 · phase 1 —
`FilterChip` per option in a `Flow` (or a horizontal scrolling row with `scroll`). Single-select
re-tapping the selected chip does **not** deselect.

### Slider

`Slider(value: place, min = 0, max = 100, step?: num, label?: str, format?: str, prefix?: str, suffix?: str, ticks = false, showValue = true, inline = false)` · tier 2 · phase 1

- M3 `Slider` (Expressive track: `SliderDefaults.Track` with `trackInsideCornerSize`), colours
  default.
- `step` → `steps = round((max - min) / step) - 1` (if that is > 0 and ≤ 100; otherwise continuous
  and values are rounded to `step` on write). Values are written rounded to `step` (or to 4
  significant decimals if no step) to avoid float noise.
- Header row above: `label` (`labelLarge`) at start, value text at end
  (`prefix + fmt(value, format or auto) + suffix`, `labelLarge`, `primary`) when `showValue`.
- `ticks: true` shows tick marks (only when stepped).
- Haptic `tick` on each step change while dragging (when stepped), throttled to one per 30 ms.
- `inline: true` (only inside `Rich`): 120 dp wide, no header.
- History: one entry per drag (03 §7.5).
- Semantics: `progressBarRangeInfo(value, min..max, steps)`, `stateDescription` = value text.
- Positional order: `min`, `max`, `step`. Aliases: `from`→`min`, `to`→`max`, `minimum`→`min`,
  `maximum`→`max`, `unit`→`suffix`, `bind`→`value`.

### RangeSlider

`RangeSlider(value: place, min = 0, max = 100, step?: num, label?, format?, prefix?, suffix?)` · tier 2 · phase 2 —
M3 `RangeSlider`; the place holds `[lo, hi]`. Header value text: `lo – hi`.

### Stepper

`Stepper(value: place, min?: num, max?: num, step = 1, label?: str, format?: str, suffix?: str)` · tier 2 · phase 1

Row: optional `label` (start, `bodyLarge`), then `FilledTonalIconButton(remove)`, value text
(`titleMedium`, min width 48 dp, centred), `FilledTonalIconButton(add)`. Buttons disable at
`min`/`max`. Long-press repeats every 120 ms after 400 ms. Semantics: adjustable (`setProgress`).

### TextField

`TextField(value: place, label: str, placeholder?: str, kind: text|number|email|phone|url|multiline|search = text, maxLen?: num, prefix?: str, suffix?: str, icon?: icon, style: filled|outlined = outlined)` · tier 2 · phase 1

- M3 `OutlinedTextField` / `TextField`. `kind` sets keyboard type (`number` → `KeyboardType.Decimal`;
  `email`; `phone`; `url`; `search` → `ImeAction.Search` and a leading `search` icon), and
  `multiline` → `minLines = 3, maxLines = 8`; others single line.
- `kind: number` writes a **num** (parsed per 03 §2.4; invalid text keeps the previous number and
  shows the error state) — the place's type becomes num.
- `maxLen` shows a counter in supporting text.
- **Secret-shaped fields are refused:** if `label` or `placeholder` matches (case-insensitive)

  ```
  \b(password|passcode|otp|one[- ]time|cvv|cvc|card number|account number|routing|ifsc|ssn|aadhaar|pan number)\b
  |\b(atm|card|debit|credit|upi|bank|mpin|secret|security|login)\s+pin\b
  |\bpin\b(?!\s*code)
  ```

  (newline not part of the pattern), the field renders disabled with supporting text "EchoFlow
  blocks can't ask for secrets". `pin` is matched as a whole word and not when followed by
  `code` — unless it is qualified as a secret (`ATM PIN`, `UPI PIN`, `card PIN`), which is refused
  even before `code`. So `PIN`, `ATM PIN`, `ATM PIN code`, `UPI PIN` and `PIN number` are refused,
  while `Pincode`, `PIN code`, `Postal code`, `Spinner` and `Pinned notes` are allowed. India is the default locale (07 preamble); an
  address form MUST work. `[T-400]`–`[T-400c]`
- Aliases: `hint`→`placeholder`, `type`→`kind`, `bind`→`value`.

### Select

`Select(value: place, options: list, label: str, labels?: list<str>)` · tier 2 · phase 1 — M3
`ExposedDropdownMenuBox` with a read-only `OutlinedTextField` and `ExposedDropdownMenu` items.

### DatePicker / DateRange / TimePicker

- `DatePicker(value: place, label: str, min?: date, max?: date)` — a read-only `OutlinedTextField`
  showing `date(medium)` with a `calendar_month` trailing icon; tapping opens M3 `DatePickerDialog`
  (`selectableDates` limited by `min`/`max`). Writes a `date`. · tier 2 · phase 1
- `DateRange(value: place, label: str)` — same, opens `DateRangePicker` in a dialog; writes
  `[start, end]`. · tier 2 · phase 2
- `TimePicker(value: place, label: str)` — opens M3 `TimePicker` in a dialog; writes a `time`.
  · tier 2 · phase 2

### ColorPick

`ColorPick(value: place, options: list<color> = [primary, secondary, tertiary, error, success, warning, neutral])` · tier 2 · phase 2 —
a row of 32 dp circles filled with each role's solid colour; the selected one has a 2 dp
`onSurface` ring and a `check`. Writes the role **name** as a symbol.

### RatingInput

`RatingInput(value: place, max = 5, half = false)` · tier 2 · phase 2 — tappable stars (48 dp touch
targets, 32 dp icons), drag across to set. Haptic `tick` per change.

### Pad2D

`Pad2D(value: place, x: {min, max, label?}, y: {min, max, label?}, h = l)` · tier 2 · phase 3 —
a rounded (`shape: m`) `surfaceContainerHigh` area with 4×4 grid lines in `outlineVariant`, axis
labels at the edges, and a 24 dp `primary` puck at the value. Drag or tap to move. Writes `{x, y}` in
the given ranges (y increases **upwards**). One history entry per drag.

### Sketch

`Sketch(value: place, h = l, tools: list = [pen, marker, eraser], tone = neutral)` · tier 2 · phase 3 —
a drawing surface (`surfaceContainerLowest`, shape `l`) with a small toolbar (tools, undo, clear).
Strokes in `onSurface` (pen 3 dp, marker 12 dp at 40 % alpha). On each stroke end, writes a
`WImage` (PNG, max 1024 px on the long side, stored in the app's cache dir, referenced by URI).

### Swipe

`Swipe(deck: place, render: fn(item) → node, onLeft?: fn(item) → action, onRight?: fn(item) → action, h = l)` · tier 2 · phase 3

A stack of cards from the `deck` list (top = first item; the next two visible behind, scaled
0.95/0.9, offset 8/16 dp). Drag horizontally; release beyond 35 % of the width (or fling > 1,000
dp/s) dismisses the card in that direction with rotation up to 12°, removes it from `deck`, and
runs the handler. Buttons below: `close` (left) and `favorite` (right) `FilledTonalIconButton`s do
the same. Empty deck → `Empty("All done")`.

### Rank

`Rank(value: place, render: fn(item) → node)` · tier 2 · phase 3 — vertical list; long-press then
drag an item to reorder (other items animate with `animateItem`). Writes the reordered list. Each
item has a `drag_indicator` handle; TalkBack: custom actions "Move up"/"Move down".

### Sort (kanban)

`Sort(value: place, columns: list<str>, render: fn(item) → node, colW: num = 0.8)` · tier 2 · phase 3 —
horizontally scrolling columns (each `colW` × width, `surfaceContainerLow`, shape `l`, header with
column name and count). Long-press-drag a card between columns; the place is a record
`{column: [items]}`. TalkBack custom actions "Move to <column>".

### Form

`Form(onSubmit: action, submitLabel = "Submit", gap = m) [fields]` · tier 2 · phase 1 — a column of the
children followed by a full-width filled `Button(submitLabel)`. On submit: mark all `rule`s as
shown; if any field is invalid, focus the first invalid field and `!haptic(reject)`; else run
`onSubmit`.

---

## 8. Collections and data

### List

`List(items: list, render: fn(item, index) → node = auto, divider = true, empty?: node, lazy: bool | auto = auto, h?)` · tier 1 · phase 1

- Default `render`: an `Item` with `title = auto(item)` (for records: the first string field).
- `lazy: auto` → lazy when `len(items) > 30`. Lazy requires `h`; without `h`, `h` defaults to `l`.
  Lazy uses `LazyColumn` with keys from §2.5 and `animateItem()`.
- Non-lazy: a `Column` with `HorizontalDivider` between items when `divider`.
- Empty list → `empty` or nothing.

### Item

`Item(title, subtitle?: str, overline?: str, lead?: node | icon, trail?: node, onTap?: action)` · tier 1 · phase 1 —
M3 `ListItem` (`headlineContent`, `supportingContent`, `overlineContent`, `leadingContent`
(a string is an icon name → `Icon(size: m)`), `trailingContent`). Transparent container colour.

### Table

`Table(rows: list<rec>, cols?: list<col>, sort = true, filter = false, sticky = 1, dense = false, totals?: list<str>, onRow?: fn(row) → action, maxRows = 50)` · tier 1 · phase 1

- `cols` default: one column per key of the first row, header = key, value = that field.
- `col(header: str, value: field, format?: str, align?: start|end|center, w?: num)` — `w` is a
  minimum width in dp-units × 8 (`w: 12` = 96 dp). Numbers default to `align: end`.
- Layout: a horizontally scrolling table. The first `sticky` columns stay fixed. Header row
  `labelLarge` on `surfaceContainerHigh`; body `bodyMedium` (`bodySmall` when `dense`); row height 48
  dp (36 dense); 1 dp `outlineVariant` row dividers.
- `sort`: tapping a header sorts by that column (asc → desc → original), with an arrow icon. Sort
  state is UI state (persisted by key), not block state.
- `filter`: a search field above filters rows containing the text in any column (case-insensitive).
- `totals`: header names whose columns get a bold sum row at the bottom.
- More than `maxRows` rows: show the first `maxRows` and a "Show all (n)" text button that expands.
- Accessibility: `collectionInfo(rows, cols)`, each cell `collectionItemInfo`.

### Tree

`Tree(root: any, children: fn(node) → list, render: fn(node) → node, open: num = 1)` · tier 2 · phase 2 —
indented (16 dp per level) collapsible rows; `open` = levels expanded initially; chevron toggles.
Max 500 visible rows.

### Timeline

`Timeline(items: list, render: fn(item) → node, time?: fn(item) → any, dir: vertical|horizontal = vertical, tone = primary)` · tier 1 · phase 2 —
vertical: a 2 dp `outlineVariant` line with 12 dp `primary` dots; `time(item)` text (`labelMedium`)
before each item. Horizontal: a `Carousel`-like scroller with the line on top.

### Calendar

`Calendar(events: list<{date, title, tone?}>, month?: date = today(), view: month|week|agenda = month, onDay?: fn(date) → action, selected?: place)` · tier 2 · phase 2 —
month: 7-column grid (Monday first per ISO, localised weekday initials), today outlined, days with
events show up to 3 dots in their tones, selected day filled `primary`. Prev/next month buttons.
`week`: 7 columns of the week containing `month`. `agenda`: grouped `List` by date.

### Heatcal

`Heatcal(values: list<{date, value}>, tone = primary, weeks: num = 17)` · tier 1 · phase 2 —
GitHub-style grid: columns are weeks (most recent at the end), rows Monday–Sunday, 12 dp cells,
2 dp gaps, colour = tone solid at alpha `0.15 + 0.85 × (value / max)` (0 → `surfaceContainerHigh`).

### Diff

`Diff(before: str, after: str, mode: inline|split = inline)` · tier 1 · phase 2 — word-level diff
(Myers on whitespace-split tokens); removed in `errorContainer` strikethrough, added in the
`success` container.

### Json

`Json(value: any, open: num = 1)` · tier 1 · phase 2 — collapsible tree of the value in `mono`.

---

## 9. Navigation inside a block

### Tabs

`Tabs(index: place, labels: list<str>, icons?: list<icon>, scrollable: bool | auto = auto) [pages]` · tier 2 · phase 1

- M3 `PrimaryTabRow` (or `PrimaryScrollableTabRow` when `scrollable` is true, or `auto` and more
  than 4 tabs or labels don't fit).
- Only the selected page is composed. Page changes animate with a cross-fade (`motion: fast`).
  Height animates (`animateContentSize`).
- `index` is a 0-based number; out of range → clamped.
- If `pages` count ≠ `labels` count, the shorter wins.

### Pager

`Pager(index: place, dots = true, h?, peek = false) [pages]` · tier 2 · phase 1 — `HorizontalPager`
with page spacing 8 dp; `peek` shows 24 dp of neighbours. Height = `h` or the tallest page
(measured). Dots: 8 dp circles, selected `primary` 24 dp wide pill (animated), others
`outlineVariant`. Writes `index` on settle.

### Wizard

`Wizard(step: place, labels: list<str>, next?: list<any>, onFinish: action, finishLabel = "Done") [steps]` · tier 2 · phase 1

- `Steps` indicator on top, the current step's page, then a row: `TextButton("Back")` (hidden on
  step 0) and `Button("Next")` / `Button(finishLabel)` on the last step.
- `next[i]` (truthiness) enables Next on step `i`; missing → enabled.
- Next/Back slide pages horizontally (`motion: default`).

### Accordion

`Accordion(open?: place, single = true) [Section…]` · tier 2 · phase 2 — the children must be
`Section`s; they become collapsible. `single`: opening one closes others. `open` (optional) holds
the open index (or list of indexes when not `single`).

### Reveal

`Reveal(label = "Show more", lines: num = 3) [content]` · tier 1 · phase 1 — clamps `content` to
`lines` × the body line height (gradient fade to the background over the last 24 dp); a `TextButton`
toggles "Show more"/"Show less".

### Flip

`Flip(side?: place, back: node) [front]` · tier 2 · phase 2 — tapping flips around the Y axis
(`rotationY` 0 → 180 over `motion: default`, `cameraDistance = 12 × density`); content swaps at 90°.
`side` (optional) holds `"front"`/`"back"`.

---

## 10. Media and spatial

### Image

`Image(src: str | image, alt: str, fit: cover|contain = cover, h?: token<height> = aspect(16/9), shape = m, zoom = false)` · tier 1 · phase 1 —
Coil `AsyncImage` (https only, max 4 MB, redirects to non-https refused). While loading: §2.9
placeholder. Failure: `surfaceContainerHigh` box with `broken_image` icon and `alt` text. `zoom`
opens a full-screen viewer (pinch, double-tap 2×). `alt` is required; missing → `"Image"` with
`W403`.

### Gallery

`Gallery(srcs: list<str>, alts?: list<str>, cols = 3, viewer = true)` · tier 1 · phase 2 — square
thumbnails in a `Grid` with 4 dp gaps, shape `s`; tap opens a full-screen pager viewer.

### Video

`Video(src: str, poster?: str, autoplay = false, loop = false, h = aspect(16/9))` · tier 2 · phase 2 —
Media3 `PlayerSurface` (as `MarkdownVideo` does), play/pause, scrubber. Never autoplays with sound;
`autoplay` starts muted only while visible. Released when off-screen (05 §6.6).

### Audio

`Audio(src: str, title?: str, waveform = true)` · tier 2 · phase 2 — a row: play button, title,
time; `waveform` draws 48 bars (amplitude from the decoded file's RMS per segment, computed once
off the main thread), played portion in `primary`.

### Map

`Map(center?: {lat, lng} | str, zoom = 12, markers?: list<{lat, lng, title?, tone?, onTap?}>, route?: list<{lat, lng}>, h = l, interactive = false)` · tier 3 · phase 3 —
see 01 open question 4 for the provider. Until decided: renders a static card with a `map` icon,
the centre text and a "Open in Maps" button (`!map`). `interactive = false` means the map doesn't
capture scroll gestures until tapped.

### Model3D

`Model3D(src: str, autoRotate = true, h = l)` · tier 4 · phase 3 — glTF via SceneView; https only,
max 10 MB. Off-screen → released (05 §6.6).

### Lottie

`Lottie(src: str, loop = true, h = m)` · tier 2 · phase 3 — Lottie Compose; https only, max 1 MB JSON.

### Qr

`Qr(content: str, size: s|m|l = m)` · tier 1 · phase 2 — QR code (ZXing core encoder, error level M)
drawn as `onSurface` modules on `surface`, 120/160/200 dp, with a 4-module quiet zone.

---

## 11. Overlays

Overlays are **bindings** opened with `!open(name)` and closed with `!close` (03 §7.3). They are not
rendered in the flow; referencing an overlay binding as a child renders nothing.

| Component | Renders |
|---|---|
| `Sheet(title?: str, full = false) [content]` | M3 `ModalBottomSheet`, `skipPartiallyExpanded = full`. Title row in `titleLarge`. Content scrolls. |
| `Dialog(title: str, confirm?: {label, onTap}, dismiss?: {label, onTap?}) [content]` | M3 `AlertDialog`. The block's title is always shown above `title` in `labelMedium` `onSurfaceVariant` (anti-spoofing, 05 §13). |
| `Popover(anchor: name) [content]` | M3 `Popup` with a `surfaceContainer` card (shape `m`, elevation 2) positioned below the anchor node. |
| `Menu(anchor: name, items: list<{label, icon?, onTap}>)` | M3 `DropdownMenu` anchored to the node. |

Overlays live in their own window; Roborazzi can't capture them; verify them on the emulator
(CLAUDE.md).

---

## 12. Charts

### 12.1 `Chart`

`Chart(data: list<rec>, h: token<height> = m, polar = false, x?: axis, y?: axis, y2?: axis, legend: auto|top|bottom|none = auto, tooltip = true, scrub = true, zoom = false, select?: place, animate = true) [marks]` · tier 1 · phase 1 (marks: phase 1 = `line`, `area`, `bar`, `point`, `rule`, `band`, `arc`, `text`; phase 2 = the rest)

**Drawing is custom** (Compose `Canvas` with `drawWithCache`), not a third-party library, so output
is identical across implementations.

### 12.2 Plot geometry

1. **Plot area** = component bounds minus: left axis width (max measured tick-label width + 8 dp),
   bottom axis height (`labelSmall` line height + 8 dp), legend (24 dp + 8 dp gap when shown), and
   8 dp padding on the top and right.
2. **x scale**: if the x field values are all numbers or dates → linear (dates by epoch day);
   otherwise **band** (categorical, in data order) with 20 % padding between bands.
3. **y scale**: linear. Domain = [min(0, minY), maxY] unless `y.min`/`y.max` given (0 is included
   only when all values are ≥ 0 or the data crosses 0). Then **niced**: tick count target 5 (`y.ticks`
   overrides), step = nice number from {1, 2, 2.5, 5} × 10^k, domain extended to multiples of the
   step.
4. `log: true` → log10 scale; non-positive values dropped.
5. Grid lines: horizontal at each y tick, 1 dp `outlineVariant` at 60 % alpha. No vertical grid.
6. Tick labels: `labelSmall`, `onSurfaceVariant`; y labels formatted with `y.format` or `compact`;
   x labels with `x.format` or `auto`. X labels that would overlap are thinned (keep every n-th so
   the remaining fit, with at least 8 dp between labels).

### 12.3 Marks

All marks take field arguments as a field name string or a lambda (03 §2.2 `field`). `series:` splits
data into series by that field (one colour each, §1.4.1).

| Mark | Parameters | Draws |
|---|---|---|
| `line(x, y, tone?, curve: linear\|smooth\|step = smooth, width: s\|m\|l = m, dashed = false, points = false, series?)` | width 1.5/2.5/4 dp | Path through points. `smooth` = monotone cubic (Fritsch–Carlson). Round caps/joins. `points` adds 4 dp dots. |
| `area(x, y, y0?, tone?, stack = false, opacity = 0.3, series?, curve = smooth)` | | Fill between line and `y0` (default 0) at `opacity`, plus a 2 dp line on top. `stack` stacks series in order. |
| `bar(x, y, tone?, stack = false, group?, horizontal = false, round: token<shape> = s, labels = false)` | | Bars in the band; `group` splits a band into sub-bars; `stack` stacks; top corners rounded (both ends when stacked segment is outermost). `labels` writes values above bars (`labelSmall`). |
| `point(x, y, size?: field \| num = 6, tone?, shape: circle\|square\|diamond = circle)` | size in dp (field values are scaled to 4–20 dp by sqrt) | |
| `rule(x? \| y?, tone = muted, label?, dashed = true)` | | Full-width/height 1 dp line at the value; dash 4/4 dp; label at the end in `labelSmall`. |
| `band(from, to, axis: x\|y = x, tone = neutral, label?)` | | Rectangle across the plot at 12 % alpha. |
| `text(x, y, content, tone?)` | | `labelSmall` text centred above the point. |
| `arc(value, label, tone?)` | requires `polar: true` | Pie slices clockwise from 12 o'clock. Donut when the Chart has `inner: 0.6` (fraction of radius). Slices < 2 % merged into "Other". 2 dp gaps (stroke in `surface`). Labels in legend. |
| `range(x, lo, hi, tone?)` | | Vertical 1.5 dp line from lo to hi with 6 dp caps. |
| `cell(x, y, value)` | | Heatmap cell: colour = tone at alpha `0.1 + 0.9 × normalised value`. |
| `link(from, to, value)` | | Sankey link (phase 2; nodes laid out in columns by first appearance). |

### 12.4 Sugar components

Each expands to `Chart` + marks. They take `(data, x:, y:, …)` and pass the rest to `Chart`.

| Sugar | Expands to |
|---|---|
| `LineChart(data, x, y, series?)` | `Chart(data) [line(x, y, series)]` (y may be a list of fields → one line per field) |
| `AreaChart(data, x, y, stack = false)` | `area` per field |
| `BarChart(data, x, y, stack = false, horizontal = false)` | `bar` per field (grouped when several, unless `stack`) |
| `PieChart(data, value, label)` / `DonutChart` | `Chart(polar: true[, inner: 0.6]) [arc(value, label)]` |
| `ScatterChart(data, x, y, size?)` | `point` |
| `Sparkline(values: list<num>, tone = primary, h = xs)` | line without axes, grid, legend or tooltip; last point dot |
| `Histogram(values: list<num>, bins = auto)` | `bar` over bins (Sturges' rule for `auto`) |
| `Radar(data, axes: list<str>, series?)` | polar polygon chart (phase 2) |
| `Funnel(data, value, label)` | centred horizontal bars (phase 2) |
| `Treemap(data, value, label)` | squarified treemap (phase 2) |
| `Sankey(data, from, to, value)` | phase 2 |
| `Candles(data, x, open, high, low, close)` | `range` + bar bodies; up = `success`, down = `error` (phase 2) |
| `BoxPlot(data, x, y)` | quartiles per group (phase 2) |
| `Heatmap(data, x, y, value)` | `cell` (phase 2) |
| `Waterfall(data, x, y)` | running-total bars, up `success`, down `error`, total `primary` (phase 2) |

### 12.5 Interaction

- **Tooltip / scrub** (`tooltip`, `scrub`): touching the plot shows a vertical 1 dp `onSurface`
  cursor at the nearest x (band centre or nearest data x) and a tooltip card (`inverseSurface`,
  shape `s`, `labelMedium`) listing the x label and each series' value with a colour dot. Dragging
  moves it; haptic `tick` each time the nearest x changes. Lifting the finger hides it after 1.5 s.
  The chart claims horizontal drags only (vertical drags scroll the chat).
- **Select** (`select: $place`): tap selects the nearest data record (bar, point, slice); it is
  written to the place; the selected element is drawn at full colour and the others at 40 % alpha.
  Tapping it again clears (`null`).
- **Legend**: shown when there are ≥ 2 series (`auto`). Chips with a colour dot; tapping toggles the
  series' visibility (UI state).
- **Zoom** (`zoom: true`): pinch on x scales the x domain (min 2 points visible), double-tap resets.
- **Animation**: on data change, every mark interpolates from its previous geometry to the new one
  (matched by series + x key) with `spring: smooth`. New points grow from the baseline; removed
  points shrink to it. Interpolation runs per frame during slider drags (no debounce).
- Downsampling: series with more points than `2 × plot width in px` are reduced with LTTB before
  drawing; tooltips still use the full data.

### 12.6 Accessibility

The chart's content description is generated: `"<Kind> chart, <n> points. <series>: from <first>
to <last>, min <min>, max <max>."` per series (max 3 series described). A custom action
"View as table" opens a `Sheet` with a `Table` of the data.

---

## 13. Canvas

`Canvas(view: [x, y, w, h] = [0, 0, 100, 60], h: token<height> = l, fit: contain|cover = contain, onTap?: fn(p) → action, onDrag?: fn(p) → action, onDragEnd?: fn(p) → action, onPinch?: fn(s) → action, label: str) [marks]` · tier 2 (static, tap) / tier 4 (drag, pinch, frame) · phase 3

- The `view` box is mapped to the component bounds preserving aspect ratio (`contain`: letterbox
  centred; `cover`: crop). The model works only in view units.
- Event data `p` = `{x, y}` in **view units**; drag also has `dx`, `dy` (view units since last event)
  and `start: bool`. Pinch `s` = `{scale, cx, cy}`.
- `label` is required (`W403`, default "Drawing").

Canvas marks (all coordinates and sizes in view units; `width` is a stroke width in view units
unless a token `s|m|l` = 1/2/3 dp is given):

| Mark | Parameters |
|---|---|
| `circle(x, y, r, fill?, stroke?, width?)` | |
| `rect(x, y, w, h, fill?, stroke?, width?, radius = 0)` | |
| `line(x1, y1, x2, y2, stroke = onSurface, width = s, dashed = false, cap: round\|butt = round)` | |
| `path(d: str, fill?, stroke?, width?)` | SVG path data (M, L, H, V, C, S, Q, T, A, Z; absolute and relative) |
| `poly(points: list<[x, y]>, fill?, stroke?, width?, closed = true)` | |
| `label(x, y, content, style: token<text> = labelMedium, tone?, color?, anchor: start\|middle\|end = middle)` | text size is in **sp** (not view units) so it stays readable |
| `icon(x, y, name, size: s\|m\|l = m, tone?)` | size in dp, centred on (x, y) |
| `image(x, y, w, h, src)` | |
| `group(translate?: [x, y], rotate?: num (degrees), scale?: num, origin?: [x, y], opacity?: num) [marks]` | transforms apply to children |

- `fill`/`stroke` take colour tokens (§1.4). Missing `fill` and `stroke` → `fill: primary`.
- Each mark takes `onTap: action` (hit test: the mark's bounds, at least 48 dp square around its
  centre for small marks; topmost first).
- Each mark takes `key` for animation identity. Numeric parameters wrapped in `spring(...)` or
  `tween(...)` animate (§13.1).
- Drawn in one `drawWithCache` pass; text measured with a cached `TextMeasurer`.

### 13.1 Motion functions (usable in any numeric, colour or shape slot)

| Function | Meaning |
|---|---|
| `spring(v, kind: bouncy\|smooth\|stiff = smooth)` | The slot shows `v`, animating to new values of `v` with that spring (`animateFloatAsState`, keyed by the node key + parameter name) |
| `tween(v, motion: fast\|default\|slow = default)` | Same, with the motion scheme's effects spec |
| `loop(from, to, period: dur, ease: linear\|sine = sine)` | A value oscillating between `from` and `to` (sine) or sawtoothing (linear) with that period, while visible. Reduced motion → `from` |
| `stagger(i, base: dur = 40ms)` | Returns `i × base` — for use as `delay:` on `enter` (common prop `delay: dur`) |

These return the **target** value when used outside a rendering slot (e.g. in arithmetic), so they
are safe anywhere. Colour animation interpolates in sRGB.

---

## 14. Visual

| Component | Signature | Renders · tier · phase |
|---|---|---|
| `Gradient` | `(tones: list<color> = [primaryContainer, tertiaryContainer], angle = 135, h?, shape = m) [children]` | `Brush.linearGradient` at `angle` degrees through the colours; content colour `onPrimaryContainer`. · 1 · 2 |
| `Shader` | `(kind: aurora\|mesh\|grain\|ripple\|liquid, tones?: list<color>, speed = 1, h = m, shape = m) [children]` | AGSL `RuntimeShader` (API 33+; source files in `weave-compose/src/main/assets/shaders/<kind>.agsl`), uniforms `time` (s × speed, paused off-screen), `resolution`, `c0..c3` (tones, default primary/tertiary/secondary/surface). Below API 33 or reduced motion → `Gradient` with the same tones. · 4 · 3 |
| `Shape` | `(kind: token<shape>, size: s\|m\|l\|xl = m, tone = primary, emphasis = medium, morphTo?: token<shape>, rotate?: num, icon?: icon)` | The shape filled with the tone (sizes 32/48/72/112 dp); `morphTo` morphs on tap (and back); `rotate` in degrees; optional centred icon. · 1 · 2 |
| `Blob` | `(seed, tone = primary, h = m)` | An organic closed path from 8 points at radius `0.7 + 0.3 × rand(seed, i)`, smooth-cubic, filled with the tone container. · 1 · 3 |
| `Skeleton` | `(kind: text\|card\|chart\|list = card)` | The §2.9 placeholder for that kind, with shimmer. · 1 · 1 |

---

## 15. Learning and play

| Component | Signature | Behaviour · tier · phase |
|---|---|---|
| `Quiz` | `(answers: place, questions: list<{q, options, answer: num, why?}>, shuffle = false, onDone?: fn(score) → action)` | One question at a time in a `Card`: question `titleMedium`, options as full-width outlined buttons. Tap → correct turns `success` container with `check`, wrong turns `error` container with `close` and the correct one turns `success`; `why` appears as a `Callout(tip)`; haptic confirm/reject; "Next" button. `answers` stores chosen indexes. After the last: score `Metric` and "Retry" (resets `answers`), runs `onDone(score)` once. `shuffle` uses `shuffle(options, seed = block id + question index)`. · 2 · 3 |
| `Flashcards` | `(state: place, cards: list<{front, back}>, spaced = true)` | `Flip` card + "Again"/"Good"/"Easy" buttons. `spaced`: Leitner boxes 1–5 stored in `state` (`{i: box}`); next card = lowest box, then order. Progress "n / total". · 2 · 3 |
| `Blanks` | `(values: place, text: str)` | `text` with `[[answer]]` markers rendered as inline 96 dp `TextField`s; "Check" marks each field success/error (case-insensitive, trimmed). · 2 · 3 |
| `Match` | `(pairs: place, left: list<str>, right: list<str>)` | Two columns; tap left then right to pair (a line connects them, colour per pair); "Check" validates by index equality. · 2 · 3 |
| `Board` | `(cells: place, rows: num, cols: num, render: fn(cell, r, c) → node, onCell: fn(r, c) → action, gap = xxs)` | Square grid of `rows × cols` cells (max 20 × 20) filling the width; each cell renders `render(cells[r][c], r, c)` and is tappable. TalkBack: "Row r, column c, <plain text>". · 2 · 3 |
| `Dice` | `(values: place, count = 1, sides = 6, onRoll?: fn(values) → action)` | Tap to roll: 600 ms tumble animation, then writes values from `randInt(block.id + rollCount, i, 1, sides)`. · 2 · 3 |
| `Spinner` | `(choice: place, options: list<str>)` | Wheel with segments in series colours; tap spins 2–4 s (decelerating), writes the chosen option, haptic ticks per segment. · 2 · 3 |
| `Scratch` | `(cover: color = surfaceContainerHighest) [reveal]` | Content covered by a layer that clears where dragged (24 dp brush); at 60 % cleared, the rest fades away. · 2 · 3 |

---

## 16. Catalog entry format (for implementers)

Every component above is registered in Kotlin as a `ComponentSpec` (05 §3.3) with these fields,
which this document gives for each component:

| Field | From this document |
|---|---|
| `name` | heading |
| `leads` | first parameters before named ones in the signature (with their types) |
| `params` | the parameter table or signature, with types and defaults |
| `positionalOrder` | "Positional order" line; if absent, the signature order after the leads |
| `aliases` | "Aliases" line |
| `children` | `none`, `any`, `exactly(n)`, `only(<components>)` |
| `tier`, `phase` | as listed |
| `doc` | the first sentence of the component's description (used in prompts, 06 §3) |
| `placeholder` | §2.9 |
| `block` | block-level or inline (§3.2) |

A component's prompt line is generated from these fields (06 §3.2). Adding a component = adding a
`ComponentSpec` + its composable + its tests (07) + one line in this document.
