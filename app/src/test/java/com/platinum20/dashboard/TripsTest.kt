package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trip
import com.platinum20.dashboard.calendar.Trips
import com.platinum20.dashboard.ui.screens.TripStops
import com.platinum20.dashboard.ui.screens.tripStops
import com.platinum20.dashboard.ui.screens.peopleLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import com.platinum20.dashboard.trips.TrainStatus

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
        assertEquals(Trip("Taranto", "Pescara Centrale", "Intercity 612", "8", "11D"), trip.trip)
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
        assertEquals("Treno da Taranto", stops.from)
        assertEquals("15:00", stops.arriveAt)
        assertEquals("Arrivo a Pescara Centrale", stops.to)
        // Sulla pagina del viaggio le due stazioni.
        val page = tripStops(e, LocalDate.of(2026, 11, 9), zone, labels = true)!!
        assertEquals("Taranto", page.from)
        assertEquals("Pescara Centrale", page.to)
        // La durata in mezzo, sotto treno, carrozza e posto.
        assertEquals("4 h 32 min", stops.middle)
        assertEquals("Intercity 612", stops.below)
        assertEquals("Intercity 612 · Carrozza 5 · Posto 42D",
            tripStops(e.copy(trip = Trip("Taranto", "Pescara Centrale", "Intercity 612", "5", "42D")), LocalDate.of(2026, 11, 9), zone)!!.below)
        assertEquals(null, stops.title)
        assertEquals(null, stops.progress(dep - 1))
        assertEquals(0.5f, stops.progress(dep + (arr - dep) / 2)!!, 0.01f)
        // Treno di notte: accanto all'arrivo il giorno.
        val night = e.copy(end = LocalDateTime.of(2026, 11, 15, 7, 10).atZone(zone).toInstant().toEpochMilli())
        assertEquals("Arrivo a Pescara Centrale · Dom 15", tripStops(night, LocalDate.of(2026, 11, 9), zone)!!.to)
    }

    @Test fun ogniEventoComeUnTreno() {
        val zone = ZoneId.of("Europe/Rome")
        val start = LocalDateTime.of(2026, 11, 14, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val stop = LocalDateTime.of(2026, 11, 14, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val shift = CalendarEvent(-3, "Turno Morning", start, stop, false, "Ufficio", 0)
        val row = tripStops(shift, LocalDate.of(2026, 11, 9), zone)!!
        assertEquals("07:00", row.departAt)
        // Il nome accanto all'ora d'inizio, nessuna riga sopra.
        assertEquals(null, row.title)
        assertEquals("Turno Morning", row.from)
        assertEquals("12:00", row.arriveAt)
        assertEquals("", row.to)
        assertEquals("5 h · Ufficio", row.middle)
        // Lo spazio fra inizio e fine è uguale per tutti, corto o lungo.
        assertEquals(TripStops.MID_DP, row.midDp(), 0.01f)
        assertEquals(row.midDp(), row.copy(end = row.begin + 15 * 60_000L).midDp(), 0.01f)
        assertEquals(row.midDp(), row.copy(end = row.begin + 12 * 3_600_000L).midDp(), 0.01f)
        // Con le creator in mezzo l'altezza la decide il testo.
        assertEquals(row.midDp(), row.middleMinDp(), 0.01f)
        assertEquals(0f, row.copy(names = "Chiara").middleMinDp(), 0.01f)
        // Un turno con le creator: durata, icona e con chi; sotto il venduto.
        val paid = CalendarEvent(-4, "Turno · Chiara Stefane", start, stop, false, "Venduto $736,00", 0, people = 2, mates = listOf("Marco Bianchi"))
        val r2 = tripStops(paid, LocalDate.of(2026, 11, 9), zone)!!
        assertEquals("5 h", r2.middle)
        assertEquals(null, r2.title)
        assertEquals("Turno su Chiara", r2.from)
        assertEquals(null, r2.names)
        assertEquals("Turno su A, B e C", tripStops(paid.copy(title = "Turno · A, B, C"), LocalDate.of(2026, 11, 9), zone)!!.from)
        assertEquals(2, r2.people)
        assertEquals("In doppio con Marco", r2.extra)
        assertEquals("Venduto $736,00", r2.below)
        assertEquals("In doppio", peopleLabel(2))
        assertEquals("Da solo", peopleLabel(1))
        assertEquals(null, row.people)
        // Sulla pagina dell'evento il nome c'è già: Inizio e Fine, senza luogo.
        val page = tripStops(shift, LocalDate.of(2026, 11, 9), zone, labels = true)!!
        assertEquals("Inizio", page.from)
        assertEquals(null, page.title)
        assertEquals("Fine", page.to)
        assertEquals("5 h", page.middle)
        // Turno di notte: accanto alla fine il giorno dopo.
        val night = shift.copy(end = LocalDateTime.of(2026, 11, 15, 3, 0).atZone(zone).toInstant().toEpochMilli(), location = "")
        assertEquals("Dom 15", tripStops(night, LocalDate.of(2026, 11, 9), zone)!!.to)
        // Tutto il giorno o senza durata: riga normale.
        assertEquals(null, tripStops(shift.copy(allDay = true), LocalDate.of(2026, 11, 9), zone))
        assertEquals(null, tripStops(shift.copy(end = start), LocalDate.of(2026, 11, 9), zone))
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

    @Test fun carrozzaEPostoDalBiglietto() {
        assertEquals("8", Trips.coach(viaggio))
        assertEquals("11D", Trips.seat(viaggio))
        assertEquals("5", Trips.coach("Carrozza 5 Posto 42d"))
        assertEquals("42D", Trips.seat("Carrozza 5 Posto 42d"))
        assertEquals(null, Trips.seat("Treno Intercity 612"))
        // Dalle note dell'evento, quando il titolo non le ha.
        val withNotes = ev(1, "Train to Pescara Centrale").copy(description = "Treno: Intercity 612, Carrozza 3, Posto 7A")
        assertEquals(Trip("Taranto", "Pescara Centrale", "Intercity 612", "3", "7A"), Trips.shape(listOf(withNotes)).single().trip)
        assertEquals(null, Trips.ticketLine(Trip("Taranto", "Pescara Centrale", null)))
        assertEquals("Carrozza 3 · Posti 7A-7B", Trips.ticketLine(Trip(null, "X", null, "3", "7A-7B")))
    }

    private val zoneR = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(zoneR).toInstant().toEpochMilli()
    private val ic = CalendarEvent(7, "Treno per Taranto", at(20, 14, 1), at(20, 18, 27), false, "", 0,
        Trip("Pescara Centrale", "Taranto", "Intercity 605", "7", "16A"))
    private val day20 = LocalDate.of(2026, 11, 20)

    @Test fun ilRitardoSpostaGliOrari() {
        val s = tripStops(ic, day20, zoneR, live = TrainStatus(97, platform = "2", readAt = 1L), now = 2L)!!
        assertEquals("15:38", s.departAt)
        assertEquals("20:04", s.arriveAt)
        assertEquals("14:01", s.plannedDepart)
        assertEquals("18:27", s.plannedArrive)
        // I testi restano quelli: solo gli orari si spostano, la durata è quella del biglietto.
        assertEquals("Treno da Pescara Centrale", s.from)
        assertEquals("Arrivo a Taranto", s.to)
        assertEquals("4 h 26 min · +97 min", s.middle)
        assertEquals("Binario 2 · Intercity 605 · Carrozza 7 · Posto 16A", s.below)
        // Senza rete resta l'ultima lettura, ma dice quanto è vecchia.
        val old = tripStops(ic, day20, zoneR, live = TrainStatus(97, platform = "2", readAt = 1L), now = 1L + 35 * 60_000L)!!
        assertEquals("4 h 26 min · +97 min · letto 35 min fa", old.middle)
        assertEquals("+97 min", s.late)
        assertEquals(at(20, 15, 38), s.begin)
    }

    @Test fun inOrarioOAnticipoNienteBarrato() {
        listOf(TrainStatus(0, readAt = 1L), TrainStatus(-3, readAt = 1L), null).forEach { st ->
            val s = tripStops(ic, day20, zoneR, live = st)!!
            assertEquals("14:01", s.departAt)
            assertEquals("18:27", s.arriveAt)
            assertEquals(null, s.plannedDepart)
            assertEquals(null, s.plannedArrive)
            assertEquals(null, s.late)
        }
    }

    @Test fun soppressoTieneGliOrariDelBiglietto() {
        val s = tripStops(ic, day20, zoneR, live = TrainStatus(40, cancelled = true, readAt = 1L), now = 2L)!!
        assertEquals("14:01", s.departAt)
        assertEquals(null, s.plannedDepart)
        assertEquals("4 h 26 min · Soppresso", s.middle)
    }

    @Test fun ilRitardoPortaLArrivoDopoMezzanotte() {
        val late = ic.copy(begin = at(20, 19, 30), end = at(20, 23, 40))
        val s = tripStops(late, day20, zoneR, live = TrainStatus(45, readAt = 1L))!!
        assertEquals("00:25", s.arriveAt)
        assertEquals("23:40", s.plannedArrive)
        assertEquals("Arrivo a Taranto · Domani", s.to)
        assertEquals("Arrivo a Taranto", tripStops(late, day20, zoneR, live = null)!!.to)
        // Anche la partenza oltre mezzanotte lo dice.
        val night = ic.copy(begin = at(20, 23, 50), end = at(21, 3, 0))
        val n = tripStops(night, day20, zoneR, live = TrainStatus(20, readAt = 1L))!!
        assertEquals("00:10", n.departAt)
        assertEquals("Treno da Pescara Centrale · Domani", n.from)
    }
}
