# First eval run — 2026-11-26, phase 1 build, tier 2, Balanced

The first run of the 07 §10 harness, used to check that models from other companies can write Weave
from the 06 prompt alone. No provider API key was used: each model answered the 70 requests
(50 tier-2 tasks + 20 controls; `play` is tier 4 and skipped in phase 1) as a coding-agent
subagent that was told to read only `system.md` and its task file.

| Model | Reached through |
|---|---|
| Gemini 4 Hydrogen (Google) | Antigravity subagent |
| GPT 6 Luna (OpenAI) | Codex subagent |

MiMo v2.6 Flash (OpenCode) was started and then dropped from this run; it has no results.

## Results

| Model | Parse | Dropped / block | Render | Completion | Tokens / block | Over-use | Tier 2 bar |
|---|---|---|---|---|---|---|---|
| **Spec 1.2 as written** ([baseline](baseline-spec-1.2/summary.md)) | | | | | | | |
| Gemini 4 Hydrogen | 98.0 % | 0.02 | 100 % | 94.0 % ¹ | 347 | 0 % | met |
| GPT 6 Luna | 88.9 % | 0.11 | 88.9 % | 74.0 % ¹ | 103 | 0 % | **not met** |
| **Spec 1.3** ([summary](summary.md)) | | | | | | | |
| Gemini 4 Hydrogen | 100 % | 0.00 | 100 % | 100 % | 347 | 0 % | met |
| GPT 6 Luna | 100 % | 0.00 | 100 % | 86.0 % | 103 | 0 % | met |

¹ Baseline completion was also lowered by harness bugs fixed during the run (see below); the 1.3
rows use the fixed harness, so the gap between the rows is spec + harness together.

Both models clear the tier-2 bar (07 §10.3) on spec 1.3, so phase 1 Weave works with models from
other providers. GPT 6 Luna writes far shorter blocks (103 vs 347 tokens) and sometimes prefers
prose.

## What the run found in the spec (fixed in 1.3)

1. **`when` keyword vs `when()` function.** 06 §3.4 teaches `when(c, node)`, but 02 §2.3 made
   `when` a keyword, so every call was `E105` and the block lost its root. It broke 5 of GPT 6
   Luna's quizzes. `when(` in expression position is now the built-in.
2. **Formats written as calls.** The prompt lists formats as `num(2) dec(1)`; both models wrote
   `format: dec(1)` (17 times for Gemini, 6 for GPT), which was `E202` and dropped the format.
   New auto-fix S13 turns it into `format: "dec(1)"`.
3. **`item` as a lambda parameter.** `map(xs, item => …)` was `E106` because `item` is reserved.
   `item` and `index` are now allowed as parameters.

## Remaining failures (model errors, not the spec)

| Model | Task | Why |
|---|---|---|
| GPT 6 Luna | calc_bmi | passed metres to `bmi(weightKg, heightCm)` |
| GPT 6 Luna | data_visits | growth shown with `format: percent` on 1400 → "140000%" |
| GPT 6 Luna | plan_jaipur, plan_study, plan_wedding, plan_meals, plan_routine | answered in prose; the Balanced prompt lists "plans and schedules" as block material |

## Harness fixes made during the run

- Unit-suffixed numbers are read both scaled and raw (`₹560 lakh` in a lakh-denominated task).
- Text checks read every reachable tab page, `List` rows produced by `render`, `Item` overlines,
  and what one tap on a button reveals; nodes with a falsy `show` are not read.
- `cmp_storage` expected 650 / 2000 = 0.325 (was 0.32 with a tolerance a correct 0.33 missed).

## Caveats

- The models ran as agents in a coding tool, not through the chat API EchoFlow uses. They were
  instructed to read only the prompt and task files, but this is not the same as a sealed API call.
- One run per model; no repeat sampling.
- Tier 3–4 and Immersive were not run: phase 1 ships tier 2 (05 §15).

## Suggested tier-table entries (05 §10.1)

`gemini-4-hydrogen` → 2 and `gpt-6-luna` → 2 (the phase 1 maximum). Re-run at tier 3 when phase 2
ships `!ask` / `lazy`.
