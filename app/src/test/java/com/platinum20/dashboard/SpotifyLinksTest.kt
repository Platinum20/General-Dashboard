package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.spotify.SpotifyApiException
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SpotifyLinksTest {
    @Test
    fun uriDaiLinkDiSpotify() {
        assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC", SpotifyPlayer.spotifyUri("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC", SpotifyPlayer.spotifyUri("https://open.spotify.com/intl-it/track/4uLU6hMCjMI75M1A2tKUQC?si=abc123"))
        assertEquals("spotify:album:1DFixLWuPkv3KT3TnV35m3", SpotifyPlayer.spotifyUri("  spotify:album:1DFixLWuPkv3KT3TnV35m3 "))
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", SpotifyPlayer.spotifyUri("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals("spotify:episode:512ojhOuo1ktJprKbVcKyQ", SpotifyPlayer.spotifyUri("https://open.spotify.com/episode/512ojhOuo1ktJprKbVcKyQ"))
    }

    @Test
    fun linkNonValidi() {
        assertNull(SpotifyPlayer.spotifyUri("https://example.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertNull(SpotifyPlayer.spotifyUri("https://open.spotify.com/user/antonio"))
        assertNull(SpotifyPlayer.spotifyUri("https://open.spotify.com/track"))
        assertNull(SpotifyPlayer.spotifyUri("spotify:user:antonio"))
    }

    @Test
    fun messaggiInItaliano() {
        assertEquals("Ricollega Spotify con la chiave in alto per ascoltare nell'app", SpotifyPlayer.spotifyMessage(SpotifyApiException("Accesso a Spotify scaduto", 401)))
        assertEquals("Per comandare la riproduzione serve Spotify Premium", SpotifyPlayer.spotifyMessage(SpotifyApiException("Per comandare la riproduzione serve Spotify Premium", 403)))
        assertEquals("Il riproduttore di Spotify si è scollegato: riprova tra qualche secondo", SpotifyPlayer.spotifyMessage(SpotifyApiException("Device not found", 404)))
        assertEquals("Troppe richieste a Spotify, riprova tra poco", SpotifyPlayer.spotifyMessage(SpotifyApiException("Troppe richieste a Spotify, riprova tra poco", 429)))
        assertEquals("Nessuna connessione: Spotify non è raggiungibile", SpotifyPlayer.spotifyMessage(UnknownHostException("api.spotify.com")))
        val generic = SpotifyPlayer.spotifyMessage(IOException("unexpected end of stream"))
        assertFalse(generic.contains("unexpected"))
    }
}
