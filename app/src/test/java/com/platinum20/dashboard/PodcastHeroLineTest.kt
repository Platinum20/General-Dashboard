package com.platinum20.dashboard

import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.PodcastHero
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PodcastHeroLineTest {
    private val now = ZonedDateTime.of(2026, 11, 21, 12, 0, 0, 0, ZoneId.of("Europe/Rome"))
    private fun ep(durationSec: Long, publishedAt: Long = now.minusDays(1).toInstant().toEpochMilli()) =
        Episode("e", "La puntata", "https://a/e.mp3", publishedAt, durationSec, "", "", "P", "f")

    @Test fun newEpisodeSaysWhenAndHowLong() {
        assertEquals("Nuova · ieri · 45 min · La puntata", PodcastHero.line(ep(45 * 60), 0, now))
    }

    @Test fun startedEpisodeSaysWhatIsLeft() {
        assertEquals("Da riprendere · restano 33 min · La puntata", PodcastHero.line(ep(45 * 60), 12 * 60_000L, now))
    }

    @Test fun missingDateAndDurationAreSkipped() {
        assertEquals("Nuova · La puntata", PodcastHero.line(ep(0, 0), 0, now))
    }
}
