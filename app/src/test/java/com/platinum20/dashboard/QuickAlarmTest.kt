package com.platinum20.dashboard

import com.platinum20.dashboard.trips.QuickAlarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Sveglia veloce della notifica del turno: quando suona, la conferma, i tasti. */
class QuickAlarmTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun ms(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, 23, h, m, s, 0, rome).toInstant().toEpochMilli()

    @Test fun suonaAlMinutoPieno() {
        // Alle 18:25:40 "10 min" suona alle 18:35:00, l'orario detto nella conferma.
        assertEquals(ms(18, 35), QuickAlarm.ringAt(ms(18, 25, 40), 10))
        assertEquals(ms(0, 4), QuickAlarm.ringAt(ms(23, 59, 59) - 86_400_000L, 5) )
    }

    @Test fun confermaInUnaRiga() {
        assertEquals("Sveglia alle 18:35 · fra 10 min", QuickAlarm.confirm(ms(18, 35), 10, rome))
    }

    @Test fun treTastiSoloMinuti() {
        assertEquals(listOf(5, 10, 15), QuickAlarm.MINUTES)
        assertEquals(listOf("5 min", "10 min", "15 min"), QuickAlarm.MINUTES.mapIndexed { i, m -> QuickAlarm.buttonTitle(m, i == 0) })
    }
}
