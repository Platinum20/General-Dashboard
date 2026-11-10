package com.platinum20.dashboard

import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.ui.screens.DayScrub
import com.platinum20.dashboard.ui.screens.ScrubDay
import com.platinum20.dashboard.ui.screens.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class DayScrubTest {
    private fun row(key: String, day: String? = null, month: String? = null, today: Boolean = false) =
        TimelineEntry(key = key, kind = BriefKind.TASK, text = key, dayLabel = day, month = month, todayHeader = today)

    @Test fun inCimaIlPrimoInFondoLUltimo() {
        assertEquals(0, DayScrub.at(0f, 10))
        assertEquals(9, DayScrub.at(1f, 10))
        assertEquals(5, DayScrub.at(0.5f, 11))
    }

    @Test fun fuoriDallaLineaRestaAiBordi() {
        assertEquals(0, DayScrub.at(-0.3f, 10))
        assertEquals(9, DayScrub.at(1.4f, 10))
        assertEquals(0, DayScrub.at(0.7f, 1))
    }

    @Test fun giorniDallaLinea() {
        val entries = listOf(
            row("a", day = "Ieri", month = "NOVEMBRE"),
            row("b"),
            row("oggi", today = true),
            row("c", day = "Domani"),
            row("d", day = "Lunedì 1", month = "DICEMBRE"),
        )
        // Abitudini dopo l'item di OGGI (indice 3 della linea): i giorni dopo slittano di uno.
        val days = DayScrub.days(entries, first = 1, habitsAt = 3, habitRows = 1)
        assertEquals(
            listOf(
                ScrubDay(1, "a", "NOVEMBRE", DayScrub.MONTH_DP + DayScrub.DAY_DOT_DP),
                ScrubDay(3, "oggi", "NOVEMBRE", DayScrub.NOW_DOT_DP),
                ScrubDay(5, "c", "NOVEMBRE", DayScrub.DAY_DOT_DP),
                ScrubDay(6, "d", "DICEMBRE", DayScrub.MONTH_DP + DayScrub.DAY_DOT_DP),
            ),
            days,
        )
    }
}
