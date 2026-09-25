package com.echoflow.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoflow.data.AppDatabase
import com.echoflow.data.usage.DeepgramCosts
import com.echoflow.data.usage.ListPrices
import com.echoflow.data.usage.OpenRouterKeySpend
import com.echoflow.data.usage.OpenRouterKeyUsage
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.usage.UsageLedger
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageRecord
import com.echoflow.ui.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spend for every saved key. Shared by the Settings row, the Spend home and each provider page,
 * so the one window filter carries across all of them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpendViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).usageDao()
    private val keys = MutableStateFlow<Map<UsageProvider, String>>(emptyMap())
    private val _window = MutableStateFlow(SpendWindow.Month)
    val window: StateFlow<SpendWindow> = _window.asStateFlow()
    private val _openRouter = MutableStateFlow<OpenRouterKeySpend?>(null)
    val openRouter: StateFlow<OpenRouterKeySpend?> = _openRouter.asStateFlow()

    var refreshing by mutableStateOf(false); private set
    var refreshNote by mutableStateOf<String?>(null); private set
    val trackingStartedAt: Long? = UsageLedger.trackingStartedAt(application)

    val home: StateFlow<SpendHome> = combine(_window, keys) { window, keys -> window to keys }
        .flatMapLatest { (window, keys) ->
            combine(dao.observeTotals(window.since(System.currentTimeMillis())), _openRouter) { totals, openRouter ->
                SpendMath.home(keys, totals, openRouter, window)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendHome(emptyList(), emptyList()))

    /**
     * The home chart: each dollar provider is one series, in the same order (and colour) as its
     * row. Only itemised, priced requests can be placed in time, so OpenRouter's pre-tracking
     * spend is in the total but not the bars.
     */
    val homeTimeline: StateFlow<SpendTimeline?> = _window
        .flatMapLatest { window ->
            combine(dao.observePoints(window.since(System.currentTimeMillis())), home) { points, home ->
                val series = home.dollars.map { it.provider.name to it.keyHash }
                SpendBuckets.timeline(
                    window = window,
                    now = System.currentTimeMillis(),
                    earliest = listOfNotNull(trackingStartedAt, points.firstOrNull()?.createdAt).minOrNull(),
                    seriesCount = series.size,
                    samples = points.mapNotNull { point ->
                        val index = series.indexOf(point.provider to point.keyHash)
                        if (index < 0) null else SpendSample(index, point.createdAt, point.costUsd)
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Price OpenAI, Anthropic and Gemini rows stored before a list price was on hand.
        viewModelScope.launch {
            try {
                ListPrices.reprice(getApplication(), dao)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No list yet; rows stay unpriced until the next visit.
            }
        }
    }

    /** This month's dollar total for the Settings row; null until a provider key is saved. */
    val monthTotal: StateFlow<Double?> = keys
        .flatMapLatest { keys ->
            combine(dao.observeTotals(SpendWindow.Month.since(System.currentTimeMillis())), _openRouter) { totals, openRouter ->
                SpendMath.home(keys, totals, openRouter, SpendWindow.Month).takeUnless { it.isEmpty }?.totalUsd
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(window: SpendWindow) { _window.value = window }

    fun setKeys(value: Map<UsageProvider, String>) {
        val clean = value.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        if (clean == keys.value) return
        val openRouterChanged = clean[UsageProvider.OpenRouter] != keys.value[UsageProvider.OpenRouter]
        keys.value = clean
        if (openRouterChanged) {
            _openRouter.value = clean[UsageProvider.OpenRouter]?.let { OpenRouterKeyUsage.cached(getApplication(), it) }
        }
    }

    fun keyFor(provider: UsageProvider): String? = keys.value[provider]

    fun records(provider: UsageProvider): Flow<List<UsageRecord>> = combine(_window, keys) { window, keys -> window to keys[provider] }
        .flatMapLatest { (window, key) ->
            if (key == null) flowOf(emptyList())
            else dao.observe(provider.name, UsageKeys.hash(key), window.since(System.currentTimeMillis()))
        }

    /** Fetches OpenRouter's own figures for the saved key. Cached figures stay on failure. */
    fun refresh() {
        val key = keys.value[UsageProvider.OpenRouter] ?: return
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            OpenRouterKeyUsage.refresh(getApplication(), key)
                .onSuccess { _openRouter.value = it; refreshNote = null }
                .onFailure { refreshNote = it.message ?: "Couldn't reach OpenRouter." }
            refreshing = false
        }
    }

    /** Fills in Deepgram's settled charge for recent transcriptions. */
    fun priceDeepgram() {
        val key = keys.value[UsageProvider.Deepgram] ?: return
        viewModelScope.launch {
            try {
                DeepgramCosts.fill(dao, key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keys without usage access keep their rows unpriced.
            }
        }
    }
}

/** Every saved provider key, read live from Settings. Blank keys are dropped by the view model. */
@Composable
internal fun rememberSpendKeys(settings: SettingsViewModel): Map<UsageProvider, String> {
    val openRouter by settings.apiKey.collectAsState()
    val config by settings.customProviderConfig.collectAsState()
    val exa by settings.exaApiKey.collectAsState()
    val parallel by settings.parallelApiKey.collectAsState()
    val firecrawl by settings.firecrawlApiKey.collectAsState()
    return remember(openRouter, config, exa, parallel, firecrawl) {
        mapOf(
            UsageProvider.OpenRouter to openRouter,
            UsageProvider.OpenAi to config.openAiApiKey,
            UsageProvider.Claude to config.claudeApiKey,
            UsageProvider.Gemini to config.geminiApiKey,
            UsageProvider.Cerebras to config.cerebrasApiKey,
            UsageProvider.Sarvam to config.sarvamApiKey,
            UsageProvider.XAi to config.xAiApiKey,
            UsageProvider.Deepgram to config.deepgramApiKey,
            UsageProvider.Exa to exa,
            UsageProvider.Parallel to parallel,
            UsageProvider.Firecrawl to firecrawl,
        )
    }
}
