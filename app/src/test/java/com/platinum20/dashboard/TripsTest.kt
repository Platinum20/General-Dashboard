package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip
import com.platinum20.dashboard.calendar.Trips
import com.platinum20.dashboard.ui.screens.tripStops
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** I treni di Gmail: un viaggio solo, partenza e arrivo sulla linea, note ripulite. */
class TripsTest {
    private val begin = 1_000_000_000L
    private val end = begin + 4 * 3_600_000L + 32 * 60_000L
    private val viaggio = "Viaggio Taranto-Pescara Centrale, Treno Intercity 612, Carr 8, Posti, 11D, CP 913601, PNR BG6GF5,"

    private fun ev(id: Long, title: String, b: Long = begin, e: Long = end) =
        CalendarEvent(id, title, b, e, allDay = false, location = "Taranto", color = 0)

    @Test fun destinazione() {
        assertEquals("Pescara Centrale", Trips.destination("Train to Pescara Centrale"))
        assertEquals("Pescara Centrale", Trips.destination(viaggio))
        assertEquals(null, Trips.destination("Visita con Danilo"))
        assertEquals("Treno per Taranto", Trips.title("Train to Taranto"))
    }

    @Test fun dueEventiDiventanoUnViaggioSolo() {
        val out = Trips.shape(listOf(ev(1, "Train to Pescara Centrale"), ev(2, viaggio), ev(3, "Cena", begin + 1, begin + 2)))
        assertEquals(listOf("Treno per Pescara Centrale", "Cena"), out.map { it.title })
        // Si tiene l'evento con i dettagli del biglietto, con l'orario intero del viaggio.
        val trip = out.first()
        assertEquals(2L, trip.eventId)
        assertEquals(begin, trip.begin)
        assertEquals(end, trip.end)
        assertEquals(Trip("Taranto", "Pescara Centrale", "Intercity 612"), trip.trip)
        assertEquals(null, out[1].trip)
    }

    @Test fun partenzaDalLuogoSenzaDettagli() {
        val trip = Trips.shape(listOf(ev(1, "Train to Pescara Centrale"))).single().trip
        assertEquals(Trip("Taranto", "Pescara Centrale", null), trip)
        assertEquals(Trip("Taranto", "Pescara Centrale", "Intercity 612"),
            Trips.tripOf("Train to Pescara Centrale", "", "Treno: Intercity 612, partenza da Taranto Ore: 10:28; arrivo a Pescara Centrale Ore: 15:00"))
        assertEquals(null, Trips.tripOf("Cena"))
    }

    @Test fun durataETrenoNellaRiga() {
        assertEquals("4 h 32 min", Trips.duration(begin, end))
        assertEquals("45 min", Trips.duration(0, 45 * 60_000L))
        assertEquals("2 h", Trips.duration(0, 2 * 3_600_000L))
        val zone = ZoneId.of("Europe/Rome")
        val dep = LocalDateTime.of(2026, 11, 14, 10, 28).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 14, 15, 0).atZone(zone).toInstant().toEpochMilli()
        val e = CalendarEvent(2, "Treno per Pescara Centrale", dep, arr, false, "", 0, Trip("Taranto", "Pescara Centrale", "Intercity 612"))
        val stops = tripStops(e, LocalDate.of(2026, 11, 9), zone)!!
        assertEquals("10:28", stops.departAt)
        assertEquals("Taranto", stops.from)
        assertEquals("15:00", stops.arriveAt)
        assertEquals("Pescara Centrale", stops.to)
        assertEquals("4 h 32 min · Intercity 612", stops.middle)
        assertEquals(null, stops.progress(dep - 1))
        assertEquals(0.5f, stops.progress(dep + (arr - dep) / 2)!!, 0.01f)
        // Treno di notte: accanto all'arrivo il giorno.
        val night = e.copy(end = LocalDateTime.of(2026, 11, 15, 7, 10).atZone(zone).toInstant().toEpochMilli())
        assertEquals("Pescara Centrale · Dom 15", tripStops(night, LocalDate.of(2026, 11, 9), zone)!!.to)
    }

    @Test fun ogniEventoComeUnTreno() {
        val zone = ZoneId.of("Europe/Rome")
        val start = LocalDateTime.of(2026, 11, 14, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val stop = LocalDateTime.of(2026, 11, 14, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val shift = CalendarEvent(-3, "Turno Morning", start, stop, false, "Ufficio", 0)
        val row = tripStops(shift, LocalDate.of(2026, 11, 9), zone)!!
        assertEquals("07:00", row.departAt)
        assertEquals("Turno Morning", row.from)
        assertEquals("12:00", row.arriveAt)
        assertEquals("", row.to)
        assertEquals("5 h · Ufficio", row.middle)
        // Lo spazio fra inizio e fine cresce con la durata, con un minimo e un massimo.
        assertEquals(18f + 12f * (Math.log(6.0) / Math.log(2.0)).toFloat(), row.midDp(), 0.01f)
        assertEquals(30f, row.copy(end = row.begin + 3_600_000L).midDp(), 0.01f)
        assertEquals(true, row.copy(end = row.begin + 15 * 60_000L).midDp() < row.midDp())
        assertEquals(56f, row.copy(end = row.begin + 12 * 3_600_000L).midDp(), 0.01f)
        // Sulla pagina dell'evento il nome c'è già: Inizio e Fine, senza luogo.
        val page = tripStops(shift, LocalDate.of(2026, 11, 9), zone, labels = true)!!
        assertEquals("Inizio", page.from)
        assertEquals("Fine", page.to)
        assertEquals("5 h", page.middle)
        // Turno di notte: accanto alla fine il giorno dopo.
        val night = shift.copy(end = LocalDateTime.of(2026, 11, 15, 3, 0).atZone(zone).toInstant().toEpochMilli(), location = "")
        assertEquals("Dom 15", tripStops(night, LocalDate.of(2026, 11, 9), zone)!!.to)
        // Tutto il giorno o senza durata: riga normale.
        assertEquals(null, tripStops(shift.copy(allDay = true), LocalDate.of(2026, 11, 9), zone))
        assertEquals(null, tripStops(shift.copy(end = start), LocalDate.of(2026, 11, 9), zone))
    }

    @Test fun speseDelTreno() {
        assertEquals(true, Trips.isTrainMerchant("Trenitalia"))
        assertEquals(true, Trips.isTrainMerchant("Italo"))
        assertEquals(false, Trips.isTrainMerchant("Esselunga"))
    }

    @Test fun datiDelBiglietto() {
        val d = Trips.details(viaggio, "Taranto-Pescara Centrale;Treno: Intercity 612, partenza da Taranto Ore: 10:28; arrivo a Pescara Centrale Ore: 15:00 Carrozza 8, Posti 11D, CP 913601; codice pnr BG6GF5")
        assertEquals(listOf("Treno" to "Intercity 612", "Carrozza" to "8", "Posto" to "11D", "PNR" to "BG6GF5"), d)
    }

    @Test fun noteDiGoogleTolte() {
        val text = "Per visualizzare informazioni dettagliate sugli eventi creati automaticamente come questo, utilizza l'app Google Calendar ufficiale. https://g.co/calendar\n\n" +
            "Questo evento è stato creato a partire da un'email che hai ricevuto in Gmail. https://mail.google.com/mail?extsrc=cal"
        assertEquals("", Trips.cleanDescription(text))
        assertEquals("Portare il caricabatterie", Trips.cleanDescription("Portare il caricabatterie\n\n$text"))
    }
}
