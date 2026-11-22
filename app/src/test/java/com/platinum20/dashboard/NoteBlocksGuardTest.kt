package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tabella, linea e vocale sono pezzi unici (1.589); i link "@" e i vocali si cancellano tutti insieme. */
class NoteBlocksGuardTest {
    private val table = "Sopra\n| a | b |\n| 1 | 2 |\nSotto"
    private val tEnd = table.indexOf("\nSotto")

    @Test
    fun cursorNeverStaysInsideABlock() {
        assertEquals(tEnd, NoteLines.outOfPrefix(table, table.indexOf("| 1")))
        assertEquals(tEnd, NoteLines.outOfPrefix(table, table.indexOf("| a") + 3))
        assertEquals(0, NoteLines.outOfPrefix(table, 0))
        assertEquals(listOf(6..tEnd), NoteLines.blocks(table))
    }

    @Test
    fun typingAfterATableGoesBelowIt() {
        val typed = table.substring(0, tEnd) + "x" + table.substring(tEnd)
        val e = NoteLines.guardBlocks(table, typed)!!
        assertEquals("Sopra\n| a | b |\n| 1 | 2 |\nx\nSotto", e.text)
        assertEquals(tEnd + 2, e.start)
        // Invio in fondo alla tabella va bene così.
        assertNull(NoteLines.guardBlocks(table, table.substring(0, tEnd) + "\n" + table.substring(tEnd)))
        // Scrivere dentro una riga della tabella non la rompe.
        val inside = table.indexOf("| 1") + 1
        assertEquals(table.substring(0, tEnd) + "\nz" + table.substring(tEnd), NoteLines.guardBlocks(table, table.substring(0, inside) + "z" + table.substring(inside))!!.text)
    }

    @Test
    fun backspaceRemovesTheWholeTable() {
        // Indietro in fondo alla tabella.
        assertEquals("Sopra\nSotto", NoteLines.guardBlocks(table, table.removeRange(tEnd - 1, tEnd))!!.text)
        // Indietro a inizio della riga sotto.
        val e = NoteLines.guardBlocks(table, table.removeRange(tEnd, tEnd + 1))!!
        assertEquals("Sopra\nSotto", e.text)
        assertEquals(6, e.start)
        // Tabella in fondo alla nota: se ne va anche il "\n" prima.
        val last = "Sopra\n| a | b |"
        assertEquals("Sopra", NoteLines.guardBlocks(last, last.dropLast(1))!!.text)
        // Indietro all'inizio della tabella non la incolla alla riga sopra.
        val glued = NoteLines.guardBlocks(table, table.removeRange(5, 6))!!
        assertEquals(table, glued.text)
        assertEquals(5, glued.start)
        // Cose lontane dalla tabella non si toccano.
        assertNull(NoteLines.guardBlocks(table, table.dropLast(1)))
    }

    @Test
    fun dividerAndAudioAreBlocksToo() {
        val d = "uno\n---\ndue"
        assertEquals("uno\n---\nx\ndue", NoteLines.guardBlocks(d, "uno\n---x\ndue")!!.text)
        assertEquals("uno\ndue", NoteLines.guardBlocks(d, "uno\n--\ndue")!!.text)
        val a = "nota\n[🎙 Audio 0:12](audio:/x/1.m4a)\nfine"
        assertEquals(NoteLines.Kind.AUDIO, NoteLines.lineAt(a, 6).kind)
        val end = a.indexOf("\nfine")
        assertEquals("nota\nfine", NoteLines.guardBlocks(a, a.removeRange(end - 1, end))!!.text)
        assertEquals(end, NoteLines.outOfPrefix(a, 7))
        // Un vocale in mezzo a una frase è un link: si cancella tutto insieme.
        val mid = "ascolta [🎙 Audio 0:12](audio:/x/1.m4a) dopo"
        assertEquals("ascolta dopo", NoteFormat.deleteMention(mid, mid.removeRange(12, 13), 12)!!.text)
    }

    @Test
    fun mentionGoesAwayInOnePiece() {
        val t = "vedi [Spesa](nota:12) poi"
        val e = NoteFormat.deleteMention(t, t.removeRange(8, 9), 8)!!
        assertEquals("vedi poi", e.text)
        assertEquals(5, e.start)
        assertNull(NoteFormat.deleteMention(t, t.removeRange(1, 2), 1))
        assertEquals(listOf(5..20), NoteFormat.mentions(t))
    }
}
