package com.echoflow.weave.runtime.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.echoflow.data.ChatMessage

/**
 * One Weave block in a reply (05 §9.1). Keyed by the message and the fence's source offset
 * ([blockKey] = `MarkdownExtensionBlock.key`). The streamed message text is never rewritten: the
 * block's current version lives in [source], and the chat renders from this row when it exists.
 * Rows go with their message (cascade), so deleting or regenerating a reply cleans up after it.
 */
@Entity(
    tableName = "weave_blocks",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId", "blockKey"], unique = true), Index("chatId"), Index("blockId")],
)
data class WeaveBlockRow(
    @PrimaryKey val id: String,
    val chatId: String,
    val messageId: String,
    val blockKey: Int,
    /** Header id, unique per chat (`b1`, `b2`, …). */
    val blockId: String,
    /** Current version number, starting at 1. */
    val version: Int,
    /** `source` or `json`. Always `source` in phase 1. */
    val form: String,
    /** Canonical print of the current version. */
    val source: String,
    /** 05 §8.6 encoding of non-transient state, untruncated. */
    val stateJson: String,
    val historyJson: String,
    val lazyJson: String,
    val queryJson: String,
    val consentJson: String,
    /** `{"appear": true, "at:<key>": true}` */
    val firedJson: String,
    val askLogJson: String,
    /** Per-node UI state by node key (selected tab scroll, sort, `"plain": true`). */
    val uiStateJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One saved version of a block (05 §9.1). Written on every accepted edit, ask, remix or restore. */
@Entity(
    tableName = "weave_versions",
    foreignKeys = [
        ForeignKey(
            entity = WeaveBlockRow::class,
            parentColumns = ["id"],
            childColumns = ["blockRowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["blockRowId", "version"], unique = true)],
)
data class WeaveVersionRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val blockRowId: String,
    val version: Int,
    val source: String,
    /** `stream`, `edit`, `ask`, `remix`, `repair`, `regen` or `restore`. */
    val cause: String,
    val createdAt: Long,
)

/** A block kept in the Library ("Made for you", 01 §23.5). Independent of any chat. */
@Entity(tableName = "weave_library")
data class WeaveLibraryRow(
    @PrimaryKey val id: String,
    val title: String,
    val icon: String,
    val source: String,
    val stateJson: String,
    val lazyJson: String,
    /** Home-screen widget id (phase 3). */
    val pinnedWidgetId: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)
