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
    fun mosaicBigTwoStackedThenGridPages() {
        // Larghezza 304, spazio 8: grande 200, piccole 96 (due impilate = 200).
        val s = NotePhotos.slots(8, 304f, 8f)
        assertEquals(NotePhotos.Slot(0f, 0f, 200f), s[0])
        assertEquals(NotePhotos.Slot(208f, 0f, 96f), s[1])
        assertEquals(NotePhotos.Slot(208f, 104f, 96f), s[2])
        // Prima le tre giuste nello schermo.
        assertEquals(304f, s[2].x + s[2].size, 0.01f)
        // Pagina 2x2: 3 e 4 in alto, 5 e 6 sotto; la 7 apre la pagina dopo.
        assertEquals(NotePhotos.Slot(312f, 0f, 96f), s[3])
        assertEquals(NotePhotos.Slot(416f, 0f, 96f), s[4])
        assertEquals(NotePhotos.Slot(312f, 104f, 96f), s[5])
        assertEquals(NotePhotos.Slot(416f, 104f, 96f), s[6])
        assertEquals(NotePhotos.Slot(520f, 0f, 96f), s[7])
        assertEquals(616f, NotePhotos.mosaicWidth(s), 0.01f)
        assertEquals(0, NotePhotos.nearest(s, 50f, 150f))
        assertEquals(2, NotePhotos.nearest(s, 250f, 160f))
        assertEquals(6, NotePhotos.nearest(s, 470f, 150f))
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

    @Test
    fun marksHideUnlessCursorTouchesThem() {
        val text = "Esatto **lui**!"
        // Senza cursore spariscono i due "**".
        assertEquals(listOf(7..8, 12..13), NoteFormat.hidden(text, 0, 0, focused = false))
        // Cursore lontano: nascosti; cursore dentro o appena dopo: visibili.
        assertEquals(2, NoteFormat.hidden(text, 2, 2, focused = true).size)
        assertTrue(NoteFormat.hidden(text, 10, 10, focused = true).isEmpty())
        assertTrue(NoteFormat.hidden(text, 14, 14, focused = true).isEmpty())
        assertEquals(listOf(0..2), NoteFormat.hidden("## Titolo\nx", 11, 11, focused = true))
        val c = NoteFormat.Collapse(NoteFormat.hidden(text, 0, 0, focused = false))
        // "Esatto lui!": la "l" sta al 7 nel disegno e al 9 nel testo.
        assertEquals(7, c.toShown(9))
        assertEquals(9, c.toText(7))
        assertEquals(11, c.toShown(text.length))
        assertEquals(text.length, c.toText(11))
        assertEquals(3, c.toShown(3))
    }

    @Test
    fun samePhotoTwiceKeepsTheFirst() {
        val prints = mapOf("/a" to "x", "/b" to "x", "/c" to "y")
        assertEquals(listOf("/a", "/c", "/d"), NotePhotos.unique(listOf("/a", "/b", "/c", "/d"), prints))
    }

    @Test
    fun metaLineSaysOnlyTheLatestMoment() {
        val zone = java.time.ZoneId.of("Europe/Rome")
        fun at(d: Int, h: Int, m: Int) = java.time.ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Modificata 14 nov, 09:44", NoteText.moment(at(13, 20, 32), at(14, 9, 44), at(21, 12, 0), zone))
    }
}
