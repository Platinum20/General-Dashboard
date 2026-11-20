package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.ui.screens.habitActions
import com.platinum20.dashboard.ui.screens.habitsBubble
import com.platinum20.dashboard.ui.screens.habitsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun fumettoUnaVocePerAbitudine() {
        val b = habitsBubble(habits, today) {}
        assertEquals(listOf("Leggere", "Correre", "Acqua"), b.map { it.title })
        assertEquals("Fatta oggi · tieni premuto per altro", b[0].detail)
        assertEquals("Tocca per segnarla · tieni premuto per altro", b[1].detail)
        // Tenendo premuta un'abitudine salgono le sue azioni (1.534).
        b.forEach { assertNotNull(it.onLongClick) }
    }

    @Test
    fun azioniDellAbitudineFinisconoConElimina() {
        var opened: Habit? = null
        val a = habitActions(habits[1], today) { opened = it }
        assertEquals(listOf("Fatta oggi", "Apri e modifica", "Elimina"), a.map { it.title })
        assertTrue(a.last().danger)
        assertEquals("Togli da oggi", habitActions(habits[0], today) {}.first().title)
        a[1].onClick()
        assertEquals(habits[1], opened)
    }
}
