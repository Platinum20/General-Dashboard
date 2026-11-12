package com.platinum20.dashboard

import com.platinum20.dashboard.media.lastfm.FmTrack
import com.platinum20.dashboard.media.lastfm.LastFmParse
import com.platinum20.dashboard.media.lyrics.Lrc
import com.platinum20.dashboard.media.spotify.MadeForYou
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpRelease
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MadeForYouTest {
    private fun track(id: String, title: String, artist: String) =
        SpItem(id, "spotify:track:$id", SpKind.TRACK, title, artist, "img-$id")

    @Test
    fun radarAndDiscoverDays() {
        val thursday = LocalDate.of(2026, 11, 12)
        assertEquals(LocalDate.of(2026, 11, 6), MadeForYou.radarDay(thursday))
        assertEquals(LocalDate.of(2026, 11, 13), MadeForYou.radarDay(LocalDate.of(2026, 11, 13)))
        assertEquals(LocalDate.of(2026, 11, 9), MadeForYou.discoverDay(thursday))
    }

    @Test
    fun radarTakesTwoPerReleaseNewestFirstAndWidensWhenFew() {
        val friday = LocalDate.of(2026, 11, 6)
        fun rel(id: String, date: LocalDate, n: Int) = SpRelease(SpItem(id, "u", SpKind.ALBUM, id, "A", "cover-$id"), date) to
            (1..n).map { SpItem("$id-$it", "spotify:track:$id-$it", SpKind.TRACK, "t$it", "A", "") }
        val list = listOf(rel("old", friday.minusDays(20), 3), rel("new", friday, 3), rel("future", friday.plusDays(1), 3))
        val tracks = MadeForYou.radar(list, friday)
        // Poche uscite in settimana: si va indietro; mai quelle dopo il venerdì.
        assertEquals(listOf("new-1", "new-2", "old-1", "old-2"), tracks.map { it.id })
        assertEquals("cover-new", tracks.first().image)
    }

    @Test
    fun discoverSkipsKnownAndCapsArtists() {
        val similar = listOf(
            "Seed" to listOf(FmTrack("A1", "Alfa"), FmTrack("A2", "Alfa"), FmTrack("A3", "Alfa"), FmTrack("Known", "Beta")),
            "Seed2" to listOf(FmTrack("G1", "Gamma")),
        )
        val known = setOf(FmTrack("Known", "Beta").key)
        val picks = MadeForYou.discover(similar, known, seed = 1)
        assertEquals(setOf("A1", "A2", "G1"), picks.map { it.first.title }.toSet())
        assertEquals("Seed", picks.first { it.first.title == "A1" }.second)
    }

    @Test
    fun genresPutEachArtistInItsMostCommonGenre() {
        val groups = MadeForYou.genres(
            listOf(
                "Uno" to listOf("indie", "pop"),
                "Due" to listOf("pop"),
                "Tre" to listOf("rap"),
                "Quattro" to listOf("pop", "rap"),
            ),
        )
        assertEquals("pop", groups.first().first)
        assertEquals(listOf("Uno", "Due", "Quattro"), groups.first().second)
        assertEquals(listOf("Tre"), groups.first { it.first == "rap" }.second)
    }

    @Test
    fun dailyMixNeedsEnoughOwnTracksAndStartsWithOne() {
        val pool = (1..8).map { track("p$it", "Pop $it", "Uno") } + (1..2).map { track("r$it", "Rap $it", "Tre") }
        val extras = listOf(track("x1", "Simile", "Altro") to "Uno")
        val mixes = MadeForYou.daily(listOf("pop" to listOf("Uno"), "rap" to listOf("Tre")), pool, extras, mapOf("uno" to "foto"), 100)
        assertEquals(1, mixes.size)
        val mix = mixes.single()
        assertEquals("Daily Mix 1", mix.title)
        assertEquals("Pop", mix.badge)
        assertEquals("foto", mix.image)
        assertEquals(9, mix.tracks.size)
        assertTrue(mix.tracks.first().subtitle == "Uno")
        assertTrue(mix.tracks.any { it.id == "x1" })
    }

    @Test
    fun matchWantsTheSameArtist() {
        val found = listOf(track("1", "Song (Remastered)", "Other"), track("2", "Song - Live", "The Band"), track("3", "Song", "The Band"))
        assertEquals("3", MadeForYou.match(FmTrack("Song", "The Band"), found)?.id)
        assertNull(MadeForYou.match(FmTrack("Song", "Nobody"), found))
    }

    @Test
    fun radioStartsFromSeedAndRadiosStayFive() {
        val seed = track("s", "Seme", "Uno")
        val radio = MadeForYou.radio(seed, listOf(track("a", "A", "Due"), track("s2", "Seme", "Uno")))
        assertEquals(listOf("s", "a"), radio.tracks.map { it.id })
        assertEquals("radio-s", radio.id)
        var radios = emptyList<com.platinum20.dashboard.media.spotify.SpMix>()
        repeat(7) { radios = MadeForYou.addRadio(radios, radio.copy(id = "radio-$it")) }
        radios = MadeForYou.addRadio(radios, radio.copy(id = "radio-4"))
        assertEquals(listOf("radio-4", "radio-6", "radio-5", "radio-3", "radio-2"), radios.map { it.id })
    }

    @Test
    fun mixSurvivesJson() {
        val mix = MadeForYou.playlist("made-x", "T", "S", "Top 50", listOf(track("1", "a", "b"), track("1", "a", "b")))!!
        assertEquals(1, mix.tracks.size)
        assertEquals(mix, MadeForYou.mixFrom(JSONObject(MadeForYou.mixJson(mix).toString())))
    }

    @Test
    fun lastFmParsing() {
        val json = JSONObject("""{"similartracks":{"track":[{"name":"Uno","artist":{"name":"Alfa"}},{"name":"uno (feat. X)","artist":{"name":"alfa"}},{"name":"","artist":{"name":"Z"}}]}}""")
        assertEquals(listOf(FmTrack("Uno", "Alfa")), LastFmParse.tracks(json, "similartracks"))
        val tags = JSONObject("""{"toptags":{"tag":[{"name":"Indie Pop"},{"name":"seen live"},{"name":"2010s"},{"name":"rock"}]}}""")
        assertEquals(listOf("indie pop", "rock"), LastFmParse.tags(tags))
        assertEquals("song", LastFmParse.norm("Song (Remastered 2011)"))
        assertEquals("a b", LastFmParse.norm("A & B feat. C"))
    }

    @Test
    fun lrcParsingAndCurrentLine() {
        val lines = Lrc.parse("[ar:x]\n[00:01.50]Prima\n[00:03.00][00:10.00]Ritornello\n[00:05.123]Terza")
        assertEquals(listOf(1500L, 3000L, 5123L, 10000L), lines.map { it.atMs })
        assertEquals("Ritornello", lines.last().text)
        assertEquals(-1, Lrc.index(lines, 1000))
        assertEquals(1, Lrc.index(lines, 4000))
        assertEquals(3, Lrc.index(lines, 60000))
    }

    @Test
    fun lrcPicksSyncedResultWithCloseDuration() {
        val results = JSONArray(
            """[{"duration":300,"syncedLyrics":"[00:01.00]lontano"},{"duration":201,"plainLyrics":"solo testo"},{"duration":199,"syncedLyrics":"[00:01.00]giusto"}]"""
        )
        val best = Lrc.pick(results, 200)!!
        assertEquals("[00:01.00]giusto", best.optString("syncedLyrics"))
        val lyrics = Lrc.from(best)!!
        assertTrue(lyrics.synced)
        assertNull(Lrc.pick(results, 100))
        assertEquals("Song", Lrc.cleanTitle("Song - Remastered 2011"))
        assertEquals("Song", Lrc.cleanTitle("Song (feat. Someone)"))
    }
}
