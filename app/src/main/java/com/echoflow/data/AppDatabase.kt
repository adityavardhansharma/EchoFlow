package com.echoflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatThread::class, ChatMessage::class, CustomModel::class, LocalModel::class],
    version = 3, // v3: local_models table + chat_messages.toolEventsJson/citationsJson (MIGRATION_2_3)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun customModelDao(): CustomModelDao
    abstract fun localModelDao(): LocalModelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN toolEventsJson TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN citationsJson TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS local_models (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "fileName TEXT NOT NULL, " +
                        "sizeBytes INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_chat_database"
                )
                .addMigrations(MIGRATION_2_3)
                // Only pre-v2 installs (no migration path defined) fall back destructively.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
