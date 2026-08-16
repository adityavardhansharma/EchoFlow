package com.echoflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    /** The Projects hub list — most-recently-touched first. */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Project?

    @Upsert
    suspend fun upsert(project: Project)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE projects SET instructions = :instructions, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setInstructions(id: String, instructions: String, updatedAt: Long)

    @Query("UPDATE projects SET colorIndex = :colorIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setColor(id: String, colorIndex: Int, updatedAt: Long)

    @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProjectDocumentDao {
    @Query("SELECT * FROM project_documents WHERE projectId = :projectId ORDER BY addedAt ASC")
    fun observeForProject(projectId: String): Flow<List<ProjectDocument>>

    @Query("SELECT * FROM project_documents WHERE projectId = :projectId ORDER BY addedAt ASC")
    suspend fun getForProjectSync(projectId: String): List<ProjectDocument>

    @Query("SELECT COUNT(*) FROM project_documents WHERE projectId = :projectId")
    fun countForProject(projectId: String): Flow<Int>

    @Query("SELECT * FROM project_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: ProjectDocument)

    @Query("DELETE FROM project_documents WHERE id = :id")
    suspend fun delete(id: String)
}
