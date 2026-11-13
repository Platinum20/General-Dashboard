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
        assertFalse(MiniTuck.magnet(MiniTuck.MAGNET_DP * 3f - 1f, 3f))
        assertTrue(MiniTuck.magnet(MiniTuck.MAGNET_DP * 3f + 1f, 3f))
    }

    @Test
    fun laLinguettaSiTiraVersoIlCentro() {
        assertTrue(MiniTuck.pulledOut(-100f, right = true, density = 2f))
        assertFalse(MiniTuck.pulledOut(100f, right = true, density = 2f))
        assertTrue(MiniTuck.pulledOut(100f, right = false, density = 2f))
    }

    @Test
    fun ritiratoNonChiedeSpazioInFondo() {
        assertEquals(0f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 210f, density = 3f, tucked = true), 0f)
        assertEquals(76f, MiniDock.bottomRoom(playing = true, atTop = false, barHeight = 210f, density = 3f, tucked = false), 0f)
        assertEquals(0f, MiniDock.bottomReserveDp(playing = true, atTop = false, tucked = true), 0f)
    }
}
