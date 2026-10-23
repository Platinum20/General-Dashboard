package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.HabitIdeas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitIdeasTest {
    @Test fun senzaAbitudiniTreIdee() {
        assertEquals(listOf("Leggere 20 minuti", "Palestra", "Niente telefono a letto"), HabitIdeas.suggest(emptyList()).map { it.title })
    }

    @Test fun maiUnaCheCeGia() {
        val ideas = HabitIdeas.suggest(listOf(Habit(1, "palestra 3 volte"), Habit(2, "Leggere")))
        assertTrue(ideas.none { it.title == "Palestra" || it.title.startsWith("Leggere") })
        assertEquals("Niente telefono a letto", ideas.first().title)
    }

    @Test fun daTreInSuNiente() {
        assertTrue(HabitIdeas.suggest((1L..3L).map { Habit(it, "A$it") }).isEmpty())
    }

    @Test fun frequenza() {
        assertEquals("3 volte a settimana", HabitIdeas.all.first { it.title == "Palestra" }.frequency)
        assertEquals("Ogni giorno", HabitIdeas.all.first().frequency)
    }
}
