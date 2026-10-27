package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.ui.screens.pickFeed
import org.junit.Assert.assertEquals
import org.junit.Test

/** I filtri di Video: Escludi Shorts ed Escludi live. */
class VideoFeedFilterTest {
    private fun v(id: String, short: Boolean = false, live: Boolean = false, upcoming: Boolean = false) =
        YtVideo(id, "Video $id", "Canale", "c", null, isShort = short, live = live, upcoming = upcoming)

    private val all = listOf(v("a"), v("s", short = true), v("l", live = true), v("u", upcoming = true))

    @Test fun noFilters() {
        val pick = pickFeed(all, hideShorts = false, hideLive = false)
        assertEquals(listOf("s"), pick.shorts.map { it.id })
        assertEquals(listOf("a", "l", "u"), pick.videos.map { it.id })
    }

    @Test fun bothFilters() {
        val pick = pickFeed(all, hideShorts = true, hideLive = true)
        assertEquals(emptyList<String>(), pick.shorts.map { it.id })
        assertEquals(listOf("a"), pick.videos.map { it.id })
    }
}
