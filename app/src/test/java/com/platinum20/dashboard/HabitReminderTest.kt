package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.HabitReminder
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** Voce 15: l'avviso delle abitudini all'ora scelta. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HabitReminderTest {
    // Mercoledì 21 ottobre 2026.
    private val wednesday = LocalDate.of(2026, 10, 21)
    private val at21 = LocalTime.of(21, 0)

    @Test
    fun oggiSeNonEAncoraPassata() {
        val h = Habit(1, "Leggere", remindAt = at21)
        assertEquals(wednesday.atTime(21, 0), HabitReminder.next(h, wednesday.atTime(18, 0)))
    }

    @Test
    fun domaniSeLOraEPassata() {
        val h = Habit(1, "Leggere", remindAt = at21)
        assertEquals(wednesday.plusDays(1).atTime(21, 0), HabitReminder.next(h, wednesday.atTime(21, 30)))
    }

    @Test
    fun giaFattaOggiNessunAvvisoOggi() {
        val h = Habit(1, "Leggere", done = setOf(wednesday), remindAt = at21)
        assertEquals(false, HabitReminder.due(h, wednesday))
        assertEquals(wednesday.plusDays(1).atTime(21, 0), HabitReminder.next(h, wednesday.atTime(18, 0)))
    }

    @Test
    fun settimanaleAObiettivoRaggiuntoFinoALunedi() {
        val done = setOf(wednesday.minusDays(2), wednesday.minusDays(1), wednesday)
        val h = Habit(1, "Palestra", done = done, perWeek = 3, remindAt = at21)
        // Lunedì 26 ottobre si ricomincia.
        assertEquals(LocalDate.of(2026, 10, 26).atTime(21, 0), HabitReminder.next(h, wednesday.atTime(18, 0)))
    }

    @Test
    fun settimanaleNonRaggiuntaAvvisaOggi() {
        val h = Habit(1, "Palestra", done = setOf(wednesday.minusDays(2)), perWeek = 3, remindAt = at21)
        assertEquals(wednesday.atTime(21, 0), HabitReminder.next(h, wednesday.atTime(18, 0)))
        assertEquals("1 su 3 questa settimana", HabitReminder.text(h, wednesday))
    }

    @Test
    fun senzaOraNessunAvviso() {
        assertNull(HabitReminder.next(Habit(1, "Leggere"), wednesday.atTime(8, 0)))
    }

    @Test
    fun testoConLaSerie() {
        val h = Habit(1, "Leggere", done = setOf(wednesday.minusDays(1), wednesday.minusDays(2)), remindAt = at21)
        assertEquals("2 di fila: non perdere la serie", HabitReminder.text(h, wednesday))
        assertEquals("Non ancora fatta oggi", HabitReminder.text(Habit(2, "Acqua"), wednesday))
    }

    @Test
    fun oraSalvataETornata() {
        val d = NotesData(habits = listOf(Habit(1, "Leggere", remindAt = LocalTime.of(21, 30)), Habit(2, "Acqua")))
        val back = NotesStore.parse(NotesStore.serialize(d))
        assertEquals(LocalTime.of(21, 30), back.habits[0].remindAt)
        assertNull(back.habits[1].remindAt)
    }

    @Test
    fun abitudiniVecchieSenzaOra() {
        val raw = """{"habits":[{"id":1,"title":"Leggere","perWeek":0,"done":["2026-10-20"]}]}"""
        val back = NotesStore.parse(raw)
        assertNull(back.habits[0].remindAt)
        assertEquals(setOf(LocalDate.of(2026, 10, 20)), back.habits[0].done)
    }

    @Test
    fun oraNellaRigaDellAbitudine() {
        assertEquals(" · avviso alle 21:05", HabitReminder.suffix(Habit(1, "Leggere", remindAt = LocalTime.of(21, 5))))
        assertEquals("", HabitReminder.suffix(Habit(2, "Acqua")))
    }
}
