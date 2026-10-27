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
import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.timelineEntries
import com.platinum20.dashboard.ui.screens.pastDays
import com.platinum20.dashboard.ui.screens.undatedEntries
import com.platinum20.dashboard.ui.screens.expenseEntries
import com.platinum20.dashboard.ui.screens.noteMarks
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
        val marks = listOf(DayMark(0, "Oggi", 0.0), DayMark(5, "Domani", 1.0), DayMark(10, "Lun 26", 4.0))
        // Distanziate secondo il tempo: 1 giorno poi 3 giorni.
        assertEquals(listOf(0f, 0.25f, 1f), DayScroll.fractions(marks))
        assertEquals("Domani", DayScroll.nearest(marks, 0.3f)?.label)
        assertEquals("Lun 26", DayScroll.nearest(marks, 1f)?.label)
        assertEquals("Domani", DayScroll.current(marks, 7)?.label)
        assertEquals("Oggi", DayScroll.current(marks, 0)?.label)
        // Lo stesso giorno per tutti: in fila come una lista.
        assertEquals(listOf(0f, 0.5f, 1f), DayScroll.fractions(marks.map { it.copy(at = 3.0) }))
    }

    @Test fun lineaDelTempoAccorciaISaltiLunghi() {
        assertEquals(10.0, DayScroll.span(10.0), 0.0)
        assertEquals(14.0 + 2 * Math.sqrt(286.0), DayScroll.span(300.0), 1e-9)
        assertEquals(DayScroll.span(5.0), DayScroll.span(-5.0), 0.0)
    }

    @Test fun etichetteCorteSenzaAccavallarsi() {
        assertEquals(setOf(0, 2), DayScroll.visibleTicks(listOf(0f, 0.02f, 0.5f), 0.05f))
        // Quella accesa vince sulla vicina.
        assertEquals(setOf(1, 2), DayScroll.visibleTicks(listOf(0f, 0.02f, 0.5f), 0.05f, keep = 1))
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("Oggi", DayScroll.tick(today, today, null))
        assertEquals("Dom", DayScroll.tick(today.plusDays(1), today, today))
        assertEquals("30", DayScroll.tick(today.plusDays(4), today, today.plusDays(1)))
        assertEquals("3 nov", DayScroll.tick(LocalDate.of(2026, 11, 3), today, today.plusDays(4)))
        assertEquals("2027", DayScroll.tick(LocalDate.of(2027, 1, 5), today, LocalDate.of(2026, 12, 1)))
    }

    @Test fun prossimiGiorniComeLineaDelTempo() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 10, 26)
        fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        val groups = listOf(
            DueDay(null, true, listOf(DueRowFor.task(1, "Bolletta", ms(today.minusDays(2).atTime(9, 0)), today.minusDays(2)))),
            DueDay(today.plusDays(1), false, listOf(
                DueRowFor.task(2, "Dentista", ms(today.plusDays(1).atTime(9, 30)), today.plusDays(1)),
                DueRowFor.task(3, "Spesa", ms(today.plusDays(1).atTime(18, 0)), today.plusDays(1)),
            )),
            DueDay(today.plusDays(5), false, listOf(DueRowFor.task(4, "Treno", ms(today.plusDays(5).atTime(7, 0)), today.plusDays(5)))),
        )
        val entries = timelineEntries(groups, today, zone).map { it.first }
        assertEquals(listOf("Scaduti", "Domani", null, "Sab 31"), entries.map { it.dayLabel })
        assertEquals(listOf("Bolletta", "09:30  Dentista", "18:00  Spesa", "07:00  Treno"), entries.map { it.text })
        assertEquals(listOf(true, false, false, false), entries.map { it.late })
        // Fra domani e sabato ci sono tre giorni vuoti: la linea si allunga.
        assertEquals(listOf(0f, 0f, 0f, 12f), entries.map { it.gapDp })
    }

    @Test fun passatoSopraLaLinea() {
        val zone = ZoneId.of("Europe/Rome")
        val now = LocalDateTime.of(2026, 10, 26, 12, 0)
        val today = now.toLocalDate()
        fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        val past = pastDays(
            tasks = listOf(
                TaskItem(1, "Fatto ieri", done = true, dueAt = ms(today.minusDays(1).atTime(9, 0))),
                TaskItem(2, "Ancora da fare", dueAt = ms(today.minusDays(1).atTime(9, 0))),
                TaskItem(3, "Fatto troppo tempo fa", done = true, dueAt = ms(today.minusDays(90).atTime(9, 0))),
            ),
            parcels = listOf(Parcel(1, "Cuffie", deliveredOn = today.minusDays(3)), Parcel(2, "In viaggio")),
            events = listOf(
                CalendarEvent(5, "Cena", ms(today.minusDays(3).atTime(20, 0)), ms(today.minusDays(3).atTime(22, 0)), allDay = false, location = "", color = 0),
                CalendarEvent(6, "Riunione", ms(today.atTime(9, 0)), ms(today.atTime(10, 0)), allDay = false, location = "", color = 0),
                CalendarEvent(7, "Più tardi", ms(today.atTime(18, 0)), ms(today.atTime(19, 0)), allDay = false, location = "", color = 0),
            ),
            now = now,
            zone = zone,
        )
        assertEquals(listOf(today.minusDays(3), today.minusDays(1), today), past.map { it.day })
        assertEquals(true, past.all { it.past })
        val entries = timelineEntries(past, today, zone).map { it.first }
        assertEquals(listOf("Arrivato: Cuffie", "20:00  Cena", "Fatto ieri", "09:00  Riunione"), entries.map { it.text })
        assertEquals(listOf("Ven 23 ott", null, "Ieri", "Oggi"), entries.map { it.dayLabel })
    }

    @Test fun noteSullaLineaDelTempo() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 10, 26)
        fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val entries = undatedEntries(
            tasks = listOf(TaskItem(1, "Chiamare Luca", createdAt = at(today, 9)), TaskItem(2, "Comprare lampadine", createdAt = at(today.minusDays(5), 9))),
            notes = listOf(
                Note(3, "Idee regalo", createdAt = at(today, 11)),
                Note(4, "Password wifi", pinned = true, createdAt = at(today.minusDays(40), 9)),
            ),
            today = today,
            zone = zone,
        ).map { it.first }
        assertEquals(listOf("Password wifi", "Idee regalo", "Chiamare Luca", "Comprare lampadine"), entries.map { it.text })
        assertEquals(listOf("Fissate", "Oggi", null, "Mer 21 ott"), entries.map { it.dayLabel })
        // Fra oggi e mercoledì scorso quattro giorni vuoti.
        assertEquals(16f, entries[3].gapDp)
    }

    @Test fun speseSullaLineaDelTempo() {
        val today = LocalDate.of(2026, 10, 26)
        val entries = expenseEntries(
            listOf(
                com.platinum20.dashboard.notes.Expense(1, 1250, "Pizza", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today.minusDays(3)),
                com.platinum20.dashboard.notes.Expense(2, 4000, "Benzina", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today),
                com.platinum20.dashboard.notes.Expense(3, 300, "Caffè", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today),
            ),
            today,
        ).map { it.first }
        assertEquals(listOf("Oggi", null, "Ven 23 ott"), entries.map { it.dayLabel })
        assertEquals(true, entries[0].text.startsWith("Benzina · "))
        assertEquals(8f, entries[2].gapDp)
    }

    private object DueRowFor {
        fun task(id: Long, text: String, due: Long, day: LocalDate) =
            com.platinum20.dashboard.ui.screens.DueRow("task-$id", day, due, task = TaskItem(id, text, dueAt = due))
    }

    @Test fun tacchePerLeNote() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 10, 26)
        fun at(d: LocalDate) = d.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val notes = listOf(
            Note(1, "Fissata", updatedAt = 1, pinned = true),
            Note(2, "Oggi", createdAt = at(today)),
            Note(3, "Oggi bis", createdAt = at(today)),
            Note(4, "Settembre", createdAt = at(LocalDate.of(2026, 9, 12))),
        )
        val marks = noteMarks(notes, today, first = 1, zone = zone)
        assertEquals(listOf(2, 4), marks.map { it.index })
        assertEquals(listOf("Oggi", "12 set"), marks.map { it.tick })
    }
}
