package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.amountFieldWidth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il campo importo della scheda spesa non sparisce più: largo quanto il numero, mai zero. */
class AmountFieldWidthTest {
    @Test
    fun followsTheText() {
        assertEquals(64f, amountFieldWidth(60f), 0.01f)
    }

    @Test
    fun neverCollapses() {
        assertEquals(24f, amountFieldWidth(0f), 0.01f)
        assertEquals(180f, amountFieldWidth(500f), 0.01f)
    }
}
