package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SettingsSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummaryTest {
    @Test
    fun homeSaysCardsAndPlace() {
        assertEquals("5 card su 9 · meteo di Roma", SettingsSummary.home(5, 9, "Roma"))
        assertEquals("5 card su 9 · meteo qui", SettingsSummary.home(5, 9, null))
        assertEquals("5 card su 9 · meteo qui", SettingsSummary.home(5, 9, "San Giovanni in Persiceto"))
    }

    @Test
    fun alertsListOnlyWhatIsOn() {
        assertEquals("Tutte spente", SettingsSummary.alerts(null, rain = false, radio = null, updates = false))
        assertEquals(
            "Mattino alle 07:30 e altre 2",
            SettingsSummary.alerts("07:30", rain = true, radio = null, updates = true),
        )
        assertEquals("Sveglia alle 7:00", SettingsSummary.alerts(null, rain = false, radio = "7:00", updates = false))
        assertEquals("Mattino alle 07:30 · pioggia", SettingsSummary.alerts("07:30", rain = true, radio = null, updates = false))
    }

    @Test
    fun accountsReadAsASentence() {
        assertEquals("Nessun account collegato", SettingsSummary.accounts(emptyList()))
        assertEquals("Spotify collegato", SettingsSummary.accounts(listOf("Spotify")))
        assertEquals("YouTube e Spotify collegati", SettingsSummary.accounts(listOf("YouTube", "Spotify")))
        assertEquals("4 account collegati", SettingsSummary.accounts(listOf("YouTube", "Spotify", "WindTre", "Gmail")))
    }
}
