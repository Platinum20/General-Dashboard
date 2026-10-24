package com.platinum20.dashboard

import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Il mini player passa subito alla fonte chiesta e non torna a quelle messe da parte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerClaimTest {
    private val videoPaused = Playing(PlaySource.VIDEO, "Video", "Canale", "", playing = false)
    private val spotifyOld = Playing(PlaySource.SPOTIFY, "Brano vecchio", "", "", playing = false)
    private val placeholder = Playing(PlaySource.SPOTIFY, "Album nuovo", "Artista", "", playing = false, loading = true)

    @Test
    fun spotifyAppareSubitoMentreSiCarica() {
        val claim = Playback.Claim(PlaySource.SPOTIFY, placeholder)
        val shown = Playback.choose(listOf(videoPaused, spotifyOld), PlaySource.SPOTIFY, claim, setOf(PlaySource.VIDEO))
        assertEquals("Album nuovo", shown?.title)
    }

    @Test
    fun quandoSuonaVinceIlVero() {
        val claim = Playback.Claim(PlaySource.SPOTIFY, placeholder)
        val real = spotifyOld.copy(title = "Brano", playing = true)
        assertEquals("Brano", Playback.choose(listOf(videoPaused, real), PlaySource.SPOTIFY, claim, emptySet())?.title)
    }

    @Test
    fun erroreDiSpotifySiVede() {
        val claim = Playback.Claim(PlaySource.SPOTIFY, placeholder)
        val shown = Playback.choose(emptyList(), PlaySource.SPOTIFY, claim, emptySet(), spotifyError = "Serve Premium")
        assertEquals("Serve Premium", shown?.error)
    }

    @Test
    fun chiusoNonTornaIlVideoDiPrima() {
        val shown = Playback.choose(listOf(videoPaused, spotifyOld), PlaySource.SPOTIFY, null, setOf(PlaySource.VIDEO, PlaySource.SPOTIFY))
        assertNull(shown)
        assertEquals(PlaySource.VIDEO, Playback.family(PlaySource.MUSIC))
    }
}
