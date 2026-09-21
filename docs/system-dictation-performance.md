# System-wide dictation performance audit

## Finding

The accessibility service shares EchoFlow's process and UI thread. In the original
implementation, accessibility events could repeatedly validate permissions, resolve
the launcher, read the full encrypted provider configuration, inspect windows and
focused nodes, and update the overlay. These operations compete with drawer and
keyboard animation even when no recording is running. Code inspection establishes
avoidable work; it does not establish the number of dropped frames on a phone.

## Implementation

- Callbacks inspect event metadata only. Known EchoFlow focus/content churn and
  external text-only changes do not query accessibility nodes or read settings.
- A conflated queue permits one worker and one pending request. Its 60 ms settling
  interval combines bursts without repeatedly restarting a debounce timer during
  continuous traffic. Teardown cancels pending work.
- Focus/window Binder queries run on IO. On API 33+, `getRoot(0)` avoids requesting
  node prefetch. The validated EchoFlow window ID avoids querying EchoFlow's own
  root again; external focus always has a recovery path through live windows.
- Window handoffs, IME bounds and structural editor removal remain observable.
  Recording also has a 500 ms reconciliation fallback for silent host changes.
- Android 13+ uses the accessibility InputMethod connection as the primary editor
  signal and insertion path. Editor restarts retain a session id; switching editors
  invalidates it, so virtual-node churn no longer hides the bubble or prevents safe
  caret insertion. Older releases retain node discovery and ACTION_PASTE.
- Configuration uses a small read-only STT snapshot on IO instead of building the
  full provider configuration. Backing preference listeners work across UI and
  service repository instances. Microphone/overlay AppOps changes invalidate
  readiness. A 15-second idle fallback covers changes without a usable callback
  and recovers dropped focus events without querying a validated own-window root;
  taps and delivery validate readiness again.
- Launcher lookup is cached for the service connection, preserving home exclusion.
- Each recording owns its recorder instance. Starting, stopping and cancellation
  run on IO; finite cleanup survives cancellation without affecting a later
  recorder. The service tracks cleanup, and a quick retry suspends until the old
  recorder releases the microphone, including after cancelling transcription.
  Leaving the editor, including entering EchoFlow, hides the bubble and
  releases capture before transcription. Session generations prevent stale results
  from completing a newer session.
- Overlay placement uses current metrics, insets and density, but sends a layout
  update only when final pixel bounds change. Idle does not animate. A phase update
  cannot reattach a bubble hidden after focus loss.
- The floating button uses the existing monochrome EchoFlow mark on a translucent
  background (24% opacity at rest). Recording retains a stop indicator and processing
  retains its progress arc.

## Review decisions for PR #164

The microphone leak, sticky foreground cache, missing window/structural events,
launcher regression, stale readiness and stale geometry findings were valid and
were addressed. Restoring every event's expensive processing would reintroduce
the performance problem; the implementation restores signals with filtering and
bounded processing instead.

A docstring-coverage percentage does not demonstrate runtime correctness or
smoothness. Comments explain lifecycle and concurrency invariants rather than
adding boilerplate to reach a percentage. The requested device screenshot is a
validation gap, not proof of a runtime defect: no Android device was connected
during this audit. A screenshot alone also cannot establish animation performance.

## Wispr Flow and platform references

Wispr's public Android setup documents the same underlying permission model:
microphone, overlay and accessibility, with a bubble appearing at text fields.
It does not disclose its event filters, threading or proprietary implementation.
Those internals cannot responsibly be claimed as copied or verified here.

- [Wispr Android installation guide](https://docs.wisprflow.ai/articles/2809924024-android-download-installation-guide)
- [Android accessibility events and window-change flags](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent)
- [Window roots and prefetch strategy](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#getRoot(int))
- [AppOps change observation](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,java.lang.String,android.app.AppOpsManager.OnOpChangedListener))

## Validation and remaining measurement

The follow-up for Greptile's cleanup/retry finding and the translucent logo button
was reviewed in source only. Tests and compilation were intentionally not rerun,
as requested; the results below describe the preceding revision.

Local validation used the Gradle JDK 21 daemon and Android SDK 37. The targeted
suite contains 38 tests across dictation, STT settings/catalog and request handling.
An unrelated, untracked `OpenRouterPkceVectorLocalTest.kt` references a missing
auth service; it was temporarily excluded from compilation and restored unchanged.
The notification-summary permission guard was also made explicit to resolve the
existing `MissingPermission` lint error without suppressing the check.

Regression tests exercise own-window churn, external focus and split-screen
handoffs, structural removal, IME movement, 1,000-event bursts, continuous traffic,
events during suspended work, teardown, cross-instance settings invalidation,
provider fallback, layout deduplication and microphone release when entering
EchoFlow or losing the editor window. Robolectric service tests verify that another
recorder can acquire the microphone after focus loss.

For physical-device acceptance, compare the same optimized build with system-wide
dictation off and enabled-but-idle. Capture a system trace while repeatedly opening
the drawer, typing and opening/closing the IME. Inspect frame deadlines and the
`Dictation.focus` trace section: it should execute on IO, and steady EchoFlow typing
should not repeatedly request its root. Record device, OS, refresh rate and missed
frame counts; do not infer a measured speedup from passing unit tests.

Also verify external recording → EchoFlow → in-app recording, split-screen focus
switches, editor deletion, keyboard movement, rotation/density changes, lock/unlock,
permission revocation and rapid off/on. Confirm transcripts never paste into a
different field. No physical-device trace or screenshot was available in this run.
