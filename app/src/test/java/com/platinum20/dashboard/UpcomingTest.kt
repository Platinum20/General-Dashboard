package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Upcoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UpcomingTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 26)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun event(id: Long, title: String, day: LocalDate, h: Int, allDay: Boolean = false) =
        CalendarEvent(id, title, ms(day.atTime(h, 0)), ms(day.atTime(h + 1, 0)), allDay = allDay, location = "", color = 0)

    @Test
    fun settimanaInOrdine() {
        val d1 = today.plusDays(1)
        val d3 = today.plusDays(3)
        val trip = event(5, "Vacanza", d3, 0, allDay = true)
        val items = Upcoming.build(
            today,
            events = mapOf(
                today to listOf(event(1, "Oggi no", today, 10)),
                d1 to listOf(event(2, "Treno", d1, 9)),
                d3 to listOf(trip),
                today.plusDays(4) to listOf(trip),
                today.plusDays(9) to listOf(event(3, "Troppo in là", today.plusDays(9), 9)),
            ),
            tasks = listOf(
                TaskItem(1, "Bolletta", dueAt = ms(d1.atTime(8, 0))),
                TaskItem(2, "Fatto", done = true, dueAt = ms(d1.atTime(7, 0))),
                TaskItem(3, "Oggi", dueAt = ms(today.atTime(20, 0))),
            ),
            countdowns = listOf(Countdown(1, "Compleanno Marta", d3)),
            parcels = listOf(Parcel(1, "Cuffie", expected = d1)),
            zone = zone,
        )
        assertEquals(
            listOf("Arriva: Cuffie", "Bolletta", "Treno", "Compleanno Marta", "Vacanza"),
            items.map { it.text },
        )
        assertEquals("08:00", items[1].time)
        assertEquals(null, items[0].time)
    }

    @Test
    fun etichetteDeiGiorni() {
        assertEquals("Domani", Upcoming.dayLabel(today.plusDays(1), today))
        assertEquals("Mer 28", Upcoming.dayLabel(today.plusDays(2), today))
    }

    @Test
    fun vuota() = assertTrue(Upcoming.build(today, emptyMap(), emptyList(), emptyList()).isEmpty())
}
