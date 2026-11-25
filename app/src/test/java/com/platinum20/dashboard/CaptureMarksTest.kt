package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureHints
import com.platinum20.dashboard.capture.CaptureMarks
import com.platinum20.dashboard.capture.VoiceSlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMarksTest {
    private fun words(t: String) = CaptureMarks.whenRanges(t).map { t.substring(it) }

    @Test fun coloraLeParoleDelQuando() {
        assertEquals(listOf("domani", "alle 9"), words("Dentista domani alle 9"))
        assertEquals(listOf("il 3 dicembre"), words("cena il 3 dicembre"))
        assertEquals(listOf("venerdì prossimo"), words("chiama Marco venerdì prossimo"))
        assertEquals(emptyList<String>(), words("comprare il latte"))
        assertEquals(emptyList<String>(), words("domanina"))
    }

    @Test fun righeColTrattinoSonoTantiDaFare() {
        assertEquals(listOf("latte", "pane"), CaptureMarks.dashItems("- latte\n- pane"))
        assertEquals(listOf("latte", "uova"), CaptureMarks.dashItems("• latte\n\n• uova\n"))
        assertNull(CaptureMarks.dashItems("- latte"))
        assertNull(CaptureMarks.dashItems("spesa\n- latte\n- pane"))
        assertNull(CaptureMarks.dashItems("comprare il latte"))
    }

    @Test fun ilVocaleBloccatoSiFermaNelSilenzio() {
        assertTrue(CaptureMarks.autoStop(true, "ciao", 4_000))
        assertFalse(CaptureMarks.autoStop(true, "ciao", 3_000))
        assertFalse(CaptureMarks.autoStop(true, "", 9_000))
        assertFalse(CaptureMarks.autoStop(false, "ciao", 9_000))
    }

    @Test fun timerEIncolla() {
        assertEquals("0:07", VoiceSlide.clock(7))
        assertEquals("1:23", VoiceSlide.clock(83))
        assertTrue(CaptureHints.freshClip(1_000, 1_000 + 60_000))
        assertFalse(CaptureHints.freshClip(1_000, 1_000 + 11 * 60_000))
        assertFalse(CaptureHints.freshClip(0, 5_000))
    }
}
