package com.platinum20.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playing
import com.platinum20.dashboard.ui.screens.FloatingPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.platinum20.dashboard.ui.screens.MiniDock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il mini player lanciato di lato si ritira nella linguetta sul bordo (1.508), anche mentre lo si sposta
 * su e giù; meno di così torna al suo posto. Si chiude solo portandolo giù, sotto lo schermo (calamita).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class MiniPlayerSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private fun show() {
        MiniDock.tucked = false
        MiniDock.tuckRight = true
        MiniDock.atTop = false
        val song = Playing(PlaySource.SPOTIFY, "Brano di prova", "Artista", "", playing = true, positionMs = 10_000, durationMs = 200_000)
        compose.setContent { FloatingPlayer(currentRoute = null, onExpand = {}, shown = song, onClose = { closed++ }) }
        compose.waitForIdle()
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    /** L'altezza della barra sullo schermo (si trascina dalla barra, non solo dal titolo). */
    private fun barY(): Float = compose.onNodeWithText("Brano di prova").fetchSemanticsNode().boundsInRoot.center.y

    private fun drag(from: Offset, to: Offset, ms: Long) {
        compose.onRoot().performTouchInput { swipe(from, to, durationMillis = ms) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun assertTab() {
        assertTrue(MiniDock.tucked)
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).assertExists()
    }

    @Test
    fun lanciatoDiLatoSiRitira() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        assertEquals(0, closed)
        assertTab()
        assertTrue(MiniDock.tuckRight)
    }

    @Test
    fun ancheVersoSinistra() {
        show()
        val y = barY()
        drag(Offset(1_100f, y), Offset(60f, y), 200)
        assertEquals(0, closed)
        assertTab()
        assertFalse(MiniDock.tuckRight)
    }

    @Test
    fun laLinguettaSiRiapreConUnTocco() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(MiniDock.tucked)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    @Test
    fun laLinguettaSiRiapreDaTalkBack() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(MiniDock.tucked)
    }

    @Test
    fun spostatoPocoTorna() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(700f, y), 800)
        assertEquals(0, closed)
        assertFalse(MiniDock.tucked)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    @Test
    fun diLatoESuInsiemeSiRitira() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y - 400f), 300)
        assertEquals(0, closed)
        assertTab()
    }

    @Test
    fun unPoDiLatoMentreLoSpostiResta() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(850f, 300f), 300)
        assertEquals(0, closed)
        assertFalse(MiniDock.tucked)
    }

    @Test
    fun suEGiuNonChiude() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(600f, 300f), 300)
        assertEquals(0, closed)
    }

    @Test
    fun portatoSottoLoSchermoSiChiude() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(600f, y + 330f), 600)
        assertEquals(1, closed)
    }

    @Test
    fun appenaGiuNonChiude() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(600f, y + 45f), 600)
        assertEquals(0, closed)
    }

    /** Senza barra delle sezioni sotto (come qui): basta portarlo fino al fondo dello schermo, non oltre (1.521). */
    @Test
    fun senzaBarraSiChiudeAlFondoDelloSchermo() {
        show()
        val y = barY()
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 2f
        drag(Offset(600f, y), Offset(600f, bottom), 600)
        assertEquals(1, closed)
    }

    private fun tabCenter(): Offset =
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).fetchSemanticsNode().boundsInRoot.center

    /** La linguetta si porta direttamente sull'altro bordo, senza riaprirla (1.521). */
    @Test
    fun laLinguettaSiSpostaSullAltroBordo() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        assertTrue(MiniDock.tuckRight)
        val c = tabCenter()
        drag(c, Offset(150f, c.y), 600)
        assertTrue(MiniDock.tucked)
        assertFalse(MiniDock.tuckRight)
        assertEquals(0, closed)
    }

    /** Trascinata su resta sul bordo, in alto: riaprendola la barra torna in alto. */
    @Test
    fun laLinguettaSiPortaInAlto() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        val c = tabCenter()
        drag(c, Offset(c.x, 300f), 600)
        assertTrue(MiniDock.tucked)
        assertTrue(MiniDock.tabFrac < 0.5f)
        assertTrue(tabCenter().y < 600f)
        assertEquals(0, closed)
    }

    /** Giù fino al fondo dello schermo, sulla X: la linguetta chiude il mini player. */
    @Test
    fun laLinguettaGiuSiChiude() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        val c = tabCenter()
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 2f
        drag(c, Offset(c.x, bottom), 600)
        assertEquals(1, closed)
    }
}
