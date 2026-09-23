package com.echoflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<ScheduleTask>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun task(id: String): ScheduleTask?

    @Query("SELECT * FROM schedules WHERE status = 'active'")
    suspend fun activeTasks(): List<ScheduleTask>

    @Upsert
    suspend fun saveTask(task: ScheduleTask)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("SELECT * FROM schedule_runs WHERE taskId = :taskId ORDER BY scheduledAt DESC LIMIT 100")
    fun observeRuns(taskId: String): Flow<List<ScheduleRun>>

    @Query("SELECT * FROM schedule_runs WHERE id = :id")
    suspend fun run(id: String): ScheduleRun?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: ScheduleRun): Long

    @Update
    suspend fun updateRun(run: ScheduleRun)

    @Query("UPDATE schedule_runs SET status = :next, startedAt = :startedAt WHERE id = :id AND status = :expected")
    suspend fun claimRun(id: String, expected: String, next: String, startedAt: Long): Int

    @Query("SELECT * FROM schedule_runs WHERE status = 'running'")
    suspend fun interruptedRuns(): List<ScheduleRun>

    @Query("SELECT * FROM schedule_runs WHERE taskId = :taskId AND status = 'running' ORDER BY startedAt DESC LIMIT 1")
    suspend fun runningForTask(taskId: String): ScheduleRun?
}
