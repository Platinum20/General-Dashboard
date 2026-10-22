package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.Snooze
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.snoozed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SnoozeTest {
    private val now = LocalDateTime.of(2026, 10, 22, 18, 30, 42)

    @Test fun traUnOra() = assertEquals(LocalDateTime.of(2026, 10, 22, 19, 30), Snooze.HOUR.until(now))

    @Test fun traUnOraAMezzanottePassaAlGiornoDopo() =
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 15), Snooze.HOUR.until(LocalDateTime.of(2026, 10, 22, 23, 15)))

    @Test fun domaniAlleNove() = assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), Snooze.TOMORROW.until(now))

    @Test fun domaniAFineMese() =
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 31, 22, 0)))

    @Test fun dopoMezzanotteDomaniVuolDireStamattina() =
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 0, 30)))

    @Test fun primaDiMezzanotteEIlGiornoDopo() =
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 23, 50)))

    @Test fun alleQuattroENovantanoveEAncoraNotte() =
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 4, 59)))

    @Test fun primaEDopoLeNove() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 8, 59)))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 9, 1)))
    }

    @Test fun etichettaDiNotte() {
        assertEquals("Stamattina 9:00", Snooze.TOMORROW.label(LocalDateTime.of(2026, 10, 23, 0, 30)))
        assertEquals("Domani 9:00", Snooze.TOMORROW.label(now))
        assertEquals("Tra 1 ora", Snooze.HOUR.label(LocalDateTime.of(2026, 10, 23, 0, 30)))
    }

    @Test fun promemoriaSempliceSpostaSoloLaScadenza() {
        val task = TaskItem(1, "Dentista", dueAt = 1_000L)
        val moved = task.snoozed(5_000L)
        assertEquals(5_000L, moved.dueAt)
        assertNull(moved.snoozedFrom)
    }

    @Test fun ricorrenteRicordaLaScadenzaOriginale() {
        val task = TaskItem(1, "Affitto", dueAt = 1_000L, repeat = Repeat.MONTHLY)
        val moved = task.snoozed(5_000L)
        assertEquals(5_000L, moved.dueAt)
        assertEquals(1_000L, moved.snoozedFrom)
    }

    @Test fun rimandatoDueVolteTieneLaPrimaScadenza() {
        val task = TaskItem(1, "Affitto", dueAt = 1_000L, repeat = Repeat.MONTHLY)
        assertEquals(1_000L, task.snoozed(5_000L).snoozed(9_000L).snoozedFrom)
    }
}
