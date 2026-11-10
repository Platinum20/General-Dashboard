package com.platinum20.dashboard

import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.ui.screens.DayScrub
import com.platinum20.dashboard.ui.screens.ScrubDay
import com.platinum20.dashboard.ui.screens.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayScrubTest {
    private fun row(key: String, day: String? = null, month: String? = null, today: Boolean = false) =
        TimelineEntry(key = key, kind = BriefKind.TASK, text = key, dayLabel = day, month = month, todayHeader = today)

    @Test fun pocoDitoNonSpostaNiente() {
        assertEquals(0, DayScrub.shift(0f))
        assertEquals(0, DayScrub.shift(DayScrub.STEP_DP - 1))
    }

    @Test fun unGiornoPerPassoNeiDueVersi() {
        assertEquals(1, DayScrub.shift(DayScrub.STEP_DP + 1))
        assertEquals(-1, DayScrub.shift(-DayScrub.STEP_DP - 1))
    }

    @Test fun allungandoIlGestoAccelera() {
        val short = DayScrub.shift(100f)
        val long = DayScrub.shift(500f)
        assertTrue(long > short * 5)
    }

    @Test fun nonEsceDallaLinea() {
        assertEquals(0, DayScrub.target(2, -1000f, 10))
        assertEquals(9, DayScrub.target(2, 1000f, 10))
    }

    @Test fun elasticoLimitato() {
        assertTrue(DayScrub.rubber(10f) < 10f)
        assertTrue(DayScrub.rubber(10_000f) <= DayScrub.PULL_DP)
        assertTrue(DayScrub.rubber(-10_000f) >= -DayScrub.PULL_DP)
    }

    @Test fun partenzaDalGiornoInCima() {
        val days = listOf(ScrubDay(2, "Ieri"), ScrubDay(5, "Oggi"), ScrubDay(9, "Domani"))
        assertEquals(1, DayScrub.from(days, 7))
        assertEquals(0, DayScrub.from(days, 0))
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
                ScrubDay(1, "Ieri", "Novembre"),
                ScrubDay(3, "Oggi", "Novembre"),
                ScrubDay(5, "Domani", "Novembre"),
                ScrubDay(6, "Lunedì 1", "Dicembre"),
            ),
            days,
        )
    }
}
