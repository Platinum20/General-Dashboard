package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.PastBarrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La resistenza fra adesso e passato in Note › Prossimi giorni. */
class PastBarrierTest {
    private fun step(boundary: Float?, delta: Float, pull: Float = 0f, unlocked: Boolean = false, fling: Boolean = false) =
        PastBarrier.step(boundary, delta, pull, unlocked, threshold = 100f, fling = fling)

    @Test fun primaDelConfineSiScorre() = assertEquals(0f, step(-50f, 30f).consumed)

    @Test fun sulConfineSiFermaEVibra() {
        val s = step(0f, 10f)
        assertEquals(10f, s.consumed)
        assertEquals(10f, s.pull)
        assertTrue(s.bump)
    }

    @Test fun insistendoSiPassa() {
        val s = step(0f, 95f, pull = 10f)
        assertEquals(0f, s.consumed)
        assertTrue(s.unlocked)
        assertTrue(s.through)
    }

    @Test fun ilLancioSiFermaSulConfine() = assertEquals(30f, step(-20f, 50f, fling = true).consumed)

    @Test fun nelPassatoNienteResistenza() {
        val s = step(30f, 10f)
        assertEquals(0f, s.consumed)
        assertTrue(s.unlocked)
    }

    @Test fun versoIlFuturoLiberoESiRiarma() {
        assertEquals(0f, step(0f, -10f, pull = 40f).consumed)
        assertEquals(false, step(-200f, -10f, unlocked = true).unlocked)
    }

    /** Il segno del mese vibra solo quando passa la riga, non quando compare. */
    @Test fun meseAttraversato() {
        val m = com.platinum20.dashboard.ui.screens.MonthCross
        assertEquals(false, m.crossed(null, 100f, 300f))
        assertEquals(false, m.crossed(500f, 400f, 300f))
        assertEquals(true, m.crossed(320f, 280f, 300f))
        assertEquals(true, m.crossed(280f, 320f, 300f))
    }
}
