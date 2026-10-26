package com.platinum20.dashboard

import com.platinum20.dashboard.news.RecapSpeaker
import org.junit.Assert.assertEquals
import org.junit.Test

/** La lettura del punto si muove come una canzone: dal tempo della barra alla parte e alla parola giusta. */
class RecapSeekTest {
    private val script = listOf("Il punto delle 9:00, due temi.", "Primo tema: la pioggia arriva al nord.", "Secondo tema: lo sciopero dei treni.")

    @Test
    fun ogniParteCominciaDopoLaPrecedente() {
        val starts = RecapSpeaker.timeline(script)
        assertEquals(3, starts.size)
        assertEquals(0L, starts[0])
        assertEquals(true, starts[1] < starts[2])
    }

    @Test
    fun trascinandoSiRipartedaInizioParola() {
        val starts = RecapSpeaker.timeline(script)
        assertEquals(0 to 0, RecapSpeaker.locate(script, 0))
        assertEquals(1 to 0, RecapSpeaker.locate(script, starts[1]))
        val (part, char) = RecapSpeaker.locate(script, starts[1] + 900)
        assertEquals(1, part)
        // A inizio parola: il carattere prima è uno spazio.
        assertEquals(' ', script[1][char - 1])
        // Oltre la fine: l'ultima parte.
        assertEquals(2, RecapSpeaker.locate(script, Long.MAX_VALUE / 2).first)
    }

    @Test
    fun iCapitoliComincianoDoveComincianoLeParti() {
        val starts = RecapSpeaker.timeline(script)
        val chapters = RecapSpeaker.chaptersOf(script, listOf("Apertura", "La pioggia", "Lo sciopero"))
        assertEquals(listOf("Apertura", "La pioggia", "Lo sciopero"), chapters.map { it.label })
        assertEquals(starts, chapters.map { it.startMs })
        // Senza nomi niente capitoli.
        assertEquals(emptyList<Any>(), RecapSpeaker.chaptersOf(script, emptyList()))
    }

    @Test
    fun lArticoloSiLeggeDalTitoloSenzaParagrafiVuoti() {
        val parts = com.platinum20.dashboard.news.RecapReader.articleScript("Titolo «forte»", listOf("Primo.", "  ", "Vedi https://x.it ora."))
        assertEquals(listOf("Titolo forte", "Primo.", "Vedi ora."), parts)
    }
}
