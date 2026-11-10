package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.Alert
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.Repeat
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
            TaskItem.date(20, "Compleanno di Marco", LocalDate.of(1990, 3, 14), yearly = true).copy(createdAt = 400, alert = Alert.DAY_BEFORE),
            TaskItem.date(21, "Viaggio", today.plusDays(30)).copy(createdAt = 500, notes = "Treno alle 8", place = "Roma", alert = Alert.NONE),
        ),
        notes = listOf(
            Note(10, "Spesa\n- pane\n- uova", updatedAt = 400, pinned = true),
            Note(11, "Idee per le vacanze", updatedAt = 500),
        ),
        habits = listOf(Habit(30, "Palestra", setOf(today, today.minusDays(1), today.minusDays(3)))),
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
        assertEquals(listOf("Chiamare il meccanico", "Comprare il latte"), d.tasks.filter { !it.allDay }.map { it.text })
        assertEquals(1_760_090_000_000L, d.tasks[0].dueAt)
        assertTrue(d.tasks[1].done)
        assertNull(d.tasks[0].repeat)
        assertEquals("Libri da leggere", d.notes.single().title)
        assertEquals(false, d.notes.single().pinned)
        // La data da ricordare di allora è diventata un promemoria di tutto il giorno, ogni anno.
        val date = d.tasks.single { it.allDay }
        assertEquals(LocalDate.of(1990, 3, 14), date.day())
        assertEquals(1990, date.firstYear)
        assertTrue(d.habits.isEmpty() && d.parcels.isEmpty())
    }

    @Test fun valoriSconosciutiNonFannoPerdereIlResto() {
        val raw = """{"tasks":[{"id":1,"text":"x","repeat":"OGNI_ORA"}],
            "expenses":[{"id":2,"cents":100,"text":"y","category":"NUOVA","date":"2026-10-22"}]}"""
        val d = NotesStore.parse(raw)
        assertNull(d.tasks.single().repeat)
        // Le spese di prima della 1.438 si scartano: riscrivendo il file non ci sono più.
        assertTrue("expenses" !in NotesStore.serialize(d))
    }

    @Test fun iPacchiConsegnatiDaPiuDiUnMeseSpariscono() {
        val old = NotesData(parcels = listOf(Parcel(1, "Vecchio", deliveredOn = today.minusDays(45)), Parcel(2, "Nuovo")))
        assertEquals(listOf("Nuovo"), NotesStore.parse(NotesStore.serialize(old)).parcels.map { it.title })
    }
}
