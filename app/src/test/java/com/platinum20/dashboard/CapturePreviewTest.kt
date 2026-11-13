package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CapturePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CapturePreviewTest {
    private val now = LocalDateTime.of(2026, 11, 12, 15, 0)

    @Test
    fun tooShortOrQuestionShowsNothing() {
        assertNull(CapturePreview.of("ci", now))
        assertNull(CapturePreview.of("cosa ho domani?", now))
    }

    @Test
    fun datedReminderGoesInTheCalendar() {
        val row = CapturePreview.of("domani alle 18 dentista", now)!!
        assertEquals(CapturePreview.CALENDAR, row.destination)
        assertEquals("18:00", row.left)
        assertEquals(true, row.detail.startsWith("Domani"))
    }

    @Test
    fun undatedThingsGoInPerDopo() {
        val row = CapturePreview.of("comprare il latte", now)!!
        assertEquals(CapturePreview.LATER, row.destination)
    }
}
