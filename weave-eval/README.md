# weave-eval — the Echo UI model eval harness

Implements [07 §10](../docs/echo-ui/07-conformance.md): 60 tasks in 6 categories plus 20 control
prompts, graded with the real `:weave-core` parser, checker and evaluator.

## No API key

The harness never calls a provider. It is file-based:

```
./gradlew :weave-eval:run --args="prepare --run weave-eval/runs/<name> --tier 2 --level balanced"
#   → system.md (the 06 prompt section), tasks/<id>.md, manifest.tsv, AGENT.md
#   any model answers: one reply per task in responses/<model-id>/<id>.md
./gradlew :weave-eval:run --args="score --run weave-eval/runs/<name> --model <model-id>"
./gradlew :weave-eval:run --args="compare --run weave-eval/runs/<name>"
#   → report-<model>.md / .json per model, summary.md across models
```

Replies can come from anything that can read `system.md` + a task and write the answer: a provider
SDK, a CLI coding agent standing in for the model (how the first run was produced), or a person.

## What is measured (07 §10.2)

| Metric | How |
|---|---|
| Parse success | first `weave` block of the reply has no `E1xx` diagnostic |
| Dropped statements | statement-removing errors (`E101`–`E107`, `E110`) per block |
| Render success | the block has a root that evaluates to a node without an error chip |
| Task completion | the task's checks pass on a live `Session`: inputs are set by label, then numbers / text / node types are read from what the block shows |
| Token cost | ceil(UTF-8 bytes / 4) of the block body, the same tokenizer as the prompt snapshots |
| Over-use | (Balanced) control prompts that got a block |

A model meets the tier bar (07 §10.3) at parse ≥ 95 %, dropped ≤ 0.5, render ≥ 97 %,
completion ≥ 85 % and over-use ≤ 10 %.

Phase 1 ships tier 2 (05 §15), so `play` tasks (tier 4) are skipped until phase 3.

## Checks

Checks are written against behaviour, not layout: `Input("rate|interest", 8.5)` finds the input
whose label (or bound state) matches and writes 8.5 through the same path a user's drag takes;
`Number(43391.16, "emi|month")` then reads the matching metric or text line, understanding
`₹43,391`, `12,34,568`, `4.3 L` and `2.5 Cr`. Expected numbers are computed from the formulas in
03 §10 (see the comments in `Tasks.kt`), and `HarnessTest` grades hand-written reference replies to
prove each kind of check can pass.
