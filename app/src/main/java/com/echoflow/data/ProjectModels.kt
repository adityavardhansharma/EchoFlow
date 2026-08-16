package com.echoflow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Project: a durable workspace that groups conversations and gives them shared standing context.
 * Two things turn "a folder of chats" into a project — a custom instruction the model is told to
 * follow for every chat in the project, and a set of reference [ProjectDocument]s whose text is
 * handed to the model as background knowledge. Both are optional; an empty project is just a folder
 * until the user fills them in.
 *
 * A chat is linked to a project by [ChatThread.projectId]. The link is nullable and set-null on
 * delete (handled in code, not by a DB foreign key) so deleting a project never takes its
 * conversations with it — they simply return to the ordinary drawer list.
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    /** The custom instruction prepended to the system prompt for every chat in this project. */
    val instructions: String = "",
    /** Index into the UI accent palette (see ProjectAccents) — the project's colour identity. */
    val colorIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One reference document attached to a [Project]. The imported file is copied into app storage
 * ([filePath], under filesDir/project_documents/) so it survives the source Uri being revoked, and
 * its extracted plain text ([extractedText]) is what gets injected into chats as background
 * knowledge. Binary formats we can't read as text (for v1, anything but text/*) keep a null
 * [extractedText]; they still list in the project but contribute no context, and the UI says so.
 */
@Entity(
    tableName = "project_documents",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId")],
)
data class ProjectDocument(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val filePath: String,
    val extractedText: String? = null,
    val addedAt: Long,
) {
    /** Whether this document actually contributes text to the project's context. */
    val hasText: Boolean get() = !extractedText.isNullOrBlank()
}
