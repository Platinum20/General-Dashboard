package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.label
import com.platinum20.dashboard.ui.screens.shortLabel
import com.platinum20.dashboard.ui.screens.NotesSection
import com.platinum20.dashboard.ui.screens.dueBadge
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.DayMark
import com.platinum20.dashboard.ui.screens.DayScroll
import com.platinum20.dashboard.ui.screens.notesChips
import com.platinum20.dashboard.ui.screens.noteTitleEnd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Titolo dell'editor (V16) e i tre tipi della pagina Note: con scadenza, senza scadenza, spese. */
class NotesUiLogicTest {
    @Test fun titoloEPrimaRiga() {
        assertEquals(16, noteTitleEnd("Libri da leggere\nIl nome della rosa"))
        assertEquals(5, noteTitleEnd("Spesa"))
        assertEquals(0, noteTitleEnd("\nsenza titolo"))
        assertEquals(0, noteTitleEnd(""))
    }

    private val oggi = LocalDate.of(2026, 10, 23)
    private val adesso = oggi.atTime(12, 0)
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test fun promemoriaDateEPacchiInUnaListaSolaPerGiorno() {
        val groups = dueDays(
            tasks = listOf(
                TaskItem(1, "Bollette", dueAt = ms(oggi.minusDays(1).atTime(9, 0))),
                TaskItem(2, "Palestra", dueAt = ms(oggi.atTime(18, 0))),
                TaskItem(3, "Senza data"),
                TaskItem(4, "Fatto", done = true, dueAt = ms(oggi.atTime(15, 0))),
            ),
            countdowns = listOf(
                Countdown(5, "Viaggio", oggi.plusDays(10)),
                Countdown(6, "Compleanno Sara", LocalDate.of(1990, 10, 24), yearly = true),
                Countdown(7, "Passato", oggi.minusDays(3)),
            ),
            parcels = listOf(
                Parcel(8, "Tazza", expected = oggi.plusDays(1)),
                Parcel(9, "Libro", expected = oggi.minusDays(1)),
                Parcel(10, "Senza data"),
                Parcel(11, "Arrivato", expected = oggi, deliveredOn = oggi),
            ),
            events = listOf(CalendarEvent(12, "Dentista", ms(oggi.plusDays(3).atTime(9, 0)), ms(oggi.plusDays(3).atTime(10, 0)), false, "", 0)),
            now = adesso,
            zone = zone,
        )
        assertEquals(
            listOf("Scaduti", "Oggi", "Domani", "Lunedì 26 ottobre", "Lunedì 2 novembre", "Senza data"),
            groups.map { it.label(oggi) },
        )
        assertEquals(listOf("Scaduti", "Oggi", "Domani", "Lun 26", "Lun 2", "Senza data"), groups.map { it.shortLabel(oggi) })
        assertEquals(
            listOf(listOf("parcel-9", "task-1"), listOf("task-2"), listOf("date-6", "parcel-8"), listOf("event-12-${ms(oggi.plusDays(3).atTime(9, 0))}"), listOf("date-5"), listOf("parcel-10")),
            groups.map { g -> g.rows.map { it.key } },
        )
    }

    @Test fun quantoMancaADestra() {
        assertEquals("Oggi" to null, dueBadge(oggi, oggi))
        assertEquals("Domani" to null, dueBadge(oggi.plusDays(1), oggi))
        assertEquals("5" to "giorni", dueBadge(oggi.plusDays(5), oggi))
        assertEquals("Scaduto" to null, dueBadge(oggi.minusDays(1), oggi, late = true))
    }

    @Test fun pilloleDeiTreTipi() {
        val data = NotesData(
            tasks = listOf(TaskItem(1, "Latte"), TaskItem(2, "Pane", done = true), TaskItem(3, "Dentista", dueAt = 1L)),
            notes = listOf(Note(4, "Idee")),
            habits = listOf(Habit(5, "Leggere", done = setOf(oggi))),
        )
        val chips = notesChips(data, oggi)
        assertEquals(listOf(NotesSection.DATED, NotesSection.UNDATED), chips.map { it.first })
        assertEquals(listOf("Prossimi giorni · 1", "Note · 2"), chips.map { it.second })
    }

    @Test fun senzaNienteNessunaPillola() = assertEquals(emptyList<Any>(), notesChips(NotesData(), oggi))

    @Test fun addebitiNelGiornoDelProssimoAddebito() {
        val affitto = com.platinum20.dashboard.notes.FixedExpense(
            1, 65000, "affitto", com.platinum20.dashboard.notes.ExpenseCategory.entries.first(), day = 5, from = java.time.YearMonth.of(2026, 1),
        )
        val groups = dueDays(emptyList(), emptyList(), emptyList(), emptyList(), listOf(affitto), now = adesso, zone = zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 5)), groups.map { it.day })
        assertEquals("charge-1", groups.single().rows.single().key)
    }

    @Test fun lineaDeiGiorni() {
        val marks = listOf(DayMark(0, "Oggi"), DayMark(5, "Domani"), DayMark(10, "Lun 26"))
        assertEquals(0.5f, DayScroll.fraction(5, 11))
        assertEquals("Domani", DayScroll.nearest(marks, 11, 0.55f)?.label)
        assertEquals("Lun 26", DayScroll.nearest(marks, 11, 1f)?.label)
        assertEquals("Domani", DayScroll.current(marks, 7)?.label)
        assertEquals("Oggi", DayScroll.current(marks, 0)?.label)
    }
}
