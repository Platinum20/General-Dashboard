package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
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

    @Test
    fun taskShowsDueRepeatAndStatus() {
        val data = NotesData(tasks = listOf(TaskItem(1, "Bollo auto", dueAt = 1L, repeat = Repeat.YEARLY)))
        val d = itemDetail(data, ItemKind.TASK, 1, now)!!
        assertEquals("Bollo auto", d.title)
        assertEquals(listOf(LineKind.WHEN, LineKind.REPEAT, LineKind.STATUS), d.lines.map { it.kind })
        assertEquals("Da fare", d.lines.last().text)
    }

    @Test
    fun countdownSaysHowManyDays() {
        val data = NotesData(countdowns = listOf(Countdown(2, "Vacanze", LocalDate.of(2026, 10, 29))))
        val d = itemDetail(data, ItemKind.DATE, 2, now)!!
        assertTrue(d.lines.any { it.text == "Tra 3 giorni" })
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
    fun expenseWithoutTextUsesCategory() {
        val data = NotesData(expenses = listOf(Expense(4, 1250, "", ExpenseCategory.FOOD, LocalDate.of(2026, 10, 1))))
        val d = itemDetail(data, ItemKind.EXPENSE, 4, now)!!
        assertEquals("Spesa", d.title)
        assertEquals(LineKind.AMOUNT, d.lines.first().kind)
    }

    @Test
    fun missingItemIsNull() {
        assertNull(itemDetail(NotesData(), ItemKind.HABIT, 9, now))
    }
}
