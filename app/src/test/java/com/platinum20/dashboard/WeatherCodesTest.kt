package com.platinum20.dashboard

import com.platinum20.dashboard.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test
    fun prevalentementeSerenoColSoleOLaLuna() {
        assertEquals(WeatherCodes.Kind.CLEAR, WeatherCodes.kind(0))
        assertEquals(WeatherCodes.Kind.CLEAR, WeatherCodes.kind(1))
        assertEquals(WeatherCodes.Kind.PARTLY, WeatherCodes.kind(2))
        assertEquals(WeatherCodes.Kind.CLOUDY, WeatherCodes.kind(3))
        assertEquals("🌙", WeatherCodes.emoji(1, isDay = false))
        assertEquals("☀️", WeatherCodes.emoji(1, isDay = true))
    }
}
