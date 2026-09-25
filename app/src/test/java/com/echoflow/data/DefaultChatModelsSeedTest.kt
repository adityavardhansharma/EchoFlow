package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.SettingsPreferenceStorage.LEGACY_FILE
import com.echoflow.data.SettingsPreferenceStorage.SECURE_FILE
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultChatModelsSeedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(SECURE_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test fun `fresh install seeds shipped models and keeps Luna as the implicit default`() = runBlocking {
        DefaultChatModelsSeed.run(context, database)

        val models = database.customModelDao().getAllCustomModelsSync()
        assertTrue(models.any { it.id == DefaultChatModels.DEFAULT_MODEL_ID })
        assertTrue(models.any { it.id == DefaultChatModels.ECHO_LUMEN_MODEL_ID })
        assertFalse(models.any { it.id == DefaultChatModels.LEGACY_DEFAULT_MODEL_ID })

        val repository = SettingsRepository(context)
        assertEquals(DefaultChatModels.DEFAULT_MODEL_ID, repository.getSelectedModelDirect())
    }

    @Test fun `existing chats without a saved selection stay on legacy Gemini`() = runBlocking {
        database.chatDao().insertThread(
            ChatThread(
                id = "thread-1",
                title = "Existing chat",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        DefaultChatModelsSeed.run(context, database)

        val models = database.customModelDao().getAllCustomModelsSync()
        assertTrue(models.any { it.id == DefaultChatModels.LEGACY_DEFAULT_MODEL_ID })
        assertTrue(models.any { it.id == DefaultChatModels.DEFAULT_MODEL_ID })
        assertTrue(models.any { it.id == DefaultChatModels.ECHO_LUMEN_MODEL_ID })

        val repository = SettingsRepository(context)
        assertEquals(DefaultChatModels.LEGACY_DEFAULT_MODEL_ID, repository.getSelectedModelDirect())
    }

    @Test fun `explicit legacy selection keeps Gemini without rewriting other models`() = runBlocking {
        context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model", DefaultChatModels.LEGACY_DEFAULT_MODEL_ID)
            .commit()

        DefaultChatModelsSeed.run(context, database)

        val models = database.customModelDao().getAllCustomModelsSync()
        assertTrue(models.any { it.id == DefaultChatModels.LEGACY_DEFAULT_MODEL_ID })

        val repository = SettingsRepository(context)
        assertEquals(DefaultChatModels.LEGACY_DEFAULT_MODEL_ID, repository.getSelectedModelDirect())
    }

    @Test fun `existing custom selection is left untouched while shipped models are added`() = runBlocking {
        database.customModelDao().insertCustomModel(CustomModel("anthropic/claude-sonnet-4.5", "Claude Sonnet 4.5"))
        context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model", "anthropic/claude-sonnet-4.5")
            .commit()

        DefaultChatModelsSeed.run(context, database)

        val models = database.customModelDao().getAllCustomModelsSync()
        assertTrue(models.any { it.id == "anthropic/claude-sonnet-4.5" })
        assertTrue(models.any { it.id == DefaultChatModels.DEFAULT_MODEL_ID })
        assertTrue(models.any { it.id == DefaultChatModels.ECHO_LUMEN_MODEL_ID })
        assertFalse(models.any { it.id == DefaultChatModels.LEGACY_DEFAULT_MODEL_ID })

        val repository = SettingsRepository(context)
        assertEquals("anthropic/claude-sonnet-4.5", repository.getSelectedModelDirect())
    }

    @Test fun `seed runs only once`() = runBlocking {
        DefaultChatModelsSeed.run(context, database)
        database.customModelDao().deleteCustomModel(DefaultChatModels.DEFAULT_MODEL_ID)

        DefaultChatModelsSeed.run(context, database)

        assertEquals(null, database.customModelDao().getCustomModelById(DefaultChatModels.DEFAULT_MODEL_ID))
    }

    @Test fun `upgrade swaps the seeded GPT 5_6 Luna row for GPT-6 Luna`() = runBlocking {
        seedV1With("openai/gpt-5.6-luna", "GPT 5.6 Luna")

        DefaultChatModelsSeed.run(context, database)

        assertNull(database.customModelDao().getCustomModelById("openai/gpt-5.6-luna"))
        assertEquals(
            "GPT-6 Luna",
            database.customModelDao().getCustomModelById(DefaultChatModels.DEFAULT_MODEL_ID)?.name,
        )
    }

    @Test fun `upgrade keeps GPT 5_6 Luna while it is the selected model`() = runBlocking {
        seedV1With("openai/gpt-5.6-luna", "GPT 5.6 Luna")
        context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model", "openai/gpt-5.6-luna")
            .commit()

        DefaultChatModelsSeed.run(context, database)

        assertNotNull(database.customModelDao().getCustomModelById("openai/gpt-5.6-luna"))
        assertNotNull(database.customModelDao().getCustomModelById(DefaultChatModels.DEFAULT_MODEL_ID))
        assertEquals("openai/gpt-5.6-luna", SettingsRepository(context).getSelectedModelDirect())
    }

    @Test fun `upgrade keeps a GPT-5_6 Luna the user added from the directory`() = runBlocking {
        seedV1With("openai/gpt-5.6-luna", "GPT-5.6 Luna")

        DefaultChatModelsSeed.run(context, database)

        assertNotNull(database.customModelDao().getCustomModelById("openai/gpt-5.6-luna"))
    }

    /** Simulates an install the first seed pass already ran on, before GPT-6 Luna shipped. */
    private suspend fun seedV1With(id: String, name: String) {
        database.customModelDao().insertCustomModel(CustomModel(id, name))
        database.customModelDao().insertCustomModel(
            CustomModel(DefaultChatModels.ECHO_LUMEN_MODEL_ID, DefaultChatModels.ECHO_LUMEN_MODEL_NAME),
        )
        listOf(LEGACY_FILE, SECURE_FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE)
                .edit().putBoolean("default_chat_models_seeded_v1", true).commit()
        }
    }
}
