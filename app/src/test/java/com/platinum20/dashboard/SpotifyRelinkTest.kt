package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyRelink
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyRelinkTest {
    @Test
    fun collegatoSiRiprovaDaSoli() {
        assertEquals(SpotifyRelink.Step.RETRY, SpotifyRelink.step(connected = true, lastRetryAt = 0, now = 1_000_000_000))
    }

    @Test
    fun appenaRiprovatoNonChiedeDiRicollegare() {
        val now = 1_000_000_000L
        assertEquals(SpotifyRelink.Step.LATER, SpotifyRelink.step(true, now - 60_000, now))
        assertEquals(SpotifyRelink.Step.RETRY, SpotifyRelink.step(true, now - SpotifyRelink.QUIET_MS, now))
    }

    @Test
    fun ricollegaSoloSeIlCollegamentoEDavveroCaduto() {
        assertEquals(SpotifyRelink.Step.ASK, SpotifyRelink.step(connected = false, lastRetryAt = 0, now = 1_000_000_000))
        assertEquals(SpotifyRelink.RELINK, SpotifyRelink.message(connected = false))
        assertEquals(SpotifyRelink.LATER, SpotifyRelink.message(connected = true))
    }
}
