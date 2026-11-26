package com.platinum20.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.platinum20.dashboard.ui.screens.Glow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowTest {
    private val brown = Color(0xFF73482C)
    private val wine = Color(0xFF93000A)

    @Test fun treMacchieColoriDelTema() {
        val b = Glow.blobs(brown, wine, dark = true, rainy = false)
        assertEquals(3, b.size)
        // In alto a sinistra il colore principale scurito, più forte delle altre; al buio niente di chiaro.
        assertTrue(b[0].y < 0.1f && b[0].x < 0.2f)
        b.forEach { assertTrue(it.color.luminance() < brown.luminance()) }
        // Mai verde: rosso sopra il verde in ogni macchia (il terzo colore oliva non entra).
        b.forEach { assertTrue(it.color.red > it.color.green) }
        assertTrue(b[0].color.alpha > b[1].color.alpha && b[1].color.alpha > b[2].color.alpha)
    }

    @Test fun conLaPioggiaSiRaffredda() {
        val sereno = Glow.blobs(brown, wine, dark = true, rainy = false)
        val pioggia = Glow.blobs(brown, wine, dark = true, rainy = true)
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

    @Test fun diGiornoSiVedeIlColore() {
        val pastel = Color(0xFFE3E8FF); val blue = Color(0xFF3550E8); val paper = Color(0xFFF5F3EE)
        val b = Glow.blobs(pastel, Color(0xFFFDE2DC), dark = false, rainy = false, strong = blue)
        // Più colore del solo pastello: la macchia in alto è più blu della carta, così il vetro si legge.
        assertTrue(b[0].color.blue - b[0].color.red > pastel.blue - pastel.red)
        assertTrue(b[0].color.blue > paper.blue - 0.2f)
    }
}
