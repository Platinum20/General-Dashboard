package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsSource
import com.platinum20.dashboard.news.NewsStories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** News più pulite: via i tg interi, "· 2 fonti" al posto della freccina, un solo "niente di nuovo". */
class NewsPageTidyTest {
    private val now = Instant.parse("2026-11-10T08:00:00Z")

    private fun item(title: String, source: String, id: String = title) = NewsItem(
        title = title,
        link = "https://example.org/${id.hashCode()}",
        summary = "",
        imageUrl = null,
        publishedAt = now,
        source = NewsSource(source.lowercase(), source, ""),
    )

    @Test fun iTelegiornaliNonSonoStorie() {
        assertTrue(NewsStories.isBulletin("World24 del 09/11/2026"))
        assertTrue(NewsStories.isBulletin("Tg1 delle 20:00"))
        assertTrue(NewsStories.isBulletin("GR1 ore 8"))
        assertTrue(NewsStories.isBulletin("Tgr Lazio edizione delle 14"))
    }

    @Test fun leNotizieRestano() {
        assertFalse(NewsStories.isBulletin("Il Papa a Buenos Aires, 2 milioni alla messa"))
        assertFalse(NewsStories.isBulletin("Tregua a Gaza, firmato l'accordo del 9/11"))
        assertFalse(NewsStories.isBulletin("Grande folla al concerto delle 21"))
    }

    @Test fun ilGruppoTogliITg() {
        val stories = NewsStories.group(listOf(item("World24 del 09/11/2026", "RaiNews"), item("Omar Sy a processo", "Open")))
        assertEquals(listOf("Omar Sy a processo"), stories.map { it.headline.title })
    }

    @Test fun dueFontiAlPostoDellaFreccina() {
        val one = NewsStories.group(listOf(item("Omar Sy a processo", "Open"))).single()
        assertEquals("Open · 3 min fa", NewsStories.metaLine(one, "3 min fa"))
    }

    @Test fun nienteDiNuovoNelSegno() {
        val clock: (Instant) -> String = { "01:30" }
        assertEquals("Ultima visita · 01:30 · niente di nuovo", NewsStories.markerLabel(now.minusSeconds(3600), now, clock, nothingNew = true))
        assertEquals("Ultima visita · 01:30", NewsStories.markerLabel(now.minusSeconds(3600), now, clock))
    }

    @Test fun quanteStorieOggiPerFonte() {
        val zone = java.time.ZoneOffset.UTC
        val today = java.time.LocalDate.of(2026, 11, 10)
        val old = item("Vecchia", "Open").copy(publishedAt = now.minusSeconds(3 * 86_400))
        val counts = NewsStories.todayBySource(listOf(item("A", "Open"), item("B", "Open"), item("C", "ANSA"), old), today, zone)
        assertEquals(mapOf("open" to 2, "ansa" to 1), counts)
        assertEquals("Niente oggi", NewsStories.todayLabel(0))
        assertEquals("1 storia oggi", NewsStories.todayLabel(1))
        assertEquals("31 storie oggi", NewsStories.todayLabel(31))
    }
}
