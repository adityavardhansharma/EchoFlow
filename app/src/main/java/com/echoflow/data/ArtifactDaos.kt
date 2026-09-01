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
    /**
     * Gallery shelf: every artifact lineage that has not been hidden from the list, newest activity
     * first. Hidden rows stay on the chat timeline.
     */
    @Query(
        "SELECT * FROM artifacts WHERE hiddenFromGallery IS NULL OR hiddenFromGallery = 0 " +
            "ORDER BY updatedAt DESC"
    )
    fun observeListed(): Flow<List<Artifact>>

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

    @Query("UPDATE artifacts SET hiddenFromGallery = 1 WHERE id = :id")
    suspend fun hideFromGallery(id: String)
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
