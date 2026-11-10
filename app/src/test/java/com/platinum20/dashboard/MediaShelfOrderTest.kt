package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.MediaShelves
import com.platinum20.dashboard.ui.screens.MediaShelves.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Media: prima le file che vanno subito, sotto gli inviti a collegare. */
class MediaShelfOrderTest {
    @Test fun tuttoCollegatoOrdineDiSempre() {
        assertEquals(listOf(Kind.MUSIC, Kind.VIDEO, Kind.LISTEN), MediaShelves.order(spotifyOn = true, youTubeOn = true))
    }

    @Test fun senzaAccountLeRadioInCima() {
        assertEquals(listOf(Kind.LISTEN, Kind.MUSIC, Kind.VIDEO), MediaShelves.order(spotifyOn = false, youTubeOn = false))
    }

    @Test fun soloYouTubeCollegato() {
        assertEquals(listOf(Kind.VIDEO, Kind.LISTEN, Kind.MUSIC), MediaShelves.order(spotifyOn = false, youTubeOn = true))
    }
}
