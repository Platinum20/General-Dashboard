package com.platinum20.dashboard

import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.HourlyPoint
import com.platinum20.dashboard.weather.WeatherText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Le frasi del meteo della revisione 2026-11-22 (card C + pagina A). */
class WeatherTextTest {
    private val day = LocalDate.of(2026, 11, 22)
    private fun daily(code: Int, rain: Int, sum: Double = 0.0, wind: Double = 5.0) =
        DailyPoint(day, code, 19.4, 9.2, rain, sum, day.atTime(7, 2), day.atTime(16, 46), wind, 2.0)

    @Test fun massimaEMinimaPerEsteso() = assertEquals("max 19° · min 10°", WeatherText.range(19.2, 9.6))

    @Test
    fun domaniLaSera() {
        assertEquals("Domani sereno · max 19° · min 9°", WeatherText.tomorrowLine(daily(0, 10)))
        assertEquals("Domani coperto · pioggia 45% · max 19° · min 9°", WeatherText.tomorrowLine(daily(3, 45)))
        assertEquals("Domani pioggia 70% · max 19° · min 9°", WeatherText.tomorrowLine(daily(63, 70)))
    }

    @Test
    fun minimaDiStanotteFinoAlleSette() {
        val now = LocalDateTime.of(2026, 11, 22, 21, 10)
        val hours = (21..35).map { h ->
            HourlyPoint(day.atStartOfDay().plusHours(h.toLong()), if (h == 31) 6.0 else if (h == 33) 4.0 else 10.0, 0, 0, false)
        }
        // Alle 9 di domani (h 33) non conta più: stanotte la minima è quella delle 7 meno un'ora.
        assertEquals(6.0, WeatherText.tonightMin(hours, now)!!, 0.0)
    }

    @Test
    fun parolePerINumeri() {
        assertEquals("Aria umida", WeatherText.humidity(82))
        assertEquals("Nella norma", WeatherText.humidity(50))
        assertEquals("Brezza da nord-ovest", WeatherText.wind(12.0, 315))
        assertEquals("Calma", WeatherText.wind(1.0, 90))
        assertEquals("Vento forte da sud", WeatherText.wind(35.0, 180))
        assertEquals("Alto: meglio la crema", WeatherText.uv(6.0))
        assertEquals("9 ore e 44 minuti di luce", WeatherText.daylight(day.atTime(7, 2), day.atTime(16, 46)))
        assertNull(WeatherText.daylight(null, day.atTime(16, 46)))
    }

    @Test
    fun rigaDelDatoVecchio() {
        assertEquals("Dati di 2 ore fa", WeatherText.staleLine("2 ore fa"))
        assertEquals("Dati delle 9:05", WeatherText.staleLine("alle 9:05"))
        assertEquals("Dati di ieri 18:20", WeatherText.staleLine("ieri 18:20"))
        assertEquals("Dati del 20/10", WeatherText.staleLine("20/10"))
        val now = 10_000_000L
        assertFalse(WeatherText.isStale(now - 30 * 60_000L, now, failed = false))
        assertTrue(WeatherText.isStale(now - 61 * 60_000L, now, failed = false))
        assertTrue(WeatherText.isStale(now, now, failed = true))
    }

    @Test
    fun oreEFraseDiUnGiorno() {
        val all = (0..47).map { h -> HourlyPoint(day.atStartOfDay().plusHours(h.toLong()), h.toDouble(), 0, 0, true) }
        val tomorrow = WeatherText.dayHours(all, day.plusDays(1))
        assertEquals(listOf(6, 9, 12, 15, 18, 21), tomorrow.map { it.time.hour })
        assertTrue(tomorrow.all { it.time.toLocalDate() == day.plusDays(1) })
        assertEquals("Sereno", WeatherText.dayPhrase(daily(0, 0)))
        assertEquals("Pioggia · 4 mm · vento fino a 30 km/h", WeatherText.dayPhrase(daily(63, 80, sum = 4.2, wind = 30.0)))
    }
}
