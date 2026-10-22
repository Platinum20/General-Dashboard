package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Il file delle Note: tutto quello che si salva deve tornare uguale, anche dopo un aggiornamento. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotesStoreTest {
    private val today = LocalDate.now()

    private val full = NotesData(
        tasks = listOf(
            TaskItem(1, "Dentista", createdAt = 100, dueAt = 5_000),
            TaskItem(2, "Affitto", createdAt = 200, dueAt = 9_000, repeat = Repeat.MONTHLY, snoozedFrom = 8_000),
            TaskItem(3, "Latte", done = true, createdAt = 300),
        ),
        notes = listOf(
            Note(10, "Spesa\n- pane\n- uova", updatedAt = 400, pinned = true),
            Note(11, "Idee per le vacanze", updatedAt = 500),
        ),
        countdowns = listOf(
            Countdown(20, "Compleanno di Marco", LocalDate.of(1990, 3, 14), yearly = true),
            Countdown(21, "Viaggio", today.plusDays(30)),
        ),
        habits = listOf(Habit(30, "Palestra", setOf(today, today.minusDays(1), today.minusDays(3)))),
        expenses = listOf(
            Expense(40, 1_250, "pizza", ExpenseCategory.EATING_OUT, today),
            Expense(41, 123_450, "affitto", ExpenseCategory.HOME, today.minusDays(2)),
        ),
        budgetCents = 80_000,
        parcels = listOf(
            Parcel(50, "Cuffie", "1Z999AA10123456784", expected = today.plusDays(2)),
            Parcel(51, "Libro", "", deliveredOn = today.minusDays(1)),
        ),
    )

    @Test fun andataERitornoConTuttiITipi() {
        assertEquals(full, NotesStore.parse(NotesStore.serialize(full)))
    }

    @Test fun datiVuoti() = assertEquals(NotesData(), NotesStore.parse(NotesStore.serialize(NotesData())))

    @Test fun fileDiUnaVersioneVecchiaSiLeggeSenzaPerdereNulla() {
        val raw = javaClass.classLoader!!.getResource("notes-1.20.json")!!.readText()
        val d = NotesStore.parse(raw)
        assertEquals(listOf("Chiamare il meccanico", "Comprare il latte"), d.tasks.map { it.text })
        assertEquals(1_760_090_000_000L, d.tasks[0].dueAt)
        assertTrue(d.tasks[1].done)
        assertNull(d.tasks[0].repeat)
        assertEquals("Libri da leggere", d.notes.single().title)
        assertEquals(false, d.notes.single().pinned)
        assertEquals(LocalDate.of(1990, 3, 14), d.countdowns.single().date)
        assertTrue(d.habits.isEmpty() && d.expenses.isEmpty() && d.parcels.isEmpty())
        assertNull(d.budgetCents)
    }

    @Test fun valoriSconosciutiNonFannoPerdereIlResto() {
        val raw = """{"tasks":[{"id":1,"text":"x","repeat":"OGNI_ORA"}],
            "expenses":[{"id":2,"cents":100,"text":"y","category":"NUOVA","date":"2026-10-22"},{"id":3,"cents":5,"text":"z","date":"rotta"}]}"""
        val d = NotesStore.parse(raw)
        assertNull(d.tasks.single().repeat)
        assertEquals(ExpenseCategory.OTHER, d.expenses.single().category)
    }

    @Test fun iPacchiConsegnatiDaPiuDiUnMeseSpariscono() {
        val old = NotesData(parcels = listOf(Parcel(1, "Vecchio", deliveredOn = today.minusDays(45)), Parcel(2, "Nuovo")))
        assertEquals(listOf("Nuovo"), NotesStore.parse(NotesStore.serialize(old)).parcels.map { it.title })
    }
}
