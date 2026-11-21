package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.CoverTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il colore del blocco in cima viene dalla copertina (giro 2, 1.566): i pixel vivi contano, mai troppo chiaro. */
class CoverTintTest {
    private fun rgb(c: Int) = Triple(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    @Test
    fun ilRossoVinceSulGrigio() {
        val pixels = IntArray(10) { if (it < 3) 0xFFC03020.toInt() else 0xFF808080.toInt() }
        val (r, g, b) = rgb(CoverTint.of(pixels))
        assertTrue("r=$r g=$g b=$b", r > g && r > b)
    }

    @Test
    fun maiPiuChiaroDelLimite() {
        val (r, g, b) = rgb(CoverTint.of(IntArray(4) { 0xFFFFFFFF.toInt() }))
        assertTrue(maxOf(r, g, b) <= 141)
    }

    @Test
    fun vuotaENero() = assertEquals(0xFF000000.toInt(), CoverTint.of(IntArray(0)))
}
