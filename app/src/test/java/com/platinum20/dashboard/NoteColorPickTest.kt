package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.ui.screens.NOTE_COLORS
import com.platinum20.dashboard.ui.screens.colorPick
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il fumetto Colore (2026-11-24): sette colori in ordine, niente tasto per togliere. */
class NoteColorPickTest {
    @Test
    fun sevenColorsInChromaticOrder() {
        assertEquals(listOf("Rosso", "Arancio", "Giallo", "Verde", "Blu", "Viola", "Rosa"), NOTE_COLORS.map { it.second })
        assertEquals(NoteFormat.COLORS, NOTE_COLORS.map { it.first }.joinToString(""))
    }

    @Test
    fun tappingTheLitColorRemovesIt() {
        assertEquals(null, colorPick('r', 'r'))
        assertEquals('g', colorPick('r', 'g'))
        assertEquals('G', colorPick(null, 'G'))
        assertEquals(null, colorPick('F', 'F'))
    }

    @Test
    fun newColorsAreRealMarks() {
        val e = NoteFormat.color("ciao mondo", 0, 4, 'g')
        assertEquals("{g:ciao} mondo", e.text)
        assertEquals('g', NoteFormat.colorHere(e.text, e.start, e.end))
        assertEquals("ciao mondo", NoteFormat.color(e.text, e.start, e.end, null).text)
    }
}
