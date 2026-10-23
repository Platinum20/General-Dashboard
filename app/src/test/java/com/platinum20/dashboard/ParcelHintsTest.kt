package com.platinum20.dashboard

import com.platinum20.dashboard.notes.ParcelHint
import com.platinum20.dashboard.notes.ParcelHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParcelHintsTest {
    @Test
    fun conPochiPacchiCiSonoISuggerimenti() {
        assertEquals(ParcelHint.entries, ParcelHints.suggest(0, autoFillOn = false))
        assertEquals(ParcelHint.entries, ParcelHints.suggest(2, autoFillOn = false))
        assertTrue(ParcelHints.suggest(3, autoFillOn = false).isEmpty())
    }

    @Test
    fun compilaDaSoloAccesoNonSiPropone() {
        assertEquals(listOf(ParcelHint.PASTE, ParcelHint.SHARE), ParcelHints.suggest(1, autoFillOn = true))
    }

    @Test
    fun codiceDagliAppunti() {
        // Una mail copiata intera: si prende il codice dentro.
        assertEquals("1Z999AA10123456784", ParcelHints.codeFrom("Il tuo pacco UPS 1Z999AA10123456784 arriva domani"))
        assertEquals("RR123456789IT", ParcelHints.codeFrom("spedizione rr123456789it"))
        // Solo il codice, di un corriere che non si riconosce dalla forma.
        assertEquals("0512345678901", ParcelHints.codeFrom(" 0512 3456 78901 "))
        // Testo qualsiasi o vuoto: il dialogo parte vuoto.
        assertEquals("", ParcelHints.codeFrom("ricordati il latte"))
        assertEquals("", ParcelHints.codeFrom("ABCDEFGHIJK"))
        assertEquals("", ParcelHints.codeFrom(null))
        assertEquals("", ParcelHints.codeFrom("   "))
    }
}
