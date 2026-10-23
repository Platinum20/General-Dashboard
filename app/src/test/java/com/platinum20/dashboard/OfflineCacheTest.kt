package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.spotify.SpHome
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpLibrary
import com.platinum20.dashboard.media.spotify.SpProfile
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.net.DiskCache
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsSources
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.news.reader.ArticleVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** I dati salvati per l'avvio veloce (notizie, giudizi sugli articoli, feed) devono tornare uguali. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OfflineCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val ansa = NewsSources.all.first { it.id == "ansa" }

    @Test
    fun notizieTornanoUguali() {
        val items = listOf(
            NewsItem("Titolo", "https://www.ansa.it/a-1.html", "Sommario", "https://img/x.jpg", Instant.ofEpochMilli(1_000_000), ansa),
            NewsItem("Senza data", "https://www.ansa.it/a-2.html", "", null, null, ansa),
        )
        val (back, savedAt) = NewsStore.decode(NewsStore.encode(items, 42L))
        assertEquals(items, back)
        assertEquals(42L, savedAt)
    }

    @Test
    fun notizieDiFontiTolteVengonoScartate() {
        val gone = ansa.copy(id = "ilpost")
        val text = NewsStore.encode(listOf(NewsItem("X", "https://x/1-a", "", null, null, gone)), 1L)
        assertTrue(NewsStore.decode(text).first.isEmpty())
    }

    @Test
    fun giudiziVecchiSiScartano() {
        val now = 10L * 24 * 3_600_000
        val verdicts = mapOf(
            "https://a/1" to (true to now - 1_000),
            "https://a/2" to (false to now - 2_000),
            "https://a/vecchio" to (true to now - 5L * 24 * 3_600_000),
        )
        val back = ArticleVerifier.decode(ArticleVerifier.encode(verdicts, now), now)
        assertEquals(setOf("https://a/1", "https://a/2"), back.keys)
        assertEquals(true, back["https://a/1"]?.first)
        assertEquals(false, back["https://a/2"]?.first)
    }

    @Test
    fun feedYouTubeTornanoUguali() {
        val videos = listOf(
            YtVideo("abc", "Video", "Canale", "UC1", Instant.ofEpochMilli(5_000), isShort = true, durationSec = 61,
                live = true, channelAvatar = "https://a", views = 1234, uploadedText = "3 giorni fa", watchedPercent = 40),
            YtVideo("def", "Altro", "Canale", "UC1", null),
        )
        val codec = FeedCache.videos("yt-prova.json")
        assertEquals(videos, FeedCache.decode(codec, FeedCache.encode(codec, videos, 1L)))
        val playlists = listOf(YtPlaylist("PL1", "Musica", "https://t", 12))
        assertEquals(playlists, FeedCache.decode(FeedCache.playlists, FeedCache.encode(FeedCache.playlists, playlists, 1L)))
    }

    @Test
    fun pagineSpotifyTornanoUguali() {
        val track = SpItem("t1", "spotify:track:t1", SpKind.TRACK, "Brano", "Artista", "https://i")
        val artist = SpItem("a1", "spotify:artist:a1", SpKind.ARTIST, "Artista", "", "")
        val home = SpHome(SpProfile("Antonio", premium = true), listOf(track), listOf(track), listOf(artist))
        assertEquals(home, FeedCache.decode(FeedCache.spHome, FeedCache.encode(FeedCache.spHome, home, 1L)))
        val library = SpLibrary(listOf(track), emptyList(), listOf(artist))
        assertEquals(library, FeedCache.decode(FeedCache.spLibrary, FeedCache.encode(FeedCache.spLibrary, library, 1L)))
    }

    @Test
    fun fileSuDiscoESlegareAccount() = runBlocking {
        DiskCache.initAt(folder.root)
        try {
            val codec = FeedCache.videos("yt-iscrizioni.json")
            val videos = listOf(YtVideo("abc", "Video", "Canale", "UC1", null))
            FeedCache.save(codec, videos, System.currentTimeMillis())
            assertEquals(videos, FeedCache.restore(codec)?.data)
            // Scollegando YouTube i feed salvati spariscono.
            FeedCache.clear("yt-")
            assertNull(FeedCache.restore(codec))
            // Un file rovinato vale come cache vuota, senza errori.
            DiskCache.write("yt-iscrizioni.json", "{rotto")
            assertNull(FeedCache.restore(codec))
        } finally {
            DiskCache.initAt(null)
        }
    }
}
