package com.platinum20.dashboard

import com.platinum20.dashboard.media.MediaStories
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** 1.486 (Antonio): durata su tutti i video, data a destra sulla riga dell'autore. */
class MediaRowsTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    @Test fun durataEFotoDallaPaginaIscrizioni() {
        val rss = listOf(
            YtVideo("a", "A", "Geopop", "geo", now),
            YtVideo("b", "B", "Geopop", "geo", now, durationSec = 90),
            YtVideo("c", "C", "Altro", "alt", now),
        )
        val feed = listOf(
            YtVideo("a", "A", "Geopop", "geo", null, durationSec = 600, channelAvatar = "https://a/geo", uploadedText = "3 ore fa"),
            YtVideo("b", "B", "Geopop", "geo", null, durationSec = 120),
        )
        val out = YouTubeStore.withFeedDetails(rss, feed)
        assertEquals(listOf(600, 90, 0), out.map { it.durationSec })
        // La foto del canale vale per tutti i suoi video, anche quelli non nella pagina.
        assertEquals("https://a/geo", out[1].channelAvatar)
        assertEquals("", out[2].channelAvatar)
        assertEquals("3 ore fa", out[0].uploadedText)
        assertEquals(rss, YouTubeStore.withFeedDetails(rss, emptyList()))
    }

    @Test fun quandoEUscitoUnEpisodio() {
        fun ep(at: Instant?) = Episode("e", "E", "u", at?.toEpochMilli() ?: 0, 0, "", "", "Il Post", "f")
        assertEquals("3 h fa", MediaStories.episodeAgo(ep(now.minusSeconds(3 * 3600)), now.toEpochMilli()))
        assertEquals("ieri", MediaStories.episodeAgo(ep(now.minusSeconds(30 * 3600)), now.toEpochMilli()))
        assertNull(MediaStories.episodeAgo(ep(null), now.toEpochMilli()))
    }
}
