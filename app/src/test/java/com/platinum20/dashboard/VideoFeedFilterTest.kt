package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.markMembers
import com.platinum20.dashboard.media.youtube.membersPlaylist
import com.platinum20.dashboard.ui.screens.pickFeed
import org.junit.Assert.assertEquals
import org.junit.Test

/** I filtri di Video: Escludi Shorts, Escludi live, Escludi video per membri. */
class VideoFeedFilterTest {
    private fun v(id: String, short: Boolean = false, live: Boolean = false, upcoming: Boolean = false, members: Boolean = false) =
        YtVideo(id, "Video $id", "Canale", "c", null, isShort = short, live = live, upcoming = upcoming, membersOnly = members)

    private val all = listOf(v("a"), v("s", short = true), v("l", live = true), v("u", upcoming = true), v("m", members = true))

    @Test fun noFilters() {
        val pick = pickFeed(all, hideShorts = false, hideLive = false)
        assertEquals(listOf("s"), pick.shorts.map { it.id })
        assertEquals(listOf("a", "l", "u", "m"), pick.videos.map { it.id })
    }

    @Test fun bothFilters() {
        val pick = pickFeed(all, hideShorts = true, hideLive = true)
        assertEquals(emptyList<String>(), pick.shorts.map { it.id })
        assertEquals(listOf("a", "m"), pick.videos.map { it.id })
    }

    @Test fun membersFilter() {
        val pick = pickFeed(all, hideShorts = false, hideLive = false, hideMembers = true)
        assertEquals(listOf("a", "l", "u"), pick.videos.map { it.id })
    }

    @Test fun membersPlaylistFromChannel() {
        assertEquals("UUMOabc123", membersPlaylist("UCabc123"))
        assertEquals(null, membersPlaylist("xyz"))
        val marked = markMembers(listOf(v("a"), v("b")), setOf("b"))
        assertEquals(listOf(false, true), marked.map { it.membersOnly })
    }
}
