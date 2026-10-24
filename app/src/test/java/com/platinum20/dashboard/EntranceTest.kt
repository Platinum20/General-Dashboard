package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.ui.screens.Entrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EntranceTest {
    @Test
    fun cardsTakeTurnsOnlyRightAfterThePageOpens() {
        val page = Any()
        // Appena aperta: una dopo l'altra, con un massimo per non far aspettare le ultime.
        val turns = List(10) { Entrance.nextTurn(page) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 7, 7), turns)
        // Scorrendo più tardi le card nuove sono già al loro posto.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        assertNull(Entrance.nextTurn(page))
        // Un'altra pagina ricomincia da capo.
        assertEquals(0, Entrance.nextTurn(Any()))
    }
}
