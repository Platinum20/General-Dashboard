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
 * Il riquadro del mini player (1.546) si aggancia ai quattro angoli; lanciato verso il suo bordo si ritira nella
 * linguetta (1.508), anche mentre lo si sposta su e giù. Si chiude portandolo sul + in basso a destra (calamita, 1.634).
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
        MiniDock.atRight = true
        val song = Playing(PlaySource.SPOTIFY, "Brano di prova", "Artista", "", playing = true, positionMs = 10_000, durationMs = 200_000)
        compose.setContent { FloatingPlayer(currentRoute = null, onExpand = {}, shown = song, onClose = { closed++ }) }
        compose.waitForIdle()
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    /** Un punto dentro il riquadro (sul titolo): si trascina da lì. */
    private fun boxPoint(): Offset = compose.onNodeWithText("Brano di prova").fetchSemanticsNode().boundsInRoot.let { Offset(it.left + 20f, it.center.y) }

    private fun drag(from: Offset, to: Offset, ms: Long) {
        compose.onRoot().performTouchInput { swipe(from, to, durationMillis = ms) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun assertTab() {
        assertTrue(MiniDock.tucked)
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).assertExists()
    }

    private fun tuckRight() {
        val p = boxPoint()
        drag(p, Offset(p.x + 500f, p.y), 200)
    }

    @Test
    fun lanciatoVersoIlBordoSiRitira() {
        show()
        tuckRight()
        assertEquals(0, closed)
        assertTab()
        assertTrue(MiniDock.tuckRight)
    }

    /** 1.546: dall'angolo a destra, lanciato a sinistra va nell'angolo a sinistra; lanciato ancora, nella linguetta. */
    @Test
    fun lanciatoVersoLAltraMetaCambiaAngoloPoiSiRitira() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x - 500f, p.y), 200)
        assertEquals(0, closed)
        assertFalse(MiniDock.tucked)
        assertFalse(MiniDock.atRight)
        val q = boxPoint()
        assertTrue(q.x < p.x - 300f)
        drag(q, Offset(q.x - 500f, q.y), 200)
        assertTab()
        assertFalse(MiniDock.tuckRight)
    }

    @Test
    fun trascinatoPianoDallAltraParteCambiaAngolo() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x - 600f, p.y), 1_200)
        assertFalse(MiniDock.tucked)
        assertFalse(MiniDock.atRight)
        assertEquals(0, closed)
    }

    @Test
    fun laLinguettaSiRiapreConUnTocco() {
        show()
        tuckRight()
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(MiniDock.tucked)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    @Test
    fun laLinguettaSiRiapreDaTalkBack() {
        show()
        tuckRight()
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(MiniDock.tucked)
    }

    @Test
    fun spostatoPocoTorna() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x + 100f, p.y), 800)
        assertEquals(0, closed)
        assertFalse(MiniDock.tucked)
        assertTrue(MiniDock.atRight)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    @Test
    fun diLatoESuInsiemeSiRitira() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x + 500f, p.y - 400f), 300)
        assertEquals(0, closed)
        assertTab()
    }

    @Test
    fun unPoDiLatoMentreLoSpostiResta() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x + 150f, 300f), 300)
        assertEquals(0, closed)
        assertFalse(MiniDock.tucked)
        assertTrue(MiniDock.atTop)
    }

    @Test
    fun suEGiuNonChiude() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x, 300f), 300)
        assertEquals(0, closed)
    }

    /** 1.634: si chiude portandolo sul + in basso a destra (qui senza barra: la ✕ compare nello stesso punto). */
    @Test
    fun portatoSulPiuSiChiude() {
        show()
        val p = boxPoint()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // Il centro del +: 16 + 30 dp dal bordo destro, 40 dp sopra il fondo (xxhdpi = 3 px per dp).
        drag(p, Offset(root.right - 46f * 3f, root.bottom - 40f * 3f), 600)
        assertEquals(1, closed)
    }

    @Test
    fun appenaGiuNonChiude() {
        show()
        val p = boxPoint()
        drag(p, Offset(p.x, p.y + 45f), 600)
        assertEquals(0, closed)
    }

    /** In fondo allo schermo ma lontano dal +: non si chiude più (prima bastava portarlo giù). */
    @Test
    fun inFondoLontanoDalPiuNonChiude() {
        show()
        val p = boxPoint()
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 2f
        drag(p, Offset(p.x, bottom), 600)
        assertEquals(0, closed)
    }

    private fun tabCenter(): Offset =
        compose.onNodeWithContentDescription("Mini player: Brano di prova", substring = true).fetchSemanticsNode().boundsInRoot.center

    /** 1.546: tirata verso il centro (con la vibrazione) la linguetta fa uscire il riquadro dal suo lato. */
    @Test
    fun laLinguettaTirataVersoIlCentroEsce() {
        show()
        tuckRight()
        val c = tabCenter()
        drag(c, Offset(c.x - 300f, c.y), 600)
        assertFalse(MiniDock.tucked)
        assertTrue(MiniDock.atRight)
        assertEquals(0, closed)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    /** Trascinata su resta sul bordo, in alto: riaprendola la barra torna in alto. */
    @Test
    fun laLinguettaSiPortaInAlto() {
        show()
        tuckRight()
        val c = tabCenter()
        drag(c, Offset(c.x, 300f), 600)
        assertTrue(MiniDock.tucked)
        assertEquals(0f, MiniDock.tabFrac, 0f)
        assertTrue(tabCenter().y < 600f)
        assertEquals(0, closed)
    }

    /** Giù fino al fondo dello schermo, sulla X: la linguetta chiude il mini player. */
    @Test
    fun laLinguettaGiuSiChiude() {
        show()
        tuckRight()
        val c = tabCenter()
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - 2f
        drag(c, Offset(c.x, bottom), 600)
        assertEquals(1, closed)
    }
}
