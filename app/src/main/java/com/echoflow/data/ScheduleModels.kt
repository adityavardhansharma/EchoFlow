package com.echoflow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-approved instruction and its calendar rule. Times are UTC epoch milliseconds. */
@Entity(tableName = "schedules", indices = [Index("status"), Index("nextRunAt")])
data class ScheduleTask(
    @PrimaryKey val id: String,
    val title: String,
    val instruction: String,
    val modelId: String,
    val status: String,
    val unit: String,
    val interval: Int,
    val anchorAt: Long,
    val zoneId: String,
    val nextRunAt: Long?,
    val revision: Long = 0,
    /**
     * Legacy v28 flag. Web use is now decided by the model at run time from whatever search the
     * user has configured, so this is never read; the column stays to avoid a table rebuild.
     */
    val needsWeb: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Weekly day set as a [ScheduleDays] bitmask. Zero means "the anchor's weekday", which is how
     * every v28 weekly schedule reads without a backfill.
     */
    @ColumnInfo(defaultValue = "0") val weekdays: Int = 0,
    /** Last instant an occurrence may start (inclusive); null runs until stopped. */
    val endAt: Long? = null,
    /** The conversation this schedule lives in: where it is edited and where every run posts. */
    val threadId: String? = null,
) {
    companion object {
        const val ACTIVE = "active"
        const val PAUSED = "paused"
        const val COMPLETED = "completed"
        const val ONCE = "once"
        const val HOUR = "hour"
        const val DAY = "day"
        const val WEEK = "week"
        const val MONTH = "month"
        val UNITS = listOf(ONCE, HOUR, DAY, WEEK, MONTH)
    }
}

/** An immutable occurrence identity with a mutable, user-visible result. */
@Entity(
    tableName = "schedule_runs",
    foreignKeys = [ForeignKey(
        entity = ScheduleTask::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId"), Index("status")],
)
data class ScheduleRun(
    @PrimaryKey val id: String,
    val taskId: String,
    val scheduledAt: Long,
    val status: String,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val resultChatId: String? = null,
    val error: String? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val RUNNING = "running"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
        const val MISSED = "missed"
        const val CANCELLED = "cancelled"
    }
}
