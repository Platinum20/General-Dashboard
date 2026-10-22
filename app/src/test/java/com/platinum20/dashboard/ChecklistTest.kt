package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Checklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    private val spesa = "Spesa\n[x] latte\n[ ] pane\n- uova\n[X] caffè\nnote a margine\n[] mele"

    @Test fun contaFatteETotali() {
        assertEquals(2 to 5, Checklist.progress(spesa))
        assertEquals("2 di 5 fatti", Checklist.progressLabel(spesa))
    }

    @Test fun notaSenzaVociNonELista() {
        assertNull(Checklist.progress("Idee\nqualcosa da scrivere"))
        assertFalse(Checklist.hasItems("Idee\nqualcosa da scrivere"))
    }

    @Test fun ilTitoloNonEMaiUnaVoce() = assertNull(Checklist.progress("- titolo con trattino"))

    @Test fun spuntaETogliLaSpunta() {
        val once = Checklist.toggle(spesa, 2)
        assertTrue(once.lines()[2] == "[x] pane")
        assertEquals(spesa.lines() - spesa.lines()[2], once.lines() - once.lines()[2])
        val back = Checklist.toggle(once, 2)
        assertEquals("[ ] pane", back.lines()[2])
    }

    @Test fun ilTrattinoSpuntatoDiventaParentesi() =
        assertEquals("[x] uova", Checklist.toggle(spesa, 3).lines()[3])

    @Test fun spuntareUnaRigaNormaleNonCambiaNulla() {
        assertEquals(spesa, Checklist.toggle(spesa, 5))
        assertEquals(spesa, Checklist.toggle(spesa, 0))
        assertEquals(spesa, Checklist.toggle(spesa, 99))
    }

    @Test fun tuttoFatto() =
        assertEquals("Tutto fatto · 2 di 2", Checklist.progressLabel("Valigia\n[x] spazzolino\n[x] caricatore"))

    @Test fun pulsanteListaTrasformaLeRighe() {
        val text = "Libri da leggere\nIl nome della rosa\n\n[x] Dune\n- Solaris"
        assertEquals(
            "Libri da leggere\n[ ] Il nome della rosa\n\n[x] Dune\n[ ] Solaris",
            Checklist.toList(text),
        )
    }

    @Test fun listaDaNotaVuota() = assertEquals("Lista\n", Checklist.toList(""))

    @Test fun aggiungiInFondo() {
        assertEquals("Spesa\n[ ] pane\n[ ] latte", Checklist.add("Spesa\n[ ] pane\n", " latte "))
        assertEquals("Spesa", Checklist.add("Spesa", "   "))
        assertEquals("Lista\n[ ] latte", Checklist.add("", "latte"))
    }

    @Test fun anteprimaConISegni() =
        assertEquals("✓ latte\n○ pane\naltro", Checklist.preview("[x] latte\n[ ] pane\naltro"))
}
