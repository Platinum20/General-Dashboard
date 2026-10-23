package com.platinum20.dashboard

import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtWatched
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtHistoryTest {
    private fun watched(id: String, pos: Long, at: Long) = YtWatched(id, "Video $id", "Canale", "", pos, 600_000, at)

    @Test
    fun svuotaERipristinaRimetteTuttoCoiPunti() {
        // V24: "Svuota" si può annullare, con le posizioni di prima.
        val before = listOf(watched("a", 120_000, 3), watched("b", 45_000, 2), watched("c", 0, 1))
        YtHistory.preview(before)
        val removed = YtHistory.clear()
        assertTrue(YtHistory.items.value.isEmpty())
        YtHistory.restore(removed)
        assertEquals(before.associateBy { it.id }, YtHistory.items.value)
        assertEquals(120_000L, YtHistory.resumeMs("a"))
    }

    @Test
    fun svuotaToglieYouTubeDaRiprendiMaNonLaRadio() {
        val yt = ResumeItem(ResumeKind.YOUTUBE, "a", "Video a", positionMs = 120_000, durationMs = 600_000, at = 3)
        val radio = ResumeItem(ResumeKind.RADIO, "deejay", "Radio Deejay", at = 2)
        MediaHistory.preview(listOf(yt, radio))
        val removed = MediaHistory.removeKind(ResumeKind.YOUTUBE)
        assertEquals(listOf(radio), MediaHistory.items.value)
        MediaHistory.restore(removed)
        assertEquals(listOf(yt, radio), MediaHistory.items.value)
    }
}
