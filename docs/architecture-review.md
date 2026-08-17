# EchoFlow Architecture Review

**Date:** 2026-08-17  
**Method:** `improve-codebase-architecture` skill (Matt Pocock) + `codebase-design` vocabulary  
**Scope:** Recent git hotspots — projects engine, artifacts gallery, `ChatViewModel` streaming, web-search/artifact routing, plus menu

---

## Executive summary

EchoFlow’s data modules (`ProjectManager`, `SystemPrompts`, stream transports) show real **depth** in places: complex behavior behind a small interface, with characterization tests at the seam. The main friction is a **shallow routing seam** inside `ChatViewModel.sendMessage` (~2,537 lines): policy and transport are decided in a 15+ branch `when`, with no tests on the decision tree. Recent commits (`e89f500`, freshness-gate work on `9f25312`) show bugs at **call sites**, not inside parsers or prompt text.

**Top recommendation:** Extract a **`TurnPlanner`** module that pairs `(systemPrompt, Flow<StreamChunk>)` atomically from a frozen `TurnContext`. This deepens the highest-leverage seam and unblocks safer work on projects, artifacts, overlays, and the plus menu.

**Missing project docs:** No `CONTEXT.md` (domain glossary) and no `docs/adr/` (architecture decision records). Future reviews should add these so seam names stay stable and rejected refactors are not re-suggested.

---

## Methodology

1. **Hot-spot inference** — `git log --oneline` (50 commits): projects/artifacts UI polish, artifact routing fix, freshness gate, plus-menu motion, model settings consolidation.
2. **Organic friction scan** — Where does understanding one concept require bouncing across modules? Where is the interface nearly as complex as the implementation? Where do pure functions have tests but call-site wiring has none?
3. **Deletion test** — Would deleting a module concentrate complexity in callers, or just move it?
4. **Vocabulary** — module, interface, implementation, depth, seam, adapter, leverage, locality (from `codebase-design`).

---

## Architecture snapshot

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose screens, ChatComposer, MainNavigation)         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  ChatViewModel (2,537 lines)                                │
│  • sendMessage routing (15+ branches)                       │
│  • Overlay stack (gallery, workspace, projects hub)         │
│  • Projects pass-through (~12 methods)                      │
│  • Gallery reads artifactDao directly                       │
└──────┬──────────────┬──────────────┬────────────────────────┘
       │              │              │
       ▼              ▼              ▼
 ProjectManager  ArtifactManager  SystemPrompts (+ transports)
 (276 lines,     (92 lines,       (705 lines, deep policy +
  deep)           shallow)         characterization tests)
       │              │              │
       ▼              ▼              ▼
 Room DAOs      Room DAOs       OpenRouter / Local / Custom
```

**Deep modules (good examples):**

- `SystemPrompts` — epistemic policy, freshness gate, per-transport `Remedy`; tested via `SystemPromptsFreshnessTest`.
- `ProjectManager` — import caps, FK race handling, `buildSystemContext`; migration tested in `ProjectsMigrationTest`.
- Plus menu **presentation** — `PlusMenuPositionProvider`, animation tests; deep UI, shallow capability wiring.

**Shallow / leaky seams (friction):**

- `ChatViewModel.sendMessage` — routing matrix at call sites.
- `ArtifactManager` — DAO facade; gallery bypasses it for `observeAll()`.
- Overlay booleans on ViewModel — unrelated to chat streaming.

---

## Deepening candidates

### 1. Turn routing matrix inside `ChatViewModel.sendMessage`

| | |
|---|---|
| **Strength** | **Strong** |
| **Files** | `ui/ChatViewModel.kt`, `data/SystemPrompts.kt`, `ui/CustomProviderFlowRouter.kt`, `data/ArtifactStreamParser.kt`, `data/SystemPromptsFreshnessTest.kt` |
| **Dependency category** | in-process |

**Problem.** The send-turn pipeline is a ~400-line coroutine: compute `effectiveProvider`, `customToolCallingActive`, build `systemPrompt`, then select among 15+ `Flow<StreamChunk>` branches in `baseResponseFlow`. Policy lives in `SystemPrompts` (tested), but **transport + prompt pairing lives at call sites**. Artifact routing once fell through to OpenRouter when a custom provider was active (`e89f500`). Understanding one turn requires bouncing across prompt builders, provider gates, mode flags, and three gateways.

**Solution.** Extract a **`TurnPlanner`** module: input = immutable `TurnContext` (model, `ChatMode`, provider config, project id, attachment flags); output = sealed `TurnPlan` with `(systemPrompt, Flow<StreamChunk>, postProcessors)`. Pair prompt builder + transport adapter atomically per variant. `ChatViewModel` holds UI state + lifecycle only.

**Benefits.**

- **Locality:** one module owns “given these inputs, which adapter runs with which prompt.”
- **Leverage:** new modes add a plan variant, not another branch in a 2.5k-line file.
- **Testability:** table-driven tests over `(context → plan class → transport)` without spinning a ViewModel.

**Before → After.**

- **Before:** `ChatViewModel` → inline `effectiveProvider` → inline `when` → gateways.
- **After:** `ChatViewModel` → `TurnPlanner.plan(context)` → `StreamExecutor.run(plan)`.

**First test slice.** `(ChatMode.Artifact, customProviderActive=true)` must select `customProviderFlow`, not `openRouterGateway` — encoding the `e89f500` regression at the module interface.

---

### 2. `ArtifactManager` is shallow; gallery bypasses the seam

| | |
|---|---|
| **Strength** | Worth exploring |
| **Files** | `data/ArtifactManager.kt`, `ui/ChatViewModel.kt` (`galleryArtifacts`, `galleryArtifactContent`), `ui/screens/ArtifactsGalleryScreen.kt`, `ui/screens/ArtifactWorkspaceScreen.kt` |
| **Dependency category** | in-process |

**Problem.** `ArtifactManager`’s interface is nearly as wide as its implementation: observe/get/saveVersion with little behavior beyond DAO forwarding. The gallery **reads `artifactDao.observeAll()` directly** in the ViewModel (`galleryArtifacts`). Thumbnail loading is a per-item suspend lambda with no shared cache. Workspace open state (`artifactWorkspaceOpen`, `workspaceArtifactId`, `artifactInitialVersion`) lives on `ChatViewModel`, coupling navigation to chat orchestration.

**Solution.** Deepen into an **`ArtifactsReadModel`** (or expand `ArtifactManager`) that exposes `Flow<List<GalleryArtifact>` with joined metadata + latest body, version-targeted reads, and gallery/workspace session state.

**Benefits.**

- **Locality:** “one artifact per chat, preview body, open at version N” in one place.
- **Leverage:** gallery and in-chat cards share one read path.
- **Testability:** read-model assembly without Compose or streaming.

---

### 3. Projects: depth in `ProjectManager`, shallow seam in ViewModel

| | |
|---|---|
| **Strength** | Worth exploring |
| **Files** | `data/ProjectManager.kt`, `ui/ChatViewModel.kt` (projects ~603–740, prompt injection ~1500–1525), `ui/screens/ProjectsScreens.kt`, `data/ProjectsMigrationTest.kt` |
| **Dependency category** | in-process |

**Problem.** `ProjectManager` has real depth (import caps, FK races, `buildSystemContext`). The ViewModel **mirrors its interface ~1:1** (`projectFlow`, `renameProject`, `addProjectDocument`, …) and adds overlay navigation (`projectsHubOpen`, `openProjectId`, `_pendingProjectId`). Prompt injection is string concat at send: `baseSystemPrompt + projectManager.buildSystemContext(id)`. `pendingProjectId` must be consumed in multiple thread-creation paths (normal send, Deep Research, Browser, Data Agent) — easy to miss a path. Tests cover schema migration only, not import failure sweeps or prompt assembly.

**Solution.** **`ProjectsCoordinator`** owning hub navigation, pending-assignment lifecycle, and `assembleSystemPrompt(base, projectId)`. Single `createThreadWithProject(mode)` that always consumes pending assignment.

**Benefits.**

- **Locality:** assignment rules beside import/context logic.
- **Leverage:** one API for “start chat from project” entry points.
- **Testability:** pending-project consumption without ViewModel.

---

### 4. Freshness policy is deep; transport remedy selection is shallow and duplicated

| | |
|---|---|
| **Strength** | **Strong** (often same refactor as Candidate 1) |
| **Files** | `data/SystemPrompts.kt`, `data/SystemPromptsFreshnessTest.kt`, `ui/ChatViewModel.kt`, `ui/CustomProviderFlowRouter.kt` |
| **Dependency category** | in-process |

**Problem.** `SystemPrompts` has excellent depth: one epistemic policy, `Remedy.forTransport`, guarded by characterization tests. But **runtime transport → remedy is re-derived in `sendMessage`** via `effectiveProvider` + branch order. Search results are injected with a raw string append (`withSearch = systemPrompt + "\n\nUse these web search results…"`) separate from `buildCustomProvider`’s injected-search guidance. `CustomProviderFlowRouter` is a shallow `when (provider)` adapter with no policy awareness.

**Solution.** **`SearchTransport`** sealed module pairing `(Remedy, promptBuilder, streamFactory, injectResults?)`. Single `SearchContext.appendTo(prompt)` for custom-provider and client-search paths.

**Benefits.**

- **Locality:** policy + mechanics co-located per transport.
- **Leverage:** freshness tests extend to “transport X always uses remedy Y.”
- **Testability:** no string-append drift between prompt and injection.

---

### 5. Overlay / workspace navigation state leaks into `ChatViewModel`

| | |
|---|---|
| **Strength** | Worth exploring |
| **Files** | `ui/ChatViewModel.kt`, `navigation/MainNavigation.kt`, `ProjectsScreens.kt`, `ArtifactsGalleryScreen.kt`, workspace screens |
| **Dependency category** | in-process |

**Problem.** At least five overlay flows (`artifactWorkspaceOpen`, `artifactsGalleryOpen`, `projectsHubOpen`, `researchWorkspace`, `browserWorkspaceChatId`) live on the chat ViewModel. `MainNavigation` encodes stacking order (gallery under workspace). `dismissProjectSurfaces()` crosses feature seams. Each new drawer destination grows ViewModel surface unrelated to streaming.

**Solution.** **`OverlayStack`** / **`WorkspaceCoordinator`** with sealed stack (`Closed`, `Gallery`, `Gallery+Workspace(version)`, `ProjectsHub(homeId?)`, …) and centralized back handling.

**Benefits.**

- **Locality:** stacking rules in one module.
- **Leverage:** generic back gestures and `dismissAll`.
- **Testability:** pure state-machine tests for back presses.

---

### 6. Plus menu: deep presentation, shallow capability seam

| | |
|---|---|
| **Strength** | Speculative |
| **Files** | `ui/screens/ChatComposer.kt`, `data/ChatMode.kt`, `ui/screens/ChatSurface.kt`, plus-menu tests |
| **Dependency category** | in-process |

**Problem.** Plus menu **presentation** is well-factored and tested (placement, shadow, motion). The **capability matrix** is ~15 boolean props (`dataAgentAvailable`, `echoAdviserAvailable`, …) from `ChatSurface` → `ChatComposer` → `PlusMenu`. Availability rules duplicate checks in `sendMessage` error paths. Twelve `toggleX()` methods on ViewModel.

**Solution.** **`CapabilityMenuModel`** from `(ChatMode, modelId, settings snapshot)` → `List<MenuSection>`. Single `setCapability(Capability)` instead of per-mode toggles.

**Benefits.**

- **Locality:** “what the + menu can show” in one derivation.
- **Leverage:** composer stops growing prop lists.
- **Testability:** policy tests independent of Compose.

---

## Recommendation summary

| # | Hotspot | Strength | Primary friction |
|---|---------|----------|------------------|
| 1 | `ChatViewModel` send/stream routing | **Strong** | Shallow routing at call sites; ordering bugs |
| 2 | Artifacts gallery read path | Worth exploring | Shallow `ArtifactManager`; DAO bypass |
| 3 | Projects ViewModel wiring | Worth exploring | Pass-through seam; pending-project scatter |
| 4 | Freshness + search transport | **Strong** | Deep policy, shallow adapter pairing |
| 5 | Overlay navigation state | Worth exploring | Chat VM owns unrelated presentation stack |
| 6 | Plus menu capability matrix | Speculative | Deep UI, shallow eligibility wiring |

---

## Top recommendation

**Extract `TurnPlanner` / `StreamRouter` from `ChatViewModel.sendMessage` (Candidates 1 + 4 together).**

1. **Git evidence** — Artifact routing fix and freshness-gate work show bugs at the **call-site seam** between policy and transport.
2. **Test asymmetry** — `SystemPromptsFreshnessTest` and `ArtifactStreamParserTest` cover pure logic; **zero tests** for the `baseResponseFlow` decision tree.
3. **Leverage** — Projects prompt injection, artifact mode, web search, custom providers, Echo modes all funnel through the same `when`.
4. **Incremental path** — Freeze `TurnContext` + golden routing tests; move branches one at a time without rewriting UI.

Follow-on (after routing is deep): Candidates 2, 3, 5 — artifacts read model, projects coordinator, overlay stack — can peel off without fighting the 2.5k-line anchor.

---

## Suggested follow-ups (not in this review)

1. **Create `CONTEXT.md`** — Domain terms (Turn, Project, Artifact lineage, ChatMode, Remedy) so reviews use stable names.
2. **Start `docs/adr/`** — Record decisions like “routing lives in TurnPlanner, not ViewModel” once implemented.
3. **Characterization tests for routing** — Before any extraction, snapshot current branch outcomes as golden files.

---

## Interactive HTML report

A visual before/after report (Tailwind + Mermaid) was generated at:

`/tmp/architecture-review-20260817.html`

Open locally with `xdg-open /tmp/architecture-review-20260817.html` (Linux) or equivalent.

---

## Skill reference

Analysis followed:

- `.agents/skills/improve-codebase-architecture/SKILL.md`
- `.agents/skills/codebase-design/SKILL.md`

Vocabulary used: **module**, **interface**, **implementation**, **depth**, **seam**, **adapter**, **leverage**, **locality** — not component, service, API, or boundary.
