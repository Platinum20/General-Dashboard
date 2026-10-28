package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.showPlayerSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta 1.279 e 1.284: Spese aperta da Note accende Note; la radio dice "In diretta" una volta sola. */
class BetaNoteSpeseLiveTest {

    @Test
    fun speseAccendeLaSezioneDaCuiSiApre() {
        assertEquals(Routes.HOME, Routes.barTab(Routes.EXPENSES))
        assertEquals(Routes.NOTES, Routes.barTab(Routes.NOTES_EXPENSES))
        assertEquals(Routes.MEDIA, Routes.barTab(Routes.MEDIA))
        assertEquals(null, Routes.barTab(null))
    }

    @Test
    fun inDirettaNonDueVolte() {
        assertFalse(showPlayerSubtitle(live = true, subtitle = "In diretta"))
        assertFalse(showPlayerSubtitle(live = true, subtitle = " in diretta "))
        // Il nome del programma resta, e un podcast che parla di dirette pure.
        assertTrue(showPlayerSubtitle(live = true, subtitle = "Radio Deejay · Say Waaad"))
        assertTrue(showPlayerSubtitle(live = false, subtitle = "In diretta"))
    }
}
