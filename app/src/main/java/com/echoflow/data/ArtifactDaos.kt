package com.echoflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    /** The most-recent artifact for one chat — used when iterating the chat's current lineage. */
    @Query("SELECT * FROM artifacts WHERE chatId = :chatId ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestForChat(chatId: String): Flow<Artifact?>

    @Query("SELECT * FROM artifacts WHERE chatId = :chatId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): Artifact?

    /** One lineage by id — workspace open targets this so historical cards don't depend on "latest". */
    @Query("SELECT * FROM artifacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Artifact?

    @Query("SELECT * FROM artifacts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Artifact?>

    @Upsert
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
