package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Le ripetizioni dei promemoria: fine mese, anni bisestili, settimane. */
class RepeatEndOfMonthTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = LocalDateTime.of(y, m, d, h, 0)

    @Test fun ogniMeseIl31NonScivolaAl28() {
        // Prima della correzione: 31 gen → 28 feb → 28 mar.
        assertEquals(at(2026, 3, 31), Repeat.MONTHLY.nextAfter(at(2026, 1, 31), at(2026, 3, 1)))
        assertEquals(at(2026, 2, 28), Repeat.MONTHLY.nextAfter(at(2026, 1, 31), at(2026, 2, 1)))
        assertEquals(at(2026, 4, 30), Repeat.MONTHLY.nextAfter(at(2026, 1, 31), at(2026, 4, 1)))
    }

    @Test fun ogniAnnoIl29FebbraioTornaNelBisestile() {
        assertEquals(at(2027, 2, 28), Repeat.YEARLY.nextAfter(at(2024, 2, 29), at(2027, 1, 1)))
        assertEquals(at(2028, 2, 29), Repeat.YEARLY.nextAfter(at(2024, 2, 29), at(2027, 3, 1)))
    }

    @Test fun ogniSettimanaStessoGiornoEOra() {
        val next = Repeat.WEEKLY.nextAfter(at(2026, 10, 2, 18), at(2026, 10, 23, 12))
        assertEquals(at(2026, 10, 23, 18), next)
        // Esattamente all'ora dell'occorrenza: si passa alla successiva.
        assertEquals(at(2026, 10, 30, 18), Repeat.WEEKLY.nextAfter(at(2026, 10, 2, 18), at(2026, 10, 23, 18)))
    }

    @Test fun ogniGiornoDopoMoltiGiorniDiAssenza() {
        assertEquals(at(2026, 10, 24, 7), Repeat.DAILY.nextAfter(at(2026, 1, 1, 7), at(2026, 10, 23, 8)))
        assertEquals(at(2026, 10, 2), Repeat.DAILY.next(at(2026, 10, 1)))
    }
}
