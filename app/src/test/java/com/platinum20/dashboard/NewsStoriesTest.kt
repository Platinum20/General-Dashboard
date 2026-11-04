package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsSource
import com.platinum20.dashboard.news.NewsStories
import com.platinum20.dashboard.news.ReadingSpots
import com.platinum20.dashboard.news.key
import com.platinum20.dashboard.news.reader.ArticleVideos
import com.platinum20.dashboard.news.reader.ReaderBlock
import com.platinum20.dashboard.news.sourcesLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewsStoriesTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z")

    private fun item(title: String, source: String, minutesAgo: Long, id: String = source) = NewsItem(
        title = title,
        link = "https://example.org/$id",
        summary = "",
        imageUrl = null,
        publishedAt = now.minusSeconds(minutesAgo * 60),
        source = NewsSource(source, source.uppercase(), ""),
    )

    private val sciopero = listOf(
        item("Sciopero treni venerdì Milano", "a", 5, "s1"),
        item("Sciopero treni venerdì Roma Milano orari", "b", 30, "s2"),
        item("Treni sciopero venerdì orari fasce garantite", "c", 60, "s3"),
    )
    private val meteo = item("Allerta arancione in Liguria per il maltempo", "d", 20, "m1")
    private val vecchia = item("Manovra approvata in consiglio dei ministri", "e", 300, "v1")

    @Test fun unaRigaPerFattoAncheConPiùTestate() {
        val stories = NewsStories.group(sciopero + meteo + vecchia)
        assertEquals(3, stories.size)
        assertEquals(3, stories.first { it.items.size > 1 }.items.size)
    }

    @Test fun lefontiNascosteELeIllegibiliEscono() {
        val stories = NewsStories.group(sciopero + meteo, readable = setOf("https://example.org/s1", "https://example.org/m1"), hidden = setOf("d"))
        assertEquals(listOf("https://example.org/s1"), stories.flatMap { it.items }.map { it.link })
    }

    @Test fun nuoveSopraIlSegnoGiàVisteSotto() {
        val stories = NewsStories.group(sciopero + meteo + vecchia)
        val feed = NewsStories.split(stories, since = now.minusSeconds(60 * 60))
        assertEquals(2, feed.fresh.size)
        assertEquals(listOf("https://example.org/v1"), feed.seen.map { it.key })
    }

    @Test fun inCimaLaStoriaDiCuiParlanoPiùTestate() {
        val feed = NewsStories.split(NewsStories.group(sciopero + meteo), since = now.minusSeconds(120 * 60))
        assertTrue(feed.fresh.first().items.size == 3)
    }

    @Test fun primaAperturaTuttoNuovoSenzaSegno() {
        val feed = NewsStories.split(NewsStories.group(sciopero + vecchia), since = null)
        assertTrue(feed.seen.isEmpty())
        assertNull(feed.since)
    }

    @Test fun unaStoriaGiàApertaScendeTraLeViste() {
        val stories = NewsStories.group(sciopero + meteo)
        val feed = NewsStories.split(stories, since = now.minusSeconds(120 * 60), settled = setOf("https://example.org/s3"))
        assertEquals(1, feed.fresh.size)
        assertEquals(1, feed.seen.size)
    }

    @Test fun prossimaNuovaSaltaQuelleAperte() {
        val feed = NewsStories.split(NewsStories.group(sciopero + meteo + item("Elezioni negli Stati Uniti, sondaggi", "f", 10, "e1")), since = now.minusSeconds(120 * 60))
        val first = feed.fresh[0].headline.link
        val next = NewsStories.next(feed, first, read = setOf(first, feed.fresh[1].headline.link))
        assertEquals(feed.fresh[2].key, next?.key)
        assertNull(NewsStories.next(feed, first, read = feed.fresh.flatMap { s -> s.items.map { it.link } }.toSet()))
    }

    @Test fun chiNeParlaInUnaRiga() {
        val story = NewsStories.group(sciopero).single()
        assertTrue(Regex("^[ABC], [ABC] e [ABC]$").matches(story.sourcesLine))
        val four = NewsStories.group(sciopero + item("Sciopero treni venerdì orari Milano", "g", 3, "s4")).single()
        assertTrue(four.sourcesLine.endsWith("e altre 2"))
    }

    @Test fun rigaDellaCardInHome() {
        val clock = { _: Instant -> "09:12" }
        assertEquals("3 nuove dalle 09:12", NewsStories.homeLine(3, now.minusSeconds(3600), now, clock))
        assertEquals("Niente di nuovo dalle 09:12", NewsStories.homeLine(0, now.minusSeconds(3600), now, clock))
        assertEquals("1 nuova da ieri", NewsStories.homeLine(1, now.minusSeconds(30 * 3600), now, clock))
        assertEquals("Le notizie di adesso", NewsStories.homeLine(2, null, now, clock))
        assertEquals("Ultima visita · 09:12", NewsStories.markerLabel(now.minusSeconds(3600), now, clock))
        assertEquals("Ultima visita · 3 giorni fa", NewsStories.markerLabel(now.minusSeconds(72 * 3600), now, clock))
    }

    @Test fun minutiDiAscoltoEDiLettura() {
        assertEquals(1, NewsStories.minutes(10))
        assertEquals(3, NewsStories.minutes(2000))
        assertEquals(4, NewsStories.readingMinutes(700))
    }

    @Test fun tornandoDalLettoreLaVisitaContinua() {
        val prima = now.minusSeconds(7200)
        val adesso = now
        assertEquals(prima, NewsHistory.sessionStart(held = true, hasSession = true, current = prima, last = adesso))
        assertEquals(adesso, NewsHistory.sessionStart(held = false, hasSession = true, current = prima, last = adesso))
        assertEquals(adesso, NewsHistory.sessionStart(held = true, hasSession = false, current = null, last = adesso))
    }

    @Test fun ilLettoreRiprendeSoloAMetà() {
        assertFalse(ReadingSpots.worthKeeping(10, 1000))
        assertTrue(ReadingSpots.worthKeeping(500, 1000))
        assertFalse(ReadingSpots.worthKeeping(990, 1000))
        val map = ReadingSpots.remember(emptyMap(), "u", 500, 1000)
        assertEquals(500, map["u"])
        assertTrue(ReadingSpots.remember(map, "u", 995, 1000).isEmpty())
    }

    @Test fun iVideoDiYouTubeVannoNelPlayer() {
        assertEquals("dQw4w9WgXcQ", ArticleVideos.youtubeId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0"))
        assertEquals("dQw4w9WgXcQ", ArticleVideos.youtubeId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", ArticleVideos.youtubeId("https://www.youtube.com/watch?feature=x&v=dQw4w9WgXcQ"))
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", ArticleVideos.playable(ReaderBlock.Video("https://www.youtube.com/embed/dQw4w9WgXcQ", true, null)))
        assertEquals("https://x.it/v.mp4", ArticleVideos.playable(ReaderBlock.Video("https://x.it/v.mp4", false, null)))
        assertNull(ArticleVideos.playable(ReaderBlock.Video("https://player.vimeo.com/video/1", true, null)))
    }
}
