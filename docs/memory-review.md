# Memory review follow-up — PR #167

Reviewed all inline comments, review bodies and discussion comments from Qodo,
Greptile and CodeRabbit, plus an independent source pass. Five follow-up commits
are pushed together. No tests, builds, screenshots or paid-account requests were run.

## Reviewer dispositions

- Qodo: swallowed queue failures now produce sanitized diagnostics and a visible
  learning note. An ID/cutoff journal survives Room or scheduling failure, with
  startup/manual/periodic recovery. WorkManager enqueue completion is awaited.
- Qodo: schema 26 already includes `includesLocal` after the prior schema refresh
  commit `fdfcfef`. Entity, migration and checked-in table definition agree; no
  new schema change or fabricated identity hash is needed in this follow-up.
- Greptile / CodeRabbit: partial batches flush once six hours old, using periodic
  maintenance. Explicit retry flushes immediately. Plan-based thresholds remain,
  rather than discarding the requested 5/3/1 batching policy. Android may defer work.
- Greptile: local-provider provenance and exclusion are durable and recorded
  before user text is persisted, not inferred from the latest successful reply.
  Upload and retrieval recheck consent; old-generation work cannot become eligible
  after reconnecting or re-enabling learning.
- Greptile: missing sources have an explicit recovery path. Retry learning clears
  their sent revision and remote ID, allowing unchanged content to upload again.
  This is intentionally not automatic because remote deletion may be deliberate.
- Qodo screenshot rules: existing host-rendered artifacts are historical, not
  device validation or images of the redesign. No new captures are made because
  the user explicitly prohibited them. PR embeds are removed to avoid suggesting
  that old images represent this revision. Existing artifacts and future output
  paths are moved to `docs/screenshots/memory/` without regenerating images.
- CodeRabbit's docstring-coverage warning is not represented as resolved. Added
  documentation explains lifecycle and privacy contracts, but the suggested
  automated docstring/review workflow was not run.

## Independent fixes

- Redact credentials before transcript truncation, reject orphan replies to
  pre-consent prompts, avoid stale extraction acknowledgements, and match whole
  words in the bounded recent-context bridge.
- Recheck request-scoped permissions after retrieval, reject unknown tools and
  non-string arguments, and isolate local fallback failures from cloud recall.
- Encode remote path segments, reject malformed list/search/profile responses,
  and display only explicitly identified, finite USD credit values.
- Observe memory settings in chat and reset one-turn recall on model/account
  changes. Memory settings actions queue rather than silently disappearing.
- Preserve search context across mutations; distinguish successful writes from
  failed refreshes. Show profile/review permission failures separately.
- Split controller, settings and library code. Saved memories, Profile and
  Suggestions have separate tabs. Actions live in row overflow menus; details
  use a sheet. Long lists are virtualized and destructive dialogs stay open on error.

## Validation still needed

Compilation, runtime checks, privacy/worker regression tests, accessibility and
light/dark device inspection remain unverified for this revision. Historical
passing checks and images must not be treated as evidence for these changes.
