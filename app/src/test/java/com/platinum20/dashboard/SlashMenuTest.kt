package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SlashRecent
import com.platinum20.dashboard.ui.screens.slashOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il menu "/" come su Notion (1.589): ricerca e voci usate di recente. */
class SlashMenuTest {
    @Test
    fun searchPutsWordStartsFirst() {
        val labels = listOf("Titolo", "Tabella", "Scatta", "Nota o pagina di Canvas")
        assertEquals(listOf(1, 2, 3), slashOrder(labels, "ta"))
        assertEquals(listOf(3), slashOrder(labels, "pag"))
        assertEquals(listOf(0, 1, 2, 3), slashOrder(labels, ""))
        assertEquals(emptyList<Int>(), slashOrder(labels, "zzz"))
    }

    @Test
    fun recentKeepsTheLastThree() {
        assertEquals(listOf("tabella"), SlashRecent.push(emptyList(), "tabella"))
        assertEquals(listOf("punti", "tabella", "linea"), SlashRecent.push(listOf("tabella", "linea", "spunta"), "punti"))
        assertEquals(listOf("linea", "tabella"), SlashRecent.push(listOf("tabella", "linea"), "linea"))
    }
}
