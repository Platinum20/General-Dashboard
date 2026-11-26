package com.platinum20.dashboard

import com.platinum20.dashboard.search.SearchWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Le parole del tempo in Cerca (2026-11-26, punto 5). Oggi = giovedì 26 novembre 2026. */
class SearchWhenTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun giorni() {
        assertEquals(today.plusDays(1), SearchWhen.parse("domani", today)!!.from)
        assertEquals(LocalDate.of(2026, 11, 27), SearchWhen.parse("venerdì", today)!!.from)
        assertEquals(today, SearchWhen.parse("giovedi", today)!!.from)
        assertEquals(LocalDate.of(2026, 12, 3), SearchWhen.parse("giovedì prossimo", today)!!.from)
        assertEquals("Venerdì 27 novembre", SearchWhen.parse("venerdi", today)!!.label)
    }

    @Test fun ilMesePiuVicino() {
        val march = SearchWhen.parse("marzo", today)!!
        assertEquals(LocalDate.of(2027, 3, 1), march.from)
        assertEquals(LocalDate.of(2027, 3, 31), march.to)
        assertEquals(LocalDate.of(2026, 10, 1), SearchWhen.parse("ottobre", today)!!.from)
        assertEquals(LocalDate.of(2025, 3, 1), SearchWhen.parse("marzo 2025", today)!!.from)
    }

    @Test fun date() {
        assertEquals(LocalDate.of(2027, 3, 12), SearchWhen.parse("12 marzo", today)!!.from)
        assertEquals(LocalDate.of(2026, 12, 5), SearchWhen.parse("5/12", today)!!.from)
        assertEquals(LocalDate.of(2027, 1, 2), SearchWhen.parse("02/01/27", today)!!.from)
    }

    @Test fun settimane() {
        val next = SearchWhen.parse("prossima settimana", today)!!
        assertEquals(LocalDate.of(2026, 11, 30), next.from)
        assertEquals(LocalDate.of(2026, 12, 6), next.to)
    }

    @Test fun conUnaParola() {
        val (span, rest) = SearchWhen.split("turno venerdì", today)!!
        assertEquals(LocalDate.of(2026, 11, 27), span.from)
        assertEquals("turno", rest)
        assertEquals("cena", SearchWhen.split("domani cena", today)!!.second)
    }

    @Test fun altroNonETempo() {
        assertNull(SearchWhen.split("pane e latte", today))
        assertNull(SearchWhen.parse("32 marzo", today))
    }
}
