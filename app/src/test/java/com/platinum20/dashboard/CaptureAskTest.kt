package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip
import com.platinum20.dashboard.capture.Ask
import com.platinum20.dashboard.capture.AskData
import com.platinum20.dashboard.capture.AskKind
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Chiedi al "+": quali frasi sono domande, cosa chiedono, e le risposte con dati finti. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CaptureAskTest {
    private val zone = ZoneId.of("Europe/Rome")
    // Mercoledì 11 novembre 2026, ore 10.
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)
    private val today = now.toLocalDate()
    private fun ms(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun data(
        shifts: List<Shift> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        tasks: List<TaskItem> = emptyList(),
        parcels: List<Parcel> = emptyList(),
        sim: SimStatus? = null,
    ) = AskData(now, zone, shifts, events, tasks, parcels, sim)

    @Test
    fun soloLeDomandeSonoDomande() {
        assertTrue(Ask.isQuestion("quanti giga ho?"))
        assertTrue(Ask.isQuestion("Quando ho il prossimo turno"))
        assertTrue(Ask.isQuestion("che tempo fa sabato"))
        assertFalse(Ask.isQuestion("domani alle 9 chiamare il dentista"))
        assertFalse(Ask.isQuestion("comprare il latte"))
        assertFalse(Ask.isQuestion("riga uno?\nriga due"))
    }

    @Test
    fun cosaChiede() {
        assertEquals(AskKind.NEXT_SHIFT, Ask.parse("quando ho il prossimo turno con Marco?", today)!!.kind)
        assertEquals("marco", Ask.parse("quando ho il prossimo turno con Marco?", today)!!.name)
        assertEquals(AskKind.MONTH_SHIFTS, Ask.parse("quante ore ho lavorato questo mese?", today)!!.kind)
        assertEquals(AskKind.NEXT_TRAIN, Ask.parse("quando parte il treno?", today)!!.kind)
        assertEquals(AskKind.SIM, Ask.parse("quanti giga mi restano?", today)!!.kind)
        assertEquals(AskKind.DOCUMENT, Ask.parse("quando scade la patente?", today)!!.kind)
        assertEquals(AskKind.PARCEL, Ask.parse("quando arriva il pacco?", today)!!.kind)
        assertEquals(AskKind.AGENDA, Ask.parse("cosa ho domani?", today)!!.kind)
        val weather = Ask.parse("che tempo fa sabato?", today)!!
        assertEquals(AskKind.WEATHER, weather.kind)
        assertEquals(LocalDate.of(2026, 11, 14), weather.day)
        assertEquals(AskKind.WHEN, Ask.parse("quando è il compleanno di Marta?", today)!!.kind)
        assertNull(Ask.parse("comprare il latte", today))
    }

    @Test
    fun giorniDellaSettimana() {
        assertEquals(today, Ask.day("mercoledi", today))
        assertEquals(LocalDate.of(2026, 11, 16), Ask.day("lunedi", today))
        assertEquals(today.plusDays(2), Ask.day("dopodomani", today))
        assertEquals(today.plusDays(1), Ask.day("domani", today))
        assertNull(Ask.day("prossima volta", today))
    }

    @Test
    fun prossimoTurnoConMarco() {
        val shifts = listOf(
            Shift("a", ms(12, 7), ms(12, 13), mates = listOf("Luca Bianchi")),
            Shift("b", ms(14, 9), ms(14, 15), mates = listOf("Marco Rossi")),
        )
        val q = Ask.parse("quando ho il prossimo turno con Marco?", today)!!
        val a = Ask.answer(q, data(shifts = shifts))!!
        assertEquals("Turno sabato 14 alle 9:00–15:00 · In doppio con Marco", a.text)
        assertEquals(OpenRequest.EVENT, a.open!!.target)
        val any = Ask.answer(Ask.parse("quando lavoro?", today)!!, data(shifts = shifts))!!
        assertTrue(any.text.startsWith("Turno domani alle 7:00–13:00"))
        assertEquals("Nessun turno in arrivo con Paolo", Ask.answer(Ask.parse("turno con paolo?", today)!!, data(shifts = shifts))!!.text)
    }

    @Test
    fun oreDelMeseSoloQuestoMese() {
        val shifts = listOf(
            Shift("old", ms(1, 7) - 31L * 86_400_000, ms(1, 13) - 31L * 86_400_000),
            Shift("a", ms(3, 7), ms(3, 13)),
            Shift("b", ms(5, 9), ms(5, 13, 30)),
            Shift("c", ms(20, 9), ms(20, 15)),
        )
        val a = Ask.answer(Ask.parse("quanti turni ho fatto questo mese?", today)!!, data(shifts = shifts))!!
        assertEquals("A novembre 2 turni, 10,5 ore · ancora 1", a.text)
    }

    @Test
    fun prossimoTreno() {
        val train = CalendarEvent(9, "Treno per Pescara Centrale", ms(13, 6, 40), ms(13, 11, 12), false, "", 0, trip = Trip("Taranto", "Pescara Centrale", "Intercity 612", "8", "11D"))
        val a = Ask.answer(Ask.parse("quando parto?", today)!!, data(events = listOf(train)))!!
        assertEquals("Treno per Pescara Centrale venerdì 13 alle 6:40–11:12 · Intercity 612 · Carrozza 8 · Posto 11D", a.text)
    }

    @Test
    fun giga() {
        val sim = SimStatus(data = Allowance(12.5, 50.0), minutes = Allowance(0.0, unlimited = true), renewal = LocalDate.of(2026, 11, 20), at = ms(11, 9))
        val a = Ask.answer(Ask.parse("quanti giga ho?", today)!!, data(sim = sim))!!
        assertEquals("12,5 GB di 50 · minuti illimitati · rinnovo 20 novembre", a.text)
        val old = Ask.answer(Ask.parse("quanti giga ho?", today)!!, data(sim = sim.copy(at = ms(10, 20))))!!
        assertTrue(old.text.endsWith("(letto ieri alle 20:00)"))
        assertEquals("La SIM non è collegata", Ask.answer(Ask.parse("quanti giga ho?", today)!!, data())!!.text)
    }

    @Test
    fun pacchi() {
        val parcels = listOf(Parcel(1, "Scarpe", "XYZ", expected = today.plusDays(1)))
        assertEquals("Scarpe · arriva domani", Ask.answer(Ask.parse("dov'è il pacco?", today)!!, data(parcels = parcels))!!.text)
        assertEquals("Nessun pacco in arrivo", Ask.answer(Ask.parse("quando arriva il pacco?", today)!!, data())!!.text)
    }

    @Test
    fun quandoCompleanno() {
        val bday = TaskItem.date(77, "Compleanno di Marta", LocalDate.of(2026, 12, 3), zone = zone)
        val a = Ask.answer(Ask.parse("quando è il compleanno di Marta?", today)!!, data(tasks = listOf(bday)))
        assertNotNull(a)
        assertTrue(a!!.text, a.text.startsWith("Compleanno di Marta: giovedì 3 dicembre"))
        assertNull(Ask.answer(Ask.parse("quando è la riunione con Gino?", today)!!, data()))
    }
}
