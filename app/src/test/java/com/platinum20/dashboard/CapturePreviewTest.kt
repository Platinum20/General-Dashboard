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

class SpotifyHeaderLineTest {
    @Test
    fun headerAndClock() {
        val p = com.platinum20.dashboard.media.spotify.SpotifyPlaylists
        assertEquals("Madame · 14 brani · 48 min", p.headerLine("Madame", 14, 48 * 60_000L))
        assertEquals("1 h 5 min", p.headerLine("", 0, 65 * 60_000L))
        assertEquals("3:05", p.clock(185_000))
    }
}

class PodcastHeroTest {
    private fun ep(id: String, at: Long, dur: Long = 3600) =
        com.platinum20.dashboard.media.podcast.Episode(id, id, "", at, dur, "", "", "", "")

    @Test
    fun startedWinsElseNewestUnheard() {
        val h = com.platinum20.dashboard.media.podcast.PodcastHero
        val eps = listOf(ep("new", 3), ep("mid", 2), ep("old", 1))
        assertEquals("mid", h.pick(eps, mapOf("mid" to 600_000L))?.id)
        assertEquals("new", h.pick(eps, emptyMap())?.id)
        assertEquals("mid", h.pick(eps, mapOf("new" to 3_600_000L))?.id)
        assertEquals(null, h.pick(listOf(ep("a", 1)), mapOf("a" to 3_600_000L)))
    }
}

class ReaderTextTest {
    @Test
    fun minutesLeftAndZoomLimits() {
        val r = com.platinum20.dashboard.ui.screens.ReaderText
        assertEquals("6 min di lettura", r.left(6, 0f))
        assertEquals("3 min alla fine", r.left(6, 0.5f))
        assertEquals("letto", r.left(6, 1f))
        assertEquals(r.MAX, r.zoomed(1.3f, 2f))
        assertEquals(r.MIN, r.zoomed(0.8f, 0.5f))
    }
}

class AccountTilesTest {
    @Test
    fun attentionFirstThenLinkedThenOff() {
        val t = com.platinum20.dashboard.ui.screens.AccountTiles.build(
            spotify = true, youtube = false, gmail = true, gmailBroken = false,
            sim = true, simStale = false, shifts = true, shiftsExpired = true,
        )
        assertEquals("Creators Pro", t.first().name)
        assertEquals("Rientra", t.first().line)
        assertEquals("YouTube", t.last().name)
        assertEquals("Collega", t.last().line)
    }
}
