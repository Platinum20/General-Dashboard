package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.FeedEntry
import com.platinum20.dashboard.media.MediaFeed
import com.platinum20.dashboard.media.MediaStories
import com.platinum20.dashboard.media.MediaStory
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.spotify.SpArtistLatest
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyArtist
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 1.481 (Antonio): Media è un feed unico con le storie dei canali e degli artisti in cima, e la pagina artista. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MediaOneFeedTest {
    private val now = Instant.parse("2026-11-11T20:00:00Z")
    private val since = Instant.parse("2026-11-11T08:00:00Z").toEpochMilli()

    private fun video(id: String, channel: String, at: Instant?) = YtVideo(id, "Video $id", channel, channel, at)
    private fun episode(id: String, at: Instant) =
        Episode(id, "Episodio $id", "https://x/$id.mp3", at.toEpochMilli(), 38 * 60L, "", "", "Il Post", "https://x/feed")
    private fun artist(id: String, name: String) = SpItem(id, "spotify:artist:$id", SpKind.ARTIST, name, "Artista", "")

    @Test fun storieDiChiHaiGuardatoEAscoltatoPerUltimoPoiQuelliCheSegui() {
        val channels = listOf(YtChannel("geo", "Geopop", ""), YtChannel("kz", "Kurzgesagt", "//yt3.ggpht.com/kz"), YtChannel("muto", "Muto", ""))
        val recent = listOf(
            video("a", "geo", now.minusSeconds(2 * 3600)).copy(channelAvatar = "https://a/geo"),
            video("b", "kz", now.minusSeconds(3 * 86_400)),
        )
        val artists = listOf(
            SpArtistLatest(artist("pf", "Pink Floyd"), LocalDate.of(2026, 11, 10)),
            SpArtistLatest(artist("nuovo", "Nuovo"), LocalDate.of(2026, 11, 12)),
        )
        val watched = listOf(
            // Vecchio salvataggio senza id: il canale si ritrova dal nome.
            com.platinum20.dashboard.media.youtube.YtWatched("w1", "Video", "Kurzgesagt", "", 0, 0, now.minusSeconds(600).toEpochMilli()),
            com.platinum20.dashboard.media.youtube.YtWatched("w2", "Video", "Geopop", "", 0, 0, now.minusSeconds(7200).toEpochMilli(), "geo"),
        )
        val listened = listOf(com.platinum20.dashboard.media.spotify.SpListened(artist("pf", "Pink Floyd"), now.minusSeconds(3600)))
        val stories = MediaStories.build(watched, listened, channels, recent, artists, since, ZoneOffset.UTC)
        assertEquals(listOf("Kurzgesagt", "Pink Floyd", "Geopop", "Nuovo", "Muto"), stories.map { it.title })
        assertEquals(listOf(false, false, true, true, false), stories.map { it.fresh })
        assertEquals("https://yt3.ggpht.com/kz", stories[0].image)
        assertEquals("https://a/geo", stories[2].image)
        assertEquals(MediaStory.Kind.ARTIST, stories[1].kind)
        // Un artista uscito oggi, con la visita stamattina, non è "nuovo": conta il giorno, non l'ora.
        val today = MediaStories.build(emptyList(), emptyList(), emptyList(), emptyList(), listOf(SpArtistLatest(artist("x", "X"), LocalDate.of(2026, 11, 11))), since, ZoneOffset.UTC)
        assertFalse(today.single().fresh)
    }

    @Test fun artistiAscoltatiDiRecenteDaSpotify() {
        fun play(at: String, vararg ids: String) = JSONObject().put("played_at", at).put(
            "track",
            JSONObject().put("artists", JSONArray().also { a -> ids.forEach { a.put(JSONObject().put("id", it).put("type", "artist").put("name", it.uppercase()).put("uri", "spotify:artist:$it")) } }),
        )
        val json = JSONObject().put(
            "items",
            JSONArray()
                .put(play("2026-11-11T19:00:00Z", "pf", "ospite"))
                .put(play("2026-11-11T18:00:00Z", "rh"))
                .put(play("2026-11-11T17:00:00Z", "pf")),
        )
        val listened = SpotifyArtist.listened(json)
        assertEquals(listOf("pf", "rh"), listened.map { it.artist.id })
        assertEquals(Instant.parse("2026-11-11T19:00:00Z"), listened.first().at)
    }

    @Test fun etaSottoLaStoria() {
        val ms = now.toEpochMilli()
        assertEquals("ora", MediaStories.age(now.minusSeconds(600), ms))
        assertEquals("5 h", MediaStories.age(now.minusSeconds(5 * 3600), ms))
        assertEquals("ieri", MediaStories.age(now.minusSeconds(30 * 3600), ms))
        assertEquals("3 g", MediaStories.age(now.minusSeconds(3 * 86_400), ms))
        assertEquals("2 sett", MediaStories.age(now.minusSeconds(15 * 86_400), ms))
        assertEquals("", MediaStories.age(null, ms))
    }

    @Test fun feedVideoEdEpisodiInsiemeConUltimaVisitaEConsigliatiSotto() {
        val subs = listOf(
            video("v1", "geo", now.minusSeconds(3600)),
            video("v2", "kz", Instant.parse("2026-11-10T10:00:00Z")),
            video("v3", "kz", Instant.parse("2026-11-09T10:00:00Z")),
            video("v4", "kz", Instant.parse("2026-11-08T10:00:00Z")),
        )
        val eps = listOf(episode("e1", now.minusSeconds(7200)), episode("e2", Instant.parse("2026-11-09T12:00:00Z")))
        val tips = listOf(video("v1", "geo", null), video("t1", "x", null), video("t2", "y", null))
        val rows = MediaFeed.build(subs, eps, tips, since)
        assertEquals(listOf("v-v1", "e-e1"), rows.fresh.map { it.key })
        // Sotto: dal più nuovo, un consigliato ogni tre (mai uno già tra le iscrizioni).
        assertEquals(listOf("v-v2", "e-e2", "v-v3", "v-t1", "v-v4"), rows.older.map { it.key })
        assertTrue((rows.older[3] as FeedEntry.Video).recommended)
    }

    @Test fun episodioGiaAscoltatoNonENuovoEPrimaVisitaNienteENuovo() {
        val eps = listOf(episode("e1", now.minusSeconds(7200)))
        assertTrue(MediaFeed.build(emptyList(), eps, emptyList(), since, listened = setOf("e1")).fresh.isEmpty())
        val first = MediaFeed.build(listOf(video("v1", "geo", now)), eps, emptyList(), since = 0L)
        assertTrue(first.fresh.isEmpty())
        assertEquals(2, first.older.size)
    }

    @Test fun ilFeedFinisceCosiLeRigheInFondoSiRaggiungono() {
        val many = (1..80).map { video("v$it", "c", Instant.parse("2026-10-01T00:00:00Z").plusSeconds(it * 3600L)) }
        val rows = MediaFeed.build(many, emptyList(), emptyList(), since, maxOlder = 30)
        assertEquals(30, rows.older.size)
        // Senza niente di vecchio, qualche consigliato da solo.
        assertEquals(6, MediaFeed.build(emptyList(), emptyList(), (1..20).map { video("t$it", "x", null) }, since).older.size)
    }

    @Test fun rigaDellEpisodio() {
        val line = MediaStories.episodeLine(episode("e1", now.minusSeconds(3 * 3600)), now.toEpochMilli())
        assertEquals("Il Post · 3 h fa", line)
    }

    private fun track(id: String, title: String, vararg artistIds: String) = JSONObject()
        .put("id", id).put("type", "track").put("name", title).put("uri", "spotify:track:$id").put("duration_ms", 252_000)
        .put("artists", JSONArray().also { a -> artistIds.forEach { a.put(JSONObject().put("id", it).put("name", it.uppercase())) } })

    @Test fun popolariDallaRicercaSoloDellArtistaSenzaDoppioni() {
        val page = JSONObject().put(
            "tracks",
            JSONObject().put(
                "items",
                JSONArray()
                    .put(track("1", "Time", "pf"))
                    .put(track("2", "Time - Remastered", "pf"))
                    .put(track("3", "Cover di Time", "altro"))
                    .put(track("4", "Money", "pf", "ospite")),
            ),
        )
        val popular = SpotifyArtist.popular(listOf(page, null), "pf")
        assertEquals(listOf("Time", "Money"), popular.map { it.title })
        assertEquals(252_000L, popular.first().durationMs)
    }

    @Test fun ultimaUscitaEDateDiSpotify() {
        val albums = JSONObject().put(
            "items",
            JSONArray()
                .put(JSONObject().put("release_date", "1973"))
                .put(JSONObject().put("release_date", "2026-10"))
                .put(JSONObject().put("release_date", "2025-03-02")),
        )
        assertEquals(LocalDate.of(2026, 10, 1), SpotifyArtist.latestRelease(albums))
        assertEquals(LocalDate.of(1973, 1, 1), SpotifyArtist.date("1973"))
        assertNull(SpotifyArtist.latestRelease(null))
    }

    @Test fun followerEArtistiSeguiti() {
        assertEquals("1,2 Mln di follower", SpotifyArtist.followersLine(1_234_000))
        assertEquals("15.300 follower", SpotifyArtist.followersLine(15_300))
        assertEquals("", SpotifyArtist.followersLine(0))
        val json = JSONObject().put(
            "artists",
            JSONObject()
                .put("items", JSONArray().put(JSONObject().put("id", "pf").put("type", "artist").put("name", "Pink Floyd").put("uri", "spotify:artist:pf")))
                .put("cursors", JSONObject().put("after", "pf")),
        )
        val (items, after) = SpotifyArtist.following(json)
        assertEquals("Pink Floyd", items.single().title)
        assertEquals("pf", after)
    }

    @Test fun canaleComeSuYouTube() {
        val all = setOf(
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS,
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.SHORTS,
        )
        assertEquals(listOf("Video", "Shorts", "Playlist"), com.platinum20.dashboard.ui.screens.channelTabs(all).map { it.second })
        assertEquals(listOf("Video"), com.platinum20.dashboard.ui.screens.channelTabs(null).map { it.second })
        assertEquals("", com.platinum20.dashboard.ui.screens.channelLine(-1, false))
        assertTrue(com.platinum20.dashboard.ui.screens.channelLine(15_300, true).endsWith("iscritti · Verificato"))
    }
}
