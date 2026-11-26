package com.platinum20.dashboard

import com.platinum20.dashboard.search.SearchRecents
import com.platinum20.dashboard.search.SearchSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le fonti nuove di Cerca (2026-11-26, punto 4) e le cose aperte di recente (punto 9). */
class SearchSourcesTest {
    @Test fun impostazioniPerParola() {
        assertEquals("Tema", SearchSources.settings("tema").first().first.title)
        assertEquals("Account", SearchSources.settings("windtre").first().first.title)
        assertEquals("Avvisami se sta per piovere", SearchSources.settings("pioggia").first().first.title)
        assertTrue(SearchSources.settings("zzzz").isEmpty())
    }

    @Test fun ogniImpostazioneHaUnaPaginaVera() {
        val pages = com.platinum20.dashboard.ui.screens.SettingsPage.entries.map { it.name }.toSet()
        assertTrue(SearchSources.SETTINGS.all { it.page == null || it.page in pages })
    }

    @Test fun etichetteConOSenzaCancelletto() {
        assertEquals(listOf("lavoro"), SearchSources.names(listOf("lavoro", "casa"), "#lav").map { it.first })
        assertEquals(listOf("lavoro"), SearchSources.names(listOf("lavoro", "casa"), "lavoro").map { it.first })
        assertTrue(SearchSources.names(listOf("lavoro"), "#").isEmpty())
    }

    @Test fun biglietti() {
        assertTrue(SearchSources.asksTickets("biglietti"))
        assertFalse(SearchSources.asksTickets("treno"))
        assertEquals(setOf(12L, 40L), SearchSources.ticketEvents(listOf("12-0.png", "12-1.png", "40-0.png", "x.png")))
    }

    @Test fun apertiDiRecenteUnaVoltaSola() {
        val a = SearchRecents.Opened("Turno", "Ven", "home/calendar/event?id=1", "event")
        val b = SearchRecents.Opened("Nota", "Nota", "notes/edit?id=2", "note")
        val list = SearchRecents.addOpened(SearchRecents.addOpened(SearchRecents.addOpened(emptyList(), a), b), a)
        assertEquals(listOf(a, b), list)
        val full = (1..8).fold(emptyList<SearchRecents.Opened>()) { acc, i -> SearchRecents.addOpened(acc, a.copy(route = "r$i")) }
        assertEquals(SearchRecents.MAX_OPENED, full.size)
    }
}
