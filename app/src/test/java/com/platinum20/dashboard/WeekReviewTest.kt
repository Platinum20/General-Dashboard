package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.FixedExpense
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.ComingKind
import com.platinum20.dashboard.today.HabitWeek
import com.platinum20.dashboard.today.WeekReview
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekReviewTest {
    private val zone = ZoneId.of("Europe/Rome")
    // Lunedì 19 ottobre 2026 - domenica 25.
    private val monday = LocalDate.of(2026, 10, 19)

    private fun doneOn(id: Long, day: LocalDate) =
        TaskItem(id, "Cosa $id", done = true, doneAt = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli())

    private fun spesa(id: Long, cents: Long, cat: ExpenseCategory, day: LocalDate, fixedId: Long? = null) =
        Expense(id, cents, "x", cat, day, fixedId)

    @Test
    fun finestraDellaCard() {
        assertNull(WeekReview.weekToReview(LocalDateTime.of(2026, 10, 25, 16, 59)))
        assertEquals(monday, WeekReview.weekToReview(LocalDateTime.of(2026, 10, 25, 17, 0)))
        assertEquals(monday, WeekReview.weekToReview(LocalDateTime.of(2026, 10, 26, 11, 59)))
        assertNull(WeekReview.weekToReview(LocalDateTime.of(2026, 10, 26, 12, 0)))
        assertNull(WeekReview.weekToReview(LocalDateTime.of(2026, 10, 22, 20, 0)))
    }

    @Test
    fun settimanaCompleta() {
        val tasks = listOf(
            doneOn(1, monday), doneOn(2, monday.plusDays(2)), doneOn(3, monday.plusDays(3)),
            doneOn(4, monday.plusDays(6)), doneOn(5, monday.minusDays(1)), // della settimana prima
            TaskItem(6, "Aperto"),
            TaskItem(7, "Vecchio chiuso senza data", done = true),
        )
        val habits = listOf(
            Habit(1, "Acqua", done = (0L..4L).map { monday.plusDays(it) }.toSet()),
            Habit(2, "Palestra", done = setOf(monday, monday.plusDays(2), monday.plusDays(4)), perWeek = 3),
        )
        val expenses = buildList {
            // 4 settimane prima: 40 € a settimana di spesa, 20 di fuori.
            (1..4).forEach { w ->
                add(spesa(10L + w, 4000, ExpenseCategory.FOOD, monday.minusWeeks(w.toLong())))
                add(spesa(20L + w, 2000, ExpenseCategory.EATING_OUT, monday.minusWeeks(w.toLong()).plusDays(3)))
            }
            add(spesa(30, 4000, ExpenseCategory.FOOD, monday.plusDays(1)))
            add(spesa(31, 2600, ExpenseCategory.EATING_OUT, monday.plusDays(5)))
            // La fissa non conta nel confronto.
            add(spesa(32, 65000, ExpenseCategory.HOME, monday.plusDays(4), fixedId = 9))
        }
        val parcels = listOf(Parcel(1, "Cuffie", deliveredOn = monday.plusDays(3)), Parcel(2, "Libro"))
        val countdowns = listOf(
            Countdown(1, "Compleanno Marco", LocalDate.of(1990, 10, 28), yearly = true),
            Countdown(2, "Viaggio", LocalDate.of(2026, 12, 1)),
        )
        val fixed = listOf(FixedExpense(9, 1299, "Netflix", ExpenseCategory.FUN, 30, YearMonth.of(2026, 1), YearMonth.of(2026, 9)))

        val r = WeekReview.build(monday, tasks, habits, expenses, fixed, parcels, countdowns, zone)

        assertEquals(4, r.tasksDone)
        assertEquals(listOf(HabitWeek("Acqua", 5, 7), HabitWeek("Palestra", 3, 3)), r.habits)
        assertEquals(6600L, r.spend!!.cents)
        assertEquals(6000L, r.spend!!.averageCents)
        assertEquals(10, r.spend!!.changePercent)
        assertEquals(ExpenseCategory.EATING_OUT to 30, r.spend!!.biggestRise)
        assertEquals(1, r.parcelsArrived)
        assertEquals(listOf(ComingKind.DATE, ComingKind.MONEY), r.coming.map { it.kind })
        assertEquals("Mer 28 · Compleanno Marco (36 anni)", r.coming[0].text)
        assertTrue(r.coming[1].text.startsWith("Ven 30 · Netflix"))
        assertEquals("Settimana buona: 4 cose fatte, palestra 3 su 3, spese +10%", r.headline)
    }

    @Test
    fun settimanaVuota() {
        val r = WeekReview.build(monday, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zone)
        assertTrue(r.isEmpty)
        assertEquals("Settimana tranquilla: niente segnato in Canvas", r.headline)
    }

    @Test
    fun speseSenzaStoriaNonHannoConfronto() {
        val spend = WeekReview.build(
            monday, emptyList(), emptyList(), listOf(spesa(1, 1500, ExpenseCategory.FOOD, monday)), emptyList(), emptyList(), emptyList(), zone,
        ).spend!!
        assertNull(spend.changePercent)
        assertNull(spend.biggestRise)
    }

    @Test
    fun frasePerSettimanaNonBuona() {
        val h = listOf(HabitWeek("Acqua", 2, 7), HabitWeek("Leggere", 1, 7))
        assertEquals("La tua settimana: 2 cose fatte", WeekReview.headline(2, h, null))
    }
}
