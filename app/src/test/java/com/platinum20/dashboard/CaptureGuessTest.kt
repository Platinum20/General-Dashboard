package com.platinum20.dashboard

import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CaptureGuessTest {
    private val now = LocalDateTime.of(2026, 10, 23, 10, 0)
    private fun guess(t: String) = CaptureGuess.guess(t, now)

    @Test fun spese() {
        assertEquals(CaptureMode.EXPENSE, guess("12,50 pizza"))
        assertEquals(CaptureMode.EXPENSE, guess("benzina 40 euro"))
        assertEquals(CaptureMode.EXPENSE, guess("€ 8 cinema"))
        assertEquals(CaptureMode.EXPENSE, guess("pizza 12€"))
    }

    @Test fun orariNonSonoSpese() {
        assertEquals(CaptureMode.TASK, guess("domani alle 9 chiamare il dentista"))
        assertEquals(CaptureMode.TASK, guess("comprare 3 libri"))
        assertEquals(CaptureMode.TASK, guess("alle 12.30 ritirare la giacca"))
    }

    @Test fun eventi() {
        assertEquals(CaptureMode.EVENT, guess("venerdì alle 20 cena da Luca"))
        assertEquals(CaptureMode.EVENT, guess("domani alle 15 riunione"))
        // Senza quando resta un promemoria: l'evento non saprebbe dove stare.
        assertEquals(CaptureMode.TASK, guess("prenotare la cena"))
    }

    @Test fun pacchiENote() {
        assertEquals(CaptureMode.PARCEL, guess("scarpe TBA123456789012"))
        assertEquals(CaptureMode.NOTE, guess("Idee regalo\nlibro, sciarpa"))
        assertEquals(CaptureMode.NOTE, guess("x".repeat(130)))
    }

    @Test fun vuotoEPromemoria() {
        assertEquals(CaptureMode.TASK, guess(""))
        assertEquals(CaptureMode.TASK, guess("Chiamare la mamma"))
    }

    @Test fun abitudini() {
        assertEquals(CaptureMode.HABIT, guess("ogni giorno leggere 20 pagine"))
        assertEquals(CaptureMode.HABIT, guess("palestra 3 volte a settimana"))
        // Con l'ora resta un promemoria che si ripete.
        assertEquals(CaptureMode.TASK, guess("ogni giorno alle 8 pillola"))
    }

    @Test fun dateDaRicordare() {
        assertEquals(CaptureMode.DATE, guess("compleanno di Marta 12 marzo"))
        assertEquals(CaptureMode.DATE, guess("anniversario 3 giugno"))
        // Senza giorno non è una data; con l'ora è un evento.
        assertEquals(CaptureMode.TASK, guess("regalo per il compleanno di Marta"))
        assertEquals(CaptureMode.EVENT, guess("sabato alle 20 festa di compleanno"))
    }
}
