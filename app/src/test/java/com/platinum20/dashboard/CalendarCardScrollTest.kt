package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.ui.screens.DayDrag
import com.platinum20.dashboard.ui.screens.DueDay
import com.platinum20.dashboard.ui.screens.DueRow
import com.platinum20.dashboard.ui.screens.cardDays
import com.platinum20.dashboard.ui.screens.cardTodo
import com.platinum20.dashboard.ui.screens.clamp
import com.platinum20.dashboard.ui.screens.window
import com.platinum20.dashboard.ui.screens.withoutTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** La card Calendario in Home: tieni premuto e trascina per scorrere i giorni, le cose da spuntare in fondo (2026-11-11). */
class CalendarCardScrollTest {
    private val today = LocalDate.of(2026, 11, 11)

    // Righe: due di ieri (passate), tre di oggi, una domani, una fra tre giorni.
    private val rows = listOf(
        today.minusDays(1) to true, today.minusDays(1) to true,
        today to false, today to false, today to false,
        today.plusDays(1) to false,
        today.plusDays(3) to false,
    )
    private val days = cardDays(rows, today)

    @Test
    fun startsOnTodayAndTomorrow() {
        assertEquals(listOf(0, 2, 5, 6), days.starts)
        assertEquals(1, days.today)
        val w = days.window(0)
        assertEquals(2, w.from)
        assertEquals(6, w.to)
        assertEquals(2, w.todayAt)
    }

    @Test
    fun forwardShowsOneDayWhenTheNextIsFar() {
        val w = days.window(1)
        assertEquals(5, w.from)
        assertEquals(6, w.to)
        assertNull(w.todayAt)
    }

    @Test
    fun backShowsYesterdayAndToday() {
        val w = days.window(-1)
        assertEquals(0, w.from)
        assertEquals(5, w.to)
        assertEquals(5, w.todayEnd)
    }

    @Test
    fun cannotScrollPastTheEnds() {
        assertEquals(-1, days.clamp(-5))
        assertEquals(2, days.clamp(9))
    }

    @Test
    fun dragStepsOneDayPerStepFingerUpIsForward() {
        val drag = DayDrag(36f)
        assertEquals(0, drag.move(-20f))
        assertEquals(1, drag.move(-20f))
        assertEquals(-1, drag.move(60f))
    }

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
}
