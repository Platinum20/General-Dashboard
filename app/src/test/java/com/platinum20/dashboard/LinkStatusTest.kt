package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.LinkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkStatusTest {
    private val hour = 3_600_000L
    private val now = 100 * hour
    private fun of(linked: Boolean = true, expired: Boolean = false, readAt: Long) =
        LinkStatus.of(linked, expired, readAt, now, staleAfter = 3 * hour, ago = { "${(now - it) / hour} ore fa" })

    @Test fun datoFrescoNonDiceNiente() {
        val s = of(readAt = now - hour)
        assertEquals(LinkStatus.Kind.OK, s.kind)
        assertNull(s.line)
        assertFalse(s.faded)
    }

    @Test fun datoVecchioDiceDaQuando() {
        val s = of(readAt = now - 5 * hour)
        assertEquals(LinkStatus.Kind.STALE, s.kind)
        assertEquals("Letto 5 ore fa", s.line)
        assertTrue(s.faded)
    }

    @Test fun accessoScadutoTieneIlDatoSbiadito() {
        val s = of(expired = true, readAt = now - 26 * hour)
        assertEquals(LinkStatus.Kind.EXPIRED, s.kind)
        assertEquals("Accesso scaduto · dati di 26 ore fa", s.line)
        assertTrue(s.faded)
    }

    @Test fun nonCollegatoNonHaRiga() = assertNull(of(linked = false, expired = true, readAt = 0).line)
}
