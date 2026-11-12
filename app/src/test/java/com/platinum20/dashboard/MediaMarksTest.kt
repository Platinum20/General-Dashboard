package com.platinum20.dashboard

import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.ui.screens.MediaMark
import com.platinum20.dashboard.ui.screens.MediaMarks
import com.platinum20.dashboard.ui.screens.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.482 (Antonio): su ogni copertina il simbolo del tipo in alto a sinistra e la durata in basso a destra. */
class MediaMarksTest {
    private fun sp(kind: SpKind, ms: Long = 0) = SpItem("x", "spotify:x", kind, "T", "S", "", durationMs = ms)

    @Test fun durataComeOrologioEInMinutiPerIPodcast() {
        assertEquals("4:12", MediaMarks.clock(252_000))
        assertEquals("1:02:03", MediaMarks.clock(3_723_000))
        assertNull(MediaMarks.clock(0))
        assertEquals("38 min", MediaMarks.minutes(38 * 60_000L))
        assertEquals("1 h 12 min", MediaMarks.minutes(72 * 60_000L))
        assertEquals("2 h", MediaMarks.minutes(120 * 60_000L))
        assertNull(MediaMarks.minutes(20_000))
    }

    @Test fun tipoDaSpotifyEDallaCronologia() {
        assertEquals(MediaMark(MediaType.MUSIC, "4:12"), MediaMarks.of(sp(SpKind.TRACK, 252_000)))
        assertEquals(MediaMark(MediaType.MUSIC), MediaMarks.of(sp(SpKind.ALBUM)))
        assertEquals(MediaMark(MediaType.PODCAST, "38 min"), MediaMarks.of(sp(SpKind.EPISODE, 38 * 60_000L)))
        assertNull(MediaMarks.of(sp(SpKind.ARTIST)))
        assertEquals(MediaMark(MediaType.VIDEO, "14:21"), MediaMarks.of(ResumeKind.YOUTUBE, 861_000))
        assertEquals(MediaMark(MediaType.RADIO, "LIVE"), MediaMarks.of(ResumeKind.RADIO, 0))
    }
}
