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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Il mini player si chiude portandolo oltre metà di lato, anche mentre lo si sposta su e giù; meno di così torna al suo posto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class MiniPlayerSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private fun show() {
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

    @Test
    fun lanciatoDiLatoSiChiude() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y), 200)
        assertEquals(1, closed)
    }

    @Test
    fun anceVersoSinistra() {
        show()
        val y = barY()
        drag(Offset(1_100f, y), Offset(60f, y), 200)
        assertEquals(1, closed)
    }

    @Test
    fun spostatoPocoTorna() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(700f, y), 800)
        assertEquals(0, closed)
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    @Test
    fun diLatoESuInsiemeChiude() {
        show()
        val y = barY()
        drag(Offset(60f, y), Offset(1_100f, y - 400f), 300)
        assertEquals(1, closed)
    }

    @Test
    fun unPoDiLatoMentreLoSpostiNonChiude() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(850f, 300f), 300)
        assertEquals(0, closed)
    }

    @Test
    fun suEGiuNonChiude() {
        show()
        val y = barY()
        drag(Offset(600f, y), Offset(600f, 300f), 300)
        assertEquals(0, closed)
    }
}
