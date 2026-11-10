package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.ui.screens.habitsBubble
import com.platinum20.dashboard.ui.screens.habitsLine
import org.junit.Assert.assertEquals
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
    fun fumettoUnaVocePerAbitudine() {
        val b = habitsBubble(habits, today)
        assertEquals(listOf("Leggere", "Correre", "Acqua"), b.map { it.title })
        assertEquals("Fatta oggi · tocca per toglierla", b[0].detail)
        assertEquals("Tocca per segnarla fatta", b[1].detail)
    }
}
