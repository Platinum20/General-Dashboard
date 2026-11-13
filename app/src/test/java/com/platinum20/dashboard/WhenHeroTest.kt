package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.WhenHero
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WhenHeroTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun timedEventLaterToday() {
        val w = WhenHero.of(at(13, 18, 30), at(13, 19, 30), false, at(13, 16, 30), zone)
        assertEquals("Tra 2 ore", w.countdown)
        assertEquals("18:30 – 19:30", w.big)
        assertEquals("Oggi · 1 ora", w.sub)
    }

    @Test
    fun runningAndFinished() {
        assertEquals("In corso · finisce tra 20 minuti", WhenHero.countdown(at(13, 18), at(13, 19), false, at(13, 18, 40), zone))
        assertEquals("Finito", WhenHero.countdown(at(13, 18), at(13, 19), false, at(13, 20), zone))
    }

    @Test
    fun otherDaysAndAllDay() {
        assertEquals("Domani", WhenHero.countdown(at(14, 9), at(14, 10), false, at(13, 22), zone))
        assertEquals("Tra 3 giorni", WhenHero.countdown(at(16, 0), at(17, 0), true, at(13, 12), zone))
        assertEquals("Oggi", WhenHero.countdown(at(13, 0), at(14, 0), true, at(13, 12), zone))
        assertEquals("Tutto il giorno", WhenHero.of(at(16, 0), at(17, 0), true, at(13, 12), zone).sub)
    }

    @Test
    fun spansAndDurations() {
        assertEquals("45 minuti", WhenHero.span(45 * 60_000L))
        assertEquals("1 ora e 30", WhenHero.span(90 * 60_000L))
        assertEquals("3 ore", WhenHero.span(170 * 60_000L))
        assertEquals("5 h 30", WhenHero.duration(330 * 60_000L))
        assertEquals("45 min", WhenHero.duration(45 * 60_000L))
    }
}

class NoteAppendTest {
    @Test
    fun dictatedTextGoesAtTheEnd() {
        assertEquals("Ciao", com.platinum20.dashboard.notes.NoteText.append("", "ciao"))
        assertEquals("Spesa Latte", com.platinum20.dashboard.notes.NoteText.append("Spesa", "latte"))
        assertEquals("Spesa\nLatte", com.platinum20.dashboard.notes.NoteText.append("Spesa\n", "latte"))
    }
}
