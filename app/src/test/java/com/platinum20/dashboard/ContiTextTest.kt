package com.platinum20.dashboard

import com.platinum20.dashboard.bank.Pocket
import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.ui.screens.ContiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContiTextTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun saldoSenzaCentesimiConIlPuntoDelleMigliaia() {
        assertEquals("3.342 €", ContiText.euros(334_210))
        assertEquals("843 €", ContiText.euros(84_260))
    }

    @Test
    fun gigaETotaleConRinnovoCorto() {
        val s = SimStatus(data = Allowance(62.0, 150.0), renewal = today.plusDays(9))
        assertEquals("62 GB", ContiText.giga(s))
        assertEquals("di 150 · rinnovo tra 9 g", ContiText.simLine(s, today))
        // Sta in mezza card.
        assertTrue(ContiText.simLine(s, today)!!.length <= 26)
    }

    @Test
    fun illimitatiSenzaTotale() {
        val s = SimStatus(data = Allowance(0.0, unlimited = true), renewal = today.plusDays(1))
        assertEquals("Illimitati", ContiText.giga(s))
        assertEquals("rinnovo domani", ContiText.simLine(s, today))
    }

    @Test
    fun daSpendereSoloConPiuTasche() {
        val main = Pocket("a", "Conto", 84_210, main = true)
        assertEquals("842 € da spendere", ContiText.spendable(listOf(main, Pocket("b", "Risparmi", 250_000, main = false))))
        assertNull(ContiText.spendable(listOf(main)))
    }
}
