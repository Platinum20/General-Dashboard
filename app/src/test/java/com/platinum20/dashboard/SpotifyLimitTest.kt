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

class SpotifyCallBudgetTest {
    @Test
    fun backgroundCallsLeaveRoomForThePlayer() {
        val budget = com.platinum20.dashboard.media.spotify.CallBudget(windowMs = 30_000, max = 4, backgroundMax = 2)
        assertEquals(0L, budget.reserve(0, true))
        assertEquals(0L, budget.reserve(1_000, true))
        // Il terzo di contorno aspetta che il primo esca dalla finestra.
        assertEquals(28_000L, budget.reserve(2_000, true))
        // Il play passa lo stesso.
        assertEquals(0L, budget.reserve(2_000, false))
        assertEquals(0L, budget.reserve(3_000, false))
        // Pieno per tutti.
        assertEquals(26_000L, budget.reserve(4_000, false))
        // Passata la finestra si riparte.
        assertEquals(0L, budget.reserve(31_000, true))
    }

    @Test
    fun playerPathsArePriority() {
        assertEquals(false, com.platinum20.dashboard.media.spotify.CallBudget.isBackground("me/player/play?device_id=x"))
        assertEquals(true, com.platinum20.dashboard.media.spotify.CallBudget.isBackground("search?q=x"))
    }

    @Test
    fun savedWaitSurvivesRestart() {
        var now = 0L
        val limit = SpotifyLimit { now }
        var saved = 0L
        limit.onChange = { saved = it }
        limit.hit("3600")
        assertEquals(3_600_000L, saved)
        val again = SpotifyLimit { now }
        again.restore(saved)
        assertEquals(3_600_000L, again.left())
    }
}
