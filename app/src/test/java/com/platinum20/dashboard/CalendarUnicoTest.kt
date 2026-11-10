package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.dueDays
import com.platinum20.dashboard.ui.screens.CalendarFilter
import com.platinum20.dashboard.ui.screens.only
import com.platinum20.dashboard.ui.screens.pastDays
import com.platinum20.dashboard.ui.screens.timelineEntries
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.314: il calendario unico, con le note senza data dentro (Antonio). Dalla 1.443 senza spese. */
class CalendarUnicoTest {
    private val oggi = LocalDate.of(2026, 11, 3)
    private val adesso = oggi.atTime(12, 0)
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun calendario(
        tasks: List<TaskItem> = emptyList(),
        notes: List<Note> = emptyList(),
    ) = dueDays(tasks, emptyList(), emptyList(), now = adesso, zone = zone, withToday = true, notes = notes)

    @Test fun oggiCeSempreSenzaSoldi() {
        val groups = calendario()
        assertEquals(1, groups.size)
        assertEquals(oggi, groups.single().day)
        assertTrue(groups.single().rows.single().today)
        val entry = timelineEntries(groups, oggi, zone, months = true).single().first
        assertTrue(entry.todayHeader)
        assertNull(entry.trailing)
        assertEquals("Oggi", entry.dayLabel)
    }

    @Test fun senzaDataEInRitardoStannoAOggi() {
        val groups = calendario(
            tasks = listOf(
                TaskItem(1, "Latte"),
                TaskItem(2, "Bollette", dueAt = ms(oggi.minusDays(1).atTime(9, 0))),
                TaskItem(3, "Palestra", dueAt = ms(oggi.plusDays(1).atTime(18, 0))),
                TaskItem(4, "Fatto", done = true, createdAt = 1),
            ),
            notes = listOf(Note(5, "Vecchia", createdAt = 1), Note(6, "Nuova", createdAt = 2), Note(7, "Fissata", createdAt = 0, pinned = true)),
        )
        val today = groups.first().rows.map { it.key }
        // Il promemoria di ieri non è "in ritardo": è passato, come un evento (1.376).
        assertEquals(listOf("today", "task-1", "note-7", "note-6", "note-5"), today)
        // Domani resta nel suo giorno, niente "Scaduti" né "Senza data".
        assertEquals(listOf(oggi, oggi.plusDays(1)), groups.map { it.day })
    }

    @Test fun ilMeseDiOggiHaIlSegnoAncheSenzaPassato() {
        val groups = calendario(tasks = listOf(TaskItem(1, "Dicembre", dueAt = ms(LocalDate.of(2026, 12, 2).atTime(9, 0)))))
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        assertEquals("NOVEMBRE", entries[0].month)
        assertEquals("DICEMBRE", entries[1].month)
        // Fra un giorno e l'altro il divisore; non sulla riga di oggi.
        assertEquals(listOf(false, true), entries.take(2).map { it.divider })
    }

    @Test fun leCoseFatteStannoNelGiornoInCuiLeHaiSpuntate() {
        val oggiFatto = TaskItem(1, "Latte", done = true, createdAt = 1, doneAt = ms(oggi.atTime(9, 0)))
        val ieriFatto = TaskItem(2, "Bollette", done = true, createdAt = 1, doneAt = ms(oggi.minusDays(1).atTime(20, 0)))
        val groups = calendario(tasks = listOf(oggiFatto, ieriFatto))
        assertEquals(listOf("today", "task-1"), groups.first().rows.map { it.key })
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        assertTrue(entries[1].done)
        val past = pastDays(listOf(oggiFatto, ieriFatto), emptyList(), emptyList(), now = adesso, zone = zone, withToday = true)
        assertEquals(listOf(oggi.minusDays(1)), past.map { it.day })
        assertEquals("task-2", past.single().rows.single().key)
    }

    @Test fun eventoDiOggiFinitoStaSottoOggi() {
        // Il turno di stamattina, finito alle 12: resta sotto OGGI, non sparisce e non va nel passato.
        val turno = CalendarEvent(-5, "Turno", ms(oggi.atTime(7, 0)), ms(oggi.atTime(11, 0)), false, "", 0)
        val ieri = CalendarEvent(-6, "Turno", ms(oggi.minusDays(1).atTime(17, 0)), ms(oggi.minusDays(1).atTime(22, 0)), false, "", 0)
        val groups = dueDays(emptyList(), emptyList(), listOf(turno, ieri), now = adesso, zone = zone, withToday = true)
        assertEquals(listOf("today", "event--5-${turno.begin}"), groups.first().rows.map { it.key })
        val past = pastDays(emptyList(), emptyList(), listOf(turno, ieri), now = adesso, zone = zone, withToday = true)
        assertEquals(listOf(oggi.minusDays(1)), past.map { it.day })
    }

    @Test fun filtriNoteEventi() {
        val groups = calendario(
            tasks = listOf(TaskItem(1, "Latte", createdAt = 1), TaskItem(2, "Palestra", createdAt = 1, dueAt = ms(oggi.plusDays(2).atTime(18, 0)))),
            notes = listOf(Note(3, "Idea", createdAt = 1)),
        )
        // OGGI resta con ogni filtro.
        assertEquals(listOf("today", "task-1", "note-3"), groups.only(CalendarFilter.NOTES).flatMap { it.rows }.map { it.key })
        // Un promemoria con la data sta con gli eventi (1.361: sono la stessa cosa).
        assertEquals(listOf("today", "task-2"), groups.only(CalendarFilter.EVENTS).flatMap { it.rows }.map { it.key })
        assertEquals(groups, groups.only(null))
    }

    @Test fun pacchiERinnovoSimSonoEventi() {
        val groups = dueDays(
            emptyList(), listOf(Parcel(5, "Cuffie", expected = oggi.plusDays(1))), emptyList(),
            now = adesso, zone = zone, simRenewal = oggi.plusDays(3), withToday = true,
        )
        assertEquals(listOf("today", "parcel-5", "sim-renewal"), groups.only(CalendarFilter.EVENTS).flatMap { it.rows }.map { it.key })
        val entries = timelineEntries(groups, oggi, zone).map { it.first }
        assertEquals(BriefKind.EVENT, entries.first { it.key == "parcel-5" }.kind)
        assertEquals(BriefKind.EVENT, entries.first { it.key == "sim-renewal" }.kind)
    }

    @Test fun dopoOggiIGiorniSenzaPiuAvanti() {
        val groups = calendario(tasks = listOf(
            TaskItem(1, "Sabato", createdAt = 1, dueAt = ms(oggi.plusDays(4).atTime(9, 0))),
            TaskItem(2, "Domenica", createdAt = 1, dueAt = ms(oggi.plusDays(5).atTime(9, 0))),
            TaskItem(3, "Dicembre", createdAt = 1, dueAt = ms(LocalDate.of(2026, 12, 2).atTime(9, 0))),
        ))
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        // Niente "PIÙ AVANTI": il primo giorno dopo oggi ha il suo divisorio.
        assertEquals(listOf(false, false, false, false), entries.map { it.later })
        assertTrue(entries[1].divider)
        assertTrue(entries[0].todayHeader)
        assertEquals("DICEMBRE", entries[3].month)
    }

    @Test fun senzaIlCalendarioUnicoTuttoComePrima() {
        val groups = dueDays(listOf(TaskItem(1, "Latte")), emptyList(), emptyList(), now = adesso, zone = zone)
        assertTrue(groups.isEmpty())
    }
}
