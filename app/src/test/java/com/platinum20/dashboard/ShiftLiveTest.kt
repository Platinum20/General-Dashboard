package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.shifts.PayMonth
import com.platinum20.dashboard.shifts.ShiftEarning
import com.platinum20.dashboard.trips.LivePhase
import com.platinum20.dashboard.trips.MarkKind
import com.platinum20.dashboard.trips.ShiftLive
import com.platinum20.dashboard.trips.ShiftPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** La notifica viva del turno (proposta A, 2026-11-21): prima, mentre lavori, finito. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShiftLiveTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 21, h, m).atZone(zone).toInstant().toEpochMilli()

    private val shift = CalendarEvent(
        eventId = -7, title = "Turno su Giulia e Sara", begin = at(12, 0), end = at(17, 0), allDay = false,
        location = "", color = 0, mates = listOf("Luca Bianchi"),
    )
    private val selling = ShiftEarning("s1", at(12, 0), at(17, 0), sold = 320.0, earned = 32.0, percent = 0.10, hours = 0.0, current = true)
    private val month = PayMonth("m", at(0, 0) - 20L * 86_400_000L, at(0, 0) + 9L * 86_400_000L, total = 684.0, sold = 0.0, shifts = 11, hours = 0.0, guaranteed = 0.0, minShifts = 0, status = "")

    @Test
    fun nasceTreQuartiDOraPrimaEFinisceComeRiepilogo() {
        assertNull(ShiftLive.phase(shift, at(11, 14)))
        assertEquals(ShiftPhase.SOON, ShiftLive.phase(shift, at(11, 16)))
        assertEquals(ShiftPhase.WORKING, ShiftLive.phase(shift, at(14, 0)))
        assertEquals(ShiftPhase.DONE, ShiftLive.phase(shift, at(17, 0)))
        assertNull(ShiftLive.phase(shift, at(17, 0) + ShiftLive.DONE_MS))
    }

    @Test
    fun primaQuantoMancaEConChi() {
        val card = ShiftLive.card(shift, at(11, 28), null, null, zone)!!
        assertEquals(LivePhase.SOON, card.phase)
        assertEquals("inizia tra 32 min", card.header)
        assertEquals("Turno su Giulia e Sara", card.title)
        assertEquals("In doppio con Luca · 5 h", card.sub)
        assertEquals("12:00 → 17:00 · in doppio con Luca", card.text)
        assertEquals("12:00", card.depart)
        assertEquals("17:00", card.arrive)
        assertTrue(card.lines.isEmpty())
        assertFalse(card.done)
    }

    @Test
    fun mentreLavoriIlVendutoFinoraSenzaLoScaglioneDopo() {
        val card = ShiftLive.card(shift, at(14, 50), selling, month, zone)!!
        assertEquals("finisce tra 2 h 10 min", card.header)
        assertEquals(listOf("Venduto $320,00 · 10% → $32,00 finora"), card.lines)
        assertEquals("Venduto $320,00 · $32,00 finora", card.text)
        assertEquals(MarkKind.NOW, card.marks.single().kind)
        assertEquals("$32,00 finora", card.marks.single().text)
        assertFalse(card.done)
    }

    @Test
    fun mentreLavoriAncheAZeroSiVede() {
        val zero = selling.copy(sold = 0.0, earned = 0.0, current = false)
        val card = ShiftLive.card(shift, at(12, 10), zero, month, zone)!!
        assertEquals(listOf("Venduto $0,00 · 10% → $0,00 finora"), card.lines)
    }

    @Test
    fun senzaNumeriMentreLavoriLoDice() {
        val card = ShiftLive.card(shift, at(12, 10), null, month, zone, readAt = at(12, 5))!!
        assertEquals(listOf("Venduto: non ancora segnato · letto alle 12:05"), card.lines)
        assertEquals("12:00 → 17:00 · in doppio con Luca", card.text)
        assertEquals("Venduto: non ancora segnato", ShiftLive.missing(0L, zone))
    }

    @Test
    fun finitoDiventaIlRiepilogoColMese() {
        val final = selling.copy(sold = 520.0, earned = 62.0, percent = 0.12, current = false)
        val card = ShiftLive.card(shift, at(17, 5), final, month, zone)!!
        assertTrue(card.done)
        assertEquals("finito alle 17:00", card.header)
        assertEquals("Venduto $520,00 · 12% → $62,00", card.lines[0])
        assertEquals("Novembre: $684,00 in 11 turni", card.lines[1])
        assertEquals("Venduto $520,00 · guadagnati $62,00", card.text)
    }

    @Test
    fun durata() {
        assertEquals("5 h", ShiftLive.length(5 * 3_600_000L))
        assertEquals("4 h 30 min", ShiftLive.length(270 * 60_000L))
        assertEquals("45 min", ShiftLive.length(45 * 60_000L))
    }

    @Test
    fun numeriVecchiDiconoDaQuando() {
        val card = ShiftLive.card(shift, at(14, 50), selling, month, zone, readAt = at(14, 0))!!
        assertEquals("letto alle 14:00", card.lines[1])
        val fresh = ShiftLive.card(shift, at(14, 50), selling, month, zone, readAt = at(14, 40))!!
        assertEquals(1, fresh.lines.size)
    }

    @Test
    fun rileggeSullaBustaPagaNonSullaTimeline() {
        val now = at(19, 45)
        // Busta paga vecchia di un'ora: si rilegge, anche se la timeline è appena stata letta.
        assertEquals(true, ShiftLive.shouldRead(now, payReadAt = at(18, 45), tried = 0L))
        // Appena provato: si aspetta un po' prima di riprovare.
        assertEquals(false, ShiftLive.shouldRead(now, payReadAt = at(18, 45), tried = at(19, 43)))
        assertEquals(true, ShiftLive.shouldRead(now, payReadAt = at(18, 45), tried = at(19, 39)))
        // Busta paga fresca: niente.
        assertEquals(false, ShiftLive.shouldRead(now, payReadAt = at(19, 40), tried = 0L))
    }
}

/** Il riepilogo di fine turno non ricompare ore dopo (Antonio, 2026-11-25, notifica delle 17:00 arrivata alle 00:49). */
class ShiftSummaryTest {
    private val h = 3_600_000L
    private val end = 100 * h

    @Test
    fun firstTimeOnlyNearTheEnd() {
        org.junit.Assert.assertEquals(ShiftLive.Summary.POST, ShiftLive.summary(false, false, false, end + h, end))
        org.junit.Assert.assertEquals(ShiftLive.Summary.SKIP, ShiftLive.summary(false, false, false, end + 8 * h, end))
    }

    @Test
    fun dismissedNeverComesBackAndUpdatesAreSilent() {
        org.junit.Assert.assertEquals(ShiftLive.Summary.SKIP, ShiftLive.summary(true, false, false, end + 8 * h, end))
        org.junit.Assert.assertEquals(ShiftLive.Summary.SKIP, ShiftLive.summary(true, true, true, end + h, end))
        org.junit.Assert.assertEquals(ShiftLive.Summary.UPDATE_SILENT, ShiftLive.summary(true, true, false, end + 8 * h, end))
    }
}
