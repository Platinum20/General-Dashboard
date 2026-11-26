package com.platinum20.dashboard

import com.platinum20.dashboard.search.CaptureMatch
import com.platinum20.dashboard.search.SearchMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cerca confronta le parole una per una, in qualunque ordine e con un errore (2026-11-26, punto 1). */
class SearchMatchTest {
    @Test fun paroleInQualunqueOrdine() {
        assertTrue(SearchMatch.score("marco turno", "Turno su Marco e Giulia") > 0)
        assertTrue(SearchMatch.score("turno marco", "Turno", "Marco") > 0)
        assertEquals(0, SearchMatch.score("turno luca", "Turno su Marco"))
    }

    @Test fun unErroreNelleParoleLunghe() {
        assertTrue(SearchMatch.matches("Spotify", "spotfy"))
        assertTrue(SearchMatch.matches("Il meccanico di sabato", "mecanico"))
        // Le parole corte vanno scritte giuste: troppe somiglianze per caso.
        assertFalse(SearchMatch.matches("pace", "pane"))
    }

    @Test fun ilTitoloEsattoValeDiPiu() {
        val exact = SearchMatch.score("pane", "Pane")
        val inside = SearchMatch.score("pane", "Comprare il pane")
        val fuzzy = SearchMatch.score("panino", "Panini al bar")
        assertTrue(exact > inside)
        assertTrue(inside > fuzzy)
    }

    @Test fun forteSoloAInizioDiParola() {
        assertTrue(SearchMatch.strong("Marco Mengoni", "marco"))
        assertFalse(SearchMatch.strong("Gianmarco", "marco"))
        assertFalse(SearchMatch.strong("Marco Mengoni", "marko"))
    }

    @Test fun doveColorare() {
        assertEquals(listOf(0..4, 11..15), SearchMatch.ranges("Turno, poi Marco", "marco turno"))
        assertEquals(listOf(0..6), SearchMatch.ranges("Spotify e basta", "spotfy"))
        assertEquals(listOf(8..12), SearchMatch.ranges("Regalo: caffè", "CAFFE"))
    }

    @Test fun distanza() {
        assertEquals(1, SearchMatch.distance("spotify", "spotfy"))
        assertEquals(1, SearchMatch.distance("turno", "tunro"))
        assertEquals(0, SearchMatch.distance("x", "x"))
    }

    @Test fun nelPiuAlMassimoTreDaTreLettere() {
        assertFalse(CaptureMatch.asks("ab"))
        assertTrue(CaptureMatch.asks("abc"))
        assertEquals(listOf(9, 5, 3), CaptureMatch.top(listOf(3, 0, 9, 5, 1)) { it })
    }
}
