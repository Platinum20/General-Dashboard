package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.DayItem
import com.platinum20.dashboard.today.DayTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayTimelineTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 10, 23)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun event(title: String, h: Int, allDay: Boolean = false) =
        CalendarEvent(h.toLong(), title, at(h), at(h + 1), allDay = allDay, location = "", color = 0)

    private fun label(i: DayItem) = when (i) {
        is DayItem.Event -> i.event.title
        is DayItem.Task -> i.task.text
        is DayItem.Date -> i.countdown.title
    }

    @Test
    fun tuttoInOrdineDiOrario() {
        val items = DayTimeline.build(
            events = listOf(event("Riunione", 15), event("Ferie", 0, allDay = true), event("Dentista", 9)),
            tasks = listOf(
                TaskItem(1, "Comprare il pane"),
                TaskItem(2, "Bolletta", dueAt = at(8)),
                TaskItem(3, "Chiamare Luca", dueAt = at(12, 30)),
            ),
            dates = listOf(Countdown(1, "Compleanno Marco", day.plusDays(3)), Countdown(2, "Anniversario", day)),
            today = day,
        )
        assertEquals(
            listOf("Ferie", "Anniversario", "Bolletta", "Dentista", "Chiamare Luca", "Riunione", "Comprare il pane", "Compleanno Marco"),
            items.map(::label),
        )
        assertEquals(3L, (items.last() as DayItem.Date).daysLeft)
    }

    @Test
    fun vuota() = assertTrue(DayTimeline.build(emptyList(), emptyList(), emptyList(), day).isEmpty())
}
