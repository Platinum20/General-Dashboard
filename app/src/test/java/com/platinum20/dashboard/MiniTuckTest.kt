package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.MiniDock
import com.platinum20.dashboard.ui.screens.MiniTuck
import com.platinum20.dashboard.ui.screens.MiniTuck.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.508: lancio di lato = linguetta, giù sotto lo schermo = calamita che chiude, scorrendo si ritira. */
class MiniTuckTest {
    private val w = 1_000f

    @Test
    fun laCalamitaVinceSuTutto() {
        assertEquals(Release.CLOSE, MiniTuck.release(magnet = true, dx = 800f, vx = 4_000f, vy = 0f, width = w))
    }

    @Test
    fun oltreMetaDiLatoVaNellaLinguetta() {
        assertEquals(Release.TUCK_RIGHT, MiniTuck.release(false, 600f, 0f, 0f, w))
        assertEquals(Release.TUCK_LEFT, MiniTuck.release(false, -600f, 0f, 0f, w))
    }

    @Test
    fun lancioDecisoDiLato() {
        assertEquals(Release.TUCK_LEFT, MiniTuck.release(false, -300f, -3_000f, 200f, w))
        assertEquals(Release.STAY, MiniTuck.release(false, 100f, 3_000f, 0f, w))
    }

    @Test
    fun pocoSpostatoResta() {
        assertEquals(Release.STAY, MiniTuck.release(false, 200f, 0f, 0f, w))
    }

    @Test
    fun calamitaSoloOltreLaSoglia() {
        assertFalse(MiniTuck.magnet(MiniTuck.MAGNET_DP * 3f - 1f, MiniTuck.MAGNET_DP * 3f))
        assertTrue(MiniTuck.magnet(MiniTuck.MAGNET_DP * 3f + 1f, MiniTuck.MAGNET_DP * 3f))
    }

    @Test
    fun senzaBarraLaSogliaStaNelloSpazioDisponibile() {
        // Con la barra sotto: soglia piena.
        assertEquals(MiniTuck.MAGNET_DP * 3f, MiniTuck.threshold(400f * 3f, 3f), 0f)
        // Senza barra (60 dp fino al bordo): si raggiunge prima del bordo.
        val t = MiniTuck.threshold(60f * 3f, 3f)
        assertTrue(t < 60f * 3f)
        // Mai meno del minimo, anche senza spazio.
        assertEquals(MiniTuck.MIN_MAGNET_DP * 3f, MiniTuck.threshold(0f, 3f), 0f)
    }

    @Test
    fun laLinguettaVaSulBordoPiuVicinoOLanciato() {
        assertTrue(MiniTuck.tabSide(800f, 0f, 1_000f))
        assertFalse(MiniTuck.tabSide(300f, 0f, 1_000f))
        assertFalse(MiniTuck.tabSide(800f, -3_000f, 1_000f))
        assertTrue(MiniTuck.tabSide(100f, 3_000f, 1_000f))
    }

    @Test
    fun ritiratoNonChiedeSpazioInFondo() {
        assertEquals(0f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 210f, density = 3f, tucked = true), 0f)
        assertEquals(76f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 210f, density = 3f, tucked = false), 0f)
        assertEquals(0f, MiniDock.bottomReserveDp(playing = true, atTop = false, tucked = true), 0f)
    }

    @Test
    fun laLinguettaSiAgganciaSoloInAltoOInBasso() {
        assertEquals(0f, MiniTuck.tabCorner(0.3f, 0f), 0f)
        assertEquals(1f, MiniTuck.tabCorner(0.7f, 0f), 0f)
        assertEquals(0f, MiniTuck.tabCorner(0.8f, -2f), 0f)
        assertEquals(1f, MiniTuck.tabCorner(0.2f, 2f), 0f)
    }
}
