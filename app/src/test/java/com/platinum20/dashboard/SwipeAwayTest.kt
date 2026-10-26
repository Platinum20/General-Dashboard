package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SwipeAway
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La regola del "portalo di lato per chiudere", condivisa da mini player e fumetto "Salva nelle note". */
class SwipeAwayTest {
    private val w = 1000f

    @Test fun oltreMetaChiude() {
        assertTrue(SwipeAway.closes(dx = 520f, vx = 0f, vy = 0f, width = w))
        assertTrue(SwipeAway.closes(dx = -520f, vx = 0f, vy = 0f, width = w))
        assertFalse(SwipeAway.closes(dx = 480f, vx = 0f, vy = 0f, width = w))
    }

    @Test fun lancioDecisoChiudeSoloSeGiaAvviato() {
        assertTrue(SwipeAway.closes(dx = 250f, vx = 3_000f, vy = 0f, width = w))
        assertFalse(SwipeAway.closes(dx = 100f, vx = 3_000f, vy = 0f, width = w))
    }

    @Test fun lancioAlContrarioOVerticaleNonChiude() {
        assertFalse(SwipeAway.closes(dx = 300f, vx = -3_000f, vy = 0f, width = w))
        assertFalse(SwipeAway.closes(dx = 300f, vx = 3_000f, vy = 2_500f, width = w))
    }
}
