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
}
