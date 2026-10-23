package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsSource
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Il copione del punto del giorno letto ad alta voce. */
class RecapReaderTest {
    private fun item(title: String, summary: String = "", source: String = "ansa") =
        NewsItem(title, "https://example.org/$source/${title.hashCode()}", summary, null, null, NewsSource(source, source.uppercase(), ""))

    private fun theme(title: String, summary: String = "", sources: Int = 1): RecapTheme {
        val items = (1..sources).map { item(title, summary, "fonte$it") }
        return RecapTheme(keywords = emptyList(), headline = items.first(), items = items)
    }

    @Test fun aperturaConOraENumeroDiTemi() {
        val script = RecapReader.script(listOf(theme("Manovra"), theme("Meteo")), LocalTime.of(18, 5))
        assertEquals("Il punto delle 18:05, 2 temi.", script.first())
        assertEquals(3, script.size)
        assertEquals("Il punto delle 7:30, un tema.", RecapReader.script(listOf(theme("Sciopero")), LocalTime.of(7, 30)).first())
    }

    @Test fun unaFrasePerTemaEQuanteTestate() {
        val script = RecapReader.script(
            listOf(theme("Sciopero dei treni venerdì", "Si fermano i regionali dalle 9 alle 17. Garantite le fasce.", sources = 3)),
            LocalTime.of(8, 0),
        )
        assertEquals("Primo tema: Sciopero dei treni venerdì. Si fermano i regionali dalle 9 alle 17. Ne parlano 3 testate.", script[1])
    }

    @Test fun senzaIndirizziNeVirgolette() {
        val script = RecapReader.script(
            listOf(theme("«Basta rinvii» – il governo", "Leggi tutto su https://example.org/a?b=1 e su www.sito.it")),
            LocalTime.of(12, 0),
        )
        val text = script[1]
        assertFalse(text.contains("http") || text.contains("www") || text.contains("«") || text.contains("»"))
        assertTrue(text.startsWith("Primo tema: Basta rinvii, il governo."))
    }

    @Test fun riassuntoUgualeAlTitoloNonSiRipete() {
        val script = RecapReader.script(listOf(theme("Nuovo ponte a Genova", "Nuovo ponte a Genova: i lavori")), LocalTime.of(9, 0))
        assertEquals("Primo tema: Nuovo ponte a Genova.", script[1])
    }

    @Test fun alMassimoSeiTemiENienteSeVuoto() {
        val many = (1..9).map { theme("Tema $it") }
        val script = RecapReader.script(many, LocalTime.of(20, 0))
        assertEquals(1 + RecapReader.MAX_THEMES, script.size)
        assertTrue(script.last().startsWith("Sesto tema"))
        assertEquals(emptyList<String>(), RecapReader.script(emptyList(), LocalTime.NOON))
    }

    @Test fun primaFraseCorta() {
        val long = "a".repeat(10) + " " + "parola ".repeat(60)
        assertTrue(RecapReader.firstSentence(long).length <= 221)
        assertEquals("Una frase.", RecapReader.firstSentence("Una frase. Poi un'altra."))
        assertEquals("", RecapReader.firstSentence(""))
    }
}
