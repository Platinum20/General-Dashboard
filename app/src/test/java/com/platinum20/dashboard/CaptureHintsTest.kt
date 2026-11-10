package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureHints
import com.platinum20.dashboard.capture.CaptureMode
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La barra del +: esempi come segnaposto, poi il simbolo (e il riassunto) di cosa diventa. */
class CaptureHintsTest {
    private val now = LocalDateTime.of(2026, 11, 10, 10, 0)

    private fun line(text: String) = CaptureHints.summary(CaptureGuess.guess(text, now), text, now)

    @Test fun gliEsempiGiranoSenzaFinire() {
        assertEquals(CaptureHints.example(0), CaptureHints.example(CaptureHints.examples.size))
        assertEquals(CaptureHints.examples.last(), CaptureHints.example(-1))
    }

    @Test fun ogniEsempioDiventaIlSuoTipo() {
        // Il segnaposto deve insegnare tutti i tipi: un esempio per tipo, tutti diversi.
        val kinds = CaptureHints.examples.map { CaptureGuess.guess(it, now) }
        assertEquals(kinds.size, kinds.toSet().size)
        assertTrue(CaptureMode.EVENT in kinds && CaptureMode.TASK in kinds)
    }

    @Test fun gliEsempiCiStannoInUnaRiga() {
        assertTrue(CaptureHints.examples.all { it.length <= 36 })
    }

    @Test fun campoVuotoNienteRiassunto() {
        assertNull(CaptureHints.summary(CaptureMode.TASK, "  ", now))
    }

    @Test fun unImportoNonEUnaSpesa() {
        // Dalla 1.447 l'app non tiene spese.
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
