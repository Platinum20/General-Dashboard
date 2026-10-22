package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.noteTitleEnd
import com.platinum20.dashboard.ui.screens.stripScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fila di pillole delle Note (V19) e titolo dell'editor (V16). */
class NotesUiLogicTest {
    @Test fun pillolaGiaInVistaNonScorre() = assertNull(stripScrollTarget(100, 200, viewport = 400, scroll = 0, margin = 16))

    @Test fun pillolaAdestraFuoriSchermoArrivaInteraConMargine() {
        // Abitudini da 500 a 640 px con lo schermo largo 400: la fine sta a 16 px dal bordo.
        assertEquals(256, stripScrollTarget(500, 640, viewport = 400, scroll = 0, margin = 16))
    }

    @Test fun pillolaASinistraTagliataTornaIntera() {
        assertEquals(84, stripScrollTarget(100, 200, viewport = 400, scroll = 150, margin = 16))
    }

    @Test fun primaPillolaTornaAllInizio() = assertEquals(0, stripScrollTarget(10, 80, viewport = 400, scroll = 60, margin = 16))

    @Test fun senzaLarghezzaNonFaNulla() = assertNull(stripScrollTarget(500, 640, viewport = 0, scroll = 0, margin = 16))

    @Test fun titoloEPrimaRiga() {
        assertEquals(16, noteTitleEnd("Libri da leggere\nIl nome della rosa"))
        assertEquals(5, noteTitleEnd("Spesa"))
        assertEquals(0, noteTitleEnd("\nsenza titolo"))
        assertEquals(0, noteTitleEnd(""))
    }
}
