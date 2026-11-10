package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureHints
import com.platinum20.dashboard.capture.CaptureMode
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le parole sopra la barra del +: esempi le prime volte, poi il riassunto di cosa diventa. */
class CaptureHintsTest {
    private val now = LocalDateTime.of(2026, 11, 10, 10, 0)

    private fun line(text: String) = CaptureHints.summary(CaptureGuess.guess(text, now), text, now)

    @Test fun esempiSoloPerIPrimiCinqueSalvataggi() {
        assertTrue(CaptureHints.showExamples(0))
        assertTrue(CaptureHints.showExamples(4))
        assertFalse(CaptureHints.showExamples(5))
    }

    @Test fun gliEsempiGiranoSenzaFinire() {
        assertEquals(CaptureHints.example(0), CaptureHints.example(CaptureHints.examples.size))
        assertTrue(CaptureHints.example(-1).startsWith("Prova: "))
    }

    @Test fun ilSegnapostoCiStaSempre() {
        assertTrue(CaptureHints.PLACEHOLDER.length <= 20)
    }

    @Test fun campoVuotoNienteRiassunto() {
        assertNull(CaptureHints.summary(CaptureMode.TASK, "  ", now))
    }

    @Test fun unImportoNonEUnaSpesa() {
        // Dalla 1.443 l'app non tiene spese.
        val s = line("pizza 12,50")
        assertTrue(s.toString(), s == null || !s.startsWith("Spesa"))
    }

    @Test fun promemoriaConIlQuando() {
        assertEquals("Promemoria · domani 9:00", line("domani alle 9 chiamare il dentista"))
    }

    @Test fun promemoriaSenzaDataSoloIlTipo() {
        assertEquals("Promemoria", line("comprare il latte"))
    }

    @Test fun eventoColGiorno() {
        val s = line("venerdì alle 20 cena da Luca")!!
        assertTrue(s, s.startsWith("Evento · ") && s.endsWith("20:00"))
    }

    @Test fun notaSoloIlTipo() {
        assertEquals("Nota", CaptureHints.summary(CaptureMode.NOTE, "prima riga\nseconda", now))
    }
}
