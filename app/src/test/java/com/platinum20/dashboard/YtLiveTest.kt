package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtLiveTest {
    @Test fun edgeWhenCloseOrUnknown() {
        assertTrue(YtLive.atEdge(-1))
        assertTrue(YtLive.atEdge(8_000))
        assertFalse(YtLive.atEdge(90_000))
    }

    @Test fun behindLabel() {
        assertEquals("-1:23", YtLive.behind(83_000))
        assertEquals("-1:02:03", YtLive.behind(3_723_000))
        assertEquals("-0:00", YtLive.behind(-5))
    }

    @Test fun endedFromYoutubeDate() {
        assertTrue(YtLive.ended("Streamed 2 hours ago"))
        assertTrue(YtLive.ended("Trasmesso in live streaming 3 giorni fa"))
        assertTrue(YtLive.ended("Trasmesso in streaming 1 settimana fa"))
        assertFalse(YtLive.ended("3 giorni fa"))
        assertFalse(YtLive.ended("2 hours ago"))
        assertFalse(YtLive.ended(""))
    }

    @Test fun endedBadgeWithDuration() {
        assertEquals("Diretta terminata · 1:02:03", YtLive.endedBadge("1:02:03"))
        assertEquals("Diretta terminata", YtLive.endedBadge(null))
    }
}
