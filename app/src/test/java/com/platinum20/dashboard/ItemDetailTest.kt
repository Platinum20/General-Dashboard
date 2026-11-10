package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
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
        // Il quando sotto il titolo (punto 26), poi tutto il giorno, si ripete, avviso; luogo e note vuoti diventano "+" (punto 28).
        val data = NotesData(tasks = listOf(TaskItem(1, "Bollo auto", dueAt = ms(now.plusDays(2)), repeat = Repeat.YEARLY)))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertEquals("Bollo auto", d.title)
        assertEquals(listOf(LineKind.ALL_DAY, LineKind.REPEAT, LineKind.ALERT), d.lines.map { it.kind })
        assertEquals(listOf(LineKind.PLACE, LineKind.NOTES), d.addable)
        assertEquals("Tra 2 giorni alle 10:00", d.whenLine!!.main)
        assertEquals("mercoledì 28 ottobre", d.whenLine!!.sub)
        assertEquals("A un'ora precisa", d.lines[0].text)
        assertEquals("Si ripete ogni anno", d.lines[1].text)
        assertEquals("Avviso all'ora", d.lines[2].text)
    }

    @Test
    fun filledPlaceIsALine() {
        val data = NotesData(tasks = listOf(TaskItem(1, "Cena", dueAt = ms(now.plusDays(1)), place = "Da Mario")))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertTrue(d.lines.any { it.kind == LineKind.PLACE && it.text == "Da Mario" })
        assertEquals(listOf(LineKind.NOTES), d.addable)
        assertEquals("Domani alle 10:00", d.whenLine!!.main)
    }

    @Test
    fun undatedTaskOnlyAsksWhen() {
        val data = NotesData(tasks = listOf(TaskItem(1, "Latte")))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertEquals(emptyList<LineKind>(), d.lines.map { it.kind })
        assertEquals("Senza data", d.whenLine!!.main)
    }

    @Test
    fun dateIsATaskForTheWholeDay() {
        val data = NotesData(tasks = listOf(TaskItem.date(2, "Vacanze", LocalDate.of(2026, 10, 29))))
        val d = itemDetail(data, ItemKind.TASK, 2, now)!!
        assertEquals("Tra 3 giorni", d.whenLine!!.main)
        assertEquals("giovedì 29 ottobre", d.whenLine!!.sub)
        assertTrue(d.lines.any { it.text == "Tutto il giorno" })
        assertTrue(d.lines.any { it.text == "Avviso il giorno stesso alle 9" })
    }

    @Test
    fun yearOnlyWhenNotThisYear() {
        val data = NotesData(tasks = listOf(TaskItem.date(2, "Viaggio", LocalDate.of(2027, 3, 2))))
        assertEquals("martedì 2 marzo 2027", itemDetail(data, ItemKind.TASK, 2, now)!!.whenLine!!.sub)
    }

    @Test
    fun birthdaySaysTheAge() {
        val data = NotesData(tasks = listOf(TaskItem.date(3, "Compleanno di Marta", LocalDate.of(1996, 10, 28), yearly = true)))
        val d = itemDetail(data, ItemKind.TASK, 3, now)!!
        assertEquals("Tra 2 giorni", d.whenLine!!.main)
        assertEquals("mercoledì 28 ottobre · compie 30 anni", d.whenLine!!.sub)
    }

    @Test
    fun parcelShowsCodeAndState() {
        val data = NotesData(parcels = listOf(Parcel(3, "Cuffie", code = "ABC123", deliveredOn = LocalDate.of(2026, 10, 20))))
        val d = itemDetail(data, ItemKind.PARCEL, 3, now)!!
        assertEquals("Arrivato", d.whenLine!!.main)
        assertEquals("il 20 ottobre", d.whenLine!!.sub)
        assertTrue(d.lines.any { it.kind == LineKind.CODE && it.text.startsWith("ABC123") })
        assertEquals(emptyList<LineKind>(), d.addable)
    }

    @Test
    fun expenseWithoutTextUsesCategory() {
        val data = NotesData(expenses = listOf(Expense(4, 1250, "", ExpenseCategory.FOOD, LocalDate.of(2026, 10, 1))))
        val d = itemDetail(data, ItemKind.EXPENSE, 4, now)!!
        assertEquals("Spesa", d.title)
        // Importo accanto al nome e categoria come chip (punto 27 B): non sono righe della card.
        assertEquals(com.platinum20.dashboard.notes.Expenses.format(1250), d.amount)
        assertEquals(ExpenseCategory.FOOD, d.category)
        assertTrue(d.lines.none { it.kind == LineKind.AMOUNT || it.kind == LineKind.CATEGORY })
    }

    @Test
    fun expenseShowsItsTime() {
        val data = NotesData(expenses = listOf(Expense(5, 920, "DropPOS", ExpenseCategory.FOOD, LocalDate.of(2026, 11, 9), at = java.time.LocalTime.of(14, 32))))
        val d = itemDetail(data, ItemKind.EXPENSE, 5, now)!!
        assertTrue(d.lines.first { it.kind == LineKind.WHEN }.text.endsWith(", 14:32"))
    }

    @Test
    fun missingItemIsNull() {
        assertNull(itemDetail(NotesData(), ItemKind.HABIT, 9, now))
    }
}
