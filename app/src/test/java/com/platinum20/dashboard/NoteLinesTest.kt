package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteLines.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinesTest {
    private val nota = "Partenza venerdì.\n## Da portare\n[x] Caricatore\n[ ] Documenti\n- Museo Egizio"

    @Test fun riconosceOgniRiga() {
        val kinds = NoteLines.parse(nota).map { it.kind }
        assertEquals(listOf(Kind.TEXT, Kind.HEADING, Kind.CHECK, Kind.CHECK, Kind.BULLET), kinds)
        assertTrue(NoteLines.parse(nota)[2].done)
        assertEquals(Kind.CHECK, NoteLines.kindOf("[] mele").first)
        assertEquals(Kind.CHECK, NoteLines.kindOf("[ ]").first)
        assertEquals(Kind.TEXT, NoteLines.kindOf("*corsivo* qui").first)
        assertEquals(Kind.BULLET, NoteLines.kindOf("• uova").first)
    }

    @Test fun tastoSpuntaSullaRigaDelCursore() {
        val e = NoteLines.setKind("latte\npane", 8, 8, Kind.CHECK)
        assertEquals("latte\n[ ] pane", e.text)
        assertEquals(12, e.start)
        // Di nuovo: torna testo.
        val back = NoteLines.setKind(e.text, e.start, e.start, Kind.CHECK)
        assertEquals("latte\npane", back.text)
        assertEquals(8, back.start)
    }

    @Test fun daPuntoAVoceEPiuRighe() {
        assertEquals("[ ] uova\n[ ] pane", NoteLines.setKind("- uova\npane", 0, 10, Kind.CHECK).text)
        assertEquals("- uova\n- pane", NoteLines.setKind("[x] uova\npane", 0, 11, Kind.BULLET).text)
        assertEquals("## Regalo", NoteLines.setKind("[ ] Regalo", 5, 5, Kind.HEADING).text)
    }

    @Test fun invioContinuaLaLista() {
        val old = "[ ] latte"
        val e = NoteLines.afterEdit(old, "[ ] latte\n", 10)!!
        assertEquals("[ ] latte\n[ ] ", e.text)
        assertEquals(14, e.start)
        val b = NoteLines.afterEdit("- uova", "- uova\n", 7)!!
        assertEquals("- uova\n- ", b.text)
        // Da una voce fatta nasce una voce da fare.
        assertEquals("[x] pane\n[ ] ", NoteLines.afterEdit("[x] pane", "[x] pane\n", 9)!!.text)
    }

    @Test fun invioAMetaVoceLaSpezza() {
        val e = NoteLines.afterEdit("[ ] lattepane", "[ ] latte\npane", 10)!!
        assertEquals("[ ] latte\n[ ] pane", e.text)
        assertEquals(14, e.start)
    }

    @Test fun invioSuVoceVuotaChiudeLaLista() {
        val e = NoteLines.afterEdit("[ ] latte\n[ ] ", "[ ] latte\n[ ] \n", 15)!!
        assertEquals("[ ] latte\n", e.text)
        assertEquals(10, e.start)
    }

    @Test fun invioNelTestoNonCambiaNulla() {
        assertNull(NoteLines.afterEdit("ciao", "ciao\n", 5))
        assertNull(NoteLines.afterEdit("ciao", "ciaox", 5))
    }

    @Test fun cancellareIlSegnoFaTornareTesto() {
        // Indietro a inizio voce: la tastiera cancella lo spazio del segno.
        val e = NoteLines.afterEdit("a\n[ ] latte", "a\n[ ]latte", 5)!!
        assertEquals("a\nlatte", e.text)
        assertEquals(2, e.start)
        assertEquals("pane", NoteLines.afterEdit("- pane", "-pane", 1)!!.text)
        // Cancellare dentro il testo della voce va bene così.
        assertNull(NoteLines.afterEdit("[ ] latte", "[ ] latt", 8))
    }

    @Test fun spuntaSpostaTogli() {
        assertEquals("a\n[x] b", NoteLines.toggle("a\n[ ] b", 1))
        assertEquals("a\n[ ] b", NoteLines.toggle("a\n[x] b", 1))
        assertEquals("[x] mele", NoteLines.toggle("[] mele", 0))
        assertEquals("a\n- b", NoteLines.toggle("a\n- b", 1))
        assertEquals("b\na", NoteLines.move("a\nb", 1, up = true))
        assertEquals("a\nb", NoteLines.move("a\nb", 0, up = true))
        assertEquals("b", NoteLines.remove("a\nb", 0))
        assertEquals("a\n- b", NoteLines.setLineKind("a\n[ ] b", 1, Kind.BULLET))
        assertEquals("a\nb", NoteLines.setLineKind("a\n[ ] b", 1, Kind.TEXT))
    }

    @Test fun lePiccoleAzioniDelleListe() {
        val t = "Valigia\n[x] spazzolino\n[ ] giacca\n[x] caricatore\nfine\n[x] a\n[ ] b"
        assertEquals("Valigia\n[ ] giacca\n[x] spazzolino\n[x] caricatore\nfine\n[ ] b\n[x] a", NoteLines.doneToBottom(t))
        assertEquals("Valigia\n[ ] giacca\nfine\n[ ] b", NoteLines.removeDone(t))
        assertFalse(NoteLines.hasDone(NoteLines.uncheckAll(t)))
        assertFalse(NoteLines.allDone(t))
        assertTrue(NoteLines.allDone("x\n[x] a\n[x] b"))
        assertFalse(NoteLines.allDone("solo testo"))
    }

    @Test fun rigaDellaCardAppunti() {
        assertEquals("1 di 3 fatte · Documenti, Ombrello", NoteLines.cardLine("Partenza\n[x] Caricatore\n[ ] Documenti\n[ ] **Ombrello**"))
        assertEquals("Tutto fatto · 1 di 1", NoteLines.cardLine("[x] a"))
        assertEquals("Museo Egizio", NoteLines.cardLine("\n- Museo Egizio"))
        assertNull(NoteLines.cardLine(""))
    }

    @Test fun testoDisegnato() {
        val v = NoteLines.Shown("a\n[ ] latte\n- **uova**", listOf(14..15, 20..21))
        assertEquals("a\n\u2009\u200Blatte\n\u2009\u200Buova", v.shown)
        // Il cursore dopo "[ ] " sta dopo il posto del cerchio; da lì torna dopo il segno.
        assertEquals(4, v.toShown(6))
        assertEquals(6, v.toText(4))
        assertEquals(6, v.toText(3))
        assertEquals(6, v.toText(2))
        assertEquals(4, v.toShown(4))
        assertEquals(v.shown.length, v.toShown(22))
        assertEquals(22, v.toText(v.shown.length))
        assertEquals(6, NoteLines.outOfPrefix("a\n[ ] latte", 3))
        assertEquals(1, NoteLines.outOfPrefix("a\n[ ] latte", 1))
    }
}
