package com.echoflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A Claude-style artifact: a self-contained, rendered piece of content (a web page, a markdown
 * document, or a printable LaTeX report) the model produced inside a chat. This is the single
 * source of truth — the in-chat card, the global pill and the fullscreen workspace all observe
 * this row, so an artifact survives the workspace being closed or the app being minimized.
 *
 * Lineage rule (v1): one artifact lineage per chat. Each artifact-mode turn that yields content
 * appends a new [ArtifactVersion] to the chat's artifact (full version history), so "make the
 * header blue" produces v2 of the same artifact rather than a fresh one.
 */
@Entity(
    tableName = "artifacts",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId")],
)
data class Artifact(
    @PrimaryKey val id: String,
    val chatId: String,
    val title: String,
    val type: String, // see TYPE_* constants
    val currentVersion: Int, // version number of the latest ArtifactVersion
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isHtml: Boolean get() = type == TYPE_HTML
    val isMarkdown: Boolean get() = type == TYPE_MARKDOWN
    val isReport: Boolean get() = type == TYPE_LATEX

    companion object {
        const val TYPE_HTML = "html"       // single self-contained HTML/CSS/JS, rendered in a WebView
        const val TYPE_MARKDOWN = "markdown" // prose document, rendered natively
        const val TYPE_LATEX = "latex"      // printable report (Markdown + LaTeX math), exportable to PDF

        val ALL_TYPES = setOf(TYPE_HTML, TYPE_MARKDOWN, TYPE_LATEX)

        /** Normalize a model-declared type attribute to a known type (defaults to HTML). */
        fun normalizeType(raw: String?): String = when (raw?.trim()?.lowercase()) {
            TYPE_MARKDOWN, "md" -> TYPE_MARKDOWN
            TYPE_LATEX, "report", "pdf", "tex" -> TYPE_LATEX
            else -> TYPE_HTML
        }
    }
}

/**
 * One generation of an [Artifact]. Versions accrue (never overwrite) so the workspace can offer a
 * v1 / v2 / v3 switcher and the user never loses a good earlier render to a bad iteration.
 */
@Entity(
    tableName = "artifact_versions",
    foreignKeys = [
        ForeignKey(
            entity = Artifact::class,
            parentColumns = ["id"],
            childColumns = ["artifactId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("artifactId")],
)
data class ArtifactVersion(
    @PrimaryKey val id: String,
    val artifactId: String,
    val versionNumber: Int,
    val content: String, // the raw artifact body (HTML, markdown, or markdown+LaTeX)
    val sourcePrompt: String, // the user message that produced this version (for context/iteration)
    val createdAt: Long,
)

@Dao
interface ArtifactDao {
    /** The most-recent artifact for one chat — drives the in-chat card and the workspace. */
    @Query("SELECT * FROM artifacts WHERE chatId = :chatId ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestForChat(chatId: String): Flow<Artifact?>

    @Query("SELECT * FROM artifacts WHERE chatId = :chatId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): Artifact?

    @Query("SELECT * FROM artifacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Artifact?

    @Query("SELECT * FROM artifacts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Artifact?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: Artifact)

    @Query("DELETE FROM artifacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ArtifactVersionDao {
    @Query("SELECT * FROM artifact_versions WHERE artifactId = :artifactId ORDER BY versionNumber ASC")
    fun observeForArtifact(artifactId: String): Flow<List<ArtifactVersion>>

    @Query("SELECT * FROM artifact_versions WHERE artifactId = :artifactId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatest(artifactId: String): ArtifactVersion?

    @Query("SELECT * FROM artifact_versions WHERE artifactId = :artifactId AND versionNumber = :version LIMIT 1")
    suspend fun getVersion(artifactId: String, version: Int): ArtifactVersion?

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM artifact_versions WHERE artifactId = :artifactId")
    suspend fun maxVersion(artifactId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: ArtifactVersion)
}
