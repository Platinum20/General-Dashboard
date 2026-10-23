package com.platinum20.dashboard

import com.platinum20.dashboard.today.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GreetingTest {
    @Test
    fun salutoPerOra() {
        assertEquals("Buonanotte", Greeting.forHour(0))
        assertEquals("Buonanotte", Greeting.forHour(4))
        assertEquals("Buongiorno", Greeting.forHour(5))
        assertEquals("Buongiorno", Greeting.forHour(12))
        assertEquals("Buon pomeriggio", Greeting.forHour(13))
        assertEquals("Buonasera", Greeting.forHour(18))
        assertEquals("Buonasera", Greeting.forHour(23))
    }

    @Test
    fun rigaMeteoNonVaACapoDentroLeParti() {
        val line = Greeting.weatherLine("Prevalentemente sereno", "22° / 10°", "pioggia 48%")
        assertEquals("Prevalentemente sereno · 22° / 10° · pioggia 48%", line)
        // Solo i separatori " · " possono andare a capo.
        assertEquals(2, line.split(" · ").size - 1)
        assertFalse(line.replace(" · ", "").contains(' '))
    }

    @Test
    fun partiVuoteSaltate() {
        assertEquals("Sereno", Greeting.weatherLine("Sereno", null, ""))
    }

    @Test
    fun conLaCardDelGiornoSoloIlCielo() {
        assertEquals("Sereno", Greeting.adessoWeather("Sereno", "22° / 10°", "pioggia 48%", dayCardShown = true))
        assertEquals("Sereno · 22° / 10° · pioggia 48%", Greeting.adessoWeather("Sereno", "22° / 10°", "pioggia 48%", dayCardShown = false))
    }
}
