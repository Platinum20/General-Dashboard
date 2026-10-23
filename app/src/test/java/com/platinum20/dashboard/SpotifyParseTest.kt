package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SpotifyParseTest {
    private fun track(id: String, name: String) =
        """{"id":"$id","uri":"spotify:track:$id","type":"track","name":"$name","artists":[{"name":"Mina"}],"album":{"images":[{"url":"big"},{"url":"mid"}]}}"""

    @Test
    fun braniDellaPlaylistConItemOTrack() {
        val nuovo = JSONObject("""{"items":[{"item":${track("a1", "Se telefonando")}},{"item":${track("a2", "Parole parole")}}]}""")
        val vecchio = JSONObject("""{"items":[{"track":${track("a1", "Se telefonando")}},{"track":${track("a2", "Parole parole")}}]}""")
        val brani = SpotifyApi.parsePlaylistItems(nuovo)
        assertEquals(listOf("a1", "a2"), brani.map { it.id })
        assertEquals(brani, SpotifyApi.parsePlaylistItems(vecchio))
        assertEquals("Mina", brani[0].subtitle)
        assertEquals("mid", brani[0].image)
    }

    @Test
    fun vociVuoteOSenzaBranoSonoScartate() {
        val json = JSONObject("""{"items":[{"item":null},{"is_local":true},{"item":${track("a1", "Brano")}}]}""")
        assertEquals(listOf("a1"), SpotifyApi.parsePlaylistItems(json).map { it.id })
        assertTrue(SpotifyApi.parsePlaylistItems(null).isEmpty())
    }

    @Test
    fun albumESingoliDellArtista() {
        val json = JSONObject(
            """{"items":[
              {"id":"al1","uri":"spotify:album:al1","type":"album","album_type":"album","name":"Disco","release_date":"2025-03-01","artists":[{"name":"Mina"}],"images":[]},
              {"id":"si1","uri":"spotify:album:si1","type":"album","album_type":"single","name":"Singolo","release_date":"2026","artists":[{"name":"Mina"}],"images":[]}
            ]}"""
        )
        val items = SpotifyApi.parseArtistAlbums(json)
        assertEquals(listOf(SpKind.ALBUM, SpKind.ALBUM), items.map { it.kind })
        assertEquals("Mina · 2025", items[0].subtitle)
    }

    @Test
    fun sottotitoloDellaPlaylistConItemsOTracks() {
        val nuovo = JSONObject("""{"id":"p1","uri":"spotify:playlist:p1","type":"playlist","name":"Mia","owner":{"display_name":"Antonio"},"items":{"total":12}}""")
        val vecchio = JSONObject("""{"id":"p1","uri":"spotify:playlist:p1","type":"playlist","name":"Mia","owner":{"display_name":"Antonio"},"tracks":{"total":12}}""")
        assertEquals("Antonio · 12 brani", SpotifyApi.parseItem(nuovo)?.subtitle)
        assertEquals("Antonio · 12 brani", SpotifyApi.parseItem(vecchio)?.subtitle)
    }

    private val brani = listOf(SpItem("a1", "spotify:track:a1", SpKind.TRACK, "Brano", "Mina", ""))

    @Test
    fun dopoUnaPlaylistBloccataLErroreDiReteResta() {
        val bloccata = SpotifyStore.childrenState(null, Loadable(data = emptyList(), loadedAt = 1), "bloccata")
        assertEquals("bloccata", bloccata.error)
        assertEquals(emptyList<SpItem>(), bloccata.data)
        // Ricarico senza rete: si vede l'errore di rete, non l'avviso di prima, e la pagina resta.
        val senzaRete = SpotifyStore.childrenState(bloccata.data, Loadable(error = "Spotify non è raggiungibile"), null)
        assertEquals("Spotify non è raggiungibile", senzaRete.error)
        assertEquals(emptyList<SpItem>(), senzaRete.data)
    }

    @Test
    fun ricaricaRiuscitaTogliLAvviso() {
        val ok = SpotifyStore.childrenState(emptyList(), Loadable(data = brani, loadedAt = 2), null)
        assertNull(ok.error)
        assertEquals(brani, ok.data)
    }

    @Test
    fun erroreTieneIBraniDiPrima() {
        val errore = SpotifyStore.childrenState(brani, Loadable(error = "Timeout"), null)
        assertEquals(brani, errore.data)
        assertEquals("Timeout", errore.error)
    }
}
