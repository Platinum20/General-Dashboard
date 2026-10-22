package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsFilter
import com.platinum20.dashboard.news.NewsHistory
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsHeadlineTest {
    private val now = Instant.parse("2026-10-22T14:00:00Z")

    @Test
    fun intestazione() {
        assertEquals("Notizie libere", NewsHistory.headline(0, null, null, now))
        // Prima visita: nessun confronto possibile.
        assertEquals("40 notizie", NewsHistory.headline(40, null, now, now))
        assertEquals("40 notizie", NewsHistory.headline(40, 40, now, now))
        assertEquals("1 nuova dall'ultima visita", NewsHistory.headline(40, 1, now, now))
        assertEquals("7 nuove dall'ultima visita", NewsHistory.headline(40, 7, now, now))
    }

    @Test
    fun zeroNuoveMaNotizieFresche() {
        assertEquals("Aggiornate 2 min fa", NewsHistory.headline(40, 0, now - Duration.ofMinutes(2), now))
        assertEquals("Aggiornate adesso", NewsHistory.headline(40, 0, now, now))
        assertEquals("Aggiornate 3 h fa", NewsHistory.headline(40, 0, now - Duration.ofMinutes(190), now))
        assertEquals("Niente di nuovo dall'ultima visita", NewsHistory.headline(40, 0, now - Duration.ofDays(2), now))
        assertEquals("40 notizie", NewsHistory.headline(40, 0, null, now))
    }

    @Test
    fun vociCheNonSonoArticoli() {
        assertFalse(NewsFilter.isArticle("Dalla redazione di Internazionale", "https://www.internazionale.it/"))
        assertFalse(NewsFilter.isArticle("Dalla redazione di Internazionale", "https://www.internazionale.it/notizie/2026/10/22/x"))
        assertFalse(NewsFilter.isArticle("Internazionale", "https://www.internazionale.it"))
        assertFalse(NewsFilter.isArticle("Cultura", "https://www.fanpage.it/cultura/"))
    }

    @Test
    fun articoliVeri() {
        assertTrue(NewsFilter.isArticle("Manovra, cosa cambia", "https://www.ansa.it/sito/notizie/politica/2026/10/22/manovra_abc.html"))
        assertTrue(NewsFilter.isArticle("Il punto", "https://www.open.online/2026/10/22/manovra-cosa-cambia/"))
        assertTrue(NewsFilter.isArticle("Roma, incidente", "https://www.fanpage.it/roma-incidente-sulla-tangenziale/"))
        assertTrue(NewsFilter.isArticle("Breve", "https://www.agi.it/?p=123"))
        assertTrue(NewsFilter.isArticle("La redazione consiglia", "https://www.internazionale.it/notizie/2026/10/22/consigli"))
    }
}
