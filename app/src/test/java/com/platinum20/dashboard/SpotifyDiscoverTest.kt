package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyDiscover
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.488 (Antonio): Scopri della musica parte dai generi dei tuoi artisti e toglie chi ascolti già. */
class SpotifyDiscoverTest {
    @Test fun generiDalPiuPresente() {
        val pairs = listOf("post-punk" to "She Wants Revenge", "french house" to "Daft Punk", "Post-Punk" to "Interpol", "electro" to "Justice", "french house" to "Air")
        assertEquals(listOf("post-punk" to "She Wants Revenge", "french house" to "Daft Punk"), SpotifyDiscover.genres(pairs, max = 2))
    }

    @Test fun senzaGeneriNienteFile() {
        assertEquals(emptyList<Pair<String, String>>(), SpotifyDiscover.genres(listOf("" to "X")))
    }

    @Test fun togliGliArtistiCheConosci() {
        fun a(id: String, name: String) = SpItem(id, "spotify:artist:$id", SpKind.ARTIST, name, "", "")
        val found = listOf(a("1", "Daft Punk"), a("2", "Cassius"), a("3", "air"), a("2", "Cassius"))
        assertEquals(listOf("Cassius"), SpotifyDiscover.newArtists(found, setOf("1", "air")).map { it.title })
    }

    @Test fun nomeDelGenere() {
        assertEquals("Post punk", SpotifyDiscover.label("post-punk"))
    }
}
