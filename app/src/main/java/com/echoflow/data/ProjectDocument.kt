package com.echoflow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A file copied into a project's folder; extractedText is injected as chat context when present. */
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
