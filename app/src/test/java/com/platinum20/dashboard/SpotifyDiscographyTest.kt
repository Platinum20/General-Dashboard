package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.spotify.SpArtistPage
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpReleaseGroup
import com.platinum20.dashboard.media.spotify.SpotifyArtist
import com.platinum20.dashboard.ui.screens.releasesLine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 1.493 (Antonio): la pagina artista mostra tutta la discografia, non solo le prime uscite. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SpotifyDiscographyTest {
    private fun album(id: String, name: String, date: String) = JSONObject()
        .put("id", id).put("uri", "spotify:album:$id").put("type", "album").put("name", name).put("release_date", date)
        .put("artists", JSONArray().put(JSONObject().put("name", "Pink Floyd")))

    private fun page(vararg albums: JSONObject) = JSONObject().put("items", JSONArray().apply { albums.forEach { put(it) } })

    private fun item(id: String, year: String) = SpItem(id, "spotify:album:$id", SpKind.ALBUM, id, "Pink Floyd · $year", "")

    @Test fun lePagineDopoLaPrimaFinoAlTotaleOAlMassimo() {
        assertEquals(listOf(10, 20, 30), SpotifyArtist.nextOffsets(total = 37, page = 10, max = 500))
        assertEquals(emptyList<Int>(), SpotifyArtist.nextOffsets(total = 7, page = 10, max = 500))
        assertEquals(emptyList<Int>(), SpotifyArtist.nextOffsets(total = 10, page = 10, max = 500))
        assertEquals((10 until 200 step 10).toList(), SpotifyArtist.nextOffsets(total = 3000, page = 10, max = 200))
    }

    @Test fun usciteDiPiuPagineDallaPiuNuovaSenzaDoppioni() {
        val first = page(album("a", "Animals", "1977-01-23"), album("w", "Wish You Were Here", "1975-09-12"))
        val second = page(
            album("d", "The Division Bell", "1994-03-28"),
            album("a", "Animals", "1977-01-23"),
            // La stessa uscita per un altro mercato: stesso titolo, stessa data.
            album("a2", "animals", "1977-01-23"),
            album("m", "Meddle", "1971"),
        )
        val list = SpotifyArtist.releases(listOf(first, null, second))
        assertEquals(listOf("d", "a", "w", "m"), list.map { it.first.id })
        assertEquals(LocalDate.of(1971, 1, 1), list.last().second)
    }

    @Test fun elencoInteroInOrdineDiUscitaColGruppo() {
        val page = SpArtistPage(
            artist = SpItem("pf", "spotify:artist:pf", SpKind.ARTIST, "Pink Floyd", "Artista", ""),
            photo = "", followers = 0, genres = emptyList(), popular = emptyList(),
            albums = listOf(item("dsotm", "1973"), item("meddle", "1971")),
            singles = listOf(item("money", "1973")),
            compilations = listOf(item("echoes", "2001")),
            appearsOn = listOf(item("altro", "2020")),
            released = mapOf(
                "dsotm" to LocalDate.of(1973, 3, 1), "meddle" to LocalDate.of(1971, 10, 30),
                "money" to LocalDate.of(1973, 5, 7), "echoes" to LocalDate.of(2001, 11, 5),
            ),
        )
        val all = SpotifyArtist.everything(page)
        assertEquals(listOf("echoes", "money", "dsotm", "meddle"), all.map { it.first.id })
        assertEquals(SpReleaseGroup.SINGLE, all[1].second)
        assertEquals(listOf("dsotm", "meddle"), SpotifyArtist.everything(page, SpReleaseGroup.ALBUM).map { it.first.id })
        assertEquals("1 uscita", releasesLine(1))
        assertEquals("4 uscite", releasesLine(4))
    }
}
