package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip
import com.platinum20.dashboard.trips.LivePhase
import com.platinum20.dashboard.trips.LiveCard
import com.platinum20.dashboard.trips.LiveMark
import com.platinum20.dashboard.trips.LiveStop
import com.platinum20.dashboard.trips.LiveTrack
import com.platinum20.dashboard.trips.LiveTrip
import com.platinum20.dashboard.trips.MarkKind
import com.platinum20.dashboard.trips.TrainLive
import com.platinum20.dashboard.trips.TrainStatus
import com.platinum20.dashboard.trips.TrainStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import com.platinum20.dashboard.ui.screens.WhenHero
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
        assertEquals("+10 min · parte tra 20 min · binario 4", card.text)
        assertTrue(LiveMark(LiveCard.TEXT, "+10 min", MarkKind.LATE) in card.marks)
        // Sotto la linea: l'orario del biglietto barrato davanti a quello vero, in arancio (proposta A).
        assertEquals("6:40 6:50 Taranto", card.depart)
        assertEquals("Pescara 11:12 11:22", card.arrive)
        assertTrue(LiveMark(LiveCard.DEPART, "6:40", MarkKind.STRUCK) in card.marks)
        assertTrue(LiveMark(LiveCard.ARRIVE, "11:12", MarkKind.STRUCK) in card.marks)
        assertTrue(LiveMark(LiveCard.ARRIVE, "11:22", MarkKind.LATE) in card.marks)
        // Versione compatta 1: il ritardo è l'ultima riga, l'intestazione solo "parte tra", il binario nella riga del treno.
        assertEquals(listOf("+10 min"), card.lines)
        assertTrue(LiveMark(0, "+10 min", MarkKind.LATE) in card.marks)
        assertEquals("parte tra 20 min", card.header)
        assertEquals("Bin. 4 · IC 612 · carr. 8 · posto 11D", card.sub)
        assertTrue(card.late)
        // La linea di progresso nell'ultima ora prima di partire: a 20 minuti dalla partenza è a due terzi.
        assertEquals(66, card.progress)
        assertEquals("6:50", card.chip)
        assertEquals(at(6, 50), card.countdownTo)
    }

    @Test
    fun inViaggioAvanzaFinoAllArrivo() {
        val card = LiveTrip.card(train, at(8, 56), TrainStatus(0, readAt = at(8, 55)), checked = true, zone = zone)!!
        assertEquals(LivePhase.ON_BOARD, card.phase)
        assertEquals(50, card.progress)
        assertEquals("In orario · arriva tra 2 h 16 min", card.text)
        assertEquals("11:12", card.chip)
        assertEquals("arriva tra 2 h 16 min", card.header)
        assertEquals("6:40 Taranto", card.depart)
        assertEquals("Pescara 11:12", card.arrive)
        assertEquals(listOf("In orario"), card.lines)
        assertEquals("IC 612 · carr. 8 · posto 11D", card.sub)
        assertTrue(card.marks.isEmpty())
    }

    @Test
    fun laLineaHaUnPuntoPerFermata() {
        val stops = listOf(
            TrainStop("TARANTO", at(6, 40)), TrainStop("BARI CENTRALE", at(7, 50)),
            TrainStop("FOGGIA", at(9, 10)), TrainStop("PESCARA CENTRALE", at(11, 12)),
        )
        val status = TrainStatus(15, readAt = at(8, 55), stops = stops)
        val card = LiveTrip.card(train, at(8, 56), status, checked = true, zone = zone)!!
        assertEquals("Pescara 11:12 11:27", card.arrive)
        assertEquals("arriva tra 2 h 31 min", card.header)
        assertEquals("Prossima: Foggia 9:25", card.next)
        assertEquals(listOf("+15 min"), card.lines)
        assertEquals(listOf(70, 150), card.track!!.steps(at(8, 56)).points)
        assertEquals(listOf(LiveStop("8:05", "Bari Centrale", true, "7:50"), LiveStop("9:25", "Foggia", false, "9:10")), card.stops)
        // Senza rete resta l'ultima lettura: orari col ritardo, e dice quanto è vecchia.
        val old = LiveTrip.card(train, at(9, 30), status, checked = true, zone = zone)!!
        assertEquals("Pescara 11:12 11:27", old.arrive)
        assertEquals("arriva tra 1 h 57 min", old.header)
        assertEquals(listOf("+15 min · letto 35 min fa"), old.lines)
        assertTrue(LiveMark(0, " · letto 35 min fa", MarkKind.SOFT) in old.marks)
        val steps = LiveTrack(at(6, 55), at(11, 27), listOf(at(8, 5), at(9, 25))).steps(at(8, 56))
        assertEquals(272, steps.max)
        assertEquals(121, steps.progress)
        assertEquals(listOf(70, 150), steps.points)
        assertEquals(listOf(70, 80, 122), steps.segments)
        assertEquals(steps.max, steps.segments.sum())
    }

    @Test
    fun fermateFraDoveSaliEDoveScendi() {
        val stops = listOf(TrainStop("LECCE", 1), TrainStop("TARANTO", 2), TrainStop("BARI CENTRALE", 3), TrainStop("PESCARA CENTRALE", 4), TrainStop("ANCONA", 5))
        assertEquals(listOf("BARI CENTRALE"), TrainLive.between(stops, "Taranto", "Pescara Centrale").map { it.name })
        assertEquals(5, TrainLive.between(stops, "Roma", "Milano").size)
        assertEquals("Bari Centrale", TrainLive.pretty("BARI  CENTRALE"))
        assertEquals("Pescara", LiveTrip.short("Pescara Centrale"))
        assertEquals("tra 3 h 54 min", LiveTrip.inText(234 * 60_000L))
        assertEquals("tra 2 h", LiveTrip.inText(120 * 60_000L))
        assertEquals("tra meno di un minuto", LiveTrip.inText(20_000L))
        val many = LiveTrack(0, 100 * 60_000L, (1..40).map { it * 2 * 60_000L }).steps(0)
        assertTrue(many.points.size <= LiveTrack.MAX_POINTS)
        assertEquals(100, many.segments.sum())
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
        assertTrue(card.late)
        assertEquals("Soppresso · doveva partire alle 6:40", card.lines.first())
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
            {"stazione":"TARANTO","id":"S11119","programmata":1000,"binarioProgrammatoPartenzaDescrizione":"2","binarioEffettivoPartenzaDescrizione":"4"},
            {"stazione":"PESCARA CENTRALE","id":"S07000","binarioProgrammatoPartenzaDescrizione":null}]}"""
        val s = TrainLive.parse(json, "Taranto", 1L)!!
        assertEquals(7, s.delay)
        assertEquals("4", s.platform)
        assertEquals(listOf(TrainStop("TARANTO", 1000)), s.stops)
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
        assertNull(TrainLive.known(999_999L))
        val saved = mapOf(42L to TrainStatus(95, platform = "3", readAt = 7L, stops = listOf(TrainStop("BARI CENTRALE", 9L))))
        assertEquals(saved, TrainLive.decode(TrainLive.encode(saved)))
        assertEquals(mapOf(1L to TrainStatus(0, readAt = 5L)), TrainLive.decode(TrainLive.encode(mapOf(1L to TrainStatus(0, readAt = 5L)))))
        assertNotNull(TrainLive.FRESH_MS)
    }

    @Test
    fun inOrarioNienteArancio() {
        val card = LiveTrip.card(train, at(6, 20), TrainStatus(0, readAt = at(6, 19)), checked = true, zone = zone)!!
        assertFalse(card.late)
        assertEquals("6:40 Taranto", card.depart)
        assertEquals("parte tra 20 min", card.header)
        assertEquals("IC 612 · carr. 8 · posto 11D", card.sub)
        assertEquals("In orario · parte tra 20 min", card.text)
        assertEquals(listOf("In orario"), card.lines)
        // In anticipo: orari del biglietto.
        assertEquals(at(6, 40), LiveTrip.card(train, at(6, 20), TrainStatus(-4, readAt = at(6, 19)), checked = true, zone = zone)!!.countdownTo)
    }

    @Test
    fun laRigaDellUltimaLettura() {
        assertNull(TrainLive.readLine(null, tried = false, now = at(7, 0), zone = zone))
        assertEquals("Ritardo non disponibile", TrainLive.readLine(null, tried = true, now = at(7, 0), zone = zone))
        assertEquals("Ultimo rilevamento 6:55", TrainLive.readLine(TrainStatus(5, readAt = at(6, 55)), true, at(7, 0), zone))
        assertEquals("Ultimo rilevamento 6:40 · 20 min fa", TrainLive.readLine(TrainStatus(5, readAt = at(6, 40)), true, at(7, 0), zone))
        assertEquals(0, TrainLive.shiftMinutes(TrainStatus(30, cancelled = true)))
        assertEquals(0, TrainLive.shiftMinutes(TrainStatus(-2)))
        assertEquals(30, TrainLive.shiftMinutes(TrainStatus(30)))
    }

    @Test
    fun laFasciaInAltoPrimaEDopoLaPartenza() {
        val late = TrainStatus(97, readAt = at(5, 0))
        val ev = train.copy(begin = at(14, 1), end = at(18, 27))
        // Non ancora partito: conta sulla partenza spostata.
        val before = WhenHero.train(ev.begin, ev.end, late, at(15, 5), zone)
        assertEquals("Parte tra 33 min", before.countdown)
        assertEquals("15:38 – 20:04", before.big)
        assertEquals("Oggi · 4 h 26 · programmato 14:01 – 18:27", before.sub)
        assertTrue(before.late)
        // In viaggio: l'arrivo spostato.
        val onBoard = WhenHero.train(ev.begin, ev.end, late, at(18, 4), zone)
        assertEquals("Arriva tra 2 h", onBoard.countdown)
        // Senza ritardo (o dato scaduto, quindi null): orari del biglietto, niente arancio.
        val plain = WhenHero.train(ev.begin, ev.end, null, at(13, 0), zone)
        assertEquals("Parte tra 1 h 1", plain.countdown)
        assertEquals("14:01 – 18:27", plain.big)
        assertEquals("Oggi · 4 h 26", plain.sub)
        assertFalse(plain.late)
        assertEquals("Arriva tra 1 h 27", WhenHero.train(ev.begin, ev.end, null, at(17, 0), zone).countdown)
        assertEquals("Soppresso", WhenHero.train(ev.begin, ev.end, TrainStatus(0, cancelled = true), at(13, 0), zone).countdown)
    }

    @Test
    fun ilViaggioArrivatoTieneIlRitardoPerSempre() {
        val h = 60 * 60_000L
        val arrived = TrainStatus(25, readAt = 0L, until = 2 * h)
        // Prima dell'arrivo col ritardo vale la regola delle 12 ore; dopo resta, anche giorni dopo.
        assertFalse(arrived.closed(h))
        assertTrue(arrived.closed(2 * h))
        assertTrue(TrainLive.kept(arrived, 30L * 24 * h))
        assertFalse(TrainLive.kept(TrainStatus(25, readAt = 0L), 30L * 24 * h))
        assertFalse(TrainLive.kept(arrived, 2 * h + TrainLive.KEEP_PAST_MS))
        assertEquals(mapOf(3L to arrived), TrainLive.decode(TrainLive.encode(mapOf(3L to arrived))))
    }

    @Test
    fun laNotificaRestaFinoAllArrivoColRitardo() {
        val ev = CalendarEvent(9L, "Treno per Roma", 0L, 60 * 60_000L, false, "", 0, Trip("Milano", "Roma", "FR 9541"))
        val late = TrainStatus(40, readAt = 0L)
        assertEquals(LivePhase.ON_BOARD, LiveTrip.phase(ev, 90 * 60_000L, late))
        assertNull(LiveTrip.phase(ev, 90 * 60_000L, null))
    }

    @Test
    fun laProvaPassaPerTutteLeFasi() {
        val base = 1_700_000_000_000L
        val ev = com.platinum20.dashboard.trips.LiveDemo.event(base)
        val frames = com.platinum20.dashboard.trips.LiveDemo.frames(base)
        val cards = frames.map { LiveTrip.card(ev, it.now, it.status, checked = true) }
        assertTrue(cards.all { it != null })
        assertEquals(LivePhase.SOON, cards.first()!!.phase)
        assertFalse(cards.first()!!.late)
        assertTrue(cards.any { it!!.phase == LivePhase.SOON && it.late })
        assertEquals(LivePhase.ON_BOARD, cards.last()!!.phase)
        assertTrue(cards.last()!!.lines.first().contains("letto"))
        // Dura poco più di un minuto: a ogni fotogramma 3 secondi.
        assertTrue(frames.size * com.platinum20.dashboard.trips.LiveDemo.FRAME_MS in 60_000L..120_000L)
    }

    @Test
    fun aViaggioFinitoRileggeSoloSeNonSappiamoComeEAndata() {
        val h = 60 * 60_000L
        // Arrivato da un'ora, nessuna lettura (persa): si rilegge. Lettura chiusa: no. Dopo 12 ore: no.
        assertTrue(TrainLive.needsLastRead(null, arrival = h, now = 2 * h))
        assertTrue(TrainLive.needsLastRead(TrainStatus(30, readAt = 0L), arrival = h, now = 2 * h))
        assertFalse(TrainLive.needsLastRead(TrainStatus(30, readAt = 0L, until = h + 30 * 60_000L), arrival = h, now = 2 * h))
        assertFalse(TrainLive.needsLastRead(null, arrival = h, now = 14 * h))
        assertFalse(TrainLive.needsLastRead(null, arrival = 2 * h, now = h))
    }

    @Test
    fun ilTrenoSiAccorciaPerStareInUnaRiga() {
        assertEquals("FR 9541", com.platinum20.dashboard.trips.SeatText.train("Frecciarossa 9541"))
        assertEquals("ICN 795", com.platinum20.dashboard.trips.SeatText.train("Intercity Notte 795"))
        assertEquals("RV 2345", com.platinum20.dashboard.trips.SeatText.train("Regionale Veloce 2345"))
        assertEquals("Italo 8901", com.platinum20.dashboard.trips.SeatText.train("Italo 8901"))
        val trip = Trip("Milano", "Bologna", "Frecciarossa 9541", coach = "8", seat = "11D")
        assertEquals("Bin. 14 · FR 9541 · carr. 8 · posto 11D", com.platinum20.dashboard.trips.SeatText.line(trip, "14"))
    }

    @Test
    fun laProvaDiceLOraFintaELeFermateHannoIlRitardo() {
        val zone = java.time.ZoneId.of("Europe/Rome")
        val at = java.time.ZonedDateTime.of(2026, 11, 20, 21, 22, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Prova · Treno per Bologna Centrale", com.platinum20.dashboard.trips.LiveDemo.title("Treno per Bologna Centrale"))
        val base = 1_700_000_000_000L
        val ev = com.platinum20.dashboard.trips.LiveDemo.event(base)
        val late = com.platinum20.dashboard.trips.LiveDemo.frames(base).map { LiveTrip.card(ev, it.now, it.status, checked = true)!! }.last()
        val middle = late.stops.first()
        assertNotNull(middle.planned)
        assertTrue(middle.planned != middle.time)
    }
}
