package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.QuickEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class QuickEventTest {
    // Giovedì 22 ottobre 2026, ore 10.
    private val now = LocalDateTime.of(2026, 10, 22, 10, 0)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    @Test fun domaniAlle15DentistaDuraUnOra() {
        val e = QuickEvent.parse("domani alle 15 dentista", now)!!
        assertEquals("Dentista", e.title)
        assertEquals(at(23, 15), e.start)
        assertEquals(at(23, 16), e.end)
        assertFalse(e.allDay)
    }

    @Test fun venerdiAlle20Cena() {
        val e = QuickEvent.parse("venerdì alle 20 cena da Luca", now)!!
        assertEquals("Cena da Luca", e.title)
        assertEquals(at(23, 20), e.start)
    }

    @Test fun perDueOre() {
        val e = QuickEvent.parse("sabato alle 10 calcetto per 2 ore", now)!!
        assertEquals("Calcetto", e.title)
        assertEquals(at(24, 10), e.start)
        assertEquals(at(24, 12), e.end)
    }

    @Test fun perMezzOraEUnOraEMezza() {
        assertEquals(30L, java.time.Duration.between(QuickEvent.parse("domani alle 9 call per mezz'ora", now)!!.start, QuickEvent.parse("domani alle 9 call per mezz'ora", now)!!.end).toMinutes())
        val e = QuickEvent.parse("domani alle 18 palestra per un’ora e mezza", now)!!
        assertEquals(at(23, 19, 30), e.end)
        assertEquals("Palestra", e.title)
    }

    @Test fun dalleAlle() {
        val e = QuickEvent.parse("domani dalle 9 alle 11 corso", now)!!
        assertEquals("Corso", e.title)
        assertEquals(at(23, 9), e.start)
        assertEquals(at(23, 11), e.end)
    }

    @Test fun dalleAlleNelPomeriggio() {
        val e = QuickEvent.parse("lunedì dalle 14:30 alle 6 riunione", now)!!
        assertEquals(at(26, 14, 30), e.start)
        assertEquals(at(26, 18), e.end)
    }

    @Test fun tuttoIlGiorno() {
        val e = QuickEvent.parse("sabato tutto il giorno gita al lago", now)!!
        assertTrue(e.allDay)
        assertEquals("Gita al lago", e.title)
        assertEquals(at(24, 0), e.start)
    }

    @Test fun senzaQuandoNonEUnEvento() {
        assertNull(QuickEvent.parse("comprare il latte", now))
    }
}
