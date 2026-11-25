package com.platinum20.dashboard

import com.platinum20.dashboard.capture.VoiceSlide
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSlideTest {
    @Test
    fun aSinistraButtaADestraInvia() {
        assertEquals(-1, VoiceSlide.armed(-120f, 110f, 32f))
        assertEquals(0, VoiceSlide.armed(-60f, 110f, 32f))
        assertEquals(0, VoiceSlide.armed(10f, 110f, 32f))
        assertEquals(1, VoiceSlide.armed(40f, 110f, 32f))
    }
}
