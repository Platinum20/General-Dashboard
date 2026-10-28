package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SettingsLines
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta 1.283: "Escludi video per membri" e "Riconosci canzoni" avevano il sottotitolo tagliato. */
class SettingsLinesTest {
    @Test
    fun sottotitoliInUnaRiga() {
        SettingsLines.all.forEach { assertTrue("Troppo lungo: $it", it.length <= SettingsLines.MAX) }
    }
}
