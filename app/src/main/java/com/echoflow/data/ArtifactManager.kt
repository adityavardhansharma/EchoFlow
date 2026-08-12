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
        val now = System.currentTimeMillis()
        val normalizedType = Artifact.normalizeType(type)
        val existing = artifactDao.getLatestForChat(chatId)
        val artifactId = existing?.id ?: UUID.randomUUID().toString()
        val nextVersion = if (existing == null) 1 else artifactVersionDao.maxVersion(artifactId) + 1
        val resolvedTitle = title.ifBlank { existing?.title ?: defaultTitle(normalizedType) }

        artifactDao.upsert(
            Artifact(
                id = artifactId,
                chatId = chatId,
                title = resolvedTitle,
                type = normalizedType,
                currentVersion = nextVersion,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        artifactVersionDao.insert(
            ArtifactVersion(
                id = UUID.randomUUID().toString(),
                artifactId = artifactId,
                versionNumber = nextVersion,
                content = content,
                sourcePrompt = sourcePrompt,
                createdAt = now,
            )
        )
        return ArtifactRef(
            artifactId = artifactId,
            title = resolvedTitle,
            type = normalizedType,
            version = nextVersion,
            uiVersion = ArtifactRef.UI_VERSION_CURRENT,
        )
    }

    private fun defaultTitle(type: String): String = when (type) {
        Artifact.TYPE_MARKDOWN -> "Document"
        Artifact.TYPE_LATEX -> "Report"
        else -> "Artifact"
    }
}
