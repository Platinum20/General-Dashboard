package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YouTubeWeb
import com.platinum20.dashboard.media.youtube.YtOriginals
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.player.YtResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

/** 1.571: YouTube nella lingua originale, mai titoli tradotti (Antonio). */
class YtOriginalLanguageTest {
    private fun video(id: String, title: String) = YtVideo(id = id, title = title, channel = "Canale", channelId = "UC1", published = null)

    @Test fun linguaChiestaEUnaCheYouTubeConosce() {
        // hl=it-IT non esiste per YouTube: rispondeva in inglese e traduceva i titoli italiani.
        assertEquals("it", YtResolver.LANGUAGE.localizationCode)
        assertTrue(ServiceList.YouTube.supportedLocalizations.any { it.localizationCode == "it" })
    }

    @Test fun paginaDelSitoInItaliano() {
        assertEquals("https://www.youtube.com/feed/history?hl=it&gl=IT", YouTubeWeb.inItalian("https://www.youtube.com/feed/history"))
        assertEquals("https://www.youtube.com/results?search_query=x&hl=it&gl=IT", YouTubeWeb.inItalian("https://www.youtube.com/results?search_query=x"))
        assertEquals("https://www.youtube.com/?hl=en", YouTubeWeb.inItalian("https://www.youtube.com/?hl=en"))
    }

    @Test fun titoliVeriAlPostoDeiTradotti() {
        val list = listOf(video("a", "The best pasta"), video("b", "Ciao"))
        val out = YtOriginals.apply(list, mapOf("a" to "La pasta migliore"))
        assertEquals(listOf("La pasta migliore", "Ciao"), out.map { it.title })
        assertSame(list, YtOriginals.apply(list, mapOf("b" to "Ciao")))
    }

    @Test fun siChiedonoSoloQuelliMancanti() {
        val list = listOf(video("a", "x"), video("b", "y"), video("c", "z"), video("c", "z"))
        assertEquals(listOf("c"), YtOriginals.missing(list, known = mapOf("a" to "x"), asked = setOf("b")))
        assertEquals(listOf("a"), YtOriginals.missing(list, emptyMap(), emptySet(), max = 1))
    }

    @Test fun fileTieneGliUltimi() {
        val all = linkedMapOf("a" to "1", "b" to "2", "c" to "3")
        assertEquals(listOf("b", "c"), YtOriginals.trimmed(all, keep = 2).keys.toList())
    }
}
