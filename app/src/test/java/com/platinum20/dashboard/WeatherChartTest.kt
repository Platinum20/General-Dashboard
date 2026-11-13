package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.WeatherChart
import com.platinum20.dashboard.weather.HourlyPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WeatherChartTest {
    private fun hour(h: Int, t: Double, rain: Int = 0) =
        HourlyPoint(LocalDateTime.of(2026, 11, 13, h % 24, 0), t, 3, rain, true)

    @Test
    fun levelsGoFromColdestToWarmest() {
        assertEquals(listOf(0f, 0.5f, 1f), WeatherChart.levels(listOf(10.0, 15.0, 20.0)))
    }

    @Test
    fun flatDayStaysInTheMiddle() {
        assertEquals(listOf(0.5f, 0.5f), WeatherChart.levels(listOf(12.0, 12.2)))
        assertEquals(listOf(0.5f, 0f, 1f), WeatherChart.levels(listOf(Double.NaN, 10.0, 20.0)))
    }

    @Test
    fun smallRainChancesDrawNoBar() {
        assertEquals(0f, WeatherChart.rain(10))
        assertEquals(0.6f, WeatherChart.rain(60))
    }

    @Test
    fun stripAndLabels() {
        val hours = (15..26).map { hour(it, 20.0) }
        assertEquals(6, WeatherChart.strip(hours).size)
        val labels = WeatherChart.labels(hours)
        assertEquals("Ora", labels.first().second)
        assertEquals(3 to "18", labels[1])
        assertEquals(4, labels.size)
    }
}

class SimDailyUseTest {
    @Test
    fun useIsTheDropBetweenReadingsAndRenewalCountsZero() {
        val h = listOf(
            com.platinum20.dashboard.sim.SimReading(3, 80.0),
            com.platinum20.dashboard.sim.SimReading(1, 92.0),
            com.platinum20.dashboard.sim.SimReading(2, 90.5),
            com.platinum20.dashboard.sim.SimReading(4, 100.0),
        )
        assertEquals(listOf(1.5, 10.5, 0.0), com.platinum20.dashboard.sim.SimUsage.dailyUse(h))
    }
}
