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
}
