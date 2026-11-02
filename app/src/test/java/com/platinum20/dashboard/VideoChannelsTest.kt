package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.channelLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Scheda Canali di Video e l'impostazione "Riproduci il video successivo". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoChannelsTest {
    private fun v(id: String, channelId: String, at: Instant?, avatar: String = "") =
        YtVideo(id, "Video $id", "Canale", channelId, at, channelAvatar = avatar)

    @Test fun canaliRecentiPrimaPoiAlfabetici() {
        val channels = listOf(
            YtChannel("z", "Zeta", ""),
            YtChannel("b", "beta", ""),
            YtChannel("a", "Alfa", ""),
            YtChannel("n", "Nuovo", "foto"),
            YtChannel("n", "Nuovo doppio", ""),
        )
        val recent = listOf(
            v("1", "a", Instant.parse("2026-10-20T10:00:00Z"), avatar = "alfa.jpg"),
            v("2", "n", Instant.parse("2026-10-26T10:00:00Z")),
            v("3", "a", Instant.parse("2026-10-25T10:00:00Z")),
        )
        val lines = channelLines(channels, recent)
        assertEquals(listOf("n", "a", "b", "z"), lines.map { it.channel.id })
        assertEquals(Instant.parse("2026-10-25T10:00:00Z"), lines[1].latest)
        assertEquals("alfa.jpg", lines[1].avatar)
        assertEquals("foto", lines[0].avatar)
        assertNull(lines[2].latest)
    }

    @Test fun videoSuccessivoSiSceglieERestaScelto() {
        AppSettings.init(ApplicationProvider.getApplicationContext())
        assertTrue(AppSettings.autoplayNext.value)
        AppSettings.setAutoplayNext(false)
        AppSettings.init(ApplicationProvider.getApplicationContext())
        assertFalse(AppSettings.autoplayNext.value)
        AppSettings.setAutoplayNext(true)
    }
}
