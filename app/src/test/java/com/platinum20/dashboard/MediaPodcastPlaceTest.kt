package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.437: i podcast stanno in Musica, la radio da sola; tirando giù si cerca nel posto giusto. */
class MediaPodcastPlaceTest {
    @Test fun daiPodcastSiCercaInMusica() {
        assertEquals(SearchFilter.MUSIC, SearchFilter.forRoute("media/podcast"))
        assertEquals(SearchFilter.MUSIC, SearchFilter.forRoute("media/spotify"))
    }

    @Test fun dallaRadioSiCercaSoloRadio() {
        assertEquals(SearchFilter.RADIO, SearchFilter.forRoute("media/radio"))
    }
}

/** 1.439: la fila Video di Media va dal video più nuovo delle iscrizioni; quelli senza data in fondo. */
class MediaVideoOrderTest {
    private fun v(id: String, at: Long?) = com.platinum20.dashboard.media.youtube.YtVideo(
        id, id, "c", "c", at?.let { java.time.Instant.ofEpochSecond(it) },
    )

    @Test fun dalPiuNuovo() {
        val out = com.platinum20.dashboard.ui.screens.MediaShelves.newestFirst(listOf(v("a", 10), v("b", null), v("c", 30), v("d", 20)))
        assertEquals(listOf("c", "d", "a", "b"), out.map { it.id })
    }
}
