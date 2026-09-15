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
