package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.ui.screens.habitView
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HabitViewTest {
    // Mercoledì 11 novembre 2026.
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)
    private val today = now.toLocalDate()

    @Test
    fun serieGrandeESoloLaSettimana() {
        val h = Habit(1, "Acqua", done = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(3)))
        val v = habitView(h, now)
        assertEquals("4 giorni di fila", v.streak)
        assertEquals(7, v.week.size)
        assertEquals(LocalDate.of(2026, 11, 9), v.week.first().date)
        assertEquals(listOf(true, true, true, false, false, false, false), v.week.map { it.done })
        assertEquals(listOf(false, false, true, false, false, false, false), v.week.map { it.today })
        assertEquals(listOf(false, false, false, true, true, true, true), v.week.map { it.future })
    }

    @Test
    fun senzaSerie() {
        val v = habitView(Habit(1, "Leggere"), now)
        assertEquals("Nessun giorno di fila", v.streak)
        assertEquals(true, v.sub.startsWith("Da fare oggi"))
    }
}
