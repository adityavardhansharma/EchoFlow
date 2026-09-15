package com.echoflow.ui.projects

/**
 * Errors raised while managing a project's files (e.g. an import that can't be read). Kept
 * separate from [errorMessage] so it surfaces inside the Files screen that raised it, not on
 * the chat surface sitting behind the hub overlay. The project id travels with the message
 * so a late failure cannot paint a banner onto a different project's Files screen.
 */
data class ProjectFileError(val projectId: String, val message: String, val importId: Long = 0L)

/**
 * Files the user picked that have not been copied into the project yet. Used so the
 * Files header can say "8 waiting" while the first batch of four occupies copy slots.
 * Keyed by project so two open imports cannot overwrite each other.
 */
data class ProjectImportProgress(
    val projectId: String,
    val selected: Int,
    val admitted: Int = 0,
    val failed: Int = 0,
) {
    val queued: Int get() = (selected - admitted - failed).coerceAtLeast(0)
}
