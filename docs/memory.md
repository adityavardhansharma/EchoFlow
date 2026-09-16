# Memory in EchoFlow

Supermemory is an optional bring-your-own-key integration. EchoBrain is a labelled
coming-soon destination, not an active service. There is no EchoFlow backend.

## Experience

- Settings → Memory has Supermemory/EchoBrain tabs. Connect validates the key against
  the selected space; learning remains off until separate consent.
- Account plan and USD usage come from the billing APIs. Scoped keys can return 403;
  the page links to the dashboard instead of inventing a balance or requiring an admin key.
- My Memories opens on a virtualized, searchable saved-memory list. Each row has a
  three-dot menu for details, edit and forget; details use a separate bottom sheet.
  Profile and Suggestions have their own tabs. Lasting/recent profile facts each get
  a separate surface within collapsible groups, not one oversized summary card.
  Search has explicit result/empty/error states; failed writes leave dialogs open,
  and successful writes aren't reported as failures when a subsequent refresh fails.
- Native chat tools produce a quiet constellation status line, not a search card.
  Completed/interrupted receipts persist with the assistant message. Motion respects
  the existing reduced-motion preference; status is announced through a polite live region.
- “Recall for next reply” is an explicit one-turn override. It retrieves before generating
  and works without native tool calling, including local models when cloud access is allowed.
  It does not enable automatic uploads. Special research/browser pipelines don't expose it.

## Retrieval and writes

Ordinary tool-capable chats receive `search_memory(query)` and `remember_memory(content)`.
The system instruction asks the model to retrieve only when personal context is relevant
or the user explicitly requests recall. Self-contained questions need no memory request.
Explicit remember requests use direct memory creation, not the background batch.
Native transport coverage: OpenRouter, OpenAI Responses, OpenAI-compatible chat completions,
Claude, Gemini and Ollama (the existing endpoint tool-calling opt-in still applies).
Specialised Echo/Artifact flows do not automatically receive client-side memory tools;
the explicit recall override can supply context without replacing those workflows.

Tools are request-scoped coroutine context, not global service state. Maximum four memory
calls per answer, bounded provider loops, no key/tag arguments exposed to the model.
Retrieved text is untrusted historical data; current user corrections take precedence.
Memory failures become tool results so the provider can still answer. Models that reject
native tool schemas may require a tool-capable model or the explicit recall path.

Search sends a focused query to `/v4/search` in `memories` mode, with a result limit and
threshold. It does not fetch/inject the full profile each turn. Up to four keyword-ranked
excerpts from recent eligible unsynced chats bridge ingestion lag. This local fallback is
bounded lexical matching, not an embedding model or a semantic guarantee. It excludes
the current chat, which the model already has.

## Background learning

Completed standard chat replies journal their ID/completion cutoff before updating the
Room revision ledger. Failed inserts can be recovered without storing duplicate chat text.
A two-minute one-off WorkManager job and periodic maintenance process eligible conversations;
new turns do not cancel an in-flight upload. Hidden policy: Free/unknown = five distinct pending chats;
Pro = three; Max/Scale/Enterprise = one. Batching limits request frequency, **not** the
number of tokens charged by Supermemory. Paid accounts can still incur provider charges.
Partial batches become eligible after six hours even if the user only uses one thread.
Maintenance is requested every 15 minutes, but Android/network restrictions can delay it.
Retry learning explicitly flushes a partial batch; the settings UI does not display a schedule.

The worker uploads visible user/assistant text only, oldest pending conversations first.
No system prompt, reasoning, tool payload, archived reply, or attachment extraction is sent.
Only user turns started after learning consent and their replies are eligible. Consent is
captured before generation; a reply finishing after consent changes cannot enqueue that turn.
Local-model provenance is recorded before saving text, including cancelled/failed replies.
Local-model conversations used while
cloud memory is disallowed are excluded even after switching that thread to a cloud model.
Common credential formats are redacted defensively; arbitrary secrets cannot be reliably
detected, so users must not put sensitive credentials into memory-enabled chats.
Very long transcripts are bounded to the latest 120 messages / 180,000 characters.

Stable per-space/chat `customId`s and revision hashes make retries safe. Accepted uploads
remain `processing`; readiness uses **`dreamingStatus == done`**, not document indexing
status. Maintenance polls processing rows while learning remains enabled; recent-context
fallback remains while extraction is pending and only reads the completed ledger revision.
Missing source documents are marked unavailable and excluded from fallback. Retry learning
explicitly makes them uploadable again; background maintenance won't silently recreate a
source that the user deliberately deleted remotely.
Conditional acknowledgements cannot overwrite a newer revision or resurrect a deleted
ledger row. Account generations isolate reconnects. 429/network/server failures back off;
auth/quota failures stop and show a learning note with retry.

Turning learning off cancels future work and invalidates the current consent generation.
Older hash-only ledger rows are pruned on consent/account changes and at startup,
including while disconnected. Cleanup deletes only generations below its captured
boundary and is serialized with inserts, preserving current/newer sessions. Disconnect removes
the encrypted key and cancels work. Already accepted remote requests cannot be recalled.
Deleting a local chat does not delete remote source data. Forget is Supermemory's soft
forget, not an assurance of source deletion; the confirmation explains that learning from
retained sources can recreate facts. Use the provider dashboard to manage source data.

## API references checked

- [OpenAPI](https://api.supermemory.ai/openapi.json) — `/v4/memories/list` returns
  `memoryEntries`, unlike `/v4/search`'s `results`.
- [Billing](https://supermemory.ai/docs/overview/billing) — admin-only billing APIs,
  per-feature `usd_credits`, dynamic extraction, and stable-ID billing semantics.
- [Current plans](https://supermemory.ai/pricing/) — includes Max (the billing guide's
  plan table may lag this page).
- [Inference review](https://supermemory.ai/docs/recall/memory-review) — approve/decline
  semantics and access restrictions.

No tests, builds, or screenshot captures were run for the PR review/redesign follow-up,
as explicitly requested. Changes received static source inspection and `git diff --check`.
Previous test/build results do not validate the revised implementation. See
[review follow-up](memory-review.md) for reviewer dispositions and remaining validation.

No live paid-account calls are made in automated tests. Existing HTTP fixtures exercise contract
shapes and all native custom-provider memory-only loops; Room tests cover stale/deleted
acknowledgements; UI tests can record connection, EchoBrain and chat receipt screenshots.
Existing image artifacts predate this redesign and must not be presented as current UI captures.
