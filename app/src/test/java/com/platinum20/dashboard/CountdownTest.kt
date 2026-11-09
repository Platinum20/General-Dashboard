package com.platinum20.dashboard

import com.platinum20.dashboard.notes.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CountdownTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun compleannoNonAncoraPassato() =
        assertEquals(30, TaskItem.date(1, "Marco", LocalDate.of(1996, 12, 3), yearly = true).yearsAtNext(today))

    @Test fun compleannoGiaPassatoQuestAnno() =
        assertEquals(31, TaskItem.date(1, "Marco", LocalDate.of(1996, 3, 3), yearly = true).yearsAtNext(today))

    @Test fun annoNonScritto() =
        assertNull(TaskItem.date(1, "Marco", LocalDate.of(2026, 3, 3), yearly = true).yearsAtNext(today))

    @Test fun nonRicorrente() =
        assertNull(TaskItem.date(1, "Viaggio", LocalDate.of(1996, 12, 3)).yearsAtNext(today))
}
