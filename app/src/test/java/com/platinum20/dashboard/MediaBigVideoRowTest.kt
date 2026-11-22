package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.CoverTiles
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBigVideoRowTest {
    @Test
    fun laSchedaGrandeLasciaSpuntareLaSuccessiva() {
        for (screen in listOf(360, 411, 480)) {
            val w = CoverTiles.bigVideoWidth(screen)
            assertEquals(CoverTiles.PEEK, screen - CoverTiles.EDGE - w - CoverTiles.GAP)
        }
        assertEquals(343, CoverTiles.bigVideoWidth(411))
    }
}
