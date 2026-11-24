package com.platinum20.dashboard

import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteLists
import com.platinum20.dashboard.notes.NotePhotoText
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesSearch
import com.platinum20.dashboard.notes.SearchKind
import com.platinum20.dashboard.widget.NoteWidgetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Le note nel resto di Canvas (2026-11-23): liste in Da fare, widget Nota, ricerca dentro le note e nelle foto. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NoteListsHomeTest {
    private val spesa = Note(1, "Spesa\n[ ] latte\n[x] uova\n[ ] pane\nnote a margine\n[ ] caffè\n[x] mele", updatedAt = 50)
    private val idee = Note(2, "Idee\nniente da spuntare", updatedAt = 90)
    private val valigia = Note(3, "Valigia\n[x] caricatore\n[x] spazzolino", updatedAt = 70)

    @Test fun laListaContaLeVociAperte() {
        val row = NoteLists.of(spesa)!!
        assertEquals(3, row.open)
        assertEquals(5, row.total)
        assertEquals("3 da fare su 5 · lista", NoteLists.line(row))
        assertEquals(listOf(1, 2, 3, 5, 6), row.entries.map { it.index })
        assertNull(NoteLists.of(idee))
    }

    @Test fun inDaFareSoloListeConVociAperte() {
        assertEquals(listOf(1L), NoteLists.rows(listOf(spesa, idee, valigia)).map { it.noteId })
        // Finita mentre si guardava: resta.
        assertEquals(listOf(3L, 1L), NoteLists.rows(listOf(spesa, idee, valigia), keep = setOf(3L)).map { it.noteId })
        assertEquals("Tutto fatto · 2 di 2", NoteLists.line(NoteLists.of(valigia)!!))
        // Archiviata: no.
        assertTrue(NoteLists.rows(listOf(spesa.copy(archivedAt = 5))).isEmpty())
    }

    @Test fun ricominciaTogliTutteLeSpunte() {
        val again = NoteLists.restart(spesa.text)
        assertEquals("Spesa\n[ ] latte\n[ ] uova\n[ ] pane\nnote a margine\n[ ] caffè\n[ ] mele", again)
        assertEquals(2, NoteLists.doneCount(spesa.text))
        assertEquals(0, NoteLists.doneCount(again))
    }

    @Test fun duplicaFaUnaCopiaDaRifare() {
        assertEquals("Valigia (2)\n[ ] caricatore\n[ ] spazzolino", NoteLists.duplicate(valigia.text))
        assertEquals("Valigia (3)\n[ ] a", NoteLists.duplicate("Valigia (2)\n[x] a"))
    }

    @Test fun laRicercaMostraLaRigaGiusta() {
        val casa = Note(4, "Casa al mare\nChiavi sotto il vaso\nPassword wifi: mare2024!", updatedAt = 1)
        val hit = NotesSearch.search(NotesData(notes = listOf(casa)), "WIFI").single()
        assertEquals(SearchKind.NOTE, hit.kind)
        assertEquals("Password wifi: mare2024!", hit.line)
        assertEquals(9..12, NotesSearch.matchRange(hit.line!!, "wifi"))
        // Solo nel titolo: niente riga.
        assertNull(NotesSearch.search(NotesData(notes = listOf(casa)), "casa").single().line)
    }

    @Test fun ilPercorsoDellaFotoNonSiCerca() {
        val n = Note(5, "Lavatrice\n[🖼 Foto](img:/data/note-photos/garanzia.jpg)", updatedAt = 1)
        assertTrue(NotesSearch.search(NotesData(notes = listOf(n)), "garanzia").isEmpty())
        // Ma il testo letto nella foto sì.
        val hit = NotesSearch.search(NotesData(notes = listOf(n)), "garanzia", mapOf("/data/note-photos/garanzia.jpg" to "Scontrino\nGaranzia fino al 03/2028")).single()
        assertTrue(hit.inPhoto)
        assertEquals("Garanzia fino al 03/2028", hit.line)
    }

    @Test fun accentiEPezziLunghi() {
        assertEquals(3..7, NotesSearch.matchRange("Un caffè lungo", "CAFFE"))
        val long = "a".repeat(100) + " parola " + "b".repeat(100)
        val cut = NotesSearch.snippet(long, "parola")
        assertTrue(cut.contains("parola"))
        assertTrue(cut.startsWith("…") && cut.endsWith("…"))
        assertTrue(cut.length <= 72)
    }

    @Test fun ilWidgetSceglieLaNotaGiusta() {
        assertEquals(2L, NoteWidgetRows.pick(listOf(spesa, idee), chosen = 2L)?.id)
        // Nessuna scelta (o cancellata): la lista più recente, non la nota più recente.
        assertEquals(1L, NoteWidgetRows.pick(listOf(spesa, idee), chosen = 99L)?.id)
        assertEquals(2L, NoteWidgetRows.pick(listOf(idee), chosen = null)?.id)
        assertNull(NoteWidgetRows.pick(emptyList(), null))
    }

    @Test fun ilWidgetMostraQuelloCheCiSta() {
        val model = NoteWidgetRows.build(spesa, heightDp = 1000)
        assertEquals(3, model.open)
        assertEquals(6, model.lines.size)
        assertEquals("note a margine", model.lines[3].text)
        assertEquals("SPESA · 3 DA FARE ›", NoteWidgetRows.header(model))
        val small = NoteWidgetRows.build(spesa, heightDp = NoteWidgetRows.HEADER_DP + 2 * NoteWidgetRows.CHECK_DP + NoteWidgetRows.MORE_DP)
        assertEquals(2, small.lines.size)
        assertEquals(4, small.more)
    }

    @Test fun fotoDaLeggereESalvate() {
        val n = Note(6, "Foto\n[🖼 Foto](img:/b.jpg)", updatedAt = 1, photos = listOf("/a.jpg"))
        assertEquals(listOf("/a.jpg", "/b.jpg"), NotePhotoText.missing(listOf(n), emptySet()))
        assertEquals(listOf("/b.jpg"), NotePhotoText.missing(listOf(n), setOf("/a.jpg")))
        assertEquals(mapOf("/a.jpg" to "x"), NotePhotoText.prune(mapOf("/a.jpg" to "x", "/old.jpg" to "y"), listOf(n)))
        val texts = mapOf("/a.jpg" to "riga 1\nriga \"2\"", "/b.jpg" to "")
        assertEquals(texts, NotePhotoText.parse(NotePhotoText.serialize(texts)))
    }
}
