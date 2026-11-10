package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.TripStops
import org.junit.Assert.assertEquals
import org.junit.Test

/** La riga compatta della card Calendario in Home: tutte alte uguali, sotto il nome fino a quando e quanto dura. */
class CompactTripRowTest {
    private fun stops(to: String, middle: String, extra: String = "", title: String? = null) =
        TripStops(departAt = "07:00", from = "Turno su Anna", arriveAt = "13:00", to = to, middle = middle,
            begin = 0L, end = 6 * 3_600_000L, title = title, extra = extra, below = "Intercity 612 · Carrozza 8")

    @Test fun turno() {
        val t = stops(to = "", middle = "6 h", extra = "In doppio con Marco")
        assertEquals("Turno su Anna", t.compactTitle())
        assertEquals("fino alle 13:00 · 6 h · In doppio con Marco", t.compactLine())
    }

    @Test fun treno() =
        assertEquals("Arrivo a Milano alle 13:00 · 6 h", stops(to = "Arrivo a Milano", middle = "6 h").compactLine())

    @Test fun finisceDomani() =
        assertEquals("fino a domani alle 13:00 · 6 h · Ufficio", stops(to = "Domani", middle = "6 h · Ufficio").compactLine())

    @Test fun titoloProprio() = assertEquals("Cena", stops(to = "", middle = "", title = "Cena").compactTitle())
}
