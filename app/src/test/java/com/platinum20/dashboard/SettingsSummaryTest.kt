package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.AccountTiles
import com.platinum20.dashboard.ui.screens.SettingsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun accountRowSaysWhatToFixFirst() {
        fun tiles(shiftsExpired: Boolean = false, gmailBroken: Boolean = false, all: Boolean = true) = AccountTiles.build(
            spotify = all, youtube = all, gmail = all, gmailBroken = gmailBroken,
            sim = all, simStale = false, shifts = all, shiftsExpired = shiftsExpired,
        )
        assertEquals("5 collegati su 5", AccountTiles.summary(tiles()))
        assertEquals("Nessun account collegato", AccountTiles.summary(tiles(all = false)))
        assertEquals("Creators Pro da sistemare", AccountTiles.summary(tiles(shiftsExpired = true)))
        assertEquals("2 account da sistemare", AccountTiles.summary(tiles(shiftsExpired = true, gmailBroken = true)))
        AccountTiles.summary(tiles(shiftsExpired = true)).let { assertTrue(it.length <= SettingsSummary.MAX) }
    }

    @Test
    fun accountPageGroupsFixFirstAndDropsEmptyGroups() {
        val t = AccountTiles.build(
            spotify = true, youtube = false, gmail = true, gmailBroken = false,
            sim = true, simStale = false, shifts = true, shiftsExpired = true,
        )
        assertEquals(listOf("Da sistemare", "Collegati", "Da collegare"), AccountTiles.groups(t).map { it.first })
        assertEquals(listOf("Creators Pro"), AccountTiles.groups(t).first().second.map { it.name })
        val ok = AccountTiles.build(true, true, true, false, true, false, true, false)
        assertEquals(listOf("Collegati"), AccountTiles.groups(ok).map { it.first })
    }
}
