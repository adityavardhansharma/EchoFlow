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
    // Nullable so the v22→v23 migration needs no SQL DEFAULT (matches the no-defaultValue entity policy).
    val extractionStatus: String? = null,
    val extractionTier: String? = null,
) {
    /** Whether this document actually contributes text to the project's context. */
    val hasText: Boolean get() = !extractedText.isNullOrBlank()

    val status: ExtractionStatus
        get() = ExtractionStatus.from(extractionStatus)

    val tier: ExtractionTier?
        get() = extractionTier?.let { raw -> ExtractionTier.entries.firstOrNull { it.name == raw } }
}

enum class ExtractionStatus {
    PENDING,
    EXTRACTING,
    EXTRACTED,
    NEEDS_PROVIDER,
    FAILED,
    UNKNOWN;

    val isBusy: Boolean get() = this == PENDING || this == EXTRACTING

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.name == s } ?: UNKNOWN
    }
}

enum class ExtractionTier { ANYDOC, OCR, PROVIDER, LEGACY_TEXT }
