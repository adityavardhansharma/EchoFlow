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

/**
 * Gallery hide is a schema bump (v25). The change is purely additive: existing artifact
 * lineages survive and stay listed (`hiddenFromGallery` is NULL).
 */
@RunWith(RobolectricTestRunner::class)
class ArtifactGalleryHideMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    private val createV24Artifacts = """
        CREATE TABLE IF NOT EXISTS artifacts (
            id TEXT NOT NULL PRIMARY KEY,
            chatId TEXT NOT NULL,
            title TEXT NOT NULL,
            type TEXT NOT NULL,
            currentVersion INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
    """.trimIndent()

    @Before fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(createV24Artifacts)
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After fun tearDown() = helper.close()

    @Test fun `existing artifacts survive and stay listed`() {
        db.execSQL(
            "INSERT INTO artifacts (id, chatId, title, type, currentVersion, createdAt, updatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf("art-1", "chat-1", "Page", "html", 2, 1_000L, 2_000L),
        )

        AppDatabase.MIGRATION_24_25.migrate(db)

        db.query(
            "SELECT id, title, hiddenFromGallery FROM artifacts WHERE id = 'art-1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("art-1", c.getString(0))
            assertEquals("Page", c.getString(1))
            assertNull(c.getString(2))
        }
    }
}
