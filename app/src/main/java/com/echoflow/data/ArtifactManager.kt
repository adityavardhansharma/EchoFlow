package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Owns the artifact store. Mirrors how [BrowserAgentManager] fronts the browser tables: the UI
 * observes the rows it writes. v1 keeps one artifact lineage per chat — [saveVersion] appends a new
 * [ArtifactVersion] to the chat's existing [Artifact] (or creates the first one), preserving full
 * version history.
 */
class ArtifactManager(
    private val artifactDao: ArtifactDao,
    private val artifactVersionDao: ArtifactVersionDao,
) {
    fun observeForChat(chatId: String): Flow<Artifact?> = artifactDao.observeLatestForChat(chatId)

    /** A one-shot read of the chat's latest artifact, for decisions that can't wait on the cold flow. */
    suspend fun getLatestForChat(chatId: String): Artifact? = artifactDao.getLatestForChat(chatId)

    /** Observe one lineage by id — used by the workspace so open is not tied to "latest for chat". */
    fun observeById(artifactId: String): Flow<Artifact?> = artifactDao.observeById(artifactId)

    /** One-shot read by id, for open guards and deep-links that already know the lineage. */
    suspend fun getById(artifactId: String): Artifact? = artifactDao.getById(artifactId)

    fun observeVersions(artifactId: String): Flow<List<ArtifactVersion>> =
        artifactVersionDao.observeForArtifact(artifactId)

    suspend fun getVersion(artifactId: String, version: Int): ArtifactVersion? =
        artifactVersionDao.getVersion(artifactId, version)

    /** The latest artifact body for a chat, fed back to the model so it iterates instead of restarting. */
    suspend fun getLatestVersionContent(chatId: String): String? {
        val artifact = artifactDao.getLatestForChat(chatId) ?: return null
        return artifactVersionDao.getLatest(artifact.id)?.content
    }

    /**
     * Persist a freshly generated artifact body as a new version of the chat's lineage (creating
     * the lineage on first use), and return a reference the timeline/card can deep-link with.
     */
    suspend fun saveVersion(
        chatId: String,
        title: String,
        type: String,
        content: String,
        sourcePrompt: String,
    ): ArtifactRef {
        val normalizedType = Artifact.normalizeType(type)
        return artifactDao.appendVersion(chatId, title, normalizedType, content, sourcePrompt)
    }

    /** Drop a lineage from the Artifacts gallery without touching the chat or the artifact body. */
    suspend fun hideFromGallery(artifactId: String) = artifactDao.hideFromGallery(artifactId)

    private fun defaultTitle(type: String): String = when (type) {
        Artifact.TYPE_MARKDOWN -> "Document"
        Artifact.TYPE_LATEX -> "Report"
        else -> "Artifact"
    }
}
