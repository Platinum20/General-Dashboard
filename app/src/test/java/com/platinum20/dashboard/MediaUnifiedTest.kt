package com.platinum20.dashboard

import com.platinum20.dashboard.media.EngineNow
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastFeed
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.spotify.SpEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaUnifiedTest {
    private fun engine(kind: ResumeKind, title: String = "Titolo", subtitle: String = "Sotto", playing: Boolean = false, loading: Boolean = false) =
        EngineNow(1, ResumeItem(kind, "k", title, subtitle, "https://img", at = 0, station = if (kind == ResumeKind.RADIO) RadioStation("r", title, "http://s", "", "", "", 128) else null), playing = playing, loading = loading, positionMs = 5_000, durationMs = 60_000)

    @Test
    fun ogniFonteHaLaSuaEtichetta() {
        assertEquals(PlaySource.VIDEO, Playback.playing(engine(ResumeKind.YOUTUBE)).source)
        assertEquals(PlaySource.MUSIC, Playback.playing(engine(ResumeKind.MUSIC)).source)
        assertEquals(PlaySource.SPOTIFY, Playback.playing(engine(ResumeKind.SPOTIFY)).source)
        assertEquals(PlaySource.LOCAL, Playback.playing(engine(ResumeKind.VIDEO)).source)
    }

    @Test
    fun soloIVideoHannoIlVideo() {
        assertTrue(Playback.playing(engine(ResumeKind.YOUTUBE)).isVideo)
        assertFalse(Playback.playing(engine(ResumeKind.MUSIC)).isVideo)
        assertFalse(Playback.playing(engine(ResumeKind.SPOTIFY)).isVideo)
    }

    @Test
    fun laRadioEInDirettaConIlBrano() {
        val radio = Playback.playing(engine(ResumeKind.RADIO, title = "Radio Uno", playing = true).copy(streamTitle = "Artista - Brano"))
        assertTrue(radio.live)
        assertFalse(radio.canSeek)
        assertEquals("Radio Uno", radio.title)
        assertEquals("Artista - Brano", radio.subtitle)
    }

    @Test
    fun senzaTitoloSiVedeLaFonteEIlCaricamento() {
        val loading = Playback.playing(engine(ResumeKind.YOUTUBE, title = "", subtitle = "", loading = true))
        assertEquals("YouTube", loading.title)
        assertEquals("Caricamento…", loading.subtitle)
    }

    @Test
    fun spotifyFinitoDaSolo() {
        // Suonava a 2:58 di 3:00, due secondi dopo è fermo all'inizio: finito.
        assertTrue(SpotifyPlayer.endedForTest(178_000, false, 0, true, 180_000, 2_000))
        // Messo in pausa a metà: non è finito.
        assertFalse(SpotifyPlayer.endedForTest(90_000, false, 90_500, true, 180_000, 500))
        // Già in pausa prima: niente.
        assertFalse(SpotifyPlayer.endedForTest(178_000, true, 0, true, 180_000, 2_000))
    }

    @Test
    fun spotifyPassatoOltreDaSolo() {
        val before = SpotifyPlayer.Reading("spotify:track:a", false, 178_000, 180_000, 0)
        // A fine brano Spotify suona un altro brano suo: per noi è finito.
        assertTrue(SpotifyPlayer.movedOn(before, SpotifyPlayer.Reading("spotify:track:x", false, 1_000, 200_000, 2_000)))
        // A metà brano un'altra lettura non vuol dire che è finito.
        assertFalse(SpotifyPlayer.movedOn(before.copy(positionMs = 60_000), SpotifyPlayer.Reading("spotify:track:x", false, 1_000, 200_000, 2_000)))
        // Stesso brano in un'altra copia (relinking): è sempre il nostro.
        val relinked = SpotifyPlayer.Reading("spotify:track:b", false, 179_000, 180_000, 1_000, linked = "spotify:track:a")
        assertTrue(relinked.isFor("spotify:track:a"))
        assertFalse(SpotifyPlayer.movedOn(before, relinked))
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
