package com.platinum20.dashboard

import com.platinum20.dashboard.capture.VoiceSlide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSlideTest {
    @Test
    fun aSinistraButta() {
        assertTrue(VoiceSlide.cancels(-120f, 110f))
        assertFalse(VoiceSlide.cancels(-60f, 110f))
        assertFalse(VoiceSlide.cancels(40f, 110f))
    }

    @Test
    fun inSuBlocca() {
        assertTrue(VoiceSlide.locks(-80f, 70f))
        assertFalse(VoiceSlide.locks(-30f, 70f))
        assertFalse(VoiceSlide.locks(50f, 70f))
    }
}

class VoiceTipsTest {
    @Test
    fun laRigaDeiGestiSoloLePrimeVolte() {
        assertTrue(VoiceSlide.showTips(0))
        assertTrue(VoiceSlide.showTips(VoiceSlide.TIP_USES - 1))
        assertFalse(VoiceSlide.showTips(VoiceSlide.TIP_USES))
    }
}
