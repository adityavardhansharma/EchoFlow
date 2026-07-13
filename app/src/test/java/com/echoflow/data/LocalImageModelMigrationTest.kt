package com.echoflow.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalImageModelMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "local-image-v14-migration"
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE local_image_models (" +
                                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "directoryName TEXT NOT NULL, installedBytes INTEGER NOT NULL, " +
                                "sourceRevision TEXT NOT NULL, sourceCheckpointSha256 TEXT NOT NULL, " +
                                "bundleSha256 TEXT NOT NULL, licenseId TEXT NOT NULL, " +
                                "activationPhrase TEXT, defaultNegativePrompt TEXT, " +
                                "bundleFormatVersion INTEGER NOT NULL, addedAt INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(databaseName)
        File(context.filesDir, "image_models/stable-diffusion-1.5").deleteRecursively()
        SettingsRepository(context).saveLocalImageModel("")
    }

    @Test
    fun `v14 MediaPipe rows receive runtime default without losing metadata`() {
        val preservedFile = File(
            context.filesDir,
            "image_models/stable-diffusion-1.5/bins/weights.bin",
        ).apply {
            parentFile?.mkdirs()
            writeText("model-data")
        }
        SettingsRepository(context).saveLocalImageModel("local-image/stable-diffusion-1.5")
        val database = helper.writableDatabase
        database.execSQL(
            "INSERT INTO local_image_models VALUES (" +
                "'local-image/stable-diffusion-1.5', 'Stable Diffusion 1.5', 'stable-diffusion-1.5', " +
                "100, 'revision', 'source-hash', 'bundle-hash', 'license', NULL, 'negative', 1, 123)"
        )

        AppDatabase.MIGRATION_14_15.migrate(database)

        database.query(
            "SELECT id, name, directoryName, installedBytes, sourceRevision, " +
                "sourceCheckpointSha256, bundleSha256, licenseId, activationPhrase, " +
                "defaultNegativePrompt, bundleFormatVersion, addedAt, runtime, modelFileName " +
                "FROM local_image_models"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("local-image/stable-diffusion-1.5", cursor.getString(0))
            assertEquals("Stable Diffusion 1.5", cursor.getString(1))
            assertEquals("stable-diffusion-1.5", cursor.getString(2))
            assertEquals(100L, cursor.getLong(3))
            assertEquals("revision", cursor.getString(4))
            assertEquals("source-hash", cursor.getString(5))
            assertEquals("bundle-hash", cursor.getString(6))
            assertEquals("license", cursor.getString(7))
            assertNull(cursor.getString(8))
            assertEquals("negative", cursor.getString(9))
            assertEquals(1, cursor.getInt(10))
            assertEquals(123L, cursor.getLong(11))
            assertEquals(LocalImageRuntime.MEDIAPIPE.id, cursor.getString(12))
            assertNull(cursor.getString(13))
            assertTrue(
                LocalImageInstalledModelFiles.isInstalled(
                    File(context.filesDir, "image_models/${cursor.getString(2)}"),
                    cursor.getString(12),
                    cursor.getString(13),
                )
            )
        }
        assertEquals("model-data", preservedFile.readText())
        assertEquals(
            "local-image/stable-diffusion-1.5",
            SettingsRepository(context).getLocalImageModelDirect(),
        )
    }
}
