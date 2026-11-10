package com.platinum20.dashboard

import com.platinum20.dashboard.media.radio.RadioGenre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Il genere delle radio in italiano, al posto dei tag del servizio. */
class RadioGenreTest {
    @Test fun notizieEParlato() = assertEquals("Notizie e parlato", RadioGenre.of("news talk"))

    @Test fun classicaECultura() = assertEquals("Classica e cultura", RadioGenre.of("classical, culture, public radio"))

    @Test fun alMassimoDue() = assertEquals("Pop e rock", RadioGenre.of("pop,rock,dance"))

    @Test fun sconosciutoNiente() = assertNull(RadioGenre.of("xyz, Italy"))

    @Test fun iniziali() {
        assertEquals("R2", RadioGenre.initials("Radio 24"))
        assertEquals("RR", RadioGenre.initials("Rai Radio 1"))
        assertEquals("R", RadioGenre.initials(""))
    }
}
