package com.platinum20.dashboard

import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.ui.screens.simRows
import org.junit.Assert.assertEquals
import org.junit.Test

/** La card SIM della Home (1.448): giga, minuti e SMS, ognuno con la sua linea. */
class ContiTextTest {
    @Test
    fun cardConGigaMinutiESms() {
        val s = SimStatus(
            data = Allowance(62.0, 150.0),
            minutes = Allowance(0.0, unlimited = true),
            sms = Allowance(40.0, 50.0),
            roaming = Allowance(7.5, 9.0),
        )
        val rows = simRows(s)
        assertEquals(listOf("Giga", "Minuti", "SMS"), rows.map { it.name })
        assertEquals("62 GB di 150 GB", rows[0].value)
        assertEquals("illimitati", rows[1].value)
        assertEquals("40 di 50", rows[2].value)
        // Ogni riga ha la sua linea.
        assertEquals(3, rows.count { it.meter != null })
        // Il roaming UE solo nella pagina.
        assertEquals("Roaming UE", simRows(s, big = true).last().name)
    }

    @Test
    fun senzaMinutiESmsSoloGiga() {
        val rows = simRows(SimStatus(data = Allowance(3.5, 10.0)))
        assertEquals(listOf("Giga"), rows.map { it.name })
    }
}
