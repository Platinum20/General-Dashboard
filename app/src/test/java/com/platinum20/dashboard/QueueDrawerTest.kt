package com.platinum20.dashboard

import com.platinum20.dashboard.media.QueueEntry
import com.platinum20.dashboard.media.Repeat
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.ui.screens.QueueDrawer
import com.platinum20.dashboard.ui.screens.playerModeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La coda del player come un foglio solo che spunta dal bordo, e le righe dei tasti Casuale/Ripeti. */
class QueueDrawerTest {
    @Test fun aFlingDecidesOtherwiseHalfway() {
        assertTrue(QueueDrawer.staysOpen(offset = 900f, velocity = -2000f, hidden = 1000f))
        assertFalse(QueueDrawer.staysOpen(offset = 100f, velocity = 2000f, hidden = 1000f))
        assertTrue(QueueDrawer.staysOpen(offset = 400f, velocity = 0f, hidden = 1000f))
        assertFalse(QueueDrawer.staysOpen(offset = 600f, velocity = 0f, hidden = 1000f))
    }

    @Test fun peekSaysWhatComesNext() {
        val next = QueueEntry(1, ResumeItem(ResumeKind.SPOTIFY, "spotify:track:a", "Silent Running", "Gorillaz", at = 0L))
        assertEquals("Silent Running · Gorillaz · altri 3", QueueDrawer.peekLine(next, 4))
        assertEquals("Silent Running · Gorillaz", QueueDrawer.peekLine(next, 1))
        assertEquals("la coda è vuota", QueueDrawer.peekLine(null, 0))
    }

    @Test fun modeButtonsSayWhatTheyDid() {
        assertEquals("Casuale: la coda è mescolata", playerModeText(shuffle = true))
        assertEquals("Ripeti questo brano", playerModeText(repeat = Repeat.ONE))
        assertEquals("Ripeti spento", playerModeText(repeat = Repeat.OFF))
    }
}
