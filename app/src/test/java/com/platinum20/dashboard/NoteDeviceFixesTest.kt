package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.ui.screens.slashPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le correzioni trovate provando l'editor su un emulatore vero (1.604). */
class NoteDeviceFixesTest {
    @Test
    fun slashMenuGoesBelowAFirstLineWithAllItsRoom() {
        // La riga in cima alla nota (y 690), tastiera e barra da 1347 in giù: sotto ci sono 650 px, il menu va lì.
        val (down, h) = slashPlace(anchorTop = 640f, anchorBottom = 690f, limit = 1347f, top = 60f, gap = 16f, minBelow = 525f, max = 997f)
        assertTrue(down)
        assertEquals(641f, h, 0.5f)
    }

    @Test
    fun slashMenuGoesAboveALineNearTheKeyboard() {
        val (down, h) = slashPlace(anchorTop = 1250f, anchorBottom = 1300f, limit = 1347f, top = 60f, gap = 16f, minBelow = 525f, max = 997f)
        assertFalse(down)
        assertEquals(997f, h, 0.5f)
    }

    @Test
    fun blockFromSlashLeavesCursorOnEmptyLineBelow() {
        for (piece in listOf(NoteLines.BOARD, NoteLines.COLUMNS_NEW, NoteLines.pageLine("Nuova pagina", 7))) {
            // Riga vuota in fondo: il pezzo al suo posto, il cursore nella riga nuova sotto.
            val e = NoteLines.insertBlock("Casa\n", 5, piece)
            assertEquals("Casa\n$piece\n", e.text)
            assertEquals(e.text.length, e.start)
            assertEquals(1, NoteLines.insertedBlockIndex(e))
            // Sotto c'era già una riga vuota: si usa quella.
            val f = NoteLines.insertBlock("Casa\n\n\naltro", 5, piece)
            assertEquals("Casa\n$piece\n\naltro", f.text)
            assertEquals(NoteLines.Kind.TEXT, NoteLines.lineAt(f.text, f.start).kind)
            assertEquals(1, NoteLines.insertedBlockIndex(f))
        }
    }

    @Test
    fun severalPhotosInARow() {
        var t = "Foto\n"
        var at = t.length
        repeat(3) { i ->
            val e = NoteLines.insertBlock(t, at, NoteLines.imageLine("/p/$i.jpg"))
            t = e.text; at = e.start
        }
        assertEquals(listOf(NoteLines.Kind.TEXT, NoteLines.Kind.IMAGE, NoteLines.Kind.IMAGE, NoteLines.Kind.IMAGE, NoteLines.Kind.TEXT), NoteLines.parse(t).map { it.kind })
        assertEquals(t.length, at)
    }
}
