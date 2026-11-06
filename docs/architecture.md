# Code organization

EchoFlow uses a single Gradle application module with feature packages and smaller collaborators inside it. `MainActivity` and navigation wire the application together; Compose screens render state, ViewModels coordinate user actions, and the data layer owns storage and provider operations.

## Where to work

Paths below are relative to `app/src/main/java/com/echoflow`.

| Responsibility | Location |
|---|---|
| Application navigation and overlays | `navigation/` |
| Conversation coordination and stream persistence | `ui/ChatViewModel.kt` |
| Draft files, extraction jobs, retry and removal | `ui/chat/ChatAttachmentController.kt` |
| Local-model search-tag protocol | `ui/chat/LocalSearchProtocol.kt` |
| Artifact workspace selection and open/close jobs | `ui/artifacts/ArtifactWorkspaceController.kt` |
| Project import progress and error ordering | `ui/projects/ProjectImportController.kt` |
| Project import state exposed to UI | `ui/projects/ProjectImportState.kt` |
| Chat surface, composer, messages, menus and pickers | `ui/screens/chat/` |
| Imagine canvas, prompt and options | `ui/screens/imagine/` |
| Project hub, home, files, document rows and instructions | `ui/screens/projects/` |
| Settings routes, provider pages and reusable forms | `ui/screens/settings/` |
| Shared result cards and rendering | `ui/components/` |
| Colors, spacing, shapes and motion | `ui/theme/` |
| Room entities, DAOs, migrations and repositories | `data/` |
| Provider requests, response decoding and transport | `data/*Service.kt`, `*Payloads.kt`, `*Transport.kt` |
| OpenRouter Fusion panel execution and answer recovery | `data/OpenRouterFusionRunner.kt` |
| File parsing, model file capabilities and OCR | `data/extract/` |

## Chat UI boundaries

`ChatScreen` is the entry point and `ChatSurface` arranges the conversation. `ChatComposer` owns the input toolbar; `ChatAttachmentChips`, `ChatPlusMenu`, and `ChatComposerButtons` own its separate controls.

`ChatMessages` owns the scrolling list. `ChatMessageBubble` renders persisted turns, `ChatStreamingMessage` renders in-progress output, and `ChatConversationChrome` contains the top bar, error banner, and empty state.

Tool results are grouped by feature: `AdvisorCard`, `SubagentCard`, and `FusionCard`. Fusion's progress rendering and detailed analysis live in `FusionProgress` and `FusionDeliberation` respectively. Shared badge/model-label helpers remain in `EchoToolComponents`.

## State and lifetime

`ChatViewModel` remains the public conversation facade for screens. It forwards attachment, project-import, and artifact-workspace operations to their owners. These collaborators receive `viewModelScope`; they do not create an independent lifetime that could outlive the ViewModel.

- Attachment edits replace the draft and cancel old extraction jobs. Removal cancels the selected file's parse; clearing cancels all parses.
- Artifact workspace opens cancel the previous lookup. Closing cancels a pending open and clears selection. The open-session counter lets Compose reset version selection on repeated opens of the same artifact.
- Project imports track admission separately from extraction completion. Progress is keyed by project, and import IDs prevent an older success from dismissing a newer failure.
- Streaming remains keyed by chat so switching surfaces does not move an answer into another conversation.
- A send claims its chat (`SendClaim`) before its first suspend and holds it until the turn registers in `streamJobs`, so two quick sends can't both start a turn.
- A send keeps the chat and surface it was typed into (`startChatId`, `startMode`) and writes only there, even if the user opens another chat while it gets ready. Blank Chat and blank Imagine sends claim separate keys (`blankSendKey`).
- A send's memory eligibility is taken from the chat it started in, so arming a temporary chat while it is getting ready can't turn learning off for a normal chat.
- Several chats can stream at once. A reply's `StreamRevealState` outlives its row: a row that comes back (chat switch, scroll, return from background) resumes at the current text instead of typing the answer out again. A reader that stays mounted while the app is stopped catches up the same way when its lifecycle restarts (`catchUpPoint`), and the thinking timer keeps its start. A failure that happens while its chat is not open is kept and shown on that chat's banner when it reopens.
- Direct providers are checked against their on/off switches before every send (`CustomProviderRouting.disabledMessage`), in chat and in `CustomProviderFlowRouter`, so schedules can't reach a turned-off provider either.

## Explicit state machines

Where a feature's phase used to live in several loose flags, it now lives in one sealed state with
pure transitions that plain JUnit tests cover:

| State | Where | Notes |
|-------|-------|-------|
| `TemporaryChat` (Off, Armed, Live) | `ui/chat/TemporaryChat.kt`, owned by `TemporaryChatController` | Transitions return effects (crash marker, memory exclusion, discard) that the controller runs. `TemporaryChatMarker` saves the crash marker to prefs and to a no-backup file, and launch recovers every id the two copies still hold. |
| `ProjectsSurface` (Closed, Hub, Home) | `ui/projects/ProjectsSurface.kt` | A project home always has the hub under it. Exposed as the old flows through `mapState`. |
| `ModeLifetime` (Sticky, OneSend, UntilRunEnds) | `data/ChatMode.kt` | Each `ChatMode` declares how long it stays on. |
| `DurableStatus.canMove` | `data/DurableStatus.kt` | Browser sessions and research runs never leave a finished status. Video jobs don't use it: OpenRouter's "completed" is followed by our "downloading". |
| `ForegroundTracker` | `ui/lock/AppLock.kt` | How long the app was away, for the lock timer. |
| `NavigationGuard` | `data/NavigationGuard.kt` | The one "has the user moved since" token, used by restore and voice mode. |
| `VoiceCall` (Idle, Connecting, Connected, Ending) | `ui/chat/VoiceModeController.kt` | One value for the call's log and job. The notification's End button arrives as `VoiceModeService.endRequests`. |
| `DictationCycle` | `data/DictationCycle.kt` | System dictation's phase and generation; a stale capture can't finish a newer one. |
| `LoadedEngine` / `SessionBinding` | `data/LocalEngineState.kt` | Which on-device model is loaded, and whether its session can carry on the same chat. |
| `ChatTurnRegistry` | `ui/ChatTurnRegistry.kt` | Each chat's turn: claimed, registered, visible. Replaces three loose fields in `ChatViewModel`. |
| `OverlayStack` | `navigation/OverlayStack.kt` | The full-screen layers over the chat, in drawing order. One back handler closes the top layer, except `ProjectsHub` and `Schedules` (`ownsBack`), which handle Back themselves so they can step back through their own pages first. |

Provider quirks that chat routing needs (which prefix, image and PDF support, the error text) live
in `CustomProviderRouting`; the Echo Agents/Adviser/Fusion model rule lives in `EchoModeGate`.
Chat streams that act on each line read `data:` payloads through `SseDecoder.dataLine`; the
frame-based Deep Research feeds use the full `SseDecoder`. `EchoFlowApplication.graph` is the one
`EchoFlowAppGraph` per process. Groups of settings move out of `SettingsRepository` into plain
stores (`CustomProviderSettingsStore`, `InferenceParamsStore`, `DeepResearchSettingsStore`) that
own their keys, while the repository keeps the flows.
`scripts/refactor-metrics.sh` prints the package import graph and special-case counts.

## Provider boundary

`OpenRouterService` owns HTTP clients and provider entry points. `OpenRouterFusionRunner` owns the one-panel protocol: force one fusion request, decode its result, and optionally synthesize an answer with tools disabled. Its injected transport and message-building functions let tests assert request ordering without credentials or real charges.

`LocalSearchProtocol` buffers local-model output until it can distinguish a search request from an answer. It then coordinates local generation and web search. It runs under the caller's stream lifetime and inference gate.

## Adding features

1. Start in the feature's screen package and identify the state owner.
2. Keep display-only components independent of the ViewModel where practical.
3. Add a focused collaborator for a new asynchronous workflow; pass its scope and dependencies explicitly.
4. Test protocol or state transitions at that boundary, then add screen tests for the user interaction.
5. Wire the new behavior through the existing navigation and ViewModel facade.

## Remaining large areas

This structure does not eliminate all large files. `ChatViewModel.sendMessage` still coordinates validation, thread creation, mode dispatch, and persistence; `SettingsRepository` and `DeepResearchEngine` also remain broad. Future extractions should preserve the existing ordering and cancellation contracts and introduce tests at each new boundary. Moving those branches into extension functions that access the same mutable state would not give them independent ownership.
