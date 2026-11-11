package com.platinum20.dashboard

import com.platinum20.dashboard.media.MediaFresh
import com.platinum20.dashboard.media.VisitClock
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.ui.screens.MediaShelves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Cosa è "nuovo" in Media (1.475): video dopo l'ultima visita, episodi non ascoltati, righe sopra il titolo. */
class MediaFreshTest {
    private fun v(id: String, at: Long?) = YtVideo(id = id, title = id, channel = "c", channelId = "c", published = at?.let { Instant.ofEpochMilli(it) })
    private fun ep(id: String, at: Long) = Episode(id = id, title = id, audioUrl = "", publishedAt = at, durationSec = 0, description = "", image = "", podcastTitle = "p", feedUrl = "f")

    @Test
    fun nothingIsNewBeforeTheFirstVisit() {
        assertFalse(MediaFresh.isNew(Instant.ofEpochMilli(5_000), since = 0))
        assertEquals(0, MediaFresh.newVideos(listOf(v("a", 5_000)), since = 0))
    }

    @Test
    fun countsVideosAfterTheVisit() {
        val list = listOf(v("a", 3_000), v("b", 2_000), v("c", 1_000), v("d", null))
        assertEquals(2, MediaFresh.newVideos(list, since = 1_500))
        assertEquals(2, MediaFresh.markerIndex(list, since = 1_500))
    }

    @Test
    fun noMarkerWhenNoneOrAllAreNew() {
        val list = listOf(v("a", 3_000), v("b", 2_000))
        assertNull(MediaFresh.markerIndex(list, since = 5_000))
        assertNull(MediaFresh.markerIndex(list, since = 1_000))
    }

    @Test
    fun episodeIsNewOnlyIfRecentAndNotListened() {
        val now = 30L * 86_400_000L
        assertTrue(MediaFresh.isNewEpisode(ep("a", now - 86_400_000L), emptySet(), now))
        assertFalse(MediaFresh.isNewEpisode(ep("a", now - 86_400_000L), setOf("a"), now))
        assertFalse(MediaFresh.isNewEpisode(ep("b", now - 10L * 86_400_000L), emptySet(), now))
    }

    @Test
    fun linesAboveTheTitle() {
        assertEquals("6 video nuovi · 2 episodi", MediaFresh.mediaLine(6, 2))
        assertEquals("1 video nuovo", MediaFresh.mediaLine(1, 0))
        assertEquals("1 episodio nuovo", MediaFresh.mediaLine(0, 1))
        assertNull(MediaFresh.mediaLine(0, 0))
        assertEquals("3 uscite · 2 episodi nuovi", MediaFresh.musicLine(3, 2))
        assertEquals("1 uscita nuova", MediaFresh.musicLine(1, 0))
        assertNull(MediaFresh.musicLine(0, 0))
        assertEquals("Video · 6 nuovi", MediaShelves.title("Video", 6))
        assertEquals("Musica", MediaShelves.title("Musica", 0))
    }

    @Test
    fun freshFirstKeepsOrder() {
        assertEquals(listOf(2, 4, 1, 3), MediaShelves.freshFirst(listOf(1, 2, 3, 4)) { it % 2 == 0 })
    }

    @Test
    fun comingBackFromAVideoKeepsTheSameVisit() {
        val clock = VisitClock(stored = 1_000)
        assertEquals(1_000, clock.enter(now = 10_000))
        clock.leave(now = 20_000)
        // Torni dal video dopo un minuto: stesso segno.
        assertEquals(1_000, clock.enter(now = 80_000))
        clock.leave(now = 90_000)
        // Torni dopo un'ora: visita nuova, il segno è l'ultima uscita.
        assertEquals(90_000, clock.enter(now = 90_000 + VisitClock.HOLD_MS + 1))
    }
}
