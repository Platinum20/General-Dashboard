package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Expenses
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

/** 1.314: il calendario unico, con note senza data e spese dentro (Antonio). */
class CalendarUnicoTest {
    private val oggi = LocalDate.of(2026, 11, 3)
    private val adesso = oggi.atTime(12, 0)
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun calendario(
        tasks: List<TaskItem> = emptyList(),
        notes: List<Note> = emptyList(),
        expenses: List<Expense> = emptyList(),
    ) = dueDays(tasks, emptyList(), emptyList(), now = adesso, zone = zone, withToday = true, notes = notes, expenses = expenses)

    @Test fun oggiCeSempreConLaSpesaAZero() {
        val groups = calendario()
        assertEquals(1, groups.size)
        assertEquals(oggi, groups.single().day)
        assertEquals(0L, groups.single().rows.single().spend)
        val entry = timelineEntries(groups, oggi, zone, months = true).single().first
        assertEquals("Spesa di oggi", entry.text)
        assertEquals(Expenses.format(0), entry.trailing)
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
        // Il promemoria di ieri non è "in ritardo": è passato, come un evento (1.372).
        assertEquals(listOf("spend-today", "task-1", "note-7", "note-6", "note-5"), today)
        // Domani resta nel suo giorno, niente "Scaduti" né "Senza data".
        assertEquals(listOf(oggi, oggi.plusDays(1)), groups.map { it.day })
    }

    @Test fun speseNelLoroGiornoETotaleDelMeseSulSegno() {
        val pizza = Expense(1, 1250, "pizza", ExpenseCategory.EATING_OUT, oggi)
        val bar = Expense(2, 300, "bar", ExpenseCategory.EATING_OUT, oggi.minusDays(1))
        val ottobre = Expense(3, 2000, "benzina", ExpenseCategory.TRANSPORT, LocalDate.of(2026, 10, 20))
        val all = listOf(pizza, bar, ottobre)
        val groups = calendario(expenses = all)
        assertEquals(1250L, groups.first().rows.first().spend)
        assertEquals("expense-1", groups.first().rows[1].key)
        // Le spese passate stanno nel passato, nel loro giorno; quelle di oggi no.
        val past = pastDays(emptyList(), emptyList(), emptyList(), now = adesso, zone = zone, expenses = all)
        assertEquals(listOf(LocalDate.of(2026, 10, 20), oggi.minusDays(1)), past.map { it.day })
        val entries = timelineEntries(past + groups, oggi, zone, months = true, expenses = all).map { it.first }
        // Ogni mese ha il suo segno, anche il primo, con quanto hai speso; oggi il mese di oggi.
        assertEquals("OTTOBRE", entries[0].month)
        assertEquals(Expenses.format(2000), entries[0].monthSpend)
        assertEquals(YearMonth.of(2026, 10), entries[0].monthOf)
        assertEquals("NOVEMBRE", entries[1].month)
        assertEquals(Expenses.format(1550), entries[1].monthSpend)
        assertNull(entries[2].month)
    }

    @Test fun ilMeseDiOggiHaIlSegnoAncheSenzaPassato() {
        val groups = calendario(tasks = listOf(TaskItem(1, "Dicembre", dueAt = ms(LocalDate.of(2026, 12, 2).atTime(9, 0)))))
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        assertEquals("NOVEMBRE", entries[0].month)
        // Il mese di oggi mostra la spesa anche a zero; un mese futuro senza spese no.
        assertEquals(Expenses.format(0), entries[0].monthSpend)
        assertEquals("DICEMBRE", entries[1].month)
        assertNull(entries[1].monthSpend)
    }

    @Test fun leCoseFatteStannoNelGiornoInCuiLeHaiSpuntate() {
        val oggiFatto = TaskItem(1, "Latte", done = true, createdAt = 1, doneAt = ms(oggi.atTime(9, 0)))
        val ieriFatto = TaskItem(2, "Bollette", done = true, createdAt = 1, doneAt = ms(oggi.minusDays(1).atTime(20, 0)))
        val groups = calendario(tasks = listOf(oggiFatto, ieriFatto))
        assertEquals(listOf("spend-today", "task-1"), groups.first().rows.map { it.key })
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        assertTrue(entries[1].done)
        val past = pastDays(listOf(oggiFatto, ieriFatto), emptyList(), emptyList(), now = adesso, zone = zone, withToday = true)
        assertEquals(listOf(oggi.minusDays(1)), past.map { it.day })
        assertEquals("task-2", past.single().rows.single().key)
    }

    @Test fun filtriSpeseNoteEventi() {
        val groups = calendario(
            tasks = listOf(TaskItem(1, "Latte", createdAt = 1), TaskItem(2, "Palestra", createdAt = 1, dueAt = ms(oggi.plusDays(2).atTime(18, 0)))),
            notes = listOf(Note(3, "Idea", createdAt = 1)),
            expenses = listOf(Expense(4, 500, "bar", ExpenseCategory.EATING_OUT, oggi)),
        )
        assertEquals(listOf("spend-today", "expense-4"), groups.only(CalendarFilter.EXPENSES).flatMap { it.rows }.map { it.key })
        assertEquals(listOf("task-1", "note-3"), groups.only(CalendarFilter.NOTES).flatMap { it.rows }.map { it.key })
        // Un promemoria con la data sta con gli eventi (1.361: sono la stessa cosa).
        assertEquals(listOf("task-2"), groups.only(CalendarFilter.EVENTS).flatMap { it.rows }.map { it.key })
        assertEquals(groups, groups.only(null))
    }

    @Test fun pacchiERinnovoSimSonoEventi() {
        val groups = dueDays(
            emptyList(), listOf(Parcel(5, "Cuffie", expected = oggi.plusDays(1))), emptyList(),
            now = adesso, zone = zone, simRenewal = oggi.plusDays(3), withToday = true,
        )
        assertEquals(listOf("parcel-5", "sim-renewal"), groups.only(CalendarFilter.EVENTS).flatMap { it.rows }.map { it.key })
        assertTrue(groups.only(CalendarFilter.EXPENSES).flatMap { it.rows }.none { it.simRenewal })
        val entries = timelineEntries(groups, oggi, zone).map { it.first }
        assertEquals(BriefKind.EVENT, entries.first { it.key == "parcel-5" }.kind)
        assertEquals(BriefKind.EVENT, entries.first { it.key == "sim-renewal" }.kind)
    }

    @Test fun piuAvantiSoloSullaPrimaCosaDopoOggiNelMese() {
        val groups = calendario(tasks = listOf(
            TaskItem(1, "Sabato", createdAt = 1, dueAt = ms(oggi.plusDays(4).atTime(9, 0))),
            TaskItem(2, "Domenica", createdAt = 1, dueAt = ms(oggi.plusDays(5).atTime(9, 0))),
            TaskItem(3, "Dicembre", createdAt = 1, dueAt = ms(LocalDate.of(2026, 12, 2).atTime(9, 0))),
        ))
        val entries = timelineEntries(groups, oggi, zone, months = true).map { it.first }
        assertEquals(listOf(false, true, false, false), entries.map { it.later })
        assertTrue(entries[0].todayHeader)
        assertEquals("DICEMBRE", entries[3].month)
    }

    @Test fun senzaIlCalendarioUnicoTuttoComePrima() {
        val groups = dueDays(listOf(TaskItem(1, "Latte")), emptyList(), emptyList(), now = adesso, zone = zone)
        assertTrue(groups.isEmpty())
    }
}
