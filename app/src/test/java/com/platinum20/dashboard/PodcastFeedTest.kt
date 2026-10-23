package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.EpisodeDate
import com.platinum20.dashboard.media.podcast.PodcastFeed
import com.platinum20.dashboard.media.podcast.PodcastProgress
import com.platinum20.dashboard.media.podcast.PodcastStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** Podcast in Media: lettura dei feed RSS, della ricerca di iTunes e del punto da cui riprendere. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PodcastFeedTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource("podcast/$name")!!.readText()

    private val itunes by lazy { PodcastFeed.parse(resource("feed-itunes.xml"), "https://example.org/feed") }

    @Test fun episodiSenzaAudioScartatiEDalPiuNuovo() {
        assertEquals(listOf("Speciale", "Mercoledì 22 ottobre", "Martedì 21 ottobre"), itunes.map { it.title })
        assertTrue(itunes.none { it.id == "morning-note" })
    }

    @Test fun durateNeiVariFormati() {
        assertEquals(3723L, itunes.first { it.id == "morning-2026-10-22" }.durationSec)
        assertEquals(3723L, itunes.first { it.id == "morning-2026-10-21" }.durationSec)
        assertEquals(0L, itunes.first { it.id == "morning-special" }.durationSec)
        assertEquals(3723L, PodcastFeed.parseDuration("62:03"))
        assertEquals(45L, PodcastFeed.parseDuration(" 45 "))
        assertEquals(0L, PodcastFeed.parseDuration("un'ora"))
        assertEquals(0L, PodcastFeed.parseDuration(""))
    }

    @Test fun descrizioneSenzaHtmlEIndirizziSicuri() {
        val first = itunes.first { it.id == "morning-2026-10-22" }
        assertEquals("Le notizie del giorno, in breve. Con ospiti & rubriche.", first.description)
        assertEquals("https://audio.example.org/morning-1022.mp3", first.audioUrl)
        // Senza immagine propria l'episodio usa quella del podcast, anch'essa in https.
        assertEquals("https://cdn.example.org/morning.jpg", first.image)
        assertEquals("https://cdn.example.org/ep21.jpg", itunes.first { it.id == "morning-2026-10-21" }.image)
        assertEquals("Morning & Notizie", first.podcastTitle)
    }

    @Test fun dateRssConFusiDiversi() {
        val rome = ZonedDateTime.of(2026, 10, 22, 6, 0, 0, 0, ZoneId.of("+02:00")).toInstant().toEpochMilli()
        assertEquals(rome, itunes.first { it.id == "morning-2026-10-22" }.publishedAt)
        assertEquals(
            ZonedDateTime.of(2026, 10, 21, 6, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(),
            PodcastFeed.parseDate("Wed, 21 Oct 2026 06:00:00 GMT"),
        )
        // Giorno della settimana sbagliato (capita spesso): la data vale lo stesso.
        assertEquals(rome, PodcastFeed.parseDate("Mon, 22 Oct 2026 06:00:00 +0200"))
        assertEquals(0L, PodcastFeed.parseDate("ieri"))
    }

    @Test fun feedSempliceConImmagineDelCanaleESenzaDoppioni() {
        val list = PodcastFeed.parse(resource("feed-semplice.xml"), "https://example.org/storie")
        assertEquals(1, list.size)
        assertEquals("https://example.org/storie.png", list[0].image)
        assertEquals(2730L, list[0].durationSec)
        assertTrue(list[0].publishedAt > 0)
    }

    @Test fun feedRovinatoNonFaCadere() {
        assertEquals(emptyList<Episode>(), PodcastFeed.parse("<html>Non è un feed", "x"))
        assertEquals(emptyList<Episode>(), PodcastFeed.parse("", "x"))
    }

    @Test fun ricercaDiItunes() {
        val results = PodcastFeed.parseSearch(resource("search.json"))
        // Quello senza feed non si può ascoltare; il doppione (stesso feed in https) sparisce.
        assertEquals(listOf("Morning"), results.map { it.title })
        assertEquals("https://feeds.example.org/morning", results[0].feedUrl)
        assertEquals("https://is1.example.org/600.jpg", results[0].artwork)
        assertEquals("Il Post", results[0].author)
        assertEquals(emptyList<Any>(), PodcastFeed.parseSearch("non json"))
    }

    @Test fun daDoveRiprendere() {
        assertEquals(0L, PodcastProgress.resumeAt(3_000, 3_600_000))
        assertEquals(120_000L, PodcastProgress.resumeAt(120_000, 3_600_000))
        // Negli ultimi 30 secondi è finito: si riparte da capo.
        assertEquals(0L, PodcastProgress.resumeAt(3_590_000, 3_600_000))
        assertEquals(120_000L, PodcastProgress.resumeAt(120_000, 0))
    }

    @Test fun etichettaDellaDurata() {
        assertEquals("1 h 2 min", PodcastProgress.label(3723))
        assertEquals("1 h", PodcastProgress.label(3600))
        assertEquals("45 min", PodcastProgress.label(2700))
        assertEquals("restano 15 min", PodcastProgress.label(2700, 1_800_000))
        assertEquals("", PodcastProgress.label(0))
    }

    @Test fun dataDellEpisodio() {
        val now = ZonedDateTime.of(2026, 10, 23, 9, 0, 0, 0, ZoneId.of("Europe/Rome"))
        fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 7, 0, 0, 0, now.zone).toInstant().toEpochMilli()
        assertEquals("oggi", EpisodeDate.label(at(2026, 10, 23), now))
        assertEquals("ieri", EpisodeDate.label(at(2026, 10, 22), now))
        assertEquals("3 giorni fa", EpisodeDate.label(at(2026, 10, 20), now))
        assertEquals("2 ott", EpisodeDate.label(at(2026, 10, 2), now))
        assertEquals("5 ott 2025", EpisodeDate.label(at(2025, 10, 5), now))
        assertEquals("", EpisodeDate.label(0, now))
    }

    @Test fun ultimiEpisodiDeiPodcastSeguiti() {
        fun ep(id: String, t: Long) = Episode(id, id, "https://a/$id.mp3", t, 0, "", "", "P", "f")
        val a = (1..8).map { ep("a$it", it * 10L) }
        val b = listOf(ep("b1", 15), ep("b2", 95))
        val latest = PodcastStore.latestOf(listOf(a, b), perPodcast = 5, max = 6)
        assertEquals(listOf("b2", "a8", "a7", "a6", "a5", "a4"), latest.map { it.id })
    }
}
