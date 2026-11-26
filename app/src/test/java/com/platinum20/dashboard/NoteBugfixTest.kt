package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteHistory
import com.platinum20.dashboard.notes.NoteHistory.Snap
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** I bug delle note trovati il 2026-11-26: ognuno col suo caso. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NoteBugfixTest {
    @Test fun grassettoSuPiuVociLasciaLaLista() {
        val on = NoteFormat.toggle("- a\n- b", 0, 7, NoteFormat.BOLD)
        assertEquals("- **a**\n- **b**", on.text)
        assertEquals(listOf(NoteLines.Kind.BULLET, NoteLines.Kind.BULLET), NoteLines.parse(on.text).map { it.kind })
        assertEquals("## **T**\n1. **x**", NoteFormat.toggle("## T\n1. x", 0, 9, NoteFormat.BOLD).text)
        assertEquals("[ ] {r:a}\n- {r:b}", NoteFormat.color("[ ] a\n- b", 0, 9, 'r').text)
    }

    @Test fun grassettoSuPiuRigheSiSpegne() {
        assertEquals("- a\n- b", NoteFormat.toggle("- **a**\n- **b**", 0, 15, NoteFormat.BOLD).text)
        assertEquals("ab\ncd", NoteFormat.toggle("**ab**\n**cd**", 0, 13, NoteFormat.BOLD).text)
        // Se una riga non lo ha, G lo mette a tutte.
        assertEquals("**ab**\n**cd**", NoteFormat.toggle("**ab**\ncd", 0, 9, NoteFormat.BOLD).text)
    }

    @Test fun indietroSuUnaSottoVoceTornaSuDiUnLivello() {
        assertEquals(NoteLines.Edit("- a\n- b", 6), NoteLines.afterEdit("- a\n  - b", "- a\n  -b", 7))
        assertEquals(NoteLines.Edit("[ ] a\n  [ ] b", 12), NoteLines.afterEdit("[ ] a\n    [ ] b", "[ ] a\n    [ ]b", 13))
        // Al primo livello la voce torna testo, come prima.
        assertEquals("b", NoteLines.afterEdit("- b", "-b", 1)!!.text)
    }

    @Test fun fatteInFondoPortanoLeSottoVoci() {
        val t = "[ ] a\n  [x] a1\n  [ ] a2\n[x] b\n  [ ] b1\n[ ] c"
        assertEquals("[ ] a\n  [ ] a2\n  [x] a1\n[ ] c\n[x] b\n  [ ] b1", NoteLines.doneToBottom(t))
    }

    @Test fun togliLeFatteTogliendoAncheLeSottoVoci() {
        val t = "[x] a\n  [ ] a1\n[ ] b\n  [x] b1\n    [ ] b11\n  [ ] b2\nfine"
        assertEquals("[ ] b\n  [ ] b2\nfine", NoteLines.removeDone(t))
    }

    @Test fun ilCursoreSegueLaRigaSpostata() {
        val t = "uno\ndue\ntre"
        assertEquals(1, NoteLines.moveCursor(t, 1, up = true, offset = 5))
        assertEquals(9, NoteLines.moveCursor(t, 1, up = false, offset = 5))
        assertEquals(5, NoteLines.moveCursor(t, 1, up = false, offset = 9))
        assertEquals(1, NoteLines.moveCursor(t, 0, up = true, offset = 1))
        val moved = NoteLines.move(t, 1, up = true)
        assertEquals("due", moved.substring(0, NoteLines.moveCursor(t, 1, up = true, offset = 7)))
    }

    @Test fun annullaNonUnisceLettereScritteInPostiDiversi() {
        val h = NoteHistory()
        h.record(Snap("", 0), Snap("a", 1), 0)
        h.record(Snap("a", 1), Snap("ab", 2), 100)
        // Tocco all'inizio e una lettera lì: è un passo nuovo.
        h.record(Snap("ab", 0), Snap("cab", 1), 200)
        assertEquals(Snap("ab", 0), h.undo(Snap("cab", 1)))
        assertEquals(Snap("", 0), h.undo(Snap("ab", 2)))
    }

    @Test fun scrivereInUnaNotaArchiviataLaLasciaNellArchivio() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        NotesStore.init(context)
        val id = NotesStore.saveNote(null, "Ricette\nPasta")!!
        NotesStore.toggleNoteArchive(id, now = 1_000)
        NotesStore.saveNote(id, "Ricette\nPasta al forno")
        val n = NotesStore.note(id)
        assertNotNull(n)
        assertEquals("Ricette\nPasta al forno", n!!.text)
        assertEquals(1_000L, n.archivedAt)
        NotesStore.deleteNote(id)
    }
}
