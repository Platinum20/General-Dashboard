package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.ui.screens.habitFlame
import com.platinum20.dashboard.ui.screens.habitTodoLine
import com.platinum20.dashboard.ui.screens.habitsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsBubbleTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val habits = listOf(
        Habit(1, "Leggere", done = setOf(today)),
        Habit(2, "Correre"),
        Habit(3, "Acqua", done = setOf(today.minusDays(1))),
    )

    @Test
    fun rigaContaQuelleFatteOggi() {
        assertEquals("Abitudini · 1 su 3", habitsLine(habits, today))
    }

    @Test
    fun serieAccantoInDaFare() {
        val h = Habit(4, "Leggere", done = setOf(today, today.minusDays(1), today.minusDays(2)))
        assertEquals(3, habitFlame(h, today))
        assertNull(habitFlame(Habit(5, "Correre"), today))
    }

    @Test
    fun rigaSottoInDaFare() {
        val morning = today.atTime(9, 0)
        val evening = today.atTime(20, 0)
        assertEquals("Fatta oggi · torna domani", habitTodoLine(habits[0], morning)?.text)
        val day = habitTodoLine(habits[1], morning)
        assertEquals("Manca oggi", day?.text)
        assertFalse(day!!.urgent)
        assertFalse(habitTodoLine(habits[1], today.atTime(19, 59))!!.urgent)
        val late = habitTodoLine(habits[1], evening)
        assertEquals("Manca oggi", late?.text)
        assertTrue(late!!.urgent)
    }
}
