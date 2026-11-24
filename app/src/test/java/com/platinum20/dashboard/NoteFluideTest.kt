package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.ui.screens.attachedText
import com.platinum20.dashboard.ui.screens.sweepEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Note più fluide (2026-11-24): righe che scivolano, il barrato che scorre, il testo dell'evento negli appunti. */
class NoteFluideTest {
    @Test fun dueRigheScambiate() {
        assertEquals(listOf(0 to 1, 1 to 0), NoteLines.moved("[ ] uova\n[ ] pane", "[ ] pane\n[ ] uova"))
    }

    @Test fun fatteGiuMuoveSoloQuelleCheCambianoPosto() {
        val before = "Spesa\n[x] latte\n[ ] pane\n[ ] uova"
        val moves = NoteLines.moved(before, NoteLines.doneToBottom(before))
        assertEquals(setOf(1 to 3, 2 to 1, 3 to 2), moves.toSet())
    }

    @Test fun iNumeriRifattiNonContano() {
        assertEquals(listOf(0 to 1, 1 to 0), NoteLines.moved("1. uno\n2. due", "1. due\n2. uno"))
    }

    @Test fun testoCambiatoNonScivola() {
        assertTrue(NoteLines.moved("[ ] pane", "[ ] pane e latte").isEmpty())
        assertTrue(NoteLines.moved("a\nb", "a\nb").isEmpty())
        assertTrue(NoteLines.moved("a\nb", "a\nb\nc").isEmpty())
    }

    @Test fun righeUgualiTengonoIlPostoVicino() {
        assertTrue(NoteLines.moved("x\n- a\n- a", "- a\n- a\nx").contains(0 to 2))
    }

    @Test fun ilBarratoScorre() {
        val fatta = NoteLines.parse("[x] latte").first()
        val aperta = NoteLines.parse("[ ] latte").first()
        // Senza la spunta appena toccata: tutta barrata o niente.
        assertEquals(fatta.end, sweepEnd(fatta, null))
        assertNull(sweepEnd(aperta, null))
        // Appena spuntata: a metà strada metà parola.
        assertEquals(fatta.contentStart + 2, sweepEnd(fatta, Triple(fatta.start, true, 0.5f)))
        assertEquals(fatta.end, sweepEnd(fatta, Triple(fatta.start, true, 1f)))
        // Appena tolta: il barrato si ritira da destra.
        assertEquals(aperta.contentStart + 5, sweepEnd(aperta, Triple(aperta.start, false, 0f)))
        assertEquals(aperta.contentStart, sweepEnd(aperta, Triple(aperta.start, false, 1f)))
        // Un'altra voce non cambia.
        assertEquals(fatta.end, sweepEnd(fatta, Triple(99, true, 0.1f)))
    }

    @Test fun ilTestoDellEventoFinisceNegliAppunti() {
        assertEquals("Portare la tessera", attachedText("", "  Portare la tessera \n"))
        assertEquals("chiedere della lastra", attachedText("chiedere della lastra", "Portare la tessera"))
        assertEquals("", attachedText("", ""))
    }
}
