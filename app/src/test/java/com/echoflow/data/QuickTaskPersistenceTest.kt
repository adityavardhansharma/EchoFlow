package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickTaskPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun `migration adds comparison storage without changing existing data`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE research_runs (id TEXT PRIMARY KEY, report TEXT)"); db.execSQL("INSERT INTO research_runs VALUES ('old','original report')") }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_25_26.migrate(db)
            db.query("SELECT report FROM research_runs WHERE id='old'").use { assertTrue(it.moveToFirst()); assertEquals("original report", it.getString(0)) }
            db.query("SELECT * FROM quick_tasks").use { assertEquals(9, it.columnCount) }
        } finally { helper.close() }
    }
    @Test fun `answers and preference survive storage and interrupted runs are marked`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val task = QuickTask("one", "question", QuickTaskJson.input(SharedInput("source", "text")),
                QuickTaskJson.answers(listOf(TaskAnswer(TaskModel("a", "A"), "partial"))), 1, preferredModelId = "a")
            db.quickTaskDao().save(task)
            db.quickTaskDao().interruptOrphans()
            val restored = db.quickTaskDao().get("one")!!
            assertEquals("interrupted", restored.status)
            assertEquals("partial", QuickTaskJson.answers(restored.answersJson).single().text)
            assertEquals("a", restored.preferredModelId)
        } finally { db.close() }
    }
}
