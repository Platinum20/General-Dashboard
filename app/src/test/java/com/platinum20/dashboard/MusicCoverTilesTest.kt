package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.CoverTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCoverTilesTest {
    @Test
    fun treCopertineRiempionoLoSchermoSenzaTagli() {
        for (screen in listOf(360, 393, 411, 480)) {
            val w = CoverTiles.width(screen)
            val used = 2 * CoverTiles.EDGE + 3 * w + 2 * CoverTiles.GAP
            assertTrue("schermo $screen: $used", used <= screen && screen - used < 3)
        }
        assertEquals(118, CoverTiles.width(411))
    }
}
