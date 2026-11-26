package com.platinum20.dashboard

import androidx.compose.ui.graphics.Color
import com.platinum20.dashboard.ui.screens.Glow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowTest {
    private val brown = Color(0xFF73482C)
    private val wine = Color(0xFF5C2B3A)
    private val amber = Color(0xFF4A3A20)

    @Test fun treMacchieColoriDelTema() {
        val b = Glow.blobs(brown, wine, amber, dark = true, rainy = false)
        assertEquals(3, b.size)
        // In alto a sinistra il colore principale, più forte delle altre.
        assertEquals(brown.copy(alpha = b[0].color.alpha), b[0].color)
        assertTrue(b[0].y < 0.1f && b[0].x < 0.2f)
        assertTrue(b[0].color.alpha > b[1].color.alpha && b[1].color.alpha > b[2].color.alpha)
    }

    @Test fun conLaPioggiaSiRaffredda() {
        val sereno = Glow.blobs(brown, wine, amber, dark = true, rainy = false)
        val pioggia = Glow.blobs(brown, wine, amber, dark = true, rainy = true)
        assertNotEquals(sereno[0].color, pioggia[0].color)
        // Più blu che rosso: grigio ardesia.
        assertTrue(pioggia[0].color.blue > pioggia[0].color.red)
    }

    @Test fun pioveSoloConPioggiaPioviggineTemporale() {
        assertTrue(Glow.rainy(61))
        assertTrue(Glow.rainy(53))
        assertTrue(Glow.rainy(95))
        assertFalse(Glow.rainy(0))
        assertFalse(Glow.rainy(3))
        assertFalse(Glow.rainy(null))
    }
}
