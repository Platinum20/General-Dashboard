package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.MuteCaptions
import com.platinum20.dashboard.media.youtube.player.MuteCaptions.Step
import com.platinum20.dashboard.media.youtube.player.YtSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuteCaptionsTest {
    @Test
    fun zeroTurnsOnAndBackOff() {
        assertEquals(Step.ON, MuteCaptions.step(muted = true, chosen = null, autoOn = false, declined = false, showing = null))
        assertEquals(Step.OFF, MuteCaptions.step(muted = false, chosen = null, autoOn = true, declined = false, showing = "it"))
        assertEquals(Step.NONE, MuteCaptions.step(muted = false, chosen = null, autoOn = false, declined = false, showing = null))
    }

    @Test
    fun handChoiceWins() {
        // Scelti a mano: restano anche quando il volume risale.
        assertEquals(Step.NONE, MuteCaptions.step(muted = false, chosen = "en", autoOn = false, declined = false, showing = "en"))
        assertEquals(Step.NONE, MuteCaptions.step(muted = true, chosen = "en", autoOn = false, declined = false, showing = "en"))
        // Spenti a mano durante lo zero: non si riaccendono.
        assertEquals(Step.NONE, MuteCaptions.step(muted = true, chosen = null, autoOn = false, declined = true, showing = null))
    }

    @Test
    fun picksOwnItalianFirst() {
        val itTr = YtSubtitle("it", "Italiano (tradotti)", "u?a=1&tlang=it", auto = true)
        val en = YtSubtitle("en", "Inglese", "u?en", auto = false)
        val itAuto = YtSubtitle("it", "Italiano (automatici)", "u?it", auto = true)
        assertEquals(en, MuteCaptions.pick(listOf(itTr, en)))
        assertEquals(itAuto, MuteCaptions.pick(listOf(en, itAuto)))
        assertNull(MuteCaptions.pick(emptyList()))
    }
}
