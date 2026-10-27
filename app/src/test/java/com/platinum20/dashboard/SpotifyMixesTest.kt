package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyMixes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Mix per te": i Daily Mix fatti da Canvas con gli artisti e i brani che ascolti. */
class SpotifyMixesTest {
    private fun artist(name: String) = SpItem(name, "spotify:artist:$name", SpKind.ARTIST, name, "Artista", "img-$name")
    private fun track(id: String, artists: String) = SpItem(id, "spotify:track:$id", SpKind.TRACK, "Brano $id", artists, "")

    private val pool = listOf(
        track("a1", "Anna"), track("a2", "Anna, Bruno"), track("a3", "Anna"),
        track("b1", "Bruno"),
        track("c1", "Carla"), track("c2", "Carla"),
    ) + (1..40).map { track("x$it", "Altro $it") }

    @Test
    fun oneMixPerArtistWithEnoughTracks() {
        val mixes = SpotifyMixes.build(listOf(artist("Anna"), artist("Bruno"), artist("Carla")), pool, 20_000)
        // Bruno ha un brano solo (più uno in coppia con Anna): due, quindi c'è anche lui.
        assertEquals(listOf("Mix di Anna", "Mix di Bruno", "Mix di Carla"), mixes.map { it.title })
        mixes.forEach { mix ->
            assertTrue(mix.tracks.size <= SpotifyMixes.MIX_SIZE)
            assertEquals(mix.tracks.size, mix.tracks.distinctBy { it.id }.size)
        }
        val anna = mixes.first()
        assertTrue(anna.tracks.first().subtitle.contains("Anna"))
        assertTrue(anna.tracks.map { it.id }.containsAll(listOf("a1", "a2", "a3")))
        assertTrue(anna.subtitle.startsWith("Anna, "))
        assertEquals("img-Anna", anna.image)
    }

    @Test
    fun skipsArtistsWithoutTracksAndChangesDaily() {
        val mixes = SpotifyMixes.build(listOf(artist("Dario"), artist("Anna")), pool, 20_000)
        assertEquals(listOf("Mix di Anna"), mixes.map { it.title })
        val same = SpotifyMixes.build(listOf(artist("Anna")), pool, 20_000).first().tracks
        val tomorrow = SpotifyMixes.build(listOf(artist("Anna")), pool, 20_001).first().tracks
        assertEquals(mixes.first().tracks, same)
        assertTrue(same != tomorrow)
    }

    @Test
    fun noTracksNoMixes() {
        assertTrue(SpotifyMixes.build(listOf(artist("Anna")), emptyList(), 1).isEmpty())
    }
}
