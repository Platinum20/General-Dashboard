package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

// Robolectric: org.json è quello di Android, serve per salvare e rileggere.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HabitWeeklyTest {
    // Lunedì 19 ottobre 2026.
    private val mon = LocalDate.of(2026, 10, 19)
    private fun gym(vararg days: LocalDate) = Habit(1, "Palestra", days.toSet(), perWeek = 3)

    @Test fun contaSoloLaSettimanaInCorso() {
        val h = gym(mon.minusDays(1), mon, mon.plusDays(2))
        assertEquals(2, h.weekCount(mon.plusDays(3)))
        assertEquals(1, h.weekCount(mon))
    }

    @Test fun lunMerFattaNonCompareFinoALunedi() {
        val h = gym(mon, mon.plusDays(1), mon.plusDays(2))
        (3L..6L).forEach { assertFalse(h.needsReminder(mon.plusDays(it).atTime(20, 0))) }
    }

    @Test fun ricordaSoloQuandoServeFarlaOggi() {
        val h = gym(mon)
        // Venerdì sera: mancano 2, restano ven-sab-dom (3 giorni): c'è tempo.
        assertFalse(h.needsReminder(mon.plusDays(4).atTime(20, 0)))
        // Sabato sera: mancano 2, restano sab e dom: va fatta oggi.
        assertTrue(h.needsReminder(mon.plusDays(5).atTime(20, 0)))
        // Sabato mattina: prima delle 17 mai.
        assertFalse(h.needsReminder(mon.plusDays(5).atTime(10, 0)))
    }

    @Test fun quotidianaComePrima() {
        assertTrue(Habit(2, "Acqua").needsReminder(mon.atTime(18, 0)))
        assertFalse(Habit(2, "Acqua", setOf(mon)).needsReminder(mon.atTime(18, 0)))
    }

    @Test fun serieInSettimane() {
        val prev = mon.minusWeeks(1)
        val prev2 = mon.minusWeeks(2)
        val h = gym(prev, prev.plusDays(2), prev.plusDays(4), prev2, prev2.plusDays(1), prev2.plusDays(6), mon)
        // Questa settimana non ancora raggiunta: contano le due prima.
        assertEquals(2, h.weekStreak(mon.plusDays(2)))
        val done = h.copy(done = h.done + mon.plusDays(1) + mon.plusDays(2))
        assertEquals(3, done.weekStreak(mon.plusDays(2)))
    }

    @Test fun rigaDiStato() {
        assertEquals("1 su 3 questa settimana", gym(mon).statusLine(mon.plusDays(1).atTime(10, 0)).text)
        val line = gym(mon).statusLine(mon.plusDays(5).atTime(20, 0))
        assertTrue(line.urgent)
        assertEquals("Manca oggi · 1 su 3 questa settimana", line.text)
    }

    @Test fun adessoNonRimproveraLaSettimanaleInPari() {
        val evening = mon.plusDays(3).atTime(19, 0)
        val lines = Briefing.build(
            evening, null, emptyList(), emptyList(), emptyList(), emptyList(),
            habits = listOf(gym(mon, mon.plusDays(1), mon.plusDays(2))),
        )
        assertFalse(lines.any { it.kind == BriefKind.HABIT })
    }

    @Test fun abitudiniVecchieRestanoQuotidianeEIlNumeroSiSalva() {
        val old = NotesStore.parse("""{"habits":[{"id":1,"title":"Acqua","done":["2026-10-19"]}]}""")
        assertEquals(0, old.habits.single().perWeek)
        val again = NotesStore.parse(NotesStore.serialize(NotesData(habits = listOf(gym(mon)))))
        assertEquals(3, again.habits.single().perWeek)
    }
}
