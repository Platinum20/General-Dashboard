package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtPremiere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class YtPremiereTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val now = Instant.parse("2026-10-27T17:00:00Z") // martedì, 18:00 a Roma

    @Test
    fun leggeOrarioDallaPagina() {
        val html = """..."videoDetails":{"isUpcoming":true},"liveStreamOfflineSlateRenderer":{"scheduledStartTime":"1793127600"}..."""
        assertEquals(Instant.ofEpochSecond(1793127600), YtPremiere.parse(html)?.startsAt)
    }

    @Test
    fun senzaOrarioUsaLoStartTimestamp() {
        val html = """"isUpcoming":true,"liveBroadcastDetails":{"isLiveNow":false,"startTimestamp":"2026-10-27T19:00:00+00:00"}"""
        assertEquals(Instant.parse("2026-10-27T19:00:00Z"), YtPremiere.parse(html)?.startsAt)
    }

    @Test
    fun videoNormaleNonEAnteprima() {
        assertNull(YtPremiere.parse(""""videoDetails":{"videoId":"x","isLiveContent":false}"""))
        val unknown = YtPremiere.parse(""""isUpcoming": true""")
        assertNotNull(unknown)
        assertNull(unknown!!.startsAt)
        assertEquals("L'anteprima non è ancora iniziata", unknown.message)
    }

    @Test
    fun quandoInizia() {
        assertEquals("oggi alle 20:00", YtPremiere.whenText(Instant.parse("2026-10-27T19:00:00Z"), now, rome))
        assertEquals("domani alle 9:30", YtPremiere.whenText(Instant.parse("2026-10-28T08:30:00Z"), now, rome))
        assertEquals("giovedì alle 21:00", YtPremiere.whenText(Instant.parse("2026-10-29T20:00:00Z"), now, rome))
        assertEquals("3 nov alle 20:00", YtPremiere.whenText(Instant.parse("2026-11-03T19:00:00Z"), now, rome))
    }

    @Test
    fun etichettaNeiFeed() {
        assertEquals("ANTEPRIMA · 20:00", YtPremiere.badge(Instant.parse("2026-10-27T19:00:00Z"), now, rome))
        assertEquals("ANTEPRIMA · DOMANI", YtPremiere.badge(Instant.parse("2026-10-28T19:00:00Z"), now, rome))
        assertEquals("ANTEPRIMA · 3 NOV", YtPremiere.badge(Instant.parse("2026-11-03T19:00:00Z"), now, rome))
        assertEquals("ANTEPRIMA", YtPremiere.badge(null, now, rome))
        assertEquals("ANTEPRIMA", YtPremiere.badge(Instant.parse("2026-10-27T16:00:00Z"), now, rome))
    }

    @Test
    fun contoAllaRovescia() {
        assertEquals("04:09", YtPremiere.countdown(now.plusSeconds(249), now))
        assertEquals("1 h 05 min", YtPremiere.countdown(now.plusSeconds(3900), now))
        assertEquals("2 g 3 h", YtPremiere.countdown(now.plusSeconds(2 * 86_400 + 3 * 3600 + 10), now))
        assertNull(YtPremiere.countdown(now, now))
        assertNull(YtPremiere.countdown(now.minusSeconds(5), now))
    }
}
