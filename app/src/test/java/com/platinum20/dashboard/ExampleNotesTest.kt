package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.notes.ExampleNotes
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteLists
import com.platinum20.dashboard.notes.NoteSmart
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Le note di esempio (2026-11-24): usano tutti i blocchi dell'editor e i link portano alle note giuste. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExampleNotesTest {
    private val ids = ExampleNotes.Ids(100, 101, 102, 103)
    private val notes = ExampleNotes.notes(ids, 1_000_000_000L, "/foto/lavagna.jpg", "/foto/napoli.jpg")

    @Test
    fun usanoTuttiIBlocchi() {
        val kinds = notes.flatMap { n -> NoteLines.parse(n.text).map { it.kind } }.toSet()
        val wanted = setOf(
            NoteLines.Kind.TEXT, NoteLines.Kind.BULLET, NoteLines.Kind.CHECK, NoteLines.Kind.HEADING, NoteLines.Kind.NUMBER,
            NoteLines.Kind.QUOTE, NoteLines.Kind.CALLOUT, NoteLines.Kind.DIVIDER, NoteLines.Kind.TABLE, NoteLines.Kind.COLUMNS,
            NoteLines.Kind.PAGE, NoteLines.Kind.BOOKMARK, NoteLines.Kind.BOARD, NoteLines.Kind.IMAGE,
        )
        assertEquals(emptySet<NoteLines.Kind>(), wanted - kinds)
        assertTrue(notes.any { n -> NoteLines.parse(n.text).any { it.level > 0 } })
        val spans = notes.flatMap { NoteFormat.spans(it.text).map { s -> s.kind } }.toSet()
        listOf(NoteFormat.Kind.BOLD, NoteFormat.Kind.ITALIC, NoteFormat.Kind.STRIKE, NoteFormat.Kind.HIGHLIGHT, NoteFormat.Kind.COLOR, NoteFormat.Kind.LINK)
            .forEach { assertTrue("manca $it", it in spans) }
    }

    @Test
    fun titoliEmojiFotoETag() {
        assertEquals(listOf("🛒 Spesa", "🎁 Idee regali", "🧳 Weekend a Napoli", "📌 Lavagna della riunione"), notes.map { it.title })
        assertEquals("/foto/napoli.jpg", notes[2].cover)
        assertEquals(listOf("/foto/lavagna.jpg"), notes[3].photos)
        assertTrue("regali" in NoteSmart.tags(notes[1].text))
    }

    @Test
    fun iLinkPortanoAlleNote() {
        val urls = notes.flatMap { NoteFormat.mentionUrls(it.text) }.toSet()
        assertEquals(setOf("nota:${ids.shopping}", "nota:${ids.trip}"), urls)
    }

    @Test
    fun spesaInDaFareESiSalva() {
        val rows = NoteLists.rows(notes)
        assertTrue(rows.any { it.noteId == ids.shopping })
        val todos = ExampleNotes.todos(10, 1_000L)
        assertTrue(todos.all { it.tickable && !it.done })
        val data = NotesData(tasks = todos, notes = notes)
        assertEquals(data.notes.map { it.text }, NotesStore.parse(NotesStore.serialize(data)).notes.map { it.text })
    }
}
