package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.ui.screens.ItemKind
import com.platinum20.dashboard.ui.screens.LineKind
import com.platinum20.dashboard.ui.screens.itemDetail
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemDetailTest {
    private val now = LocalDateTime.of(2026, 10, 26, 10, 0)

    private fun ms(t: LocalDateTime) = t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun taskHasTheSameLinesAsAnEvent() {
        // Quando, tutto il giorno, si ripete, avviso, luogo, note: le stesse righe della scheda di un evento (1.361).
        val data = NotesData(tasks = listOf(TaskItem(1, "Bollo auto", dueAt = ms(now.plusDays(2)), repeat = Repeat.YEARLY)))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertEquals("Bollo auto", d.title)
        assertEquals(
            listOf(LineKind.WHEN, LineKind.ALL_DAY, LineKind.REPEAT, LineKind.ALERT, LineKind.PLACE, LineKind.NOTES, LineKind.STATUS),
            d.lines.map { it.kind },
        )
        assertEquals("Mercoledì 28 ottobre 2026, 10:00", d.lines.first().text)
        assertEquals("A un'ora precisa", d.lines[1].text)
        assertEquals("Si ripete ogni anno", d.lines[2].text)
        assertEquals("Avviso all'ora", d.lines[3].text)
        assertEquals("Tra 2 giorni", d.lines.last().text)
    }

    @Test
    fun undatedTaskOnlyAsksWhen() {
        val data = NotesData(tasks = listOf(TaskItem(1, "Latte")))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertEquals(listOf(LineKind.WHEN, LineKind.PLACE, LineKind.NOTES, LineKind.STATUS), d.lines.map { it.kind })
        assertEquals("Senza data", d.lines.first().text)
        assertEquals("Da fare", d.lines.last().text)
    }

    @Test
    fun dateIsATaskForTheWholeDay() {
        val data = NotesData(tasks = listOf(TaskItem.date(2, "Vacanze", LocalDate.of(2026, 10, 29))))
        val d = itemDetail(data, ItemKind.TASK, 2, now)!!
        assertEquals("Giovedì 29 ottobre 2026", d.lines.first().text)
        assertTrue(d.lines.any { it.text == "Tutto il giorno" })
        assertTrue(d.lines.any { it.text == "Avviso il giorno stesso alle 9" })
        assertTrue(d.lines.any { it.text == "Tra 3 giorni" })
    }

    @Test
    fun birthdaySaysTheAge() {
        val data = NotesData(tasks = listOf(TaskItem.date(3, "Compleanno di Marta", LocalDate.of(1996, 10, 28), yearly = true)))
        val d = itemDetail(data, ItemKind.TASK, 3, now)!!
        assertEquals("Mercoledì 28 ottobre 2026", d.lines.first().text)
        assertEquals("Tra 2 giorni · compie 30 anni", d.lines.last().text)
    }

    @Test
    fun parcelShowsCodeAndState() {
        val data = NotesData(parcels = listOf(Parcel(3, "Cuffie", code = "ABC123", deliveredOn = LocalDate.of(2026, 10, 20))))
        val d = itemDetail(data, ItemKind.PARCEL, 3, now)!!
        assertEquals("Arrivato il 20 ottobre", d.lines.first().text)
        assertTrue(d.lines.any { it.kind == LineKind.CODE && it.text.startsWith("ABC123") })
        assertEquals("Arrivato", d.lines.last().text)
    }

    @Test
    fun missingItemIsNull() {
        assertNull(itemDetail(NotesData(), ItemKind.HABIT, 9, now))
    }
}
