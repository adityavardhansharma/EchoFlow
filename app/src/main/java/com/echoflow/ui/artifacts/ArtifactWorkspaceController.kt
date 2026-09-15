package com.echoflow.ui.artifacts

import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactManager
import com.echoflow.data.ArtifactVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Owns artifact selection and cancels obsolete workspace lookups when opening or closing. */
internal class ArtifactWorkspaceController(
    private val artifactManager: ArtifactManager,
    private val scope: CoroutineScope,
) {
    fun isShowing(artifactId: String): Boolean = _workspaceArtifactId.value == artifactId

    /** True while the fullscreen Artifact Workspace overlay is open. */
    private val _artifactWorkspaceOpen = MutableStateFlow(false)
    val artifactWorkspaceOpen: StateFlow<Boolean> = _artifactWorkspaceOpen.asStateFlow()

    /**
     * The lineage the workspace is showing — set by [openArtifactWorkspace] from the card that
     * was tapped. Observing by id (not "latest for chat") means open stays correct if a chat
     * ever holds more than one lineage; the one-lineage-per-chat rule is an authoring invariant,
     * not a workspace routing invariant.
     */
    private val _workspaceArtifactId = MutableStateFlow<String?>(null)

    /**
     * The version the workspace should open at, set by whichever card was tapped. A card in
     * scrolled-back history represents an earlier version than the lineage's latest, so tapping it
     * must open *that* version — not silently jump to the newest. Null means "open the latest"
     * of the targeted lineage.
     */
    private val _artifactInitialVersion = MutableStateFlow<Int?>(null)
    val artifactInitialVersion: StateFlow<Int?> = _artifactInitialVersion.asStateFlow()

    /**
     * Bumped on every successful open so the workspace can re-seed its local selection even when
     * the same lineage id is opened again at a different version. Keying Compose state on artifact
     * id alone left [selectedVersion] sticky across close → reopen of another card in the lineage.
     */
    private val _workspaceOpenSession = MutableStateFlow(0)
    val workspaceOpenSession: StateFlow<Int> = _workspaceOpenSession.asStateFlow()

    /** The open workspace's lineage row (title, type, currentVersion). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceArtifact: StateFlow<Artifact?> = _workspaceArtifactId
        .flatMapLatest { id -> if (id == null) flowOf(null) else artifactManager.observeById(id) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    /** All versions of the open workspace's lineage (drives the version switcher). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceArtifactVersions: StateFlow<List<ArtifactVersion>> = _workspaceArtifactId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else artifactManager.observeVersions(id)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Only the newest open request may publish. Two cards tapped in quick succession each suspend
     * on the DAO, and without this the first query to return last would win and the workspace
     * would show the wrong lineage.
     */
    private var artifactWorkspaceJob: Job? = null

    /**
     * Open the workspace on a specific lineage at [targetVersion] (or that lineage's latest when
     * null). Existence is resolved from the store by id — not from the chat's latest row — so a
     * historical card never opens a different artifact than the one it represents.
     */
    fun openArtifactWorkspace(artifactId: String, targetVersion: Int? = null) {
        artifactWorkspaceJob?.cancel()
        artifactWorkspaceJob = scope.launch {
            if (artifactManager.getById(artifactId) == null) return@launch
            _workspaceArtifactId.value = artifactId
            _artifactInitialVersion.value = targetVersion
            _workspaceOpenSession.value = _workspaceOpenSession.value + 1
            _artifactWorkspaceOpen.value = true
        }
    }

    fun closeArtifactWorkspace() {
        artifactWorkspaceJob?.cancel()
        artifactWorkspaceJob = null
        _artifactWorkspaceOpen.value = false
        _workspaceArtifactId.value = null
        _artifactInitialVersion.value = null
    }

}
