package com.platinum20.dashboard

import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class QuickParseTest {
    // Giovedì 22 ottobre 2026, ore 10:20.
    private val now = LocalDateTime.of(2026, 10, 22, 10, 20)
    private fun at(day: Int, h: Int, m: Int = 0, month: Int = 10, year: Int = 2026) = LocalDateTime.of(year, month, day, h, m)

    private fun check(input: String, text: String, due: LocalDateTime?, repeat: Repeat? = null) {
        val e = QuickParse.parse(input, now)
        assertEquals(input, text, e.text)
        assertEquals(input, due, e.due)
        assertEquals(input, repeat, e.repeat)
    }

    @Test fun senzaData() = check("comprare 2 litri di latte", "Comprare 2 litri di latte", null)
    @Test fun domaniAlle() = check("domani alle 9 chiamare il dentista", "Chiamare il dentista", at(23, 9))
    @Test fun domaniSera() = check("cena da Marco domani sera", "Cena da Marco", at(23, 20))
    @Test fun domaniSeraAlle() = check("domani sera alle 9 film", "Film", at(23, 21))
    @Test fun alleTrePomeriggio() = check("palestra alle 3", "Palestra", at(22, 15))
    @Test fun oraPassataVaADomani() = check("medicina alle 8:30", "Medicina", at(23, 8, 30))
    @Test fun oraConPunto() = check("riunione 18.30", "Riunione", at(22, 18, 30))
    @Test fun eMezza() = check("alle 9 e mezza colazione", "Colazione", at(23, 9, 30))
    @Test fun giornoSettimana() = check("venerdì palestra alle 18", "Palestra", at(23, 18))
    @Test fun stessoGiornoSettimanaDopo() = check("giovedì calcetto", "Calcetto", at(29, 9))
    @Test fun lunediSenzaAccento() = check("lunedi bolletta", "Bolletta", at(26, 9))
    @Test fun traMinuti() = check("tra 20 minuti forno", "Forno", at(22, 10, 40))
    @Test fun traUnOra() = check("fra un'ora richiamare", "Richiamare", at(22, 11, 20))
    @Test fun traMezzora() = check("tra mezz'ora pasta", "Pasta", at(22, 10, 50))
    @Test fun traGiorni() = check("tra 3 giorni ritirare pacco", "Ritirare pacco", at(25, 9))
    @Test fun dataNumerica() = check("bollo auto 15/11", "Bollo auto", at(15, 9, month = 11))
    @Test fun dataPassataAnnoProssimo() = check("tagliando 3/2", "Tagliando", at(3, 9, month = 2, year = 2027))
    @Test fun dataConMese() = check("il 15 novembre compleanno di Luca", "Compleanno di Luca", at(15, 9, month = 11))
    @Test fun ilGiornoDelMese() = check("il 28 pagare F24", "Pagare F24", at(28, 9))
    @Test fun ilGiornoPassato() = check("il 5 affitto", "Affitto", at(5, 9, month = 11))
    @Test fun oggiSenzaOra() = check("oggi spesa", "Spesa", at(22, 11))
    @Test fun stasera() = check("stasera chiamare mamma", "Chiamare mamma", at(22, 20))
    @Test fun mezzogiorno() = check("pranzo con Giulia a mezzogiorno", "Pranzo con Giulia", at(22, 12))

    @Test fun ogniGiorno() = check("ogni giorno alle 8 vitamine", "Vitamine", at(23, 8), Repeat.DAILY)
    @Test fun ogniLunedi() = check("ogni lunedì spazzatura alle 21", "Spazzatura", at(26, 21), Repeat.WEEKLY)
    @Test fun ogniMeseIl() = check("affitto ogni mese il 5", "Affitto", at(5, 9, month = 11), Repeat.MONTHLY)
    @Test fun ilDiOgniMese() = check("il 27 di ogni mese bolletta luce", "Bolletta luce", at(27, 9), Repeat.MONTHLY)
    @Test fun ogniAnno() = check("ogni anno 3/1 assicurazione", "Assicurazione", at(3, 9, month = 1, year = 2027), Repeat.YEARLY)

    @Test
    fun soloDataResta() {
        val e = QuickParse.parse("domani", now)
        assertEquals("domani", e.text)
        assertEquals(at(23, 9), e.due)
    }

    @Test
    fun ripetizioneSuccessiva() {
        assertEquals(at(5, 9, month = 12), Repeat.MONTHLY.nextAfter(at(5, 9, month = 11), at(6, 0, month = 11)))
        assertEquals(at(23, 8), Repeat.DAILY.nextAfter(at(20, 8), now))
    }

    @Test
    fun numeriNonSonoOre() {
        assertNull(QuickParse.parse("leggere 30 pagine", now).due)
    }
}
