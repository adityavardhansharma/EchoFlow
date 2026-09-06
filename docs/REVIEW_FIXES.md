# Code review remediation

This pass addresses the high and medium findings from the review. Release automation is unchanged.

## Correctness and concurrency

- Chat sends capture the conversation, project, mode, selected model and sampler settings before asynchronous preparation.
- A preparation reservation prevents overlapping sends from starting in the same conversation. Completion clears the reservation even when preparation fails.
- Switching conversations during preparation cannot redirect the pending message or reopen the wrong conversation.
- If an edited turn fails during preparation, its previous assistant reply is restored before the reservation is released.
- Artifact lineage changes and version insertion share a Room transaction; a failed version insert rolls back the metadata update.
- Download jobs retain ownership of their partial files until cancellation finishes. State updates are atomic across concurrent downloads.
- Generated image writes clean up files when database insertion fails.

## Browser safety

- The selected chat model receives page text and element descriptions and proposes one typed action.
- Supported actions are navigation, click, fill and scroll. Answers and manual handoffs do not execute browser actions.
- Opening the first URL requires approval. Every subsequent action also requires its own approval.
- The review displays the full page URL, target, destination and exact text where applicable.
- Approval is bound to the persisted proposal, expires after five minutes and is consumed before execution. Stale tokens and replayed approvals cannot execute an action.
- The executor checks the page URL, element descriptors and captured field values again before acting. Changed state requires another proposal.
- Model output cannot supply executable JavaScript, selectors or action batches. Only app-owned Playwright operations reach Firecrawl.
- Cancellation, replacement instructions, session shutdown and process restart invalidate pending approvals.
- Sensitive fields require manual interaction. The planner also directs login, payment and other sensitive workflows to the live browser.
- A website can attach side effects to a click or field edit; approval is for the displayed browser action, not a guarantee about the site's implementation.
- Firecrawl integration follows its documented [code execution API](https://docs.firecrawl.dev/features/interact). Live authenticated sessions and paid provider calls were not exercised in this pass.

## Security and privacy

- Failure to open encrypted preferences stops initialization with a retry screen. Production code no longer falls back to ordinary preferences.
- Migration commits secure storage before deleting legacy settings. Existing secure values take precedence and old migration remnants are removed.
- Artifact preview, workspace and printing share file/content access restrictions and an explicit network policy.
- Locally generated and offline HTML retain an embedded content security policy when copied or exported.
- Markdown/math printing bundles its rendering libraries and fonts instead of fetching them from a CDN. Markdown is encoded before embedding in the HTML script.
- Android cloud backup excludes app data. Android device transfer retains transferable content while excluding secret preferences and downloaded models.
- The README describes provider processing and locally retained browser history instead of promising that nothing is stored.
- Custom endpoint HTTP is limited to local network connections, including a check of the actual connected address.

## AI prompts and context

- Project documents and browser snapshots are explicitly untrusted reference material.
- Freshness prompts no longer treat the newest result as automatically correct. Conflicting claims require attention to authority and corroboration.
- Fusion agreement is evidence to assess, not proof of correctness.
- Requests reserve response space, keep the latest user turn intact and trim older whole turns/reference material. Oversized current messages produce a clear error.
- OpenRouter context metadata is used when cached; unknown cloud/custom models use an 8,192-token fallback. Local models use their configured capacity.
- Final cloud payload checks include tool results, so later rounds cannot silently bypass the initial budget. Local search rebuilds a budgeted request each round.
- Local inference reuse checks the retained history and system prompt as well as the chat and sampler settings.
- Token estimates are conservative heuristics. Provider tokenization and raw-file/image accounting can differ.

## Performance and resilience

- Application graph initialization and model seeding run off the UI thread. The graph is reused across Activity recreation.
- Local image input has a byte-size limit and uses bounds inspection and sampled bitmap decoding before resizing.
- Coroutine cancellation closes blocking HTTP connections. Socket errors caused by cancellation retain cancellation semantics.
- OpenRouter parsing no longer swallows exceptions raised by downstream stream consumers.
- Research resume retains collected sources and resumes incomplete searches from the saved plan; partial source collection no longer jumps directly to synthesis.
- Notification summary posting explicitly handles permission revocation.

## Verification

Final local run: **443 tests passed**, **lint passed with 137 warnings and 4 hints**, and **debug APK assembled successfully**. `git diff --check` passed.

Regression coverage includes approval replay/stale tokens, secure migration failure, cancellation of stalled HTTP, stream-consumer failures, context budgeting, image sampling, research resume, artifact network policy and export encoding, transactional rollback, and switching chats during suspended preparation.

Run locally with JDK 21 and the Android SDK matching `compileSdk`:

```sh
bash ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=2
```

No release workflow changes were made. Native model execution, Android device-transfer behavior and live provider/browser sessions still require device or integration validation. Passing lint does not mean the repository has no warnings; existing dependency, API migration and style warnings remain.
