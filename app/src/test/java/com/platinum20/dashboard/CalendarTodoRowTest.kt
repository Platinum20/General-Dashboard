package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.cardTodo
import com.platinum20.dashboard.ui.screens.todoLine
import com.platinum20.dashboard.ui.screens.withoutTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Le cose da spuntare fuori dai giorni, nella riga Da fare dentro OGGI (card e pagina, 2026-11-11). */
class CalendarTodoRowTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun tickablesLeaveTheDaysAndGoToTheBottom() {
        val todo = TaskItem(1, "Comprare il latte")
        val dated = TaskItem(2, "Dentista", dueAt = 1L)
        val groups = listOf(
            DueDay(today, false, listOf(
                DueRow("today", today, Long.MIN_VALUE, today = true),
                DueRow("task-1", today, Long.MAX_VALUE, task = todo),
                DueRow("task-2", today, 5L, task = dated),
            )),
        )
        assertEquals(listOf("today", "task-2"), groups.withoutTicks().single().rows.map { it.key })
        val section = cardTodo(groups, today)
        assertEquals(listOf("task-1"), section.map { it.first.key })
        assertNull(section.single().first.dayLabel)
    }

    @Test
    fun todoLineSaysHowManyAreLeft() {
        assertEquals("Da fare · 3", todoLine(3))
        assertEquals("Da fare · tutto fatto", todoLine(0))
    }
}
