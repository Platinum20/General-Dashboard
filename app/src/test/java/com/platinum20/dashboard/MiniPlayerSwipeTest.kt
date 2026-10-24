package com.platinum20.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.ui.screens.FloatingPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Il mini player si chiude lanciandolo di lato; uno spostamento piccolo lo fa tornare al suo posto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class MiniPlayerSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        Playback.starting(PlaySource.SPOTIFY, "Brano di prova", "Artista")
        compose.setContent { FloatingPlayer(currentRoute = null, onExpand = {}) }
        compose.waitUntil(3_000) { Playback.now.value != null }
        compose.onNodeWithText("Brano di prova").assertExists()
    }

    /** L'altezza della barra sullo schermo (si trascina dalla barra, non solo dal titolo). */
    private fun barY(): Float = compose.onNodeWithText("Brano di prova").fetchSemanticsNode().boundsInRoot.center.y

    @Test
    fun lanciatoDiLatoSiChiude() {
        show()
        val y = barY()
        compose.onRoot().performTouchInput { swipe(Offset(60f, y), Offset(width - 20f, y), durationMillis = 200) }
        compose.waitUntil(3_000) { Playback.now.value == null }
        assertNull(Playback.now.value)
    }

    @Test
    fun spostatoPocoTorna() {
        show()
        val y = barY()
        compose.onRoot().performTouchInput { swipe(Offset(width / 2f, y), Offset(width / 2f + width * 0.1f, y), durationMillis = 800) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("Brano di prova", Playback.now.value?.title)
        compose.onRoot().assertExists()
    }
}
