package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.ui.screens.comingRows
import com.platinum20.dashboard.ui.screens.noteTitleEnd
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Titolo dell'editor (V16) e la lista "In arrivo" della pagina Note. */
class NotesUiLogicTest {
    @Test fun titoloEPrimaRiga() {
        assertEquals(16, noteTitleEnd("Libri da leggere\nIl nome della rosa"))
        assertEquals(5, noteTitleEnd("Spesa"))
        assertEquals(0, noteTitleEnd("\nsenza titolo"))
        assertEquals(0, noteTitleEnd(""))
    }

    private val oggi = LocalDate.of(2026, 10, 23)

    @Test fun dateEPacchiInOrdineDiGiorno() {
        val rows = comingRows(
            upcoming = listOf(
                Countdown(1, "Viaggio", oggi.plusDays(10)),
                Countdown(2, "Compleanno Marta", LocalDate.of(1990, 10, 25), yearly = true),
            ),
            parcels = listOf(
                Parcel(3, "Scarpe", expected = oggi.plusDays(1)),
                Parcel(4, "Senza data"),
                Parcel(5, "Cuffie", expected = oggi.minusDays(2), deliveredOn = oggi.minusDays(1)),
                Parcel(6, "Libro", expected = oggi.minusDays(1)),
            ),
            today = oggi,
        )
        // In ritardo, domani, dopodomani (il compleanno torna ogni anno), tra 10 giorni, senza data, poi i consegnati.
        assertEquals(listOf("parcel-6", "parcel-3", "date-2", "date-1", "parcel-4", "parcel-5"), rows.map { it.key })
    }

    @Test fun senzaNienteListaVuota() = assertEquals(emptyList<Any>(), comingRows(emptyList(), emptyList(), oggi))
}
