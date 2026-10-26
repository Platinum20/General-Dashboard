package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureComposer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dettare tenendo premuto il tasto invia: la voce si aggiunge a quello che si stava scrivendo. */
class CaptureComposerJoinTest {
    @Test
    fun campoVuotoPrendeSoloLaVoce() {
        assertEquals("Comprare il pane", CaptureComposer.joinDictated("", "Comprare il pane"))
    }

    @Test
    fun laVoceSiAccodaConUnoSpazioSolo() {
        assertEquals("Comprare il pane e il latte", CaptureComposer.joinDictated("Comprare il pane ", " e il latte "))
    }

    @Test
    fun vocaleVuotoNonCambiaIlTesto() {
        assertEquals("Comprare il pane ", CaptureComposer.joinDictated("Comprare il pane ", "   "))
    }
}
