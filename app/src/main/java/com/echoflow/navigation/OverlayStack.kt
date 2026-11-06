package com.echoflow

/**
 * The full-screen layers drawn over the chat, bottom to top, in the order [MainNavigation]
 * composes them. [ownsBack] layers handle the back gesture themselves (they step through
 * their own pages first); the others close on back.
 */
enum class Overlay(val ownsBack: Boolean = false) {
    BrowserWorkspace,
    ResearchWorkspace,
    ArtifactsGallery,
    ProjectsHub(ownsBack = true),
    Schedules(ownsBack = true),

    /** Top-most: opening a gallery tile slides it over the still-mounted gallery. */
    ArtifactWorkspace,
}

/** Which overlays are open, and which one back should close. */
data class OverlayStack(val open: List<Overlay>) {
    /** The overlay on top, or null when the chat is showing. */
    val top: Overlay? get() = open.lastOrNull()

    /** The overlay a back gesture closes here, or null when it belongs to the top layer itself. */
    val backTarget: Overlay? get() = top?.takeUnless { it.ownsBack }

    fun isOpen(overlay: Overlay): Boolean = overlay in open

    companion object {
        fun of(
            browserWorkspace: Boolean = false,
            researchWorkspace: Boolean = false,
            artifactsGallery: Boolean = false,
            projectsHub: Boolean = false,
            schedules: Boolean = false,
            artifactWorkspace: Boolean = false,
        ): OverlayStack = OverlayStack(
            buildList {
                if (browserWorkspace) add(Overlay.BrowserWorkspace)
                if (researchWorkspace) add(Overlay.ResearchWorkspace)
                if (artifactsGallery) add(Overlay.ArtifactsGallery)
                if (projectsHub) add(Overlay.ProjectsHub)
                if (schedules) add(Overlay.Schedules)
                if (artifactWorkspace) add(Overlay.ArtifactWorkspace)
            },
        )
    }
}
