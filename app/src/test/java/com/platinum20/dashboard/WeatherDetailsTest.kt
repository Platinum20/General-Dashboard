package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.weatherDetails
import com.platinum20.dashboard.weather.CurrentWeather
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.Forecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherDetailsTest {
    private fun forecast(uv: Double) = Forecast(
        place = "Roma", latitude = 0.0, longitude = 0.0, fetchedAt = 0L,
        current = CurrentWeather(LocalDateTime.now(), 20.0, 19.0, 60, 0, true, 10.0, 180, 0.0, 1013.0),
        hourly = emptyList(),
        daily = listOf(DailyPoint(LocalDate.now(), 0, 22.0, 12.0, 40, 1.2, null, null, 15.0, uv)),
    )

    @Test
    fun nientePressioneNePioggiaOggi() {
        val labels = weatherDetails(forecast(5.0)).map { it.first }
        assertFalse("Pressione" in labels)
        assertFalse("Pioggia oggi" in labels)
        assertEquals(listOf("Umidità", "Vento", "Indice UV", "Alba · Tramonto"), labels)
    }

    @Test
    fun uvSoloDaTreInSu() {
        assertFalse(weatherDetails(forecast(2.4)).any { it.first == "Indice UV" })
        assertTrue(weatherDetails(forecast(3.0)).any { it.first == "Indice UV" })
    }
}
