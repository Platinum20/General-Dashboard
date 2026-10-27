package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.mergeChannelPage
import com.platinum20.dashboard.media.youtube.player.YtResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pagina del canale con tutti i video: id dal link del canale e pagine unite senza doppioni. */
class ChannelAllVideosTest {
    private fun v(id: String) = YtVideo(id, "Video $id", "Canale", "UC", null)

    @Test fun idDelCanaleDalLink() {
        val id = "UC" + "a".repeat(22)
        assertEquals(id, YtResolver.channelId("https://www.youtube.com/channel/$id"))
        assertEquals(id, YtResolver.channelId("https://youtube.com/channel/$id/videos"))
        assertNull(YtResolver.channelId("https://www.youtube.com/@canale"))
        assertNull(YtResolver.channelId(""))
    }

    @Test fun paginaSuccessivaInFondoSenzaDoppioni() {
        val merged = mergeChannelPage(listOf(v("1"), v("2"), v("3")), listOf(v("3"), v("4"), v("5")))
        assertEquals(listOf("1", "2", "3", "4", "5"), merged.map { it.id })
    }
}
