package com.platinum20.dashboard

import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.ui.screens.simPageRows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SimPageRowsTest {
    @Test
    fun righeSottoIGiga() {
        val today = LocalDate.of(2026, 11, 10)
        val s = SimStatus(
            data = Allowance(42.0, 200.0),
            minutes = Allowance(0.0, unlimited = true),
            sms = Allowance(0.0, unlimited = true),
            credit = 3.5,
            renewal = LocalDate.of(2026, 11, 15),
        )
        assertEquals(
            listOf("Minuti" to "illimitati", "SMS" to "illimitati", "Credito" to "3,50 €", "Rinnovo" to "15/11 · tra 5 giorni"),
            simPageRows(s, today),
        )
    }
}
