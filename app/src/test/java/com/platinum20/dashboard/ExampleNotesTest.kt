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

    // ---- La guida (2026-11-26) ----
    private val g = ExampleNotes.GuideIds(200, 201, 202, 203, 204)
    private val guide = ExampleNotes.guide(g, 1_000_000_000L, "/foto/home.jpg", "/foto/copertina.jpg", "/audio/campanello.wav")

    @Test
    fun guidaUsaTuttiIBlocchi() {
        val kinds = guide.flatMap { n -> NoteLines.parse(n.text).map { it.kind } }.toSet()
        assertEquals(emptySet<NoteLines.Kind>(), NoteLines.Kind.values().toSet() - NoteLines.Kind.BOOKMARK - kinds)
        assertTrue(guide.any { n -> NoteLines.parse(n.text).any { it.level >= 2 } })
        val spans = guide.flatMap { NoteFormat.spans(it.text).map { s -> s.kind } }.toSet()
        listOf(NoteFormat.Kind.BOLD, NoteFormat.Kind.ITALIC, NoteFormat.Kind.STRIKE, NoteFormat.Kind.HIGHLIGHT, NoteFormat.Kind.COLOR, NoteFormat.Kind.LINK)
            .forEach { assertTrue("manca $it", it in spans) }
    }

    @Test
    fun guidaIndiceFissatoELinkGiusti() {
        assertEquals(listOf("🧭 Guida di Canvas", "✍️ Il tasto +", "📅 Agenda e Da fare", "📝 Le note, tutto quello che sanno fare", "🎧 Musica, video e Cerca"), guide.map { it.title })
        assertTrue(guide[0].pinned && guide.drop(1).none { it.pinned })
        assertEquals(listOf("/foto/home.jpg"), guide[0].photos)
        val pages = NoteLines.parse(guide[0].text).filter { it.kind == NoteLines.Kind.PAGE }
            .map { NoteLines.pageId(guide[0].text.substring(it.start, it.end)) }
        assertEquals(listOf(g.plus, g.agenda, g.notes, g.media), pages)
        guide.drop(1).forEach { assertTrue(it.title, "nota:${g.hub}" in NoteFormat.mentionUrls(it.text)) }
        guide.forEach { assertTrue(it.title, "guida" in NoteSmart.tags(it.text)) }
    }

    @Test
    fun guidaSoloLAgendaInDaFare() {
        assertEquals(listOf(g.agenda), NoteLists.rows(guide).map { it.noteId })
    }

    @Test
    fun guidaDateECodiciSiToccano() {
        val smart = NoteSmart.find(guide[3].text)
        assertTrue(smart.any { it.kind == NoteSmart.Kind.CODE && it.value == "CanvasCasa24" })
        assertTrue(smart.any { it.kind == NoteSmart.Kind.DATE && it.value.startsWith("domani alle 9") })
    }

    @Test
    fun campanelloDuraTreSecondi() {
        val wav = ExampleNotes.chimeWav()
        assertEquals("RIFF", String(wav, 0, 4))
        val seconds = (wav.size - 44) / 2.0 / 22_050
        assertEquals(3.2, seconds, 0.01)
    }

    @Test
    fun guidaVecchiaPrendeLEmojiNuova() {
        val old = guide[1].copy(emoji = "➕")
        assertEquals(old.id, ExampleNotes.plusNoteToFix(listOf(guide[0], old)))
        // Se l'ha già cambiata, o è un'altra nota col +, non si tocca.
        assertEquals(null, ExampleNotes.plusNoteToFix(guide))
        assertEquals(null, ExampleNotes.plusNoteToFix(listOf(old.copy(text = "Il tasto + del telecomando"))))
    }
}
