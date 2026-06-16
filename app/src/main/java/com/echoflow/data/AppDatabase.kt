package com.echoflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatThread::class, ChatMessage::class, CustomModel::class, LocalModel::class,
        DeepResearchModel::class, ResearchRun::class, AdvisorProfile::class, FusionPanel::class,
        AgentProfile::class
    ],
    version = 10, // v10: Echo Agent profiles (MIGRATION_9_10)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun customModelDao(): CustomModelDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun deepResearchModelDao(): DeepResearchModelDao
    abstract fun researchRunDao(): ResearchRunDao
    abstract fun advisorProfileDao(): AdvisorProfileDao
    abstract fun fusionPanelDao(): FusionPanelDao
    abstract fun agentProfileDao(): AgentProfileDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN segmentsJson TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deep_research_models (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS research_runs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL, " +
                        "topic TEXT NOT NULL, " +
                        "engineId TEXT NOT NULL, " +
                        "engineKind TEXT NOT NULL, " +
                        "engineLabel TEXT NOT NULL, " +
                        "searchProvider TEXT, " +
                        "maxSearches INTEGER NOT NULL, " +
                        "maxSources INTEGER NOT NULL, " +
                        "providerJobId TEXT, " +
                        "status TEXT NOT NULL, " +
                        "phase TEXT, " +
                        "progressDone INTEGER NOT NULL, " +
                        "progressTotal INTEGER NOT NULL, " +
                        "planJson TEXT, " +
                        "sourcesJson TEXT, " +
                        "report TEXT, " +
                        "error TEXT, " +
                        "assistantMessageId TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_research_runs_chatId ON research_runs (chatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_research_runs_status ON research_runs (status)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE research_runs ADD COLUMN level TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN costInfo TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN maxCredits INTEGER NOT NULL DEFAULT 2500")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_models ADD COLUMN maxTokens INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS advisor_profiles (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "modelId TEXT NOT NULL, " +
                        "modelName TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS fusion_panels (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "modelIds TEXT NOT NULL, " +
                        "modelNames TEXT NOT NULL, " +
                        "judgeModelId TEXT, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE research_runs ADD COLUMN localAttachmentUri TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN localAttachmentMimeType TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN localAttachmentName TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS agent_profiles (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "workerModelId TEXT NOT NULL, " +
                        "workerModelName TEXT NOT NULL, " +
                        "maxToolCalls INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
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
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                // Only pre-v2 installs (no migration path defined) fall back destructively.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
