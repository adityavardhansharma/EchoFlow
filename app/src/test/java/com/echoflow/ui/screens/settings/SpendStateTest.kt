package com.echoflow.ui.screens.settings

import com.echoflow.data.usage.OpenRouterKeySpend
import com.echoflow.data.usage.UsageKeys
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageTotal
import java.time.ZonedDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendStateTest {
    // Friday 25 Sep 2026, 14:30 UTC.
    private val now = ZonedDateTime.of(2026, 9, 25, 14, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun utc(month: Int, day: Int, hour: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `windows follow openrouter's utc boundaries`() {
        assertEquals(utc(9, 25), SpendWindow.Today.since(now))
        assertEquals(utc(9, 21), SpendWindow.Week.since(now)) // Monday
        assertEquals(utc(9, 1), SpendWindow.Month.since(now))
        assertEquals(0L, SpendWindow.AllTime.since(now))
    }

    @Test fun `home lists only saved keys and totals dollars only`() {
        val keys = mapOf(UsageProvider.OpenRouter to "or", UsageProvider.Claude to "ant", UsageProvider.Firecrawl to "fc", UsageProvider.Exa to "")
        val totals = listOf(
            UsageTotal("Claude", UsageKeys.hash("ant"), 3, 0.5, null, 100, 20),
            UsageTotal("Claude", UsageKeys.hash("old"), 9, 9.0, null, 1, 1),
            UsageTotal("Firecrawl", UsageKeys.hash("fc"), 4, null, 7.0, null, null),
        )
        val reported = OpenRouterKeySpend(UsageKeys.hash("or"), 40.0, 1.0, 3.0, 12.0, null, null, 0L)
        val home = SpendMath.home(keys, totals, reported, SpendWindow.Month)
        assertEquals(listOf(UsageProvider.OpenRouter, UsageProvider.Claude), home.dollars.map { it.provider })
        assertEquals(listOf(UsageProvider.Firecrawl), home.ownUnits.map { it.provider })
        assertEquals(12.5, home.totalUsd, 1e-9)
        assertTrue(home.dollars.first().providerReported)
    }

    @Test fun `timeline buckets stack by series`() {
        val week = SpendBuckets.timeline(
            SpendWindow.Week, now, earliest = null, seriesCount = 2,
            samples = listOf(
                SpendSample(0, utc(9, 21, 3), 1.0),
                SpendSample(1, utc(9, 21, 20), 0.5),
                SpendSample(0, utc(9, 25, 9), 2.0),
                SpendSample(0, utc(9, 1), 99.0), // before the window
            ),
        )
        assertEquals(SpendStep.Day, week.step)
        assertEquals(7, week.starts.size)
        assertEquals(listOf(1.0, 0.5), week.values[0])
        assertEquals(2.0, week.totals[4], 0.0)
        assertEquals(3.5, week.totals.sum(), 0.0)

        assertEquals(24, SpendBuckets.timeline(SpendWindow.Today, now, null, 1, emptyList()).starts.size)
        assertEquals(30, SpendBuckets.timeline(SpendWindow.Month, now, null, 1, emptyList()).starts.size)
        val young = SpendBuckets.timeline(SpendWindow.AllTime, now, earliest = utc(9, 24), seriesCount = 1, samples = emptyList())
        assertEquals(SpendStep.Day, young.step)
        assertEquals(7, young.starts.size) // always at least a week
        val old = SpendBuckets.timeline(SpendWindow.AllTime, now, earliest = utc(3, 10), seriesCount = 1, samples = emptyList())
        assertEquals(SpendStep.Month, old.step)
        assertEquals(7, old.starts.size) // Mar..Sep
    }

    @Test fun `formatting keeps small charges visible`() {
        assertEquals("$0.0031", SpendFormat.usd(0.0031))
        assertEquals("$12.40", SpendFormat.usd(12.4))
        assertEquals("$1" to ".50", SpendFormat.usdParts(1.5))
        assertEquals("1.2M", SpendFormat.compact(1_234_567))
        assertEquals(1.0, SpendBuckets.niceCeiling(0.7), 0.0)
        assertEquals(0.5, SpendBuckets.niceCeiling(0.36), 1e-12)
    }
}
