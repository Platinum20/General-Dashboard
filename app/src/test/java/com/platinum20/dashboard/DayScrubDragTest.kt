package com.platinum20.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.ui.screens.DayScrub
import com.platinum20.dashboard.ui.screens.DayScrubState
import com.platinum20.dashboard.ui.screens.ScrubDay
import com.platinum20.dashboard.ui.screens.dayScrub
import com.platinum20.dashboard.ui.screens.rememberDayScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Trascina davvero la linea a destra su una lista finta e guarda come si muove: deve andare solo in
 * avanti, a passi regolari (niente scatti avanti-indietro che lampeggiano), e lasciando non si apre niente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class DayScrubDragTest {
    @get:Rule
    val compose = createComposeRule()

    private val count = 240
    /** Un giorno ogni tre righe; la prima riga del giorno ha sopra l'intestazione (26 dp). */
    private fun starts(i: Int) = i % 3 == 0
    private fun heightDp(i: Int) = 48f + if (starts(i)) 26f else 0f

    private lateinit var list: LazyListState
    private lateinit var scrub: DayScrubState
    private var density = 1f
    private var clicks = 0
    private var width = 0f
    private var height = 0f

    private fun show() {
        val days = (0 until count).filter(::starts).map { ScrubDay(it, "d$it", month = "M${it / 60}", dotDp = DayScrub.DAY_DOT_DP) }
        compose.setContent {
            density = LocalDensity.current.density
            list = rememberLazyListState()
            scrub = rememberDayScrub(list, days)
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().dayScrub(scrub)) {
                items(count, key = { it }) { i ->
                    Column(Modifier.fillMaxWidth().clickable { clicks++ }) {
                        if (starts(i)) Box(Modifier.fillMaxWidth().height(26.dp))
                        Box(Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        val size = compose.onRoot().fetchSemanticsNode().size
        width = size.width.toFloat()
        height = size.height.toFloat()
    }

    /** Quanto è scesa la lista, in px dall'inizio. */
    private fun position(): Float {
        var px = 0f
        for (i in 0 until list.firstVisibleItemIndex) px += heightDp(i) * density
        return px + list.firstVisibleItemScrollOffset
    }

    private fun drag(fromY: Float, toY: Float, steps: Int): List<Float> {
        val x = width - 20f
        val seen = mutableListOf<Float>()
        compose.onRoot().performTouchInput { down(Offset(x, fromY)) }
        for (k in 1..steps) {
            compose.onRoot().performTouchInput { moveTo(Offset(x, fromY + (toY - fromY) * k / steps)) }
            compose.waitForIdle()
            seen += position()
        }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun scendendoLaListaVaSoloAvantiSenzaScatti() {
        show()
        val seen = drag(10f, height - 10f, 120)
        val steps = seen.zipWithNext { a, b -> b - a }
        assertTrue("torna indietro: ${steps.filter { it < -2f }}", steps.all { it >= -2f })
        val mean = (seen.last() - seen.first()) / steps.size
        assertTrue("scatto troppo grande: max ${steps.max()} su media $mean", steps.max() <= mean * 3f + 4f)
        // In fondo alla linea si è arrivati agli ultimi giorni (il dito è a 10 px dal fondo: il penultimo giorno al più).
        assertTrue(list.layoutInfo.visibleItemsInfo.any { it.index >= count - 6 })
        assertEquals(0, clicks)
    }

    @Test
    fun salendoLaListaVaSoloIndietro() {
        show()
        drag(10f, height - 10f, 40)
        val seen = drag(height - 10f, 10f, 120)
        val steps = seen.zipWithNext { a, b -> b - a }
        assertTrue("torna avanti: ${steps.filter { it > 2f }}", steps.all { it <= 2f })
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, clicks)
    }

    @Test
    fun lasciandoNonApreNienteEIlGiornoSiSpegne() {
        show()
        drag(10f, height / 2, 30)
        assertEquals(0, clicks)
        assertEquals(null, scrub.hot)
        assertEquals(false, scrub.active)
    }

    @Test
    fun unToccoFermoApreLaRiga() {
        show()
        compose.onRoot().performTouchInput { click(Offset(width - 20f, 100f)) }
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
