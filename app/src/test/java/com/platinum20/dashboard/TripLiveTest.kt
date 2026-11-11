package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip
import com.platinum20.dashboard.trips.LivePhase
import com.platinum20.dashboard.trips.LiveTrip
import com.platinum20.dashboard.trips.TrainLive
import com.platinum20.dashboard.trips.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** Il viaggio vivo: quando nasce la notifica, cosa dice, e la lettura dei dati di ViaggiaTreno. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TripLiveTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 12, h, m).atZone(zone).toInstant().toEpochMilli()

    private val train = CalendarEvent(
        eventId = 42, title = "Treno per Pescara Centrale", begin = at(6, 40), end = at(11, 12), allDay = false,
        location = "", color = 0, trip = Trip("Taranto", "Pescara Centrale", "Intercity 612", coach = "8", seat = "11D"),
    )
    private val shift = CalendarEvent(
        eventId = -5, title = "Turno su Giulia", begin = at(9, 0), end = at(15, 0), allDay = false,
        location = "", color = 0, mates = listOf("Marco Rossi"),
    )

    @Test
    fun laNotificaNasceUnOraPrimaEFinisceDopoLArrivo() {
        assertNull(LiveTrip.phase(train, at(5, 39)))
        assertEquals(LivePhase.SOON, LiveTrip.phase(train, at(5, 41)))
        assertEquals(LivePhase.ON_BOARD, LiveTrip.phase(train, at(8, 0)))
        assertEquals(LivePhase.ON_BOARD, LiveTrip.phase(train, at(11, 15)))
        assertNull(LiveTrip.phase(train, at(11, 18)))
    }

    @Test
    fun ilRitardoSpostaPartenzaEArrivo() {
        val late = TrainStatus(delay = 10, platform = "4", readAt = at(6, 30))
        assertEquals(LivePhase.SOON, LiveTrip.phase(train, at(6, 45), late))
        assertEquals(LivePhase.ON_BOARD, LiveTrip.phase(train, at(11, 25), late))
        val card = LiveTrip.card(train, at(6, 30), late, checked = true, zone = zone)!!
        assertEquals("Parte alle 6:50 · +10 min · binario 4 · carr. 8, posto 11D", card.text)
        assertEquals("6:50", card.chip)
        assertEquals(at(6, 50), card.countdownTo)
    }

    @Test
    fun inViaggioAvanzaFinoAllArrivo() {
        val card = LiveTrip.card(train, at(8, 56), TrainStatus(0, readAt = at(8, 55)), checked = true, zone = zone)!!
        assertEquals(LivePhase.ON_BOARD, card.phase)
        assertEquals(50, card.progress)
        assertTrue(card.text.startsWith("Arrivo alle 11:12 · In orario"))
        assertEquals("11:12", card.chip)
    }

    @Test
    fun senzaDatiLoDice() {
        val card = LiveTrip.card(train, at(6, 0), null, checked = true, zone = zone)!!
        assertTrue(card.text.contains("ritardo non disponibile"))
        assertFalse(LiveTrip.card(train, at(6, 0), null, checked = false, zone = zone)!!.text.contains("ritardo"))
    }

    @Test
    fun trenoSoppresso() {
        val card = LiveTrip.card(train, at(6, 0), TrainStatus(0, cancelled = true, readAt = at(6, 0)), checked = true, zone = zone)!!
        assertEquals("Soppresso", card.chip)
        assertEquals("Intercity 612 · Soppresso", card.text)
    }

    @Test
    fun ilTurnoHaSoloIlContoAllaRovescia() {
        assertNull(LiveTrip.phase(shift, at(8, 10)))
        val card = LiveTrip.card(shift, at(8, 20), null, checked = false, zone = zone)!!
        assertEquals("Turno su Giulia", card.title)
        assertEquals("Inizia alle 9:00 · In doppio con Marco", card.text)
        assertNull(LiveTrip.card(shift, at(10, 0), null, checked = false, zone = zone))
    }

    @Test
    fun numeroEStazioneDOrigine() {
        assertEquals("612", TrainLive.number("Intercity 612"))
        assertEquals("9542", TrainLive.number("Frecciarossa 9542"))
        assertNull(TrainLive.number(null))
        val text = "612 - TARANTO - 12/11/26|612-S11119-1762902000000\n612 - MILANO CENTRALE - 12/11/26|612-S01700-1762902000000\n"
        assertEquals("S11119", TrainLive.origin(text, "Taranto"))
        assertEquals("S01700", TrainLive.origin(text, "Milano Centrale"))
        assertEquals("S11119", TrainLive.origin(text, null))
        assertEquals("S11119", TrainLive.origin("612 - TARANTO|612-S11119", "Bari"))
        assertNull(TrainLive.origin("", "Taranto"))
    }

    @Test
    fun andamentoDelTreno() {
        val json = """{"numeroTreno":612,"ritardo":7,"provvedimento":0,"fermate":[
            {"stazione":"TARANTO","id":"S11119","binarioProgrammatoPartenzaDescrizione":"2","binarioEffettivoPartenzaDescrizione":"4"},
            {"stazione":"PESCARA CENTRALE","id":"S07000","binarioProgrammatoPartenzaDescrizione":null}]}"""
        val s = TrainLive.parse(json, "Taranto", 1L)!!
        assertEquals(7, s.delay)
        assertEquals("4", s.platform)
        assertFalse(s.cancelled)
        assertEquals("+7 min · binario 4", TrainLive.line(s))
        val programmed = TrainLive.parse(json.replace(",\"binarioEffettivoPartenzaDescrizione\":\"4\"", ""), "Taranto", 1L)!!
        assertEquals("2", programmed.platform)
        assertTrue(TrainLive.parse("""{"ritardo":0,"provvedimento":1}""", null, 1L)!!.cancelled)
        assertNull(TrainLive.parse("non è json", null, 1L))
        assertNull(TrainLive.parse("{}", null, 1L))
        assertEquals("In orario", TrainLive.delayText(TrainStatus(0)))
        assertEquals("2 min in anticipo", TrainLive.delayText(TrainStatus(-2)))
    }

    @Test
    fun unRitardoVecchioNonSiMostra() {
        assertNull(TrainLive.fresh(999_999L))
        assertNotNull(TrainLive.FRESH_MS)
    }
}
