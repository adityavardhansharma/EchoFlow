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
        AgentProfile::class, BrowserSession::class, BrowserStep::class,
        Artifact::class, ArtifactVersion::class,
        ImageModel::class, GeneratedImage::class,
        VideoModel::class, GeneratedVideo::class,
        Project::class, ProjectDocument::class
    ],
    version = 22, // v22: Projects (projects, project_documents, chat_threads.projectId)
    exportSchema = true
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
    abstract fun browserSessionDao(): BrowserSessionDao
    abstract fun browserStepDao(): BrowserStepDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun artifactVersionDao(): ArtifactVersionDao
    abstract fun imageModelDao(): ImageModelDao
    abstract fun generatedImageDao(): GeneratedImageDao
    abstract fun videoModelDao(): VideoModelDao
    abstract fun generatedVideoDao(): GeneratedVideoDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectDocumentDao(): ProjectDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Version 2 added the nullable reasoning trace to chat messages. The original app used
         * destructive fallback for this upgrade; keeping the explicit migration here ensures
         * users of the first release retain their conversations.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN reasoning TEXT")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
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

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS browser_sessions (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL, " +
                        "goal TEXT NOT NULL, " +
                        "resolvedUrl TEXT, " +
                        "scrapeId TEXT, " +
                        "liveViewUrl TEXT, " +
                        "interactiveLiveViewUrl TEXT, " +
                        "status TEXT NOT NULL, " +
                        "phase TEXT, " +
                        "pendingKind TEXT, " +
                        "pendingInstruction TEXT, " +
                        "pendingDraft TEXT, " +
                        "candidatesJson TEXT, " +
                        "lastOutput TEXT, " +
                        "error TEXT, " +
                        "openedAt INTEGER, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "lastActivityAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(chatId) REFERENCES chat_threads(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_sessions_chatId ON browser_sessions (chatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_sessions_status ON browser_sessions (status)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS browser_steps (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "sessionId TEXT NOT NULL, " +
                        "role TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(sessionId) REFERENCES browser_sessions(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_browser_steps_sessionId ON browser_steps (sessionId)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS artifacts (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "currentVersion INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(chatId) REFERENCES chat_threads(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artifacts_chatId ON artifacts (chatId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS artifact_versions (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "artifactId TEXT NOT NULL, " +
                        "versionNumber INTEGER NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "sourcePrompt TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(artifactId) REFERENCES artifacts(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artifact_versions_artifactId ON artifact_versions (artifactId)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS image_models (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS generated_images (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL, " +
                        "filePath TEXT NOT NULL, " +
                        "prompt TEXT NOT NULL, " +
                        "parentId TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(chatId) REFERENCES chat_threads(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generated_images_chatId ON generated_images (chatId)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS local_image_models (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "directoryName TEXT NOT NULL, " +
                        "installedBytes INTEGER NOT NULL, " +
                        "sourceRevision TEXT NOT NULL, " +
                        "sourceCheckpointSha256 TEXT NOT NULL, " +
                        "bundleSha256 TEXT NOT NULL, " +
                        "licenseId TEXT NOT NULL, " +
                        "activationPhrase TEXT, " +
                        "defaultNegativePrompt TEXT, " +
                        "bundleFormatVersion INTEGER NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
            }
        }

        /** Existing v14 installs were all MediaPipe directory bundles. */
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE local_image_models ADD COLUMN runtime TEXT NOT NULL " +
                        "DEFAULT 'mediapipe'"
                )
                db.execSQL(
                    "ALTER TABLE local_image_models ADD COLUMN modelFileName TEXT"
                )
            }
        }

        /**
         * Video generation. `generated_videos` doubles as the async job store, so it carries
         * the provider job id and polling URL alongside the downloaded file path — a run
         * interrupted by a kill is resumable from these rows alone.
         */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS video_models (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS generated_videos (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "chatId TEXT NOT NULL, " +
                        "prompt TEXT NOT NULL, " +
                        "modelId TEXT NOT NULL, " +
                        "jobId TEXT, " +
                        "pollingUrl TEXT, " +
                        "status TEXT NOT NULL, " +
                        "filePath TEXT, " +
                        "aspectRatio TEXT, " +
                        "resolution TEXT, " +
                        "error TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(chatId) REFERENCES chat_threads(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generated_videos_chatId ON generated_videos (chatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generated_videos_status ON generated_videos (status)")
            }
        }

        /**
         * On-device image generation is gone: the two diffusion runtimes were ~81 MB of the
         * APK and the models they could actually run were not worth it. Only the install
         * bookkeeping lives in Room — the bundles themselves are files under
         * filesDir/image_models/, swept separately on first launch after the upgrade.
         */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS local_image_models")
            }
        }

        /**
         * Chat and Imagine become separate surfaces, each with its own history. The default
         * on this column IS the grandfather rule: every conversation that already exists —
         * including ones full of generated images — becomes a Chat thread, with no content
         * inspection and nothing rewritten. New threads are stamped from the active mode.
         */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN kind TEXT NOT NULL DEFAULT 'chat'")
            }
        }

        /** Prompt-edit reply history: older assistant answers live as JSON on the latest row. */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyVersionsJson TEXT")
            }
        }

        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN pinnedAt INTEGER")
            }
        }

        /**
         * The Deep Research step timeline. Both columns are purely additive — no table rebuild,
         * no data movement — so every existing run and every finished report stored on
         * `chat_messages.segmentsJson` is left exactly as it was.
         *
         * `uiVersion` defaults to 1, which is what quarantines the old data: existing rows are
         * stamped legacy for free and keep rendering through `ui/legacy`, while new runs are
         * inserted at 2 and get the timeline. Finished reports are dispatched separately, on the
         * persisted segment type ("report"/"data" = legacy, "research" = new), so old chats can
         * never be reclassified by a default.
         */
        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE research_runs ADD COLUMN stepsJson TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN uiVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Projects. Two new tables plus an additive nullable column on chat_threads — no data
         * movement, so every existing conversation is untouched and simply starts life with a
         * null projectId (a loose chat). The projectId column is deliberately *not* a foreign
         * key: a project delete returns its chats to the drawer rather than cascading them away,
         * which the app does in code. The index matches ChatThread's declared Index("projectId").
         */
        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No DEFAULT clauses: the entity fields carry no @ColumnInfo(defaultValue), so a
                // fresh Room install creates these columns without SQL defaults — the migrated
                // table must match that exactly. Every insert supplies all columns anyway.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS projects (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "instructions TEXT NOT NULL, " +
                        "colorIndex INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS project_documents (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "projectId TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "mimeType TEXT NOT NULL, " +
                        "sizeBytes INTEGER NOT NULL, " +
                        "filePath TEXT NOT NULL, " +
                        "extractedText TEXT, " +
                        "addedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_documents_projectId ON project_documents (projectId)")
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN projectId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_threads_projectId ON chat_threads (projectId)")
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
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
