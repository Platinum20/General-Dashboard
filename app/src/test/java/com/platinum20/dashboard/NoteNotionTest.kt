package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le idee prese da Notion (1.590): trasforma in, colori, due colonne, sotto-note, anteprime, bacheca. */
class NoteNotionTest {
    @Test
    fun transformNeverTogglesBack() {
        assertEquals("- uno", NoteLines.transform("uno", 0, 0, NoteLines.Kind.BULLET).text)
        assertEquals("- uno", NoteLines.transform("- uno", 2, 2, NoteLines.Kind.BULLET).text)
        assertEquals("uno", NoteLines.transform("[ ] uno", 4, 4, NoteLines.Kind.TEXT).text)
        assertEquals("## uno", NoteLines.transform("- uno", 2, 2, NoteLines.Kind.HEADING).text)
    }

    @Test
    fun colorsWrapAndChange() {
        val e = NoteFormat.color("ciao bella", 5, 10, 'r')
        assertEquals("ciao {r:bella}", e.text)
        assertEquals("bella", e.text.substring(e.start, e.end))
        assertEquals("ciao bella", NoteFormat.plain(e.text))
        assertEquals('r', NoteFormat.colorAt(e.text, 8))
        assertEquals("ciao {B:bella}", NoteFormat.color(e.text, 8, 13, 'B').text)
        assertEquals("ciao bella", NoteFormat.color(e.text, 8, 13, null).text)
        assertEquals(listOf(5..7, 13..13), NoteFormat.hidden(e.text))
    }

    @Test
    fun twoColumnsKeepTheirLines() {
        val line = NoteLines.columnsLine("uno\ndue", "tre")
        assertEquals(NoteLines.Kind.COLUMNS, NoteLines.kindOf(line).first)
        assertEquals("uno\ndue" to "tre", NoteLines.columns(line))
        assertEquals("" to "", NoteLines.columns(NoteLines.COLUMNS_NEW))
        assertEquals("a\nuno\ndue\ntre\nb", NoteLines.uncolumn("a\n$line\nb", 1))
    }

    @Test
    fun pagesAndBookmarks() {
        val p = NoteLines.pageLine("Viaggio [Roma]", 42)
        assertEquals("[📄 Viaggio (Roma)](nota:42)", p)
        assertEquals(NoteLines.Kind.PAGE, NoteLines.kindOf(p).first)
        assertEquals(42L, NoteLines.pageId(p))
        val b = NoteLines.bookmarkLine("https://www.corriere.it/x?y=1")
        assertEquals("[🔗 corriere.it](https://www.corriere.it/x?y=1)", b)
        assertEquals(NoteLines.Kind.BOOKMARK, NoteLines.kindOf(b).first)
        val old = "guarda\nhttps://ansa.it/a"
        val e = NoteLines.autoBookmark(old, old + "\n", old.length + 1)!!
        assertEquals("guarda\n[🔗 ansa.it](https://ansa.it/a)\n", e.text)
        assertEquals(e.text.length, e.start)
        assertNull(NoteLines.autoBookmark("ciao", "ciao\n", 5))
    }

    @Test
    fun boardHoldsItsChecks() {
        val t = "Casa\n[bacheca]\n[ ] pittura\n[-] luce\n[x] porta\nfine"
        val items = NoteLines.boardItems(t, 1)
        assertEquals(listOf(0, 1, 2), items.map { NoteLines.stateOf(it) })
        assertEquals(listOf(t.indexOf("\n[ ]") until t.indexOf("\nfine")), NoteLines.boardRanges(t))
        // Il cursore non entra nelle schede nascoste, scrivere va sotto la bacheca.
        assertEquals(t.indexOf("\nfine"), NoteLines.outOfPrefix(t, t.indexOf("luce")))
        assertEquals("Casa\n[bacheca]\n[x] pittura\n[-] luce\n[x] porta\nfine", NoteLines.setState(t, 2, 2))
        assertEquals("Casa\n[bacheca]\n[ ] pittura\n[-] luce\n[x] porta\n[ ] vetri\nfine", NoteLines.boardAdd(t, 1, "vetri"))
        assertEquals("Casa\n[bacheca]\n[ ] muri\n[-] luce\n[x] porta\nfine", NoteLines.setContent(t, 2, "muri"))
        // Indietro all'inizio della riga sotto: se ne va tutta la bacheca.
        val end = t.indexOf("\nfine")
        assertEquals("Casa\nfine", NoteLines.guardBlocks(t, t.removeRange(end, end + 1))!!.text)
        assertEquals("Casa\n[bacheca]\n[x] luce", NoteLines.boardAdd("Casa\n[bacheca]", 1, "luce", 2).let { NoteLines.setState(it, 2, 2) })
    }
}
