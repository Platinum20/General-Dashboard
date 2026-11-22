package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteHistory
import com.platinum20.dashboard.notes.NoteHistory.Snap
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteLines.Edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'editor più completo (1.579): numeri, sotto-voci, scorciatoie, cerca, annulla. */
class NoteEditorToolsTest {
    @Test
    fun numberedListsRenumberThemselves() {
        assertEquals(NoteLines.Kind.NUMBER, NoteLines.kindOf("1. ciao").first)
        assertEquals(
            "1. a\n2. b\n  1. x\n  2. y\n3. c\ntesto\n1. d",
            NoteLines.renumber(Edit("1. a\n1. b\n  1. x\n  5. y\n1. c\ntesto\n3. d", 0)).text,
        )
        assertEquals(Edit("1. a\n2. ", 8), NoteLines.afterEdit("1. a", "1. a\n", 5))
        assertEquals("  1. b", NoteLines.setKind("  - b", 4, 4, NoteLines.Kind.NUMBER).text)
    }

    @Test
    fun indentMakesSubItems() {
        assertEquals(2, NoteLines.parse("    - a")[0].level)
        assertEquals(Edit("- a\n  - b", 8), NoteLines.indent("- a\n- b", 6, 6, 1))
        assertEquals(Edit("- a\n- b", 6), NoteLines.indent("- a\n  - b", 8, 8, -1))
        assertEquals("1. a\n  1. b\n2. c", NoteLines.indent("1. a\n2. b\n3. c", 9, 9, 1).text)
        assertFalse(NoteLines.canIndent("testo", 2, 1))
        assertTrue(NoteLines.canIndent("- a", 2, 1))
        // Invio su una sotto-voce vuota: torna su di un livello.
        assertEquals(Edit("- a\n- ", 6), NoteLines.afterEdit("- a\n  - ", "- a\n  - \n", 9))
        assertEquals(NoteLines.PLACE + "a", NoteLines.Shown("  1. a").shown)
    }

    @Test
    fun hashSpaceBecomesHeading() {
        assertEquals(Edit("x\n## ", 5), NoteLines.afterEdit("x\n#", "x\n# ", 4))
    }

    @Test
    fun findIgnoresCase() {
        assertEquals(listOf(0..3, 10..13), NoteLines.matches("Wifi e il wifi", "wifi"))
        assertTrue(NoteLines.matches("abc", " ").isEmpty())
    }

    @Test
    fun undoGroupsAWord() {
        val h = NoteHistory()
        h.record(Snap("", 0), Snap("c", 1), 0)
        h.record(Snap("c", 1), Snap("ci", 2), 100)
        h.record(Snap("ci", 2), Snap("cia", 3), 200)
        h.record(Snap("cia", 3), Snap("cia ", 4), 300)
        h.record(Snap("cia ", 4), Snap("cia x", 5), 400)
        assertEquals(Snap("cia ", 4), h.undo(Snap("cia x", 5)))
        assertEquals(Snap("cia", 3), h.undo(Snap("cia ", 4)))
        assertEquals(Snap("", 0), h.undo(Snap("cia", 3)))
        assertNull(h.undo(Snap("", 0)))
        assertEquals(Snap("cia", 3), h.redo(Snap("", 0)))
        assertTrue(h.canRedo)
        h.record(Snap("cia", 3), Snap("ciao", 4), 9000)
        assertFalse(h.canRedo)
    }
}
