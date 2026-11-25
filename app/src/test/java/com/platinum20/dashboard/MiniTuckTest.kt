package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.MiniDock
import com.platinum20.dashboard.ui.screens.MiniTuck
import com.platinum20.dashboard.ui.screens.MiniTuck.Drop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.508/1.546: riquadro nei quattro angoli, lancio verso il bordo = linguetta, giù sotto lo schermo = calamita che chiude. */
class MiniTuckTest {
    private val w = 1_000f

    // Riquadro 600 px su 1000: a destra sta a 400, a sinistra a 0.
    private val box = 600f

    @Test
    fun laCalamitaVinceSuTutto() {
        assertEquals(Drop.CLOSE, MiniTuck.drop(magnet = true, left = 800f, boxW = box, maxW = w, vx = 4_000f, fromRight = true))
    }

    @Test
    fun oltreIlBordoVaNellaLinguetta() {
        assertEquals(Drop.TUCK_RIGHT, MiniTuck.drop(false, 700f, box, w, 0f, true))
        assertEquals(Drop.TUCK_LEFT, MiniTuck.drop(false, -300f, box, w, 0f, false))
    }

    @Test
    fun lanciatoVersoIlSuoBordoSiRitiraVersoLAltroCambiaAngolo() {
        assertEquals(Drop.TUCK_RIGHT, MiniTuck.drop(false, 450f, box, w, 3_000f, true))
        assertEquals(Drop.LEFT, MiniTuck.drop(false, 50f, box, w, -3_000f, true))
        assertEquals(Drop.TUCK_LEFT, MiniTuck.drop(false, -50f, box, w, -3_000f, false))
        assertEquals(Drop.RIGHT, MiniTuck.drop(false, 350f, box, w, 3_000f, false))
    }

    @Test
    fun lasciatoVaNellAngoloDellaSuaMeta() {
        assertEquals(Drop.RIGHT, MiniTuck.drop(false, 350f, box, w, 0f, false))
        assertEquals(Drop.LEFT, MiniTuck.drop(false, 100f, box, w, 0f, true))
    }

    @Test
    fun sbiadisceSoloFuoriDalloSchermo() {
        assertEquals(0f, MiniTuck.offscreen(400f, box, w), 0f)
        assertEquals(0.5f, MiniTuck.offscreen(700f, box, w), 0.001f)
        assertEquals(0.5f, MiniTuck.offscreen(-300f, box, w), 0.001f)
    }

    @Test
    fun laLinguettaEsceTirandolaVersoIlCentro() {
        assertFalse(MiniTuck.pulledOut(MiniTuck.PULL_OUT_DP * 3f - 1f, 3f))
        assertTrue(MiniTuck.pulledOut(MiniTuck.PULL_OUT_DP * 3f + 1f, 3f))
    }

    @Test
    fun tastiSulVideoSoloAlToccoConMusicaSempre() {
        assertTrue(MiniTuck.controlsAlways(isVideo = false, playing = true))
        assertTrue(MiniTuck.controlsAlways(isVideo = true, playing = false))
        assertFalse(MiniTuck.controlsAlways(isVideo = true, playing = true))
        assertEquals(MiniTuck.Tap.SHOW_CONTROLS, MiniTuck.tap(isVideo = true, controlsShown = false))
        assertEquals(MiniTuck.Tap.EXPAND, MiniTuck.tap(isVideo = true, controlsShown = true))
        assertEquals(MiniTuck.Tap.EXPAND, MiniTuck.tap(isVideo = false, controlsShown = true))
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

    @Test
    fun siChiudePortandoloSulPiu() {
        // 1.634: la calamita prende quando il dito arriva entro 64 dp dal centro del +.
        val d = 3f
        assertTrue(MiniTuck.overPlus(1_000f, 2_000f, 1_000f, 2_000f, d))
        assertTrue(MiniTuck.overPlus(1_000f + 60f * d, 2_000f, 1_000f, 2_000f, d))
        assertFalse(MiniTuck.overPlus(1_000f + 70f * d, 2_000f, 1_000f, 2_000f, d))
        assertFalse(MiniTuck.overPlus(1_000f - 50f * d, 2_000f - 50f * d, 1_000f, 2_000f, d))
    }

    @Test
    fun piuTiAvviciniPiuSiArrossa() {
        // 1.638: 0 oltre 4 volte la calamita (256 dp), 1 dentro la calamita, in mezzo cresce.
        val d = 3f
        assertEquals(0f, MiniTuck.plusNear(1_000f + 300f * d, 2_000f, 1_000f, 2_000f, d), 0f)
        assertEquals(1f, MiniTuck.plusNear(1_000f + 30f * d, 2_000f, 1_000f, 2_000f, d), 0f)
        val far = MiniTuck.plusNear(1_000f + 200f * d, 2_000f, 1_000f, 2_000f, d)
        val close = MiniTuck.plusNear(1_000f + 100f * d, 2_000f, 1_000f, 2_000f, d)
        assertTrue(far in 0.01f..0.99f)
        assertTrue(close > far)
    }
}
