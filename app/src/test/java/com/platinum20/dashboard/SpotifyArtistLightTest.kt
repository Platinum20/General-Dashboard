package com.platinum20.dashboard

import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.spotify.SpArtistPage
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpReleaseGroup
import com.platinum20.dashboard.media.spotify.SpotifyArtist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SpotifyArtistLightTest {
    private fun album(id: String) = SpItem(id, "spotify:album:$id", SpKind.ALBUM, "Album $id", "", "")
    private fun page(id: String, albums: Int = 10) = SpArtistPage(
        artist = SpItem(id, "spotify:artist:$id", SpKind.ARTIST, "Artista $id", "", ""),
        photo = "", followers = 5, genres = listOf("rock"), popular = emptyList(),
        albums = (1..albums).map { album("a$it") }, singles = emptyList(), appearsOn = emptyList(),
        released = mapOf("a1" to LocalDate.of(1973, 3, 1)),
        totals = mapOf(SpReleaseGroup.ALBUM to 42, SpReleaseGroup.SINGLE to 7),
    )

    @Test
    fun onlyTheMissingPagesAreAsked() {
        // 42 album, 10 già qui: mancano le pagine da 10, 20, 30, 40.
        assertEquals(listOf(10, 20, 30, 40), SpotifyArtist.missingOffsets(42, 10, 10, 500))
        assertEquals(emptyList<Int>(), SpotifyArtist.missingOffsets(8, 8, 10, 500))
        assertEquals(listOf(10, 20, 30, 40), SpotifyArtist.missingOffsets(2000, 10, 10, 50))
    }

    @Test
    fun totalCountsWhatIsNotLoadedYet() {
        assertEquals(49, page("x").ownTotal)
    }

    @Test
    fun keptListIsSmallAndNewestFirst() {
        val old = (1..30).map { it.toLong() to page("p$it") }
        val kept = SpotifyArtist.kept(old, 99L to page("p5"), 30)
        assertEquals(30, kept.size)
        assertEquals("p5", kept.first().second.artist.id)
        assertEquals(1, kept.count { it.second.artist.id == "p5" })
    }

    @Test
    fun savedPageComesBackTheSame() {
        val list = listOf(7L to page("z"))
        val text = FeedCache.encode(FeedCache.spArtistPages, list, 7L)
        val back = FeedCache.decode(FeedCache.spArtistPages, text)
        assertEquals(list, back)
        assertTrue(text.length < 5_000)
    }
}
