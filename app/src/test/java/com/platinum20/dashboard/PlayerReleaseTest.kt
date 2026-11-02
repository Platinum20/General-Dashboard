package com.platinum20.dashboard

import androidx.compose.ui.geometry.Offset
import com.platinum20.dashboard.ui.screens.PlayerRelease
import com.platinum20.dashboard.ui.screens.playerRelease
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lasciando la pagina del player: di lato chiude, giù torna al mini player, su apre la coda. */
class PlayerReleaseTest {
    private val w = 1080f
    private val h = 2400f

    @Test
    fun sideClosesDownDocksUpOpensQueue() {
        assertEquals(PlayerRelease.CLOSE, playerRelease(Offset(600f, 0f), Offset.Zero, w, h))
        assertEquals(PlayerRelease.MINI, playerRelease(Offset(0f, 700f), Offset.Zero, w, h))
        assertEquals(PlayerRelease.QUEUE, playerRelease(Offset(0f, -110f), Offset.Zero, w, h))
        assertEquals(PlayerRelease.STAY, playerRelease(Offset(20f, -30f), Offset.Zero, w, h))
    }

    @Test
    fun aQuickFlickCountsToo() {
        assertEquals(PlayerRelease.MINI, playerRelease(Offset(0f, 40f), Offset(0f, 2_000f), w, h))
        assertEquals(PlayerRelease.QUEUE, playerRelease(Offset(0f, -40f), Offset(0f, -2_000f), w, h))
        // Uno scatto di lato non è uno scatto in su.
        assertEquals(PlayerRelease.STAY, playerRelease(Offset(0f, -40f), Offset(2_500f, -1_600f), w, h))
    }
}
