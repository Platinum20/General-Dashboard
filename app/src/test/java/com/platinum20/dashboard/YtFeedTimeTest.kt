package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtFeedTime
import com.platinum20.dashboard.media.youtube.YtVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class YtFeedTimeTest {
    private val now = Instant.parse("2026-11-20T14:00:00Z")

    @Test
    fun leggeIlTempoInItalianoEInglese() {
        assertEquals(now.minus(Duration.ofHours(2)), YtFeedTime.ago("2 ore fa", now))
        assertEquals(now.minus(Duration.ofHours(1)), YtFeedTime.ago("1 ora fa", now))
        assertEquals(now.minus(Duration.ofDays(1)), YtFeedTime.ago("Trasmesso in streaming 1 giorno fa", now))
        assertEquals(now.minus(Duration.ofMinutes(15)), YtFeedTime.ago("15 minuti fa", now))
        assertEquals(now.minus(Duration.ofDays(14)), YtFeedTime.ago("2 settimane fa", now))
        assertEquals(now.minus(Duration.ofDays(30)), YtFeedTime.ago("1 mese fa", now))
        assertEquals(now.minus(Duration.ofDays(3)), YtFeedTime.ago("3 days ago", now))
        assertEquals(now.minus(Duration.ofHours(5)), YtFeedTime.ago("Streamed 5 hours ago", now))
        assertNull(YtFeedTime.ago("", now))
        assertNull(YtFeedTime.ago("1,2 Mln di visualizzazioni", now))
    }

    @Test
    fun aggiungeIVideoNuoviDellaPaginaIscrizioni() {
        val rss = listOf(YtVideo("old", "t", "A", "A", Instant.parse("2026-11-17T10:00:00Z")))
        val feed = listOf(
            YtVideo("new", "t", "B", "B", null, uploadedText = "3 ore fa"),
            YtVideo("old", "t", "A", "A", null, uploadedText = "3 giorni fa"),
            YtVideo("nodate", "t", "C", "C", null),
        )
        val out = YtFeedTime.withFeedVideos(rss, feed, now)
        assertEquals(listOf("new", "old"), out.map { it.id })
        // Chi era già nel feed RSS tiene la data esatta.
        assertEquals(Instant.parse("2026-11-17T10:00:00Z"), out[1].published)
    }
}
