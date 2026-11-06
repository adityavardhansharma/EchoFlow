package com.echoflow.ui.projects

/**
 * The Projects overlay: closed, the hub list, or one project's home stacked on the hub.
 * A project home without the hub under it can't be expressed, so back always steps
 * home → hub → closed.
 */
sealed interface ProjectsSurface {
    data object Closed : ProjectsSurface
    data object Hub : ProjectsSurface
    data class Home(val projectId: String) : ProjectsSurface

    val hubOpen: Boolean get() = this != Closed
    val openProjectId: String? get() = (this as? Home)?.projectId

    /** Opens a project's home over the hub. Ignored while the hub is closed. */
    fun openHome(projectId: String): ProjectsSurface = if (this == Closed) this else Home(projectId)

    /** Back from a project home to the hub list. */
    fun closeHome(): ProjectsSurface = if (this is Home) Hub else this

    /** The project was deleted: leave its home if it is the one showing. */
    fun projectDeleted(projectId: String): ProjectsSurface = if (openProjectId == projectId) Hub else this
}
