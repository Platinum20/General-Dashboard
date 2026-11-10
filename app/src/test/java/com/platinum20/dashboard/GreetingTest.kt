package com.platinum20.dashboard

import com.platinum20.dashboard.today.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun dataComeTitoloCorta() {
        val day = java.time.LocalDate.of(2026, 11, 10)
        assertEquals("Martedì 10", Greeting.dateTitle(day))
        assertEquals("Buonanotte · novembre", Greeting.subtitle(2, day))
        // Anche il giorno più lungo sta in una riga di titolo.
        assertTrue(Greeting.dateTitle(java.time.LocalDate.of(2026, 9, 30)).length <= 12)
    }

}
