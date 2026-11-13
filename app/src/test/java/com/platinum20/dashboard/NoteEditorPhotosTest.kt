package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NotePhotos
import com.platinum20.dashboard.notes.NoteText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editor delle note 1.529: più foto, formati, titolo separato, dettatura al cursore. */
class NoteEditorPhotosTest {
    @Test
    fun photosAddRemoveRestoreMove() {
        val list = NotePhotos.add(emptyList(), listOf("/a", "/b", "/a", ""))
        assertEquals(listOf("/a", "/b"), list)
        assertEquals(listOf("/a", "/b", "/c"), NotePhotos.add(list, listOf("/b", "/c")))
        val three = listOf("/a", "/b", "/c")
        assertEquals(listOf("/a", "/c"), NotePhotos.remove(three, "/b"))
        assertEquals(three, NotePhotos.restore(listOf("/a", "/c"), "/b", 1))
        assertEquals(three, NotePhotos.restore(three, "/b", 0))
        assertEquals(listOf("/b", "/c", "/a"), NotePhotos.move(three, 0, 2))
        assertEquals(listOf("/c", "/a", "/b"), NotePhotos.toFront(three, "/c"))
        assertEquals(listOf("/a", "/z", "/c"), NotePhotos.replace(three, "/b", "/z"))
        assertEquals("3 foto", NotePhotos.label(3))
    }

    @Test
    fun dragTargetAndShift() {
        assertEquals(2, NotePhotos.target(0, 250f, 120f, 3))
        assertEquals(2, NotePhotos.target(0, 900f, 120f, 3))
        assertEquals(0, NotePhotos.target(1, -200f, 120f, 3))
        assertEquals(-1, NotePhotos.shift(1, 0, 2))
        assertEquals(1, NotePhotos.shift(0, 2, 0))
        assertEquals(0, NotePhotos.shift(2, 0, 1))
    }

    @Test
    fun coverIsFirstPhoto() {
        val note = Note(1, "**Spesa**\n## Lista\nlatte e *pane*", photos = listOf("/x", "/y"))
        assertEquals("/x", note.photo)
        assertEquals("Spesa", note.title)
        assertEquals("Lista\nlatte e pane", note.body)
        assertEquals("", Note(2, "testo").photo)
    }

    @Test
    fun toggleWrapsAndUnwraps() {
        val on = NoteFormat.toggle("ciao mondo", 5, 10, NoteFormat.BOLD)
        assertEquals(NoteFormat.Edit("ciao **mondo**", 7, 12), on)
        assertEquals(NoteFormat.Edit("ciao mondo", 5, 10), NoteFormat.toggle(on.text, on.start, on.end, NoteFormat.BOLD))
        assertEquals("ciao mondo", NoteFormat.toggle("ciao **mondo**", 5, 14, NoteFormat.BOLD).text)
        // Gli spazi scelti restano fuori dai segni.
        assertEquals("ciao ~~mondo~~ ", NoteFormat.toggle("ciao mondo ", 4, 11, NoteFormat.STRIKE).text)
    }

    @Test
    fun headingToggles() {
        val on = NoteFormat.heading("uno\ndue", 5, 5)
        assertEquals("uno\n## due", on.text)
        assertEquals(8, on.start)
        val off = NoteFormat.heading(on.text, on.start, on.end)
        assertEquals("uno\ndue", off.text)
        assertEquals(5, off.start)
    }

    @Test
    fun spansAndPlain() {
        val spans = NoteFormat.spans("**ciao**")
        assertEquals(NoteFormat.Span(2, 6, NoteFormat.Kind.BOLD), spans.first { it.kind == NoteFormat.Kind.BOLD })
        assertEquals(2, spans.count { it.kind == NoteFormat.Kind.MARK })
        assertTrue(NoteFormat.spans("a * b * c").none { it.kind == NoteFormat.Kind.ITALIC })
        assertEquals("Spesa\nlatte e pane vino", NoteFormat.plain("## Spesa\n**latte** e *pane* ~~vino~~"))
        assertEquals(setOf(NoteFormat.Kind.BOLD), NoteFormat.active("x **ciao** y", 4, 6))
    }

    @Test
    fun titleBodySplitJoin() {
        assertEquals("Titolo" to "riga 1\nriga 2", NoteText.split("Titolo\nriga 1\nriga 2"))
        assertEquals("Solo" to "", NoteText.split("Solo"))
        assertEquals("Solo", NoteText.join("Solo", ""))
        assertEquals("A\nB", NoteText.join("A", "B"))
    }

    @Test
    fun dictationGoesAtCursor() {
        assertEquals("Ciao mondo" to 10, NoteText.insert("Ciao", 4, "mondo"))
        assertEquals("Ciao" to 4, NoteText.insert("", 0, "ciao"))
        assertEquals("Fine. Poi" to 9, NoteText.insert("Fine.", 5, "poi"))
        assertEquals("a x b" to 3, NoteText.insert("ab", 1, "x"))
    }
}
