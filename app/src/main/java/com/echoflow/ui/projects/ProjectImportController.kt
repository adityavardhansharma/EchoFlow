package com.echoflow.ui.projects

import android.net.Uri
import com.echoflow.data.ProjectManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tracks concurrent document imports per project, including admission progress and error ordering. */
internal class ProjectImportController(
    private val projectManager: ProjectManager,
    private val scope: CoroutineScope,
) {
    private val _projectFileError = MutableStateFlow<ProjectFileError?>(null)
    val projectFileError: StateFlow<ProjectFileError?> = _projectFileError.asStateFlow()
    private var nextProjectFileImportId = 0L
    private val importProgressLock = Any()
    private val _projectImportProgress = MutableStateFlow<Map<String, ProjectImportProgress>>(emptyMap())
    val projectImportProgress: StateFlow<Map<String, ProjectImportProgress>> =
        _projectImportProgress.asStateFlow()
    fun clearProjectFileError(projectId: String) {
        if (_projectFileError.value?.projectId == projectId) _projectFileError.value = null
    }

    fun addProjectDocument(projectId: String, uri: Uri) {
        addProjectDocuments(projectId, listOf(uri))
    }

    fun addProjectDocuments(projectId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        beginImport(projectId, uris.size)
        for (uri in uris) {
            val importId = ++nextProjectFileImportId
            scope.launch {
                val admitted = AtomicBoolean(false)
                val added = try {
                    projectManager.addDocument(projectId, uri) {
                        if (admitted.compareAndSet(false, true)) {
                            noteImportFinished(projectId, admitted = true)
                        }
                    }
                } catch (t: CancellationException) {
                    if (!admitted.get()) noteImportFinished(projectId, admitted = false)
                    throw t
                } catch (_: Throwable) {
                    if (!admitted.get()) {
                        val failed = noteImportFinished(projectId, admitted = false)
                        reportProjectFileError(projectId, importId, failed)
                    }
                    return@launch
                }
                if (added == null) {
                    if (!admitted.get()) {
                        val failed = noteImportFinished(projectId, admitted = false)
                        reportProjectFileError(projectId, importId, failed)
                    }
                } else {
                    // Only a newer success may drop this project's banner. An older import
                    // finishing later must not hide a failure that started after it.
                    val current = _projectFileError.value
                    if (current != null && current.projectId == projectId && current.importId < importId) {
                        _projectFileError.value = null
                    }
                }
            }
        }
    }

    private fun beginImport(projectId: String, count: Int) {
        synchronized(importProgressLock) {
            val map = _projectImportProgress.value.toMutableMap()
            val current = map[projectId]
            map[projectId] = if (current != null) {
                current.copy(selected = current.selected + count)
            } else {
                ProjectImportProgress(projectId = projectId, selected = count)
            }
            _projectImportProgress.value = map
        }
    }

    /** Returns the running failure count for [projectId] after applying this result. */
    private fun noteImportFinished(projectId: String, admitted: Boolean): Int {
        synchronized(importProgressLock) {
            val map = _projectImportProgress.value.toMutableMap()
            val current = map[projectId] ?: return if (admitted) 0 else 1
            val next = if (admitted) {
                current.copy(admitted = current.admitted + 1)
            } else {
                current.copy(failed = current.failed + 1)
            }
            if (next.admitted + next.failed >= next.selected) {
                map.remove(projectId)
            } else {
                map[projectId] = next
            }
            _projectImportProgress.value = map
            return next.failed
        }
    }

    private fun reportProjectFileError(projectId: String, importId: Long, failedCount: Int = 1) {
        val current = _projectFileError.value
        if (current != null && current.projectId == projectId && current.importId > importId) return
        val message = if (failedCount <= 1) "Couldn't add that file." else "Couldn't add $failedCount files."
        _projectFileError.value = ProjectFileError(projectId, message, importId)
    }

}
