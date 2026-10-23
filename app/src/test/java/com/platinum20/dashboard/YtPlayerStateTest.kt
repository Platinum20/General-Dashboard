package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.youtube.player.YtComment
import com.platinum20.dashboard.media.youtube.player.YtComments
import com.platinum20.dashboard.media.youtube.player.YtNow
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class YtPlayerStateTest {
    private val id = "dQw4w9WgXcQ"
    private val link = "https://www.youtube.com/watch?v=$id"
    private val stream = YtStream(id, "Titolo", "Canale", "", null, null, "https://x/v.mp4", null, 360)

    @Test
    fun statoDeiCommenti() {
        assertEquals(YtComments.Loading, YtComments.from(null))
        assertEquals(YtComments.Failed, YtComments.from(Result.failure(IOException("rete"))))
        assertEquals(YtComments.Empty, YtComments.from(Result.success(emptyList())))
        val c = YtComment("@antonio", "Bello", 3, "1 giorno fa", "", 0)
        assertEquals(YtComments.Loaded(listOf(c)), YtComments.from(Result.success(listOf(c))))
    }

    @Test
    fun stessoVideoInPausaNonRiparte() {
        val paused = YtNow(link, stream, playing = false, loading = false)
        assertTrue(YtPlayer.isLoaded(paused, link))
        assertTrue(YtPlayer.isLoaded(paused, "https://youtu.be/$id?t=10"))
        // Ancora in caricamento (flussi non arrivati): niente seconda richiesta.
        assertTrue(YtPlayer.isLoaded(YtNow(link, null), "https://youtu.be/$id"))
    }

    @Test
    fun altroVideoOErroreSiRicarica() {
        assertFalse(YtPlayer.isLoaded(null, link))
        assertFalse(YtPlayer.isLoaded(YtNow(link, stream, loading = false), "https://youtu.be/aaaaaaaaaaa"))
        assertFalse(YtPlayer.isLoaded(YtNow(link, stream, loading = false, error = "Video non riproducibile"), link))
        assertFalse(YtPlayer.isLoaded(YtNow(link, stream), "non è un link"))
    }

    @Test
    fun vocePerRiprendiConIlPuntoGiusto() {
        // V22: passando da A a B, A resta in Riprendi al punto in cui eri (qui 2:00 su 10:00).
        val a = YtPlayer.resumeEntry(stream, positionMs = 120_000, playerDurationMs = 600_000, at = 5)
        assertEquals(id, a.key)
        assertEquals(120_000L, a.positionMs)
        assertEquals(600_000L, a.durationMs)
        // Quasi alla fine: barra piena, la prossima volta riparte da capo.
        assertEquals(600_000L, YtPlayer.resumeEntry(stream, 597_000, 600_000, 5).positionMs)
        // Durata non ancora nota al player: quella del video.
        assertEquals(90_000L, YtPlayer.resumeEntry(stream.copy(durationSec = 90), 10_000, 0, 5).durationMs)
        // Diretta: nessun punto.
        assertEquals(0L, YtPlayer.resumeEntry(stream.copy(hlsUrl = "https://x/live.m3u8"), 50_000, 0, 5).positionMs)
    }

    @Test
    fun ascoltatoComeMusicaRiparteComeMusica() {
        assertEquals(com.platinum20.dashboard.media.ResumeKind.MUSIC, YtPlayer.resumeEntry(stream, 1_000, 600_000, 5, audioOnly = true).kind)
        assertEquals(com.platinum20.dashboard.media.ResumeKind.YOUTUBE, YtPlayer.resumeEntry(stream, 1_000, 600_000, 5).kind)
    }
}
