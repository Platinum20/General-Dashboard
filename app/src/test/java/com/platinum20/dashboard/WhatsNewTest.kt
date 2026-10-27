package com.platinum20.dashboard

import com.platinum20.dashboard.update.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test
    fun leggeTitoloEPunti() {
        val notes = WhatsNew.parse(
            "1.2.0",
            "**SponsorBlock nella sua pagina**\n- Una riga sola\n- \"Accedi\" uguale a \"Collega\"\n",
        )!!
        assertEquals("SponsorBlock nella sua pagina", notes.title)
        assertEquals(listOf("Una riga sola", "\"Accedi\" uguale a \"Collega\""), notes.points)
    }

    @Test
    fun sezioneVuotaNonMostraNiente() {
        assertNull(WhatsNew.parse("1.2.0", "  \n"))
    }

    @Test
    fun soloDopoUnAggiornamento() {
        assertTrue(WhatsNew.shouldShow(null, "1.2.0", freshInstall = false))
        assertTrue(WhatsNew.shouldShow("1.1.0", "1.2.0", freshInstall = false))
        assertFalse(WhatsNew.shouldShow("1.2.0", "1.2.0", freshInstall = false))
        assertFalse(WhatsNew.shouldShow(null, "1.2.0", freshInstall = true))
    }
}
