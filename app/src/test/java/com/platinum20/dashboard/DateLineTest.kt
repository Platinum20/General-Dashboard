package com.platinum20.dashboard

import com.platinum20.dashboard.notes.WhenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateLineTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun etaEDistanza() {
        assertEquals("compie 34 anni · tra 12 giorni", WhenText.dateLine(34, today.plusDays(12), today))
        assertEquals("compie 34 anni · domani", WhenText.dateLine(34, today.plusDays(1), today))
    }

    @Test
    fun senzaEtaSoloOltreTreGiorni() {
        assertNull(WhenText.dateLine(null, today.plusDays(3), today))
        assertEquals("tra 4 giorni", WhenText.dateLine(null, today.plusDays(4), today))
        assertEquals("tra 3 mesi", WhenText.dateLine(null, today.plusMonths(3), today))
    }
}
