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
import com.platinum20.dashboard.today.Upcoming
import com.platinum20.dashboard.ui.screens.color
import com.platinum20.dashboard.ui.screens.timelineEntries
import com.platinum20.dashboard.ui.screens.pastDays
import com.platinum20.dashboard.ui.screens.undatedEntries
import com.platinum20.dashboard.ui.screens.expenseEntries
import com.platinum20.dashboard.ui.screens.noteMarks
import com.platinum20.dashboard.ui.screens.notesChips
import com.platinum20.dashboard.ui.screens.noteTitleEnd
import com.platinum20.dashboard.ui.screens.nowRoom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(listOf(0f, 0f, 0f), entries.take(3).map { it.gapDp })
        assertEquals(Upcoming.gapDp(today.plusDays(1), today.plusDays(5)), entries[3].gapDp)
        assertEquals(true, entries[3].gapDp > 10f)
        // Tutto in ottobre: nessun segno del mese.
        assertEquals(true, entries.all { it.month == null })
    }

    @Test fun segnoDelMeseSullaLinea() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 10, 26)
        fun ms(d: LocalDate) = d.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val nov = LocalDate.of(2026, 11, 3)
        val groups = listOf(
            DueDay(today.minusDays(40), false, listOf(DueRowFor.task(1, "Settembre", ms(today.minusDays(40)), today.minusDays(40))), past = true),
            DueDay(today.minusDays(2), false, listOf(DueRowFor.task(2, "Ottobre", ms(today.minusDays(2)), today.minusDays(2))), past = true),
            DueDay(today.plusDays(1), false, listOf(DueRowFor.task(3, "Domani", ms(today.plusDays(1)), today.plusDays(1)))),
            DueDay(nov, false, listOf(
                DueRowFor.task(4, "Novembre", ms(nov), nov),
                DueRowFor.task(5, "Ancora", ms(nov), nov),
            )),
        )
        val entries = timelineEntries(groups, today, zone).map { it.first }
        assertEquals(listOf(null, "OTTOBRE", null, "NOVEMBRE", null), entries.map { it.month })
        // Il mese è già nel segno: "Mar 3", non "Mar 3 nov"; prima del primo segno il giorno resta intero.
        assertEquals(listOf("Mer 16 set", "Sab 24", "Domani", "Mar 3", null), entries.map { it.dayLabel })
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
        assertEquals(listOf(today.minusDays(90), today.minusDays(3), today.minusDays(1), today), past.map { it.day })
        assertEquals(true, past.all { it.past })
        val entries = timelineEntries(past, today, zone).map { it.first }
        // I promemoria fatti restano nel passato anche se vecchi: non c'è più un limite.
        assertEquals(listOf("Fatto troppo tempo fa", "Arrivato: Cuffie", "20:00  Cena", "Fatto ieri", "09:00  Riunione"), entries.map { it.text })
        // Sotto il segno OTTOBRE il giorno è corto.
        assertEquals(listOf("Mar 28 lug", "Ven 23", null, "Ieri", "Oggi"), entries.map { it.dayLabel })
        assertEquals(listOf(null, "OTTOBRE", null, null, null), entries.map { it.month })
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
        // Il passato sopra, le più nuove in fondo, poi le fissate (Antonio: stesso verso di Prossimi giorni).
        assertEquals(listOf("Comprare lampadine", "Chiamare Luca", "Idee regalo", "Password wifi"), entries.map { it.text })
        assertEquals(listOf("Mer 21", "Oggi", null, "Fissate"), entries.map { it.dayLabel })
        // Fra mercoledì scorso e oggi quattro giorni vuoti.
        assertEquals(16f, entries[1].gapDp, 0.01f)
        // "OGGI" prima delle righe di oggi.
        assertEquals(1, com.platinum20.dashboard.ui.screens.nowIndex(entries))
        // Niente di oggi: "OGGI" in fondo.
        assertEquals(1, com.platinum20.dashboard.ui.screens.nowIndex(entries.take(1)))
    }

    @Test fun fattiSullaLineaDelleNote() {
        val zone = ZoneId.of("Europe/Rome")
        val today = LocalDate.of(2026, 10, 26)
        fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val entries = undatedEntries(
            tasks = listOf(TaskItem(1, "Prova", createdAt = at(today, 9))),
            notes = emptyList(),
            today = today,
            zone = zone,
            // Scritto tre giorni fa, spuntato oggi alle 10: sta a oggi, in fondo.
            done = listOf(TaskItem(2, "Sessi", done = true, createdAt = at(today.minusDays(3), 9), doneAt = at(today, 10))),
        ).map { it.first }
        assertEquals(listOf("Prova", "Sessi"), entries.map { it.text })
        assertEquals(listOf(false, true), entries.map { it.done })
    }

    @Test fun meseTrascinando() {
        val now = java.time.YearMonth.of(2026, 10)
        val swipe = com.platinum20.dashboard.ui.screens.MonthSwipe
        assertEquals(java.time.YearMonth.of(2026, 9), swipe.target(now, 60f, 56f, now))
        assertEquals(null, swipe.target(now, -60f, 56f, now))
        assertEquals(now, swipe.target(java.time.YearMonth.of(2026, 9), -60f, 56f, now))
        assertEquals(null, swipe.target(now, 20f, 56f, now))
    }

    @Test fun meseSegueIlDito() {
        val swipe = com.platinum20.dashboard.ui.screens.MonthSwipe
        // Segue il dito quasi 1:1 all'inizio, poi l'elastico frena.
        assertEquals(10f, swipe.follow(10f, 56f), 0.5f)
        assertTrue(swipe.follow(400f, 56f) < 56f * 2.2f)
        assertEquals(-swipe.follow(80f, 56f), swipe.follow(-80f, 56f), 0.001f)
        // Verso un mese che non c'è ancora resiste di più.
        assertTrue(kotlin.math.abs(swipe.follow(-80f, 56f, blocked = true)) < kotlin.math.abs(swipe.follow(-80f, 56f)))
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
        // Le più recenti in fondo.
        assertEquals(listOf("Ven 23", "Oggi", null), entries.map { it.dayLabel })
        // L'importo per primo, il nome dopo.
        assertEquals(com.platinum20.dashboard.notes.Expenses.format(4000) + "  Benzina", entries[1].text)
        val other = com.platinum20.dashboard.notes.ExpenseCategory.OTHER
        assertEquals("Spesa", com.platinum20.dashboard.notes.Expenses.displayName(com.platinum20.dashboard.notes.Expense(9, 303, "fronte di", other)))
        assertEquals("Spesa", com.platinum20.dashboard.notes.Expenses.displayName(com.platinum20.dashboard.notes.Expense(9, 303, " ", other)))
        assertEquals("Antico Caffe' Roma", com.platinum20.dashboard.notes.Expenses.displayName(com.platinum20.dashboard.notes.Expense(9, 300, "Antico Caffe' Roma", other)))
        assertEquals(10f, entries[1].gapDp, 0.01f)
        // Nella card in Oggi solo le ultime, sempre in fondo la più recente.
        assertEquals(listOf("Benzina", "Caffè"), expenseEntries(listOf(
            com.platinum20.dashboard.notes.Expense(1, 1250, "Pizza", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today.minusDays(3)),
            com.platinum20.dashboard.notes.Expense(2, 4000, "Benzina", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today),
            com.platinum20.dashboard.notes.Expense(3, 300, "Caffè", com.platinum20.dashboard.notes.ExpenseCategory.values().first(), today),
        ), today, limit = 2).map { it.second.text })
        // Icona e colore della categoria.
        assertEquals(com.platinum20.dashboard.notes.ExpenseCategory.values().first().color(), entries[0].color)
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

    /** Spese si chiudeva all'apertura: prima della misura il conto andava in overflow (1.231). */
    @Test fun spazioSottoOggiSenzaOverflow() {
        assertEquals(0, nowRoom(viewport = 0, bottomPadding = 300, below = null))
        assertEquals(0, nowRoom(viewport = 2000, bottomPadding = 300, below = null))
        assertEquals(1200, nowRoom(viewport = 2000, bottomPadding = 300, below = 500))
        assertEquals(0, nowRoom(viewport = 2000, bottomPadding = 300, below = 5000))
        assertEquals(0, nowRoom(viewport = 0, bottomPadding = 300, below = 100))
    }
}
