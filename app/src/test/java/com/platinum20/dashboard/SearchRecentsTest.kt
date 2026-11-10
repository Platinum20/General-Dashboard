package com.platinum20.dashboard

import com.platinum20.dashboard.search.SearchRecents
import com.platinum20.dashboard.ui.screens.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le ricerche recenti di Cerca: la più nuova in cima, niente doppioni, poche. */
class SearchRecentsTest {
    @Test fun laPiuNuovaInCima() {
        assertEquals(listOf("patente", "commercialista"), SearchRecents.add(listOf("commercialista"), "patente"))
    }

    @Test fun nienteDoppioniAncheConLeMaiuscole() {
        assertEquals(listOf("Patente", "rinnovo"), SearchRecents.add(listOf("rinnovo", "patente"), " Patente "))
    }

    @Test fun unaLetteraNonConta() {
        assertEquals(listOf("rinnovo"), SearchRecents.add(listOf("rinnovo"), "p"))
    }

    @Test fun alMassimoCinque() {
        val full = (1..5).map { "cosa $it" }
        val next = SearchRecents.add(full, "nuova")
        assertEquals(SearchRecents.MAX, next.size)
        assertEquals("nuova", next.first())
    }

    @Test fun iChipStannoInteri() {
        assertTrue(SearchFilter.entries.all { it.label.length <= 10 })
    }
}
