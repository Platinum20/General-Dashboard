package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLimitTest {
    @Test
    fun retryAfterInSecondsWithDefaultAndCap() {
        assertEquals(30_000L, SpotifyLimit.retryAfterMs(null))
        assertEquals(30_000L, SpotifyLimit.retryAfterMs("boh"))
        assertEquals(30_000L, SpotifyLimit.retryAfterMs("0"))
        assertEquals(7_000L, SpotifyLimit.retryAfterMs(" 7 "))
        assertEquals(24 * 3600 * 1000L, SpotifyLimit.retryAfterMs("999999"))
    }

    @Test
    fun waitRunsDownAndNeverShrinks() {
        var now = 1_000_000L
        val limit = SpotifyLimit { now }
        assertEquals(0L, limit.left())
        limit.hit("120")
        assertEquals(120_000L, limit.left())
        // Un 429 più corto non accorcia l'attesa già chiesta.
        limit.hit("5")
        assertEquals(120_000L, limit.left())
        now += 100_000
        assertEquals(20_000L, limit.left())
        now += 50_000
        assertEquals(0L, limit.left())
    }

    @Test
    fun messageSaysHowLong() {
        assertTrue(SpotifyLimit.message(20_000).endsWith("tra 20 secondi"))
        assertTrue(SpotifyLimit.message(60_000).endsWith("tra un minuto"))
        assertTrue(SpotifyLimit.message(150_000).endsWith("tra 3 minuti"))
        assertTrue(SpotifyLimit.message(3 * 3600_000L).endsWith("tra 3 ore"))
    }
}
