package com.platinum20.dashboard

import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.Trips
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

    @Test fun dueEventiDiventanoPartenzaEArrivo() {
        val out = Trips.shape(listOf(ev(1, "Train to Pescara Centrale"), ev(2, viaggio), ev(3, "Cena", begin + 1, begin + 2)))
        assertEquals(listOf("Partenza treno per Pescara Centrale", "Cena", "Arrivo treno a Pescara Centrale"), out.map { it.title })
        // Si tiene l'evento con i dettagli del biglietto.
        assertEquals(listOf(2L, 3L, 2L), out.map { it.eventId })
        assertEquals(end, out.last().begin)
        assertEquals(begin, out.first().end)
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
