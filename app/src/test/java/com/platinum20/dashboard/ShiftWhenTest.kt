package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.shiftTitle
import com.platinum20.dashboard.ui.screens.shiftWhen
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ShiftWhenTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun orariInUnaRiga() {
        val today = LocalDate.of(2026, 11, 10)
        val day = today.plusDays(1)
        val w = shiftWhen(at(day, 7), at(day, 12), today, zone)
        assertEquals("Domani · 07:00 – 12:00 · 5 h", w.main)
        assertEquals("mercoledì 11 novembre", w.sub)
    }

    @Test
    fun turnoDiNotte() {
        val today = LocalDate.of(2026, 11, 10)
        val w = shiftWhen(at(today, 22), at(today.plusDays(1), 3), today, zone)
        assertEquals("Oggi · 22:00 – Domani 03:00 · 5 h", w.main)
    }

    @Test
    fun senzaCreatorSoloTurno() {
        assertEquals("Turno", shiftTitle(""))
        assertEquals("Turno su Debora e Chiara", shiftTitle("Turno · Debora Martini, Chiara Rossi"))
    }
}
