package com.echoflow.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.echoflow.data.AppDatabase
import com.echoflow.data.SettingsRepository
import com.echoflow.data.usage.OpenRouterKeyUsage
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.usage.UsageKind
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageRecord
import com.echoflow.ui.screens.settings.SpendWindow
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * Debug builds only. Seeds a believable fortnight of spend so the Spend pages can be reviewed and
 * screenshotted without live keys. Saved keys are kept; blank ones get placeholders.
 *
 *     adb shell am broadcast -n com.echoflow/.debug.SpendDemoReceiver
 *     adb shell am force-stop com.echoflow   # so Settings re-reads the keys
 */
class SpendDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                seed(context.applicationContext)
                Log.i("SpendDemo", "Seeded sample spend")
            } catch (e: Exception) {
                Log.e("SpendDemo", "Seeding failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun seed(context: Context) = runBlocking {
        val settings = SettingsRepository(context)
        if (settings.apiKey.value.isBlank()) settings.saveApiKey("sk-or-v1-demo")
        listOf("exa", "firecrawl").forEach { if (settings.getSearchApiKeyDirect(it).isBlank()) settings.saveSearchApiKey(it, "demo-$it") }
        val config = settings.customProviderConfig.value
        settings.saveCustomProviderConfig(
            config.copy(
                openAiApiKey = config.openAiApiKey.ifBlank { "sk-demo-openai" },
                claudeApiKey = config.claudeApiKey.ifBlank { "sk-ant-demo" },
                xAiApiKey = config.xAiApiKey.ifBlank { "xai-demo" },
                deepgramApiKey = config.deepgramApiKey.ifBlank { "demo-deepgram" },
            )
        )
        val saved = settings.customProviderConfig.value
        val keys = mapOf(
            UsageProvider.OpenRouter to settings.apiKey.value,
            UsageProvider.OpenAi to saved.openAiApiKey,
            UsageProvider.Claude to saved.claudeApiKey,
            UsageProvider.XAi to saved.xAiApiKey,
            UsageProvider.Deepgram to saved.deepgramApiKey,
            UsageProvider.Exa to settings.getSearchApiKeyDirect("exa"),
            UsageProvider.Firecrawl to settings.getSearchApiKeyDirect("firecrawl"),
        ).mapValues { UsageKeys.hash(it.value) }

        val dao = AppDatabase.getDatabase(context).usageDao()
        val random = Random(7)
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        var n = 0
        fun at(hoursAgo: Double) = now - (hoursAgo * hour).toLong()
        suspend fun put(provider: UsageProvider, hoursAgo: Double, kind: UsageKind, model: String?, build: UsageRecord.() -> UsageRecord) {
            val base = UsageRecord(
                id = "demo-${provider.name}-${n++}", provider = provider.name, keyHash = keys.getValue(provider),
                createdAt = at(hoursAgo), kind = kind.name, model = model, externalId = "demo-${provider.name.lowercase()}-$n",
            )
            dao.put(base.build())
        }

        val openRouterModels = listOf(
            Triple("anthropic/claude-sonnet-5", 3.0, 15.0),
            Triple("openai/gpt-5.5", 1.25, 10.0),
            Triple("google/gemini-3.5-flash", 0.3, 2.5),
            Triple("x-ai/grok-4.5", 3.0, 15.0),
            Triple("deepseek/deepseek-v4", 0.28, 1.1),
            Triple("moonshotai/kimi-k2.5", 0.6, 2.5),
            Triple("qwen/qwen3.5-max", 1.2, 6.0),
        )
        var orCost = 0.0
        var orToday = 0.0; var orWeek = 0.0; var orMonth = 0.0
        val todayStart = SpendWindow.Today.since(now); val weekStart = SpendWindow.Week.since(now); val monthStart = SpendWindow.Month.since(now)
        var h = 0.3
        while (h < 24 * 16) {
            val (model, inPrice, outPrice) = openRouterModels[random.nextInt(openRouterModels.size)]
            val input = random.nextLong(400, 18_000)
            val output = random.nextLong(120, 2_400)
            val cost = (input * inPrice + output * outPrice) / 1_000_000.0
            val created = at(h)
            put(UsageProvider.OpenRouter, h, if (random.nextInt(12) == 0) UsageKind.Image else UsageKind.Chat, model) {
                copy(inputTokens = input, outputTokens = output, cachedTokens = if (random.nextBoolean()) input / 3 else null,
                    reasoningTokens = if (model.contains("gpt") || model.contains("deepseek")) output / 2 else null, costUsd = cost)
            }
            orCost += cost
            if (created >= todayStart) orToday += cost
            if (created >= weekStart) orWeek += cost
            if (created >= monthStart) orMonth += cost
            h += random.nextDouble(0.4, 5.5)
        }
        // OpenRouter's own account figures include spend from other apps and from before tracking.
        val body = String.format(
            Locale.US,
            """{"data":{"label":"sk-or-v1-demo","usage":%.6f,"usage_daily":%.6f,"usage_weekly":%.6f,"usage_monthly":%.6f,"limit":100,"limit_remaining":%.6f}}""",
            orMonth + 31.46, orToday + 0.12, orWeek + 0.94, orMonth + 3.41, 100 - (orMonth + 31.46),
        )
        OpenRouterKeyUsage.store(context, body, now, keys.getValue(UsageProvider.OpenRouter))

        repeat(9) { i -> put(UsageProvider.XAi, 2.0 + i * 17.3, UsageKind.Chat, "grok-4.5") {
            val input = random.nextLong(800, 9_000); val output = random.nextLong(200, 1_800)
            copy(inputTokens = input, outputTokens = output, costUsd = (input * 3.0 + output * 15.0) / 1_000_000.0)
        } }
        repeat(14) { i -> put(UsageProvider.Deepgram, 1.1 + i * 9.7, UsageKind.Transcription, "nova-3") {
            val seconds = random.nextDouble(3.0, 48.0)
            copy(audioSeconds = seconds, costUsd = if (i == 0) null else seconds / 60.0 * 0.0092)
        } }
        repeat(11) { i -> put(UsageProvider.Exa, 3.4 + i * 13.1, if (i % 4 == 0) UsageKind.Research else UsageKind.Search, if (i % 4 == 0) "exa-research" else "exa") {
            copy(costUsd = if (i % 4 == 0) 0.25 else 0.007)
        } }
        repeat(16) { i -> put(UsageProvider.OpenAi, 0.8 + i * 8.9, UsageKind.Chat, if (i % 3 == 0) "gpt-5.5-mini" else "gpt-5.5") {
            val input = random.nextLong(600, 14_000); val output = random.nextLong(150, 2_200)
            copy(inputTokens = input, outputTokens = output, cachedTokens = input / 4, costUsd = (input * 1.25 + output * 10.0) / 1_000_000.0)
        } }
        repeat(10) { i -> put(UsageProvider.Claude, 1.6 + i * 14.2, UsageKind.Chat, "claude-opus-5-5") {
            val input = random.nextLong(900, 22_000); val output = random.nextLong(300, 3_000)
            copy(inputTokens = input, outputTokens = output, cachedTokens = input / 2, costUsd = (input * 5.0 + output * 25.0) / 1_000_000.0)
        } }
        repeat(12) { i -> put(UsageProvider.Firecrawl, 2.2 + i * 11.4, if (i % 3 == 0) UsageKind.Browse else UsageKind.Search, null) {
            copy(credits = if (i % 3 == 0) 1.0 else 2.0)
        } }
        Log.i("SpendDemo", String.format(Locale.US, "OpenRouter itemised $%.2f", orCost))
    }
}
