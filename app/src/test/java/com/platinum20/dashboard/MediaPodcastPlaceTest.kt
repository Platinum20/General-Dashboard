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
