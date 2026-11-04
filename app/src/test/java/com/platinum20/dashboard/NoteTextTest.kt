package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteTextTest {
    @Test fun paroleSenzaPunteggiatura() {
        assertEquals(6, NoteText.words("Idee regalo\n- libro, per Luca —  ok"))
        assertEquals(0, NoteText.words("  \n — "))
    }

    @Test fun tempoDiLetturaSoloPerLeNoteLunghe() {
        assertNull(NoteText.stats(""))
        assertEquals("1 parola", NoteText.stats("Ciao"))
        assertEquals("12 parole", NoteText.stats((1..12).joinToString(" ") { "parola" }))
        assertEquals("1.240 parole · 7 min di lettura", NoteText.stats((1..1240).joinToString(" ") { "parola" }))
    }

    @Test fun badgeSoloPerLeListe() {
        assertNull(NoteText.badge("Spesa\nlatte e pane"))
        assertEquals("1/3", NoteText.badge("Spesa\n[x] latte\n[ ] pane\n[ ] uova"))
    }

    @Test fun orarioDellaNotaAperta() {
        val zone = java.time.ZoneId.of("Europe/Rome")
        fun at(d: Int, h: Int, m: Int) = java.time.ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant().toEpochMilli()
        val now = at(4, 12, 0)
        assertEquals("Scritta oggi, 10:05", NoteText.moment(at(4, 10, 5), at(4, 10, 5), now, zone))
        assertEquals("Scritta oggi, 10:05 · modificata 11:20", NoteText.moment(at(4, 10, 5), at(4, 11, 20), now, zone))
        assertEquals("Scritta ieri, 09:00 · modificata oggi, 08:30", NoteText.moment(at(3, 9, 0), at(4, 8, 30), now, zone))
        assertEquals("Scritta 1 nov, 18:45", NoteText.moment(at(1, 18, 45), at(1, 18, 45), now, zone))
    }
}
