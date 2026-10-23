package com.platinum20.dashboard

import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.Track
import com.platinum20.dashboard.media.radio.RadioNow
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.youtube.player.YtNow
import com.platinum20.dashboard.media.youtube.player.YtStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Salva nelle note": quello che suona diventa una voce da riaprire, ferma al punto giusto. */
class SaveCurrentMediaTest {
    private val stream = YtStream(
        id = "abcdefghijk", title = "Video", channel = "Canale", thumbnail = "",
        videoUrl = null, audioUrl = "a", muxedUrl = null, hlsUrl = null, height = 0, durationSec = 600,
    )

    private fun item(source: PlaySource, yt: YtNow? = null, track: Track? = null, radio: RadioNow? = null) =
        Playback.itemFor(source, yt, 90_000, 600_000, track, null, radio, null, at = 1L)

    @Test
    fun videoEMusicaConIlPunto() {
        val video = item(PlaySource.VIDEO, yt = YtNow(link = "x", stream = stream))!!
        assertEquals(ResumeKind.YOUTUBE, video.kind)
        assertEquals(90_000, video.positionMs)
        val music = item(PlaySource.MUSIC, yt = YtNow(link = "x", stream = stream, audioOnly = true))!!
        assertEquals(ResumeKind.MUSIC, music.kind)
    }

    @Test
    fun spotifyConAlbumERadio() {
        val track = Track(MediaService.SPOTIFY, "Brano", "Artista", "", playing = true, positionMs = 5_000, uri = "spotify:track:1", contextUri = "spotify:album:2")
        val sp = item(PlaySource.SPOTIFY, track = track)!!
        assertEquals("spotify:track:1", sp.key)
        assertEquals("spotify:album:2", sp.link)
        val station = RadioStation("r", "Radio", "http://s", "", "", "", 128)
        assertEquals(station, item(PlaySource.RADIO, radio = RadioNow(station, playing = true, buffering = false, track = "", error = null))!!.station)
    }

    @Test
    fun laLetturaNonSiSalva() {
        assertNull(item(PlaySource.SPEECH))
        assertNull(item(PlaySource.SPOTIFY, track = Track(MediaService.SPOTIFY, "Brano", "", "", playing = true)))
    }
}
