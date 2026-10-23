package com.platinum20.dashboard

import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Track
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastFeed
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.spotify.SpEpisode
import com.platinum20.dashboard.media.youtube.player.YtNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaUnifiedTest {
    private val ytPaused = YtNow(link = "https://youtu.be/abcdefghijk", stream = null, playing = false, loading = false)
    private val spotifyPlaying = Track(MediaService.SPOTIFY, "Brano", "Artista", "", playing = true)

    @Test
    fun ilPlayerUnicoMostraQuelloCheSuona() {
        val all = Playback.candidates(ytPaused, spotifyPlaying, null, null, null)
        assertEquals(PlaySource.SPOTIFY, Playback.pick(all, last = PlaySource.VIDEO)?.source)
    }

    @Test
    fun tuttoInPausaVinceLUltimoPartito() {
        val all = Playback.candidates(ytPaused, spotifyPlaying.copy(playing = false), null, null, null)
        assertEquals(PlaySource.SPOTIFY, Playback.pick(all, last = PlaySource.SPOTIFY)?.source)
        assertEquals(PlaySource.VIDEO, Playback.pick(all, last = null)?.source)
        assertNull(Playback.pick(emptyList(), last = PlaySource.RADIO))
    }

    @Test
    fun youTubeInSoloAudioEMusica() {
        val all = Playback.candidates(ytPaused.copy(audioOnly = true), null, null, null, null)
        assertEquals(PlaySource.MUSIC, all.single().source)
        assertTrue(!all.single().isVideo)
    }

    @Test
    fun podcastDiSpotifySenzaDoppioni() {
        val rss = Podcast("1", "Il Caffè - Podcast", "Autore", "https://feed.example/rss", "")
        val spotify = listOf(
            Podcast("a", "Il caffè", "", "spotify:show:a", "", fromSpotify = true),
            Podcast("b", "Solo su Spotify", "", "spotify:show:b", "", fromSpotify = true),
        )
        val merged = PodcastStore.merge(listOf(rss), spotify)
        assertEquals(listOf("1", "b"), merged.map { it.id })
    }

    @Test
    fun feedTrovatoPerTitolo() {
        val results = listOf(
            Podcast("1", "Altro", "", "https://a", ""),
            Podcast("2", "Muschio Selvaggio", "", "https://b", ""),
        )
        assertEquals("https://b", PodcastFeed.matchFeed("Muschio selvaggio podcast", results)?.feedUrl)
        assertNull(PodcastFeed.matchFeed("Inesistente", results))
    }

    @Test
    fun episodioDiSpotifySuonaConSpotify() {
        val show = Podcast("b", "Solo su Spotify", "", "spotify:show:b", "https://img", fromSpotify = true)
        val ep = PodcastFeed.fromSpotify(SpEpisode("e1", "spotify:episode:e1", "Puntata 1", "2026-10-20", 1_800_000, "<p>Ciao</p>", ""), show)
        assertEquals("spotify:episode:e1", ep.audioUrl)
        assertEquals(1800, ep.durationSec)
        assertEquals("https://img", ep.image)
        assertTrue(ep.publishedAt > 0)
        assertEquals(0L, PodcastFeed.spotifyDate("non una data"))
    }
}
