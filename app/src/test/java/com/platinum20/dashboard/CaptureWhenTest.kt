package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureDestination
import com.platinum20.dashboard.capture.CaptureWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CaptureWhenTest {
    private val now = LocalDateTime.of(2026, 11, 20, 16, 30)
    private val today = now.toLocalDate()

    @Test fun asksOnlyForCalendarWithoutDay() {
        assertTrue(CaptureWhen.needed(CaptureDestination.CALENDAR, "Cena con Luca", now))
        assertFalse(CaptureWhen.needed(CaptureDestination.CALENDAR, "domani alle 20 cena con Luca", now))
        assertFalse(CaptureWhen.needed(CaptureDestination.TODO, "Cena con Luca", now))
        assertFalse(CaptureWhen.needed(null, "Cena con Luca", now))
        assertFalse(CaptureWhen.needed(CaptureDestination.CALENDAR, "  ", now))
    }

    @Test fun dayLabels() {
        assertEquals("Oggi", CaptureWhen.dayLabel(today, today))
        assertEquals("Domani", CaptureWhen.dayLabel(today.plusDays(1), today))
        assertEquals("Dom 22", CaptureWhen.dayLabel(today.plusDays(2), today))
        assertEquals(CaptureWhen.DAYS, CaptureWhen.days(today).size)
    }

    @Test fun todayOffersOnlyFutureHours() {
        assertEquals(listOf(LocalTime.of(18, 0), LocalTime.of(21, 0)), CaptureWhen.hours(today, now))
        assertEquals(CaptureWhen.HOURS.size, CaptureWhen.hours(today.plusDays(1), now).size)
    }

    @Test fun draftKeepsTextAsTitle() {
        val day = LocalDate.of(2026, 11, 23)
        val allDay = CaptureWhen.draft("cena con Luca", day, null)
        assertEquals("Cena con Luca", allDay.title)
        assertTrue(allDay.allDay)
        val timed = CaptureWhen.draft("cena con Luca", day, LocalTime.of(20, 0))
        assertEquals(day.atTime(20, 0), timed.start)
        assertEquals(day.atTime(21, 0), timed.end)
        assertFalse(timed.allDay)
    }
}
