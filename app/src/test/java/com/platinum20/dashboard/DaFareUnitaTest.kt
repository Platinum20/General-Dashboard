package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteLists
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.LooseItem
import com.platinum20.dashboard.ui.screens.LooseKind
import com.platinum20.dashboard.ui.screens.laterPageLine
import com.platinum20.dashboard.ui.screens.noteLine
import com.platinum20.dashboard.ui.screens.withoutLists
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

/** 1.616 (Antonio, 2026-11-24): Da fare e Appunti in una card sola, una nota con le spunte compare una volta. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DaFareUnitaTest {
    private fun noteItem(n: Note) = LooseItem("note-${n.id}", LooseKind.NOTE, n.title, row = DueRow("note-${n.id}", null, Long.MAX_VALUE, note = n))

    @Test fun laNotaConLeSpunteNonCompareDueVolte() {
        val gta = Note(1, "Gta\n[ ] salvare\n[ ] mappa")
        val idee = Note(2, "Idee regali\nlibro")
        val jottings = listOf(noteItem(gta), noteItem(idee))
        val lists = NoteLists.rows(listOf(gta, idee)).map { it.noteId }.toSet()
        assertEquals(listOf("note-2"), jottings.withoutLists(lists).map { it.key })
    }

    @Test fun laSecondaRigaDiceIlTipo() {
        assertEquals("Nota · Libro per mamma", noteLine("Libro per mamma"))
        assertEquals("Nota", noteLine(null))
        assertEquals("3 da fare su 5 · lista", NoteLists.line(NoteLists.of(Note(3, "S\n[ ] a\n[ ] b\n[ ] c\n[x] d\n[x] e"))!!))
    }

    @Test fun laBarrettaSiRiempie() {
        assertEquals(0.4f, NoteLists.progress(NoteLists.of(Note(3, "S\n[ ] a\n[ ] b\n[ ] c\n[x] d\n[x] e"))!!), 0.001f)
    }

    @Test fun sottotitoloDellaPagina() {
        assertEquals("3 da fare · 2 appunti · 12 fatte o archiviate", laterPageLine(3, 2, 12))
        assertEquals("1 appunto", laterPageLine(0, 1, 0))
        assertEquals("Niente da fare", laterPageLine(0, 0, 0))
    }
}
