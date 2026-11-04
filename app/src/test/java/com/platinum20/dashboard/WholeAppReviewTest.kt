package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.MediaShelves
import com.platinum20.dashboard.ui.screens.calendarCardCut
import com.platinum20.dashboard.ui.screens.habitsIndex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** La revisione di tutta l'app (1.357): abitudini in OGGI, card Calendario fino a fine settimana, file di Media. */
class WholeAppReviewTest {
    // Mercoledì 4 novembre 2026.
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun habitsGoInsideTodayBeforeTheNextDays() {
        val days = listOf(
            today.minusDays(1) to true, // passato
            today to false, // OGGI
            null to false, // promemoria senza data, in OGGI
            today.plusDays(1) to false, // domani
            today.plusDays(3) to false,
        )
        assertEquals(3, habitsIndex(days, nowAt = 1, today = today))
    }

    @Test
    fun habitsAtTheEndWhenNothingComesAfterToday() {
        val days = listOf(today to false, null to false)
        assertEquals(2, habitsIndex(days, nowAt = 0, today = today))
        assertEquals(2, habitsIndex(days, nowAt = -1, today = today))
    }

    @Test
    fun calendarCardShowsTheRestOfTheWeekThenCountsTheMonth() {
        // Dopo oggi: giovedì, venerdì, domenica (fine settimana), poi 4 cose a novembre e una a dicembre.
        val days = listOf(
            today.plusDays(1), today.plusDays(2), today.plusDays(4),
            today.plusDays(6), today.plusDays(8), today.plusDays(10), today.plusDays(20),
            LocalDate.of(2026, 12, 2),
        )
        // Almeno 5 righe anche se la settimana ne ha 3; restano 2 cose di novembre (la 6ª e la 7ª).
        assertEquals(5 to 2, calendarCardCut(days, today, min = 5))
        // Con un minimo basso conta la settimana: 3 righe, 4 altre a novembre.
        assertEquals(3 to 4, calendarCardCut(days, today, min = 2))
    }

    @Test
    fun calendarCardWithFewThingsShowsThemAll() {
        val days = listOf(today.plusDays(1), null)
        assertEquals(2 to 0, calendarCardCut(days, today, min = 5))
    }

    @Test
    fun mediaShelfMixesWithoutDuplicates() {
        assertEquals(listOf("a", "b", "c"), MediaShelves.mix(listOf("a", "b"), listOf("b", "c"), { it }))
        assertEquals(listOf("a", "b"), MediaShelves.mix(listOf("a", "b"), listOf("c"), { it }, max = 2))
    }
}
