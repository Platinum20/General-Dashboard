package com.platinum20.dashboard

import com.platinum20.dashboard.capture.VoiceSlide
import org.junit.Assert.assertEquals
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
    fun lOndaEntraDaDestraECopreLaScritta() {
        assertEquals(300f, VoiceSlide.waveStart(300f, 0, 3f, 3f), 0f)
        assertEquals(243f, VoiceSlide.waveStart(300f, 10, 3f, 3f), 0f)
        assertEquals(0f, VoiceSlide.waveStart(300f, 90, 3f, 3f), 0f)
    }

    @Test
    fun leFrecceSiAccendonoVersoSinistra() {
        assertEquals(1f, VoiceSlide.chevronAlpha(2, 0.1f), 0f)
        assertEquals(1f, VoiceSlide.chevronAlpha(1, 0.5f), 0f)
        assertEquals(1f, VoiceSlide.chevronAlpha(0, 0.9f), 0f)
        assertEquals(0.3f, VoiceSlide.chevronAlpha(0, 0.1f), 0f)
    }
}
