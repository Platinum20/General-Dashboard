package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.DayLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il numero del giorno sta nel cerchio: il nome accanto non lo ripete (Antonio, 2026-11-11). */
class DayLabelNumberTest {
    @Test
    fun numberIsDroppedFromTheLabel() {
        assertEquals("Dom", DayLabel.withoutNumber("Dom 15", 15))
        assertEquals("Ven · dic", DayLabel.withoutNumber("Ven 20 dic", 20))
        assertEquals("nov 2027", DayLabel.withoutNumber("15 nov 2027", 15))
    }

    @Test
    fun labelsWithoutTheNumberStay() {
        assertEquals("Domani", DayLabel.withoutNumber("Domani", 12))
        assertEquals("Dom 15", DayLabel.withoutNumber("Dom 15", null))
    }
}
