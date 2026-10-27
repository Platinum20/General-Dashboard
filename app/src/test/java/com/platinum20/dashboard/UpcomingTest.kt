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
            days = 7,
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
        assertEquals("Oggi", Upcoming.dayLabel(today, today))
        assertEquals("Mer 9 dic", Upcoming.dayLabel(LocalDate.of(2026, 12, 9), today))
        assertEquals("9 feb 2027", Upcoming.dayLabel(LocalDate.of(2027, 2, 9), today))
    }

    @Test
    fun vuota() = assertTrue(Upcoming.build(today, emptyMap(), emptyList(), emptyList()).isEmpty())

    @Test
    fun lineaDellaCard() {
        // Giorni di fila: nessuno spazio in più; più giorni in mezzo, più spazio, fino a un massimo.
        assertEquals(0f, Upcoming.gapDp(today.plusDays(1), today.plusDays(1)))
        assertEquals(0f, Upcoming.gapDp(today.plusDays(1), today.plusDays(2)))
        // Scala logaritmica: 1 giorno vuoto 4 dp, 2 → 10, 4 → 16, 8 → 22, poi il massimo.
        assertEquals(4f, Upcoming.gapDp(today.plusDays(1), today.plusDays(3)), 0.01f)
        assertEquals(10f, Upcoming.gapDp(today.plusDays(1), today.plusDays(4)), 0.01f)
        assertEquals(16f, Upcoming.gapDp(today.plusDays(1), today.plusDays(6)), 0.01f)
        assertEquals(22f, Upcoming.gapDp(today.plusDays(1), today.plusDays(10)), 0.01f)
        assertEquals(Upcoming.MAX_GAP_DP, Upcoming.gapDp(today.plusDays(1), today.plusDays(50)))
        // Vale anche all'indietro (note e spese, dalle più nuove).
        assertEquals(10f, Upcoming.gapDp(today.plusDays(4), today.plusDays(1)), 0.01f)
    }

    @Test
    fun segnoDelMese() {
        assertEquals(null, Upcoming.monthBreak(null, today.plusDays(10), today))
        assertEquals(null, Upcoming.monthBreak(today, today.plusDays(2), today))
        assertEquals("NOVEMBRE", Upcoming.monthBreak(today, LocalDate.of(2026, 11, 2), today))
        assertEquals("GENNAIO 2027", Upcoming.monthBreak(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4), today))
        // Andando indietro: il mese della riga sotto.
        assertEquals("SETTEMBRE", Upcoming.monthBreak(today, LocalDate.of(2026, 9, 28), today))
    }

    @Test
    fun guardaPiuInLaDiUnaSettimana() {
        val far = today.plusDays(30)
        val items = Upcoming.build(today, mapOf(far to listOf(event(9, "Concerto", far, 21))), emptyList(), emptyList(), zone = zone)
        assertEquals(listOf("Concerto"), items.map { it.text })
    }
}
