package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.spotify.NewReleases
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Voce 22: le nuove uscite degli artisti che segui su Spotify, da risposte vere salvate. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NewReleasesTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun json(name: String) = JSONObject(javaClass.getResource("/spotify/$name")!!.readText())

    @Test fun soloLeUltimeQuattroSettimaneDalPiuNuovo() {
        val list = NewReleases.pick(listOf(json("artist-albums.json")), today)
        // Quella di soli anno si scarta, quella "2026-10" vale il primo del mese.
        assertEquals(listOf("Nuvole d'autunno", "Materia (Prisma)", "Live a Roma"), list.map { it.item.title })
        assertEquals(LocalDate.of(2026, 10, 1), list.last().released)
        assertEquals("https://i.scdn.co/image/ab67616d00001e02mid", list.first().item.image)
    }

    @Test fun stessoAlbumDaDueArtistiUnaVoltaSolaENienteUsciteFuture() {
        val list = NewReleases.pick(listOf(json("artist-albums.json"), json("artist-albums-feat.json"), null), today)
        assertEquals(1, list.count { it.item.id == "4aawyAB9vmqN3uQ7FjRGTy" })
        assertEquals("Dicembre", list.first().item.title)
        assertEquals(0, list.count { it.item.id == "9future" })
    }

    @Test fun precisioneDellaData() {
        assertEquals(LocalDate.of(2026, 10, 20), NewReleases.releaseDate("2026-10-20", "day", today))
        assertEquals(LocalDate.of(2026, 9, 1), NewReleases.releaseDate("2026-09", "month", today))
        // Un mese di due mesi fa è troppo lontano, un anno da solo troppo vago.
        assertNull(NewReleases.releaseDate("2026-08", "month", today))
        assertNull(NewReleases.releaseDate("2026", "year", today))
        assertNull(NewReleases.releaseDate("non-una-data", "day", today))
        // Senza precisione la si ricava dalla lunghezza.
        assertEquals(LocalDate.of(2026, 10, 20), NewReleases.releaseDate("2026-10-20", "", today))
    }

    @Test fun paginazioneDegliArtistiSeguiti() {
        val (first, after) = NewReleases.parseFollowing(json("following.json"))
        assertEquals(listOf("0TnOYISbd1XYRBk9myaseg", "5abc"), first)
        assertEquals("5abc", after)
        val (last, end) = NewReleases.parseFollowing(json("following-last.json"))
        assertEquals(listOf("9last"), last)
        assertNull(end)
        assertEquals(emptyList<String>() to null, NewReleases.parseFollowing(null))
    }

    @Test fun nuoveFinchéNonLeApri() {
        val list = NewReleases.pick(listOf(json("artist-albums.json")), today)
        assertEquals(3, NewReleases.unseen(list, emptySet()))
        assertEquals(2, NewReleases.unseen(list, setOf("4aawyAB9vmqN3uQ7FjRGTy", "altro")))
    }

    @Test fun quantoTempoFa() {
        assertEquals("oggi", NewReleases.ago(today, today))
        assertEquals("ieri", NewReleases.ago(today.minusDays(1), today))
        assertEquals("3 giorni fa", NewReleases.ago(today.minusDays(3), today))
        assertEquals("3 settimane fa", NewReleases.ago(today.minusDays(22), today))
    }

    @Test fun salvateSuFileTornanoUguali() {
        val list = NewReleases.pick(listOf(json("artist-albums.json")), today)
        val text = FeedCache.encode(FeedCache.spReleases, list, 1_000L)
        assertEquals(list, FeedCache.decode(FeedCache.spReleases, text))
    }
}
