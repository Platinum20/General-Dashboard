package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.RowStart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowStartTest {
    @Test fun torna_all_inizio_se_le_tessere_arrivano_davanti() = assertTrue(RowStart.reset(touched = false, firstIndex = 7, offset = 0))
    @Test fun lasciata_stare_se_gia_all_inizio() = assertFalse(RowStart.reset(touched = false, firstIndex = 0, offset = 0))
    @Test fun il_dito_vince() = assertFalse(RowStart.reset(touched = true, firstIndex = 7, offset = 30))
}
