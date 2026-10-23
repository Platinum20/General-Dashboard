package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureSave
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Abitudini e date scritte a parole nel campo delle Note. */
class CaptureSaveTest {
    private val now = LocalDateTime.of(2026, 10, 23, 10, 0)

    @Test fun abitudineDiOgniGiorno() {
        val h = CaptureSave.habitOf("ogni giorno leggere 20 pagine", now)!!
        assertEquals("Leggere 20 pagine", h.title)
        assertEquals(0, h.perWeek)
        assertNull(h.remindAt)
    }

    @Test fun abitudineSettimanaleConAvviso() {
        val h = CaptureSave.habitOf("palestra 3 volte a settimana alle 19", now)!!
        assertEquals("Palestra", h.title)
        assertEquals(3, h.perWeek)
        assertEquals(LocalTime.of(19, 0), h.remindAt)
    }

    @Test fun compleannoOgniAnno() {
        val d = CaptureSave.dateOf("compleanno di Marta 12 marzo", now)!!
        assertEquals("Compleanno di Marta", d.title)
        assertEquals(LocalDate.of(2027, 3, 12), d.date)
        assertTrue(d.yearly)
    }

    @Test fun viaggioUnaVolta() {
        val d = CaptureSave.dateOf("viaggio a Lisbona 5 dicembre", now)!!
        assertEquals("Viaggio a Lisbona", d.title)
        assertFalse(d.yearly)
    }

    @Test fun senzaGiornoNienteData() = assertNull(CaptureSave.dateOf("compleanno di Marta", now))
}
