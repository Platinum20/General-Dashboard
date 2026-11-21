package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.MiniTuck
import com.platinum20.dashboard.ui.screens.RADIO_TILE_LINE
import com.platinum20.dashboard.ui.screens.SHIFT_LIVE_OFF
import com.platinum20.dashboard.ui.screens.SHIFT_LIVE_ON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testi che finivano coi puntini: restano corti. */
class ShortLinesTest {
    @Test
    fun miniPlayerSenzaFonteDavanti() {
        assertEquals("Collegamento…", MiniTuck.line("Radio", "Collegamento…"))
        assertEquals("Radio", MiniTuck.line("Radio", ""))
    }

    @Test
    fun sottotitoliCorti() {
        // "Una notifica quando la pioggia è in arrivo" (42) sta su una riga nella stessa pagina.
        listOf(SHIFT_LIVE_ON, SHIFT_LIVE_OFF).forEach { assertTrue(it, it.length <= 38) }
        // "Scegline qualcuno" (17) sta sotto la tessera dei podcast.
        assertTrue(RADIO_TILE_LINE.length <= 17)
    }
}
