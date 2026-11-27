# 02 — The Weave language

**Spec version:** 1.3 · **Status:** normative · **Language version:** 1

This document defines exactly how Weave text becomes a parsed program: characters → tokens →
logical lines → statements → syntax tree. Semantics (what the tree *means*) are in
[03-semantics.md](03-semantics.md). Components and their parameters are in
[04-components.md](04-components.md).

Every rule here has at least one test in [07-conformance.md](07-conformance.md), referenced as
`[T-xxx]`.

---

## 1. Where Weave text comes from

### 1.1 Fences

A block is a **top-level fenced code block** in an assistant message whose info string is one of:

| Info string | Meaning |
|---|---|
| `weave` | A new block in Weave source. |
| `weave edit <id>` | An edit to the block with header id `<id>` in the same thread (§11). |
| `weave-json` | A new block, or an edit, in Weave-JSON (§12). |

- Matching is on the first word of the info string, case-sensitive (`Weave` is **not** a block; it
  renders as a code fence). `[T-001]`
- `<id>` is the second whitespace-separated word. Extra words after it are ignored.
- The fence is found by the existing `MarkdownStreamPlanner`; Echo UI receives a
  `MarkdownExtensionBlock(kind, info, body, complete)`. `kind` is the first word. The block's
  identity within the message is `MarkdownExtensionBlock.key` (the fence's source offset).
- The planner promotes a fence only when its kind is registered (05 §5.1 registers `weave`, and
  `weave-json` from phase 2). Both backtick (```` ``` ````) and tilde (`~~~`) fences are accepted, exactly as
  `openingFence` in `MarkdownSourceScan.kt` already does; the closing fence must use the same
  character. `[T-004]`
- Fences nested inside lists, quotes or other fences are **not** blocks. Only top-level fences are.

### 1.2 Text normalisation

Before lexing, the body MUST be normalised in this order:

1. Strip a leading UTF-8 BOM (`U+FEFF`).
2. Replace `\r\n` and lone `\r` with `\n`.
3. Replace tab characters (`\t`) outside strings with a single space.
4. Remove any line that consists only of optional spaces and three or more backticks (a stray fence
   the model wrote inside the body). `[T-002]`

Normalisation is applied to the whole body each time it is re-read during streaming. It MUST be
idempotent.

---

## 2. Lexical structure

### 2.1 Characters

Source is Unicode text. Identifiers are ASCII only. Strings may contain any Unicode.

### 2.2 Token kinds

The lexer produces these tokens. Whitespace (space) between tokens is discarded but **recorded**
as a `spaceBefore: Boolean` flag on each token, because three rules (§2.5 percent, §2.6
durations, §2.7 unit suffixes) depend on adjacency.

| Kind | Pattern | Examples |
|---|---|---|
| `NEWLINE` | `\n` | |
| `IDENT` | `[a-z_][A-Za-z0-9_]*` that is not a keyword | `root`, `emi`, `rate2`, `_` |
| `CAP_IDENT` | `[A-Z][A-Za-z0-9_]*` | `Card`, `LineChart` |
| `STATE` | `$` immediately followed by `[A-Za-z_][A-Za-z0-9_]*` | `$rate`, `$Count` |
| `ACTION` | `!` immediately followed by `[a-z][A-Za-z0-9_]*` | `!set`, `!say` |
| `KEYWORD` | see §2.3 | `fn`, `on`, `and` |
| `NUMBER` | §2.4 | `12`, `3.5`, `1_000`, `1e6` |
| `PERCENT` | §2.5 | `45%` |
| `DURATION` | §2.6 | `300ms`, `2s` |
| `STRING` | `"…"` with escapes and interpolation, §2.8 | `"Hi {name}"` |
| `RAW_STRING` | `"""…"""`, §2.9 | |
| `HEX` | §2.10 | `#FF5722` |
| `HEADER` | `@weave` | |
| `PUNCT` | `( ) [ ] { } , : :: . .. = == != < <= > >= + - * / % ? ?? \|> => ->` | |
| `EOF` | end of input | |

Longest match wins among punctuation: `..` before `.`, `::` before `:`, `??` before `?`, `==` before
`=`, `|>` (there is no lone `|` token outside string interpolation), `=>` before `=`, `->` before
`-`, `<=` before `<`, `>=` before `>`, `!=` before `!`.

A `!` that is followed by neither `=` nor a lowercase letter is handled by auto-fix rule A3 (§8.1).

### 2.3 Keywords

```
fn  js  on  when  export  and  or  not  in  true  false  null  transient  reset
```

Keywords cannot be used as binding, state, function or parameter names (`E106 reserved-name`).
`$in` and `$on` are allowed: the `$` makes them state names, not keywords.

`when` immediately followed by `(` in expression position is the built-in function `when(cond,
value)` (03 §10.1), which 06 §3.4 teaches. Only the effect form `on trigger when cond -> …` uses
`when` as a keyword. (Found by the eval harness: without this rule every `when(…)` call is `E105`.)
`[T-089]`

**Reserved identifiers** (not keywords, but cannot be declared as bindings, functions or
parameters): `root` is allowed and special (§5.6). These cannot be declared:
`dt`, `device`, `block`, `user`, `item`, `index`. Declaring one is `E106 reserved-name`. (They are
provided by the runtime in some contexts; see 03 §4.6.) Exception: `item` and `index` may be used as
`fn` and lambda **parameters** (`items |> map(item => item.name)`), where they shadow nothing.
`[T-089b]`

### 2.4 Numbers

```
NUMBER  = DIGITS [ "." DIGITS ] [ ("e" | "E") [ "+" | "-" ] DIGITS ]
DIGITS  = DIGIT { DIGIT | "_" }      (an underscore must be between two digits)
```

- `1_000_000` = 1000000. `1_`, `_1` are not numbers (`_1` is an identifier). `[T-010]`
- A digit run that contains `__` or ends in `_` (`1__0`, `1_`) is lexed as one malformed number
  token and reported as `E104 bad-number`, so the model gets one clear message instead of a
  cascade of parse errors. `[T-010b]`
- The lexer MUST NOT consume a `.` that is followed by another `.`: `1..10` is `NUMBER(1) .. NUMBER(10)`. `[T-011]`
- The lexer MUST NOT consume a `.` that is followed by a letter: `2.max` is not valid; it lexes as
  `NUMBER(2) . IDENT(max)` and fails in the parser.
- A leading minus is **not** part of the number; it is unary minus in the grammar.
- A number with a leading dot (`.5`) is fixed by auto-fix rule A5.
- Values are IEEE-754 doubles. A literal that overflows to infinity is `E104 bad-number`.

### 2.5 Percent literals

A `NUMBER` immediately followed by `%` (no space) is a `PERCENT` token **unless** the character
right after `%` is a digit, a letter, `$`, `_` or `(`. In that case `%` is the modulo operator.

| Source | Tokens | Value |
|---|---|---|
| `45%` | `PERCENT` | 0.45 |
| `45%,` | `PERCENT ,` | 0.45 |
| `45%)` | `PERCENT )` | 0.45 |
| `10%3` | `NUMBER % NUMBER` | 10 mod 3 |
| `10 % 3` | `NUMBER % NUMBER` | 10 mod 3 |
| `x%2` | `IDENT % NUMBER` | |

`[T-012]`

### 2.6 Duration literals

A `NUMBER` immediately followed (no space) by one of the units `ms`, `s`, `m`, `h`, `d`, where the
character after the unit is **not** `[A-Za-z0-9_]`, is a `DURATION` token. The value is stored as
whole milliseconds (rounded half-up).

| Unit | Milliseconds |
|---|---|
| `ms` | 1 |
| `s` | 1,000 |
| `m` | 60,000 |
| `h` | 3,600,000 |
| `d` | 86,400,000 |

`ms` is checked before `m`. `2m` is two minutes. `2min` is **not** a duration: it lexes as
`NUMBER(2)` then `IDENT(min)`, which fails to parse. Auto-fix A7 handles `min`, `sec`, `secs`,
`hr`, `hrs`, `day`, `days`. `[T-013]`

### 2.7 Unit suffixes the model should not write

A `NUMBER` immediately followed by `dp`, `px` or `sp` (then a non-identifier character) is lexed as
the number alone, and the suffix is discarded with `W403 autofix` (rule A6). Token snapping then
applies where the slot is a token (rule S4). `[T-014]`

### 2.8 Strings

```
STRING  = '"' { CHAR | ESCAPE | INTERP | "{{" | "}}" } '"'
ESCAPE  = "\" ( "n" | "t" | '"' | "\" | "u{" HEX1-6 "}" )
INTERP  = "{" interp_body "}"
```

- `{{` is a literal `{`, `}}` is a literal `}`.
- An unknown escape (`\q`) keeps both characters literally and adds `W403`.
- A string MUST end on the same logical line it started, except that it may continue across
  lines while inside an interpolation's brackets. An unterminated string at the end of a
  statement is `E102 unterminated-string` (outside streaming; see §10 for streaming).
- `'single quoted'` strings are converted to double-quoted by auto-fix A1.

**Interpolation body.** After `{`, the lexer scans to the matching `}` at depth zero, tracking
`( ) [ ] { }` nesting and skipping over nested strings (which may themselves contain
interpolations). The text is then split on top-level `|` characters that are **not** followed by
`>`:

```
interp_body = expr_text { "|" format_spec }
format_spec = IDENT [ "(" [ format_arg { "," format_arg } ] ")" ]
format_arg  = NUMBER | STRING | IDENT
```

`expr_text` is lexed and parsed as an expression. Formats are applied left to right
(`{x | num(2) | upper}`). Unknown format names are `E202 unknown-function` for that
interpolation only; the interpolation renders its value with the `auto` format. `[T-015]`

An empty interpolation `{}` is literal text `{}` with `W403`.

### 2.9 Raw strings

`"""` starts a raw string that ends at the next `"""`. Raw strings may span lines, have **no**
escapes and **no** interpolation. If the first character after the opening `"""` is a newline, it
is removed. Then the common leading indentation (spaces and tabs, compared character by
character) of all non-blank lines is removed from every line. Nothing else is stripped: a final
newline before the closing `"""` is **kept**, and lines containing only whitespace become empty.
Do not implement this with Kotlin's `trimIndent()`, which also drops a trailing blank line and
would fail `T-016`. Raw strings are used for `js fn` bodies and long text. `[T-016]`

### 2.10 Hex colours and comments

`#` starts a comment that runs to the end of the line, **except** when all of the following hold,
in which case it is a `HEX` token:

1. it is followed by exactly 3, 4, 6 or 8 hex digits `[0-9A-Fa-f]`, and
2. the character after those digits is not `[A-Za-z0-9_]`, and
3. the previous non-space token on the line is `:`, `(`, `,`, `=` or `[`.

`HEX` tokens are only ever valid as input to auto-fix rule S5 (they are never kept). `[T-017]`

A `#` inside a string is just a character.

### 2.11 The header token

`@weave` is only recognised at the start of the first non-blank logical line of the body. Anywhere
else it is `E110 header-position` and that statement is dropped. Any other `@word` is
`E105 unexpected-token`.

---

## 3. Logical lines (splitting a body into statements)

The model writes one statement per line, but a statement may continue over several physical lines.
The splitter turns the token stream into **logical lines**, each of which is one statement.

### 3.1 The rule

A `NEWLINE` token **ends** the current statement if and only if all three hold:

1. **Bracket depth is zero.** Depth counts `(`, `[`, `{` minus `)`, `]`, `}` seen so far in the
   statement. Brackets inside strings, raw strings and comments do not count.
2. **The last token is not a continuation token.** Continuation tokens are:
   `+ - * / % = == != < <= > >= ? ?? : :: . .. , |> => -> and or not in when` and the keyword `on`.
3. **The next non-blank line does not start with `|>`, `.`, `?`, `??`, `:`, `and`, `or`, `]`, `)`
   or `}`.**

Otherwise the `NEWLINE` is discarded and the statement continues.

Blank lines and comment-only lines never end a statement that is still open under rule 1 or 2;
they are skipped. `[T-020]`–`[T-026]`

### 3.2 Pseudo-code

```kotlin
fun splitStatements(tokens: List<Token>): List<List<Token>> {
    val out = mutableListOf<List<Token>>()
    var cur = mutableListOf<Token>()
    var depth = 0
    var i = 0
    while (i < tokens.size) {
        val t = tokens[i]
        when {
            t.kind == NEWLINE -> {
                val last = cur.lastOrNull()
                val next = nextNonNewline(tokens, i)          // null at EOF
                val ends = depth == 0 &&
                    (last == null || !last.isContinuation()) &&
                    (next == null || !next.isLeadingContinuation())
                if (ends && cur.isNotEmpty()) { out += cur; cur = mutableListOf() }
            }
            t.kind == EOF -> { if (cur.isNotEmpty()) out += cur }
            else -> {
                if (t.isOpenBracket()) depth++
                if (t.isCloseBracket()) depth = maxOf(0, depth - 1)
                cur += t
            }
        }
        i++
    }
    return out
}
```

A close bracket with no matching open bracket is `E103 unbalanced`. The statement containing it is
dropped, and the splitter continues with depth clamped at zero.

### 3.3 Consequence for streaming

Rule 3 looks at the **next** line. While streaming, a statement whose line has ended is therefore
only **confirmed complete** when the first non-space character of the next non-blank line arrives,
or the fence closes. See §10.

---

## 4. Grammar

EBNF notation: `{ x }` = zero or more, `[ x ]` = optional, `|` = alternative, quoted text = literal
token. The grammar applies to **one logical line** (one statement), except `block`.

### 4.1 Statements

```ebnf
block         = [ header ] { statement } ;

header        = "@weave" NUMBER { header_attr } ;
header_attr   = IDENT ":" header_value ;
header_value  = STRING | NUMBER | IDENT | "true" | "false" ;

statement     = state_decl
              | fn_decl
              | js_fn_decl
              | effect
              | export_stmt
              | delete_stmt            (* only valid in edits, §11 *)
              | binding ;

state_decl    = STATE [ ":" type_name ] "=" expr { state_flag } ;
state_flag    = "transient" | "reset" ;
type_name     = "num" | "str" | "bool" | "list" | "rec" | "dur"
              | "date" | "time" | "datetime" | "any" ;

binding       = IDENT "=" expr ;

fn_decl       = "fn" IDENT "(" [ param_list ] ")" "=" expr ;
js_fn_decl    = "js" "fn" IDENT "(" [ param_list ] ")" "=" RAW_STRING ;
param_list    = param { "," param } [ "," ] ;
param         = IDENT | "_" ;

effect        = "on" trigger [ "when" expr ] "->" expr ;
trigger       = IDENT [ "(" [ arg_list ] ")" ] ;

export_stmt   = "export" export_item { "," export_item } ;
export_item   = IDENT | STATE ;

delete_stmt   = "-" ( IDENT | STATE | "on" trigger ) ;
```

Disambiguation: a statement starting with `-` is a `delete_stmt` (there is no statement that is a
bare expression). A statement starting with `IDENT "="` is a binding. A statement starting with
`STATE` followed by `=` or `:` is a state declaration.

### 4.2 Expressions

Lowest precedence first. All binary operators are left-associative except comparison
(non-associative: `a < b < c` is `E105`) and `??` (right-associative).

```ebnf
expr          = lambda | pipe ;

lambda        = lambda_params "=>" expr ;
lambda_params = IDENT | "_" | "(" [ param_list ] ")" ;

pipe          = conditional { "|>" pipe_target } ;
pipe_target   = postfix ;                (* must be a call; see §6.5 *)

conditional   = coalesce [ "?" expr ":" expr ] ;
coalesce      = or_expr [ "??" coalesce ] ;
or_expr       = and_expr { "or" and_expr } ;
and_expr      = not_expr { "and" not_expr } ;
not_expr      = "not" not_expr | comparison ;
comparison    = range_expr [ comp_op range_expr ] ;
comp_op       = "==" | "!=" | "<" | "<=" | ">" | ">=" | "in" ;
range_expr    = additive [ ".." additive ] ;
additive      = multiplicative { ( "+" | "-" ) multiplicative } ;
multiplicative= unary { ( "*" | "/" | "%" ) unary } ;
unary         = "-" unary | postfix ;

postfix       = primary { member | index | call_args | children } ;
member        = "." IDENT ;
index         = "[" expr "]" ;
call_args     = "(" [ arg_list ] ")" ;
children      = "[" [ expr_list ] "]" ;  (* only where §6.4 allows *)
arg_list      = arg { "," arg } [ "," ] ;
arg           = [ IDENT ":" ] expr ;
expr_list     = expr { "," expr } [ "," ] ;

primary       = NUMBER | PERCENT | DURATION | STRING | RAW_STRING
              | "true" | "false" | "null"
              | STATE
              | qualified
              | IDENT
              | CAP_IDENT
              | ACTION                     (* must be followed by call_args *)
              | list_lit
              | record_lit
              | "(" expr ")" ;

qualified     = IDENT "::" ( IDENT | STATE ) ;
list_lit      = "[" [ expr_list ] "]" ;
record_lit    = "{" [ field { "," field } [ "," ] ] "}" ;
field         = ( IDENT | STRING ) ":" expr ;
```

### 4.3 Lambda detection

When the parser is at `expr` and sees:

- `IDENT` or `_` followed by `=>` → lambda with one parameter.
- `(` → scan forward to the matching `)`. If the token after it is `=>` **and** everything between
  is a valid `param_list`, parse a lambda. Otherwise parse `( expr )`.

`[T-030]`

### 4.4 Ternary vs named-argument colons

Inside `call_args`, `IDENT ":"` at the start of an argument is always a named argument. A ternary
inside an argument value works normally: `tone: $bad ? error : primary` parses as named arg `tone`
with a conditional value. `[T-031]`

### 4.5 What `=` means inside an expression

`=` is never valid inside an expression. If the parser meets `=` where it expects a binary
operator, it MUST treat it as `==` and add `W403 autofix` (rule S1). `[T-032]`

---

## 5. Statements in detail

### 5.1 Names

| Kind | Form | Namespace |
|---|---|---|
| State | `$name` | State namespace |
| Binding | `name` | Value namespace |
| Function | `fn name` / `js fn name` | Value namespace (shared with bindings) |
| Effect | identified by its **trigger key** (§5.5) | Effect namespace |
| Parameter | inside `fn` or lambda | Local scope |

- Binding and function names MUST start with a lowercase letter or `_` (`IDENT`). A binding written
  with a capital first letter (`Header = Title("x")`) where `Header` is not a catalog component is
  fixed by auto-fix S9: renamed to `header` and every reference in the block is rewritten. If a
  lowercase name already exists, the statement is `E107 bad-name` instead. `[T-040]`
- A state and a binding may share a name (`$rate` and `rate` are different).

### 5.2 Duplicate names

In a **new block**, if two statements declare the same name in the same namespace, the **later one
wins** and the earlier one is discarded with `W404 duplicate-name`. Order is source order.
`[T-041]`

In an **edit**, a statement with an existing name replaces it (§11). That is not a duplicate.

### 5.3 State declarations

```
$name [: type] = initial [transient] [reset]
```

- `type` is optional. If present, the initial value is coerced to it (03 §2.3); failure is
  `E205 bad-type` and the state starts as `null`.
- `transient` and `reset` may appear in either order, each at most once. A repeated flag is
  `W403`, ignored.
- `reset` only has an effect in edits (§11.3). In a new block it is accepted and ignored.

### 5.4 Functions

- `fn name(a, b) = expr`. Parameter names must be unique within the function (`E107` otherwise).
  `_` may repeat and is never bound.
- `js fn` is tier 4 only (05 §10). Below tier 4 it is `E209 tier` and the function evaluates to
  `null` when called.
- A function and a binding with the same name: later wins (`W404`).

### 5.5 Effects and trigger keys

An effect's **trigger key** is the canonical print (§13) of its trigger, for example
`change($rate,debounce:600ms)` or `every(1s)`. The `when` condition is **not** part of the key.
After checking, a block has at most one effect per trigger key.

Models often write several effects on the same trigger, usually with different guards:

```weave
on change($x) when $x > 10 -> !toast("high")
on change($x) when $x < 0 -> !toast("low")
```

Nothing is dropped. Auto-fix S12 (§8.2) **merges** all effects in one fragment (a new block, or
one edit) that share a trigger key into a single effect, in source order. Each effect `i`
contributes `!if(cond_i, action_i)` if it had a `when`, or `action_i` if it did not; the merged
effect has no `when` and its action is the list of those contributions. The example above becomes:

```weave
on change($x) -> [!if($x > 10, !toast("high")), !if($x < 0, !toast("low"))]
```

The merge emits `W403`, not `W404`: it is a rewrite, not a loss. What the merged effect does, exactly:

- The branches run **in source order inside one transaction** (03 §7.2). `!if` evaluates its
  condition when its turn comes, so a later branch's guard **sees writes made by earlier
  branches**. (`on change($x) when $x > 10 -> !set($x, 0)` followed by `… when $x < 5 -> !toast("low")`
  toasts after the first branch resets `$x`.) A model that needs independent guards should write
  conditions that don't depend on what the other branches write.
- It is **one effect firing** for the effect-loop cap (03 §8.5), however many branches it has.
- It commits **one** history entry.

`[T-042]` `[T-042b]` `[T-042d]`

In an edit (§11.2), statements apply in source order. S12 merges only a **run** of same-key effects
that no `-on <trigger>` for that key separates; each deletion applies at its own position. So
`on change($x) -> A`, `-on change($x)`, `on change($x) -> B` leaves one effect, `B`. Each merged
run **replaces** the block's effect with that key at the run's position. To keep an existing guarded
branch, the edit must restate it. `[T-104b]`

### 5.6 The root

- The binding named `root` is the block's entry node.
- If there is no `root` when the fence closes, the **first binding in source order whose
  expression is a component call** (`CAP_IDENT` call, after auto-fix) becomes the root, with
  `W405 no-root`.
- If there is still none, the block renders as a code fence with the "Couldn't render this block"
  chip (01 §18.4). `[T-043]`

### 5.7 Exports

`export a, $b` makes `a` and `$b` readable by later blocks in the same thread as `id::a` and
`id::$b`. Exporting a name that is never declared is `W407 unresolved` (export ignored). Multiple
`export` statements accumulate. `[T-044]`

---

## 6. Calls

### 6.1 Kinds of call

The callee decides the kind. Resolution order for a call `name(...)`:

| Callee form | Resolves to | If not found |
|---|---|---|
| `CAP_IDENT(...)` or `CAP_IDENT [ … ]` | Catalog **component** of that exact name | Auto-fix S7 (case-insensitive match), else `E201 unknown-component` |
| `ACTION(...)` | Catalog **action** | `E203 unknown-action` |
| `IDENT(...)` inside a `Chart` or `Canvas` children list, or inside a mark's children | Catalog **mark** for that container, then functions | as below |
| `IDENT(...)` anywhere | 1. user `fn` / `js fn` in this block; 2. a parameter or local holding a lambda; 3. catalog **built-in function** | `E202 unknown-function` |
| `qualified(...)` | not callable | `E207 not-callable` |

A user `fn` with the same name as a built-in **shadows** the built-in inside this block, with
`W404 duplicate-name`.

### 6.2 Lead arguments

Each component and action declares, in the catalog, an ordered list of **lead parameters** (0, 1
or 2). Marks (04 §12.3, §13) declare as leads every parameter before the first optional one (for
example `rect(x, y, w, h)` has 4 leads), and built-in functions accept every parameter
positionally (03 §10).

- Arguments without a name are matched to leads in order.
- For components and actions, a positional argument after all leads are filled is mapped by
  auto-fix S2 to the next parameter in the catalog's **positional order** list (04 gives it for
  every component) with `W403`. If the positional order list is exhausted, the argument is
  `E109 too-many-leads` and dropped. `[T-050]`
- A positional argument after a named argument is `E108 arg-order` and dropped. `[T-051]`
- A lead may also be passed by name using its parameter name (`Text(content: "x")`).
- Passing the same parameter twice (positionally and by name, or two names) keeps the **last** one
  with `W404`.

### 6.3 Named arguments

- A named argument whose name is not a parameter of the callee is checked against the callee's
  **alias table** (04). If it is an alias, it is renamed (`W403`). Otherwise it is dropped with
  `W401 unknown-arg`. `[T-052]`
- Common props (04 §2) are valid on every component.

### 6.4 Children

A `[ … ]` directly after a call is a **children list** (not an index) if and only if:

- the callee is a `CAP_IDENT` (component), with or without `( … )`; or
- the callee is a mark that the catalog marks as `acceptsChildren` (currently `group`).

In every other position `[ … ]` after an expression is an **index** (`list[0]`, `rec["k"]`).
`[T-053]`

Children expressions are evaluated and flattened one level: a list value contributes its items; a
`null` contributes nothing. A component whose catalog entry says `children: none` but which
receives children gets `E208 children-not-allowed`; the children are dropped and the component
still renders.

A component call without parentheses and without children (`Divider`) is a call with no
arguments. `[T-054]`

### 6.5 The pipe

`a |> f(b, c)` is rewritten at parse time to `f(a, b, c)`: the left side becomes the **first
positional argument**. The right side MUST be a call (`f(...)`, `Comp(...)` or `!action(...)`); a
bare name `a |> f` is accepted as `f(a)`. Anything else is `E105`. Pipes chain left to right. `[T-055]`

### 6.6 Actions as values

`!name(args)` creates an action value. It does not run when parsed or evaluated. It runs only when
an event fires (03 §7). An `ACTION` token not followed by `(` is accepted as a call with no
arguments (`!confetti`, `!undo`). `[T-056]`

---

## 7. The header

```
@weave 1 id: loan title: "Home loan" size: card icon: "savings" keep: true tier: 2
```

| Key | Type | Rule |
|---|---|---|
| version (the number) | integer | MUST be `1`. A higher number renders the block as code with the chip "Needs a newer EchoFlow". A missing number is treated as `1` with `W403`. |
| `id` | IDENT | `[a-z][a-z0-9_]{0,31}`. Invalid → generated id with `W403`. If the id is already used by an earlier block in the thread, this block's id becomes `<id>_2`, `<id>_3`… with `W404`. Default id: `b1`, `b2`… in thread order. |
| `title` | STRING | Max 60 characters, truncated with `…`. |
| `size` | IDENT | `inline` (default), `card`, `wide`, `full`. Unknown → `inline` with `W401`. |
| `icon` | STRING or IDENT | Material Symbols name. |
| `keep` | bool | Default `false`. |
| `tier` | NUMBER | Informational. Ignored except for diagnostics logging. |

Unknown keys: `W401`, ignored. Each key at most once; later wins with `W404`. `[T-060]`

---

## 8. Auto-fix

Models make the same mistakes repeatedly. Auto-fix repairs them **deterministically** before the
checker runs, so the block renders and a `W403 autofix` diagnostic records what changed.

Rules are applied **in the order listed**. Lexical rules (A) run on the token stream of each
logical line before parsing. Structural rules (S) run on the syntax tree after parsing and before
checking. Each rule is applied once per statement. `[T-070]`–`[T-089]`

### 8.1 Lexical rules

| Id | Detects | Rewrites to |
|---|---|---|
| A1 | `“` `”` `„` `‟` (smart double quotes) | `"` |
| A1 | `'…'` single-quoted string on one line (also `‘…’`) | `"…"` with inner `"` escaped |
| A2 | `&&` / `\|\|` | `and` / `or` |
| A3 | `!` followed by space, `(`, `$`, `!` or an uppercase letter | `not` |
| A4 | `===` / `!==` | `==` / `!=` |
| A5 | `.` followed by a digit where the previous token is not an operand (`IDENT`, `STATE`, `)`, `]`, `}`, `NUMBER`, `STRING`) | `0.` |
| A6 | `NUMBER` + `dp`/`px`/`sp` (§2.7) | `NUMBER` |
| A7 | `NUMBER` + `min`/`mins`/`sec`/`secs`/`hr`/`hrs`/`day`/`days` with no space | the matching `DURATION` |
| A8 | `True`, `False`, `None`, `nil`, `undefined`, `NULL` as whole identifiers | `true`, `false`, `null` |
| A9 | `;` at the end of a logical line | removed |
| A10 | `elif`, `else if` — not supported | not fixed (left as error) |

### 8.2 Structural rules

| Id | Detects | Rewrites to |
|---|---|---|
| S1 | `=` in expression position (§4.5) | `==` |
| S2 | Extra positional argument on a component or action (§6.2) | Named argument using the catalog's positional order |
| S3 | Named argument that is an alias (§6.3) | The canonical parameter name |
| S4 | A `NUMBER` in a token-typed slot (spacing, height, shape, elevation) | The nearest token by dp value, using the dp values in the tables of 04 §1 (for shapes, the EchoFlow default `Shapes` values in 04 §1.5, never the runtime theme, so the result is deterministic). Ties go to the **smaller** token. E.g. `gap: 13` → `m` (12); `gap: 14` → `m` (12 and 16 tie, smaller wins); `shape: 24` → `m` (20 and 28 tie) |
| S5 | A `HEX` token in a colour slot | A role, by the algorithm in §8.3 |
| S6 | `if(c, a, b)` function call (not defined by the block) | `c ? a : b` |
| S7 | `IDENT(...)` that is not a function, mark or parameter, but whose capitalised form is a catalog component (`card(...)`); or a `CAP_IDENT` that matches a component case-insensitively (`LINECHART`) | The exact component name |
| S8 | A string literal in a token-typed slot whose text is a valid token (`gap: "m"`) | The token |
| S9 | A binding declared with a `CAP_IDENT` name that is not a component (§5.1) | Lowercase-first name, all references rewritten |
| S10 | `Column(children: [a, b])` — `children` passed as a named argument | A children list |
| S11 | A record field or named argument written as `key = value` inside `{ }` or `( )` | `key: value` |
| S12 | Two or more effects in one fragment with the same trigger key (§5.5) | One effect whose action is the source-ordered list of `!if(cond, action)` (or `action` where there was no `when`) |
| S13 | In a `format` or `deltaFormat` slot, a call whose name is a format (03 §11) with only literal or bare-word arguments: `format: dec(1)` | The format text as a string: `format: "dec(1)"`. `[T-088]` |

### 8.3 Hex to colour role (S5)

1. Parse the hex (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`). Ignore alpha.
2. Convert to HSL (H in degrees 0–360, S and L in 0–1).
3. If `S < 0.15` or `L < 0.08` or `L > 0.95` → `neutral` (in a text colour slot: `muted`).
4. Else by hue: `H < 20` or `H ≥ 345` → `error`; `20 ≤ H < 70` → `warning`; `70 ≤ H < 170` →
   `success`; `170 ≤ H < 345` → `primary`.

`[T-085]`

---

## 9. Diagnostics

Every problem produces a `Diagnostic(code, severity, statement, span, message)`. **Errors** remove
the smallest unit they affect (argument, node or statement). **Warnings** change nothing visible.

The message text is used verbatim in repair prompts (06 §6), so it MUST match these templates
exactly. `{x}` marks a substitution.

### 9.1 Errors

| Code | Name | Removes | Message template |
|---|---|---|---|
| E101 | syntax | statement | `Line {line}: can't read this statement near "{near}".` |
| E102 | unterminated-string | statement | `Line {line}: a string is missing its closing quote.` |
| E103 | unbalanced | statement | `Line {line}: brackets don't match.` |
| E104 | bad-number | statement | `Line {line}: "{text}" is not a valid number.` |
| E105 | unexpected-token | statement | `Line {line}: unexpected "{token}".` |
| E106 | reserved-name | statement | `"{name}" is reserved and can't be used as a name.` |
| E107 | bad-name | statement | `"{name}" is not a valid name here.` |
| E108 | arg-order | argument | `{callee}: positional arguments must come before named ones.` |
| E109 | too-many-leads | argument | `{callee}: too many unnamed arguments; name them.` |
| E110 | header-position | statement | `"@weave" must be the first line.` |
| E201 | unknown-component | node (children kept, §18.1 of 01) | `Unknown component "{name}".{suggestion}` |
| E202 | unknown-function | expression → `null` | `Unknown function "{name}".{suggestion}` |
| E203 | unknown-action | action → no-op | `Unknown action "!{name}".{suggestion}` |
| E204 | missing-required | node renders error chip | `{callee} needs "{param}".` |
| E205 | bad-type | argument | `{callee}.{param} expects {expected}, got {actual}.` |
| E206 | bad-token | argument | `{callee}.{param}: "{value}" is not one of {allowed}.` |
| E207 | not-callable | expression → `null` | `"{name}" can't be called.` |
| E208 | children-not-allowed | children | `{callee} doesn't take children.` |
| E209 | tier | node/expression/statement | `{feature} isn't available at this level.` |
| E210 | bind-not-state | argument | `{callee} needs a state variable like $value as its first argument.` |
| E301 | missing-ref | reference → `null` | `"{name}" is used but never defined.` |
| E302 | cycle | all statements in cycle → `null` | `{names} depend on each other in a loop.` |
| E303 | budget | expression → `null` | `"{name}" took too long to compute.` |
| E304 | limit | nodes past the limit | `This block is too large ({what}).` |
| E305 | — | — | Reserved. Not used in language version 1; never emitted. |
| E306 | effect-loop | effect chain stopped | `Effects triggered each other too many times.` |

`{suggestion}` is ` Did you mean "{closest}"?` when a catalog name is within Damerau–Levenshtein
distance 2 (ties: alphabetical first), otherwise empty.

### 9.2 Warnings

| Code | Name | Message template |
|---|---|---|
| W401 | unknown-arg | `{callee}: ignored unknown "{param}".` |
| W402 | token-snapped | `{callee}.{param}: used "{token}" for {value}.` |
| W403 | autofix | `Fixed: {description}.` |
| W404 | duplicate-name | `"{name}" was defined twice; using the later one.` |
| W405 | no-root | `No "root"; using "{name}".` |
| W406 | — | Reserved. Not used in language version 1; never emitted. |
| W407 | unresolved | `"{name}" isn't defined; treated as a plain word.` |
| W408 | deprecated | `{callee}.{param} is deprecated; use {replacement}.` |

Warnings are logged (debug builds show them in the block's ⋯ → "Diagnostics") and counted for the
auto-downgrade rule (05 §10.4). They are never shown to users in release builds.

---

## 10. Streaming parse

While a reply streams, the fence body grows. The parser MUST behave as follows.

### 10.1 Frozen statements

The parser keeps a list of **frozen** statements: ones confirmed complete under §3.3. A frozen
statement is never re-lexed or re-parsed while the body only grows. If the body ever changes in a
way that does not extend the previous text (the transport restarted), the parser discards
everything and starts again from the full text.

### 10.2 The tail

Everything after the last frozen statement is the **tail**. On each update the tail is parsed
**speculatively**:

1. Lex the tail. If the tail ends inside a string, append `"`. If it ends inside a raw string,
   append `"""`.
2. If the last token touches the end of the text (no newline or space after it) and is an
   `IDENT`, `CAP_IDENT`, `STATE`, `ACTION`, `NUMBER` or `KEYWORD`, **remove it** (it may be a
   partial word: `Sli` of `Slider`).
3. Remove trailing tokens while the last token is a continuation token (§3.1 rule 2) or is
   `IDENT :` inside a call (a named argument without a value).
4. Append the closers for every open bracket, innermost first.
5. Split into statements (§3) and parse each. Statements that parse are **speculative**; those that
   don't are ignored.

Speculative statements are rendered (so a `Column [a, b, …` shows `a` and `b` while `c` arrives)
but are replaced on the next update. `[T-090]`–`[T-095]`

### 10.3 Confirmation

A statement in the tail becomes frozen when the splitter ends it (§3.1) **and** the first
non-space character of the following non-blank line has arrived (or the fence is closed). On fence
close, the whole body is parsed again from scratch, and the result MUST be identical to the frozen
statements plus the final tail. Tests assert this for every corpus program (07 §8).

### 10.4 What is shown for statements that haven't arrived

See 05 §6.3 (placeholders).

---

## 11. Edits

### 11.1 Form

````
```weave edit loan
$rate = 9.1 reset
chart = Chart(sched, h: l) [bar(x: "year", y: "interest")]
-cta
-on change($rate)
on change($years) -> !haptic(tick)
```
````

An edit body follows the same grammar as a block, except: no header (a header is `E110`), and
`delete_stmt` is allowed.

### 11.2 Applying an edit

Given the block's current statements `S` and the edit's statements `E`, in `E`'s source order:

| Edit statement | Effect on `S` |
|---|---|
| State/binding/function with a name already in `S` | Replace it. |
| State/binding/function with a new name | Add it. |
| Effect whose trigger key exists in `S` | Replace it. |
| Effect with a new trigger key | Add it. |
| `export …` | Add the names to the export set. |
| `-name` / `-$name` / `-on trigger` | Remove it. Removing something that doesn't exist is `W407`. Removing `root` is `E107` (ignored). |

The result is checked as a whole new program (version `n+1`). The edit is **rejected entirely**,
the block stays at version `n`, and the rejection is logged, if the result:

- has **no renderable root**; or
- exceeds `Limits.SOURCE_BYTES` (canonical print, §13) or `Limits.STATEMENTS` (05 §13.3).

There is no partial application and no truncation. A rejected `!ask` response is handled as a
failed `!ask` (03 §9.2: previous content kept, inline "Retry" chip).
`[T-100]`–`[T-106b]`, `[T-108]`

### 11.3 State during edits

- A state that exists in both versions **keeps its current value**, unless the edit's declaration
  has the `reset` flag, in which case it is re-initialised from the new expression.
- A new state starts from its initial value.
- A removed state's value is discarded.
- If a kept state's declared type changes, the current value is coerced to the new type; if that
  fails it is re-initialised.

### 11.4 Edits to unknown blocks

If no block with that id exists in the thread: if the edit contains `root`, it is rendered as a new
block with that id (as if it were a `weave` fence); otherwise it renders as a code fence. `[T-107]`

---

## 12. Weave-JSON

> **Phase 2.** Weave-JSON is specified here so the IR does not paint itself into a corner, but it
> is **not** part of phase 1 (01 §29). A phase 1 implementation MUST reject `weave-json` fences
> (they render as an ordinary code fence) and need not pass `T-120`–`T-124`. It ships only when a
> model in the tier table is measured (07 §10) to do clearly better with constrained JSON than with
> source Weave.

### 12.1 Shape

```json
{
  "weave": 1,
  "id": "loan", "title": "Home loan", "size": "card", "icon": "savings", "keep": false,
  "state":  { "rate": 8.5, "years": 20, "draft": { "=": "\"\"", "transient": true } },
  "let":    { "emi": "monthly($principal, $rate, $years)" },
  "fn":     { "monthly(p, r, y)": "p * (r/1200) * pow(1 + r/1200, y*12) / (pow(1 + r/1200, y*12) - 1)" },
  "nodes":  {
    "root": { "t": "Card", "a": { "pad": "l" }, "c": ["rate", "kpi"] },
    "rate": { "t": "Slider", "a": { "value": "$rate", "min": 6, "max": 14 } },
    "kpi":  { "t": "Metric", "a": { "label": "Monthly EMI", "value": "=emi", "format": "inr" } }
  },
  "on":     [ { "trigger": "change($rate)", "when": "$rate > 10", "do": "!haptic(tick)" } ],
  "export": [ "emi", "$rate" ]
}
```

### 12.2 Lowering rules (JSON → Weave statements)

| JSON | Becomes |
|---|---|
| `weave`, `id`, `title`, `size`, `icon`, `keep` | The header. `weave` is required; missing → `E110`-equivalent: render as code. |
| `state.k: <json value>` | `$k = <literal>` where JSON objects become records, arrays lists. |
| `state.k: {"=": "expr", "type"?: "num", "transient"?: true}` | `$k[: type] = expr [transient]`. An object state value is only treated this way if it has the key `"="`. |
| `let.k: "expr"` | `k = expr`. A leading `=` in the string is stripped. |
| `fn."name(a, b)": "expr"` | `fn name(a, b) = expr` |
| `fn."name(a, b)": {"js": "source"}` | `js fn name(a, b) = """source"""` |
| `nodes.k: {"t": T, "a": {…}, "c": […]}` | `k = T(named args…) [children…]` |
| `on[i]: {"trigger", "when"?, "do"}` | `on trigger [when …] -> do` (`do` may be a string or a list of strings) |
| `export: […]` | `export …` |

**Argument values** in `a` (and list items in `c` that are strings):

| JSON value | Means |
|---|---|
| number, boolean, null | literal |
| string starting with `=` | expression (the rest of the string) |
| string starting with `!` | action expression (the whole string) |
| string that is exactly `$name` or a `$name` path (`$a.b`, `$a[0]`) | state reference (for a lead input parameter this is the binding) |
| string starting with `'` followed by `=`, `!` or `$` | literal string without the `'` |
| any other string | literal string; in a token slot, a token |
| object with `"t"` | an inline node |
| other object / array | literal record / list (values inside follow the same rules) |

**Children** `c`: each string item that names a key of `nodes` is a reference to that node; any
other string is a `Text` node with that string; objects with `"t"` are inline nodes.

Leads are passed **by parameter name** in JSON (`"value"` for `Slider`, `"content"` for `Text`); the
alias `"bind"` is accepted for the first lead of any input component.

### 12.3 Edits in JSON

```json
{ "edit": "loan", "state": {…}, "let": {…}, "fn": {…}, "nodes": {…}, "on": […],
  "export": […], "delete": ["cta", "$draft", "on change($rate)"] }
```

Lowered to an edit (§11) with the same rules. `state` entries in an edit may carry `"reset": true`.

### 12.4 Streaming JSON

The JSON parser is tolerant and incremental. It MUST emit an entry of `state`, `let`, `fn`, `nodes`
or `on` as soon as that entry's value is complete, so the block renders progressively like source.
Unterminated JSON at fence close is repaired by closing open strings, arrays and objects (same as
§10.2 steps 1 and 4); entries that are still invalid are dropped with `E101`.

The JSON Schema for constrained decoding is generated from the catalog (05 §12) and restricts `t` to
component names and `a` keys to each component's parameters.

---

## 13. Canonical printing

The runtime needs one exact text form of a program, for: trigger keys (§5.5), sending block source
back to the model (06), the "View source" sheet, `.weave` export, and test comparisons. The
canonical printer MUST produce:

1. Header first (if any): `@weave 1` then attributes in the order `id title size icon keep tier`,
   each as ` key: value`.
2. Then statements grouped and ordered: state declarations, `root`, other bindings in their
   original relative order, functions, effects, exports. One blank line between groups.
3. Inside a statement: single spaces around binary operators, `=`, `=>`, `->`, `|>`; no space
   inside brackets; `, ` between items; `name: value` for named args; one space before a children
   list (`Card(pad: l) [a, b]`); leads before named args; named args in the catalog's parameter
   order.
4. Numbers: shortest round-trip decimal (`8.5`, `1000000`, `0.45`; no underscores, no exponent
   below 1e21). Percent literals print as their decimal value. Durations print in the largest unit
   that divides evenly (`600ms`, `2s`, `5m`).
5. Strings: double quotes, escapes `\n \t \" \\`, braces doubled, interpolations printed
   canonically.
6. Statements longer than 100 characters are **not** wrapped (the printer output is one line per
   statement; this keeps trigger keys and diffs simple).
7. Comments are dropped.

The trigger key printer is the same printer applied to the trigger alone with **no spaces at all**.

`print(parse(print(p))) == print(p)` MUST hold for every program `p`. `[T-110]`
