package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewsRecapTest {
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private fun item(title: String, source: String, minutesAgo: Long) = NewsItem(
        title = title,
        link = "https://example.org/$source",
        summary = "",
        imageUrl = null,
        publishedAt = now.minusSeconds(minutesAgo * 60),
        source = NewsSource(source, source.uppercase(), ""),
    )

    private val sciopero = listOf(
        item("Sciopero treni venerdì Milano", "a", 5),
        item("Sciopero treni venerdì Roma Milano orari", "b", 30),
        item("Treni sciopero venerdì orari fasce garantite", "c", 60),
        item("Milano sciopero orari", "d", 90),
    )

    @Test fun titoloDelTemaÈLArticoloPiùCentrale() {
        val theme = NewsRecap.build(sciopero, now = now).single()
        assertEquals("Sciopero treni venerdì Roma Milano orari", theme.headline.title)
    }

    @Test fun ilTitoloÈIlPrimoDellaListaPoiDalPiùRecente() {
        val theme = NewsRecap.build(sciopero, now = now).single()
        assertEquals(theme.headline, theme.items.first())
        assertEquals(listOf("a", "c", "d"), theme.items.drop(1).map { it.source.id })
    }

    @Test fun articoloDaSoloÈIlSuoTitolo() {
        val solo = item("Nevica sulle Dolomiti", "e", 10)
        val themes = NewsRecap.build(sciopero + solo, now = now)
        assertTrue(themes.any { it.headline == solo && it.items == listOf(solo) })
    }

    @Test fun articoliVecchiEsclusi() =
        assertTrue(NewsRecap.build(listOf(item("Notizia di due giorni fa", "a", 48 * 60)), now = now).isEmpty())
}
