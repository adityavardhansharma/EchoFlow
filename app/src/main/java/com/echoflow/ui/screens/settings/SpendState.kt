package com.echoflow.ui.screens.settings

import com.echoflow.data.usage.OpenRouterKeySpend
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageRecord
import com.echoflow.data.usage.UsageTotal
import com.echoflow.data.usage.UsageUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * The one filter every Spend page shares. Windows follow OpenRouter's own: UTC day, UTC week
 * starting Monday, UTC month, and all time. Using OpenRouter's boundaries everywhere is what lets
 * its reported figure and the itemised rows add up.
 */
enum class SpendWindow(val label: String, val phrase: String) {
    AllTime("All time", "all time"),
    Month("Month", "this month"),
    Week("Week", "this week"),
    Today("Today", "today");

    fun since(now: Long): Long {
        val day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        val start = when (this) {
            AllTime -> return 0L
            Month -> day.withDayOfMonth(1)
            Week -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Today -> day
        }
        return start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun of(spend: OpenRouterKeySpend): Double = when (this) {
        AllTime -> spend.total
        Month -> spend.month
        Week -> spend.week
        Today -> spend.today
    }
}

/** One provider on the Spend home, for the selected window. */
data class SpendRow(
    val provider: UsageProvider,
    val keyHash: String,
    /** Dollars for [UsageUnit.Usd] providers; null for everyone else. */
    val usd: Double?,
    val requests: Int,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val credits: Double?,
    /** True when [usd] is the provider's own account figure rather than a sum of rows. */
    val providerReported: Boolean,
)

data class SpendHome(val dollars: List<SpendRow>, val ownUnits: List<SpendRow>) {
    val totalUsd: Double get() = dollars.sumOf { it.usd ?: 0.0 }
    val isEmpty: Boolean get() = dollars.isEmpty() && ownUnits.isEmpty()
}

object SpendMath {
    /** Providers in the order the Spend home lists them when amounts tie. */
    val order = listOf(
        UsageProvider.OpenRouter, UsageProvider.OpenAi, UsageProvider.Claude, UsageProvider.Gemini,
        UsageProvider.XAi, UsageProvider.Cerebras, UsageProvider.Sarvam, UsageProvider.Deepgram,
        UsageProvider.Exa, UsageProvider.Parallel, UsageProvider.Firecrawl,
    )

    /**
     * Builds the home from saved keys only: a provider without a key never appears, and rows
     * recorded under an older key are ignored. OpenRouter uses its own reported figure when one
     * has been fetched, so spend from before tracking and from other apps is included.
     */
    fun home(
        keys: Map<UsageProvider, String>,
        totals: List<UsageTotal>,
        openRouter: OpenRouterKeySpend?,
        window: SpendWindow,
    ): SpendHome {
        val rows = order.mapNotNull { provider ->
            val key = keys[provider]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val hash = UsageKeys.hash(key)
            val total = totals.firstOrNull { it.provider == provider.name && it.keyHash == hash }
            val reported = provider == UsageProvider.OpenRouter && openRouter?.keyHash == hash
            SpendRow(
                provider = provider,
                keyHash = hash,
                usd = when {
                    provider.unit != UsageUnit.Usd -> null
                    reported -> window.of(openRouter!!)
                    else -> total?.costUsd ?: 0.0
                },
                requests = total?.requests ?: 0,
                inputTokens = total?.inputTokens,
                outputTokens = total?.outputTokens,
                credits = total?.credits,
                providerReported = reported,
            )
        }
        val (dollars, own) = rows.partition { it.provider.unit == UsageUnit.Usd }
        return SpendHome(
            dollars = dollars.sortedByDescending { it.usd ?: 0.0 },
            ownUnits = own,
        )
    }

    /**
     * OpenRouter's figure minus what EchoFlow itemised: spend from other apps, from before
     * tracking began, or on replies stopped before OpenRouter reported a price.
     */
    fun unitemised(reported: Double, records: List<UsageRecord>): Double {
        val gap = reported - records.sumOf { it.costUsd ?: 0.0 }
        return if (gap > 0.005) gap else 0.0
    }

    /** Rows grouped by the phone's calendar day, newest first. */
    fun days(records: List<UsageRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<UsageRecord>>> =
        records.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { it.key to it.value.sortedByDescending(UsageRecord::createdAt) }
}

/** Number formatting for Spend. Figures use tabular digits in the UI, so widths stay steady. */
object SpendFormat {
    /** `$12.40`, `$0.42`, and small charges to four places (`$0.0031`) so they never read as zero. */
    fun usd(amount: Double): String {
        val a = abs(amount)
        return when {
            a == 0.0 -> "$0.00"
            a < 0.01 -> String.format(Locale.US, "$%.4f", amount)
            a < 1_000 -> String.format(Locale.US, "$%.2f", amount)
            else -> String.format(Locale.US, "$%,.0f", amount)
        }
    }

    /** Splits `$1,234.56` into `$1,234` and `.56` for the hero figure. */
    fun usdParts(amount: Double): Pair<String, String> {
        val text = if (abs(amount) in 0.0001..0.0099) usd(amount) else String.format(Locale.US, "$%,.2f", amount)
        val dot = text.indexOf('.')
        return if (dot < 0) text to "" else text.substring(0, dot) to text.substring(dot)
    }

    fun compact(value: Long): String = when {
        value >= 1_000_000_000 -> trim(value / 1e9) + "B"
        value >= 1_000_000 -> trim(value / 1e6) + "M"
        value >= 10_000 -> trim(value / 1e3) + "K"
        else -> String.format(Locale.US, "%,d", value)
    }

    fun credits(value: Double): String =
        if (value % 1.0 == 0.0) String.format(Locale.US, "%,.0f", value) else String.format(Locale.US, "%,.1f", value)

    fun seconds(value: Double): String = when {
        value >= 3600 -> String.format(Locale.US, "%dh %02dm", (value / 3600).toInt(), ((value % 3600) / 60).toInt())
        value >= 60 -> String.format(Locale.US, "%dm %02ds", (value / 60).toInt(), (value % 60).toInt())
        else -> String.format(Locale.US, "%.1fs", value)
    }

    fun plural(count: Int, noun: String): String = "${String.format(Locale.US, "%,d", count)} $noun" + if (count == 1) "" else "s"

    private fun trim(value: Double): String =
        if (value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value).removeSuffix(".0")
}

enum class SpendStep { Hour, Day, Month }

/** One sample for the chart: [value] belongs to series [series] at time [at]. */
data class SpendSample(val series: Int, val at: Long, val value: Double)

/**
 * Spend over a window as bars: [values] holds, per bucket, one amount per series, so each bar
 * can be stacked by provider. Buckets are UTC, matching the window they sit in.
 */
data class SpendTimeline(val step: SpendStep, val starts: List<Long>, val values: List<List<Double>>) {
    val totals: List<Double> get() = values.map { it.sum() }
    val isEmpty: Boolean get() = totals.all { it <= 0.0 }
}

object SpendBuckets {
    private const val DAY = 86_400_000L

    /**
     * Today is split by hour, a week and a month by day. All time is by day for the first six
     * weeks of tracking and by month after that, always showing at least a week.
     */
    fun timeline(window: SpendWindow, now: Long, earliest: Long?, seriesCount: Int, samples: List<SpendSample>): SpendTimeline {
        val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        fun dayStart(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val (step, starts) = when (window) {
            SpendWindow.Today -> SpendStep.Hour to (0 until 24).map { window.since(now) + it * 3_600_000L }
            SpendWindow.Week -> SpendStep.Day to (0 until 7).map { window.since(now) + it * DAY }
            SpendWindow.Month -> SpendStep.Day to (0 until today.lengthOfMonth()).map { window.since(now) + it * DAY }
            SpendWindow.AllTime -> {
                val first = Instant.ofEpochMilli(minOf(earliest ?: now, now)).atZone(ZoneOffset.UTC).toLocalDate()
                if (java.time.temporal.ChronoUnit.DAYS.between(first, today) <= 45) {
                    val start = minOf(first, today.minusDays(6))
                    SpendStep.Day to generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map(::dayStart).toList()
                } else {
                    SpendStep.Month to generateSequence(first.withDayOfMonth(1)) { it.plusMonths(1) }
                        .takeWhile { !it.isAfter(today) }.map(::dayStart).toList()
                }
            }
        }
        val values = List(starts.size) { DoubleArray(seriesCount) }
        for (sample in samples) {
            if (sample.series !in 0 until seriesCount || sample.at < starts.first()) continue
            val index = starts.binarySearch(sample.at).let { if (it >= 0) it else -it - 2 }
            if (index in values.indices) values[index][sample.series] += sample.value
        }
        return SpendTimeline(step, starts, values.map { it.toList() })
    }

    /** A short axis or tooltip label for the bucket starting at [start]. */
    fun label(step: SpendStep, start: Long, long: Boolean = false): String {
        val pattern = when (step) {
            SpendStep.Hour -> "HH:mm"
            SpendStep.Day -> if (long) "EEE, d MMM" else "d MMM"
            SpendStep.Month -> if (long) "MMMM yyyy" else "MMM"
        }
        // Hours read best on the phone's clock; days and months are the UTC buckets themselves.
        val zone = if (step == SpendStep.Hour) ZoneId.systemDefault() else ZoneOffset.UTC
        return java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(Instant.ofEpochMilli(start).atZone(zone))
    }

    /** A round axis maximum at or above [value]: 1, 2 or 5 times a power of ten. */
    fun niceCeiling(value: Double): Double {
        if (value <= 0.0) return 1.0
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
        return listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= value }
    }
}
