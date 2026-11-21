package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyRelink
import com.platinum20.dashboard.media.spotify.SpotifyRelink.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyRelinkTest {
    private val now = 1_000_000_000L

    @Test
    fun collegatoSiRiprovaDaSoliPiuVolte() {
        assertEquals(Step.RETRY, SpotifyRelink.step(true, true, emptyList(), playing = true))
        // Il secondo intoppo subito dopo (prima: "riprova tra poco") si riprova ancora in silenzio.
        assertEquals(Step.RETRY, SpotifyRelink.step(true, true, listOf(now - 60_000), playing = true))
        assertEquals(Step.RETRY, SpotifyRelink.step(true, true, listOf(now - 60_000, now - 30_000), playing = true))
    }

    @Test
    fun tentativiFinitiSiDiceSoloSeStaiAscoltando() {
        val tre = listOf(now - 90_000, now - 60_000, now - 30_000)
        assertEquals(Step.LATER, SpotifyRelink.step(true, true, tre, playing = true))
        assertEquals(Step.QUIET, SpotifyRelink.step(true, true, tre, playing = false))
    }

    @Test
    fun senzaReteSiAspettaSenzaContareTentativi() {
        assertEquals(Step.WAIT_NETWORK, SpotifyRelink.step(true, false, emptyList(), playing = false))
    }

    @Test
    fun vecchiTentativiEsconoDallaFinestra() {
        val old = listOf(now - SpotifyRelink.WINDOW_MS - 1, now - SpotifyRelink.WINDOW_MS, now - 1_000)
        assertEquals(listOf(now - 1_000), SpotifyRelink.recent(old, now))
    }

    @Test
    fun attesaCresce() {
        assertEquals(0L, SpotifyRelink.delayFor(0))
        assertEquals(5_000L, SpotifyRelink.delayFor(1))
        assertEquals(30_000L, SpotifyRelink.delayFor(5))
    }

    @Test
    fun ricollegaSoloSeIlCollegamentoEDavveroCaduto() {
        assertEquals(Step.ASK, SpotifyRelink.step(connected = false, online = true, recent = emptyList(), playing = true))
        assertEquals(SpotifyRelink.RELINK, SpotifyRelink.message(connected = false))
        assertEquals(SpotifyRelink.LATER, SpotifyRelink.message(connected = true))
    }
}
