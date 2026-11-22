package com.platinum20.dashboard

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NoteText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le cose nuove dell'editor (1.584): evidenzia, link, note collegate, citazione, riquadro, linea. */
class NoteEditorBlocksTest {
    @Test
    fun highlightHidesItsMarks() {
        val t = "un ==punto== chiave"
        assertEquals("un punto chiave", NoteFormat.plain(t))
        val e = NoteFormat.toggle("un punto chiave", 3, 8, NoteFormat.HIGHLIGHT)
        assertEquals(t, e.text)
    }

    @Test
    fun linksShowOnlyTheirText() {
        val t = "vedi [il sito](https://esempio.it) ora"
        assertEquals("vedi il sito ora", NoteFormat.plain(t))
        assertEquals("https://esempio.it" to "il sito", NoteFormat.linkAt(t, 7))
        assertNull(NoteFormat.linkAt(t, 1))
        val bare = "apri https://a.it/x subito"
        assertEquals("https://a.it/x", NoteFormat.linkAt(bare, 8)?.first)
        assertEquals(bare, NoteFormat.plain(bare))
    }

    @Test
    fun linkButtonWrapsAndUnwraps() {
        val on = NoteFormat.link("vedi il sito", 5, 12, "https://e.it")
        assertEquals("vedi [il sito](https://e.it)", on.text)
        val off = NoteFormat.link(on.text, 7, 7, null)
        assertEquals("vedi il sito", off.text)
    }

    @Test
    fun mentionBecomesNoteLink() {
        val e = NoteFormat.noteLink("guarda @spe", 7, 11, "Spesa [casa]", 42)
        assertEquals("guarda [Spesa (casa)](nota:42) ", e.text)
        assertEquals(e.text.length, e.start)
        assertEquals("nota:42", NoteFormat.linkAt(e.text, 9)?.first)
    }

    @Test
    fun quoteCalloutDividerAreBlocks() {
        assertEquals(NoteLines.Kind.QUOTE, NoteLines.kindOf("> detto").first)
        assertEquals(NoteLines.Kind.CALLOUT, NoteLines.kindOf("!!🔥 attento").first)
        assertEquals(NoteLines.Kind.DIVIDER, NoteLines.kindOf("---").first)
        assertEquals("🔥", NoteLines.calloutEmoji("!!🔥 attento"))
        assertEquals(NoteLines.CALLOUT_EMOJI, NoteLines.calloutEmoji("!! attento"))
        assertEquals("!!✅ attento", NoteLines.setCalloutEmoji("!!🔥 attento", 0, "✅"))
    }

    @Test
    fun enterKeepsTheQuote() {
        val old = "> detto"
        val e = NoteLines.afterEdit(old, "$old\n", old.length + 1)!!
        assertEquals("> detto\n> ", e.text)
        val closed = NoteLines.afterEdit(e.text, e.text + "\n", e.text.length + 1)!!
        assertEquals("> detto\n", closed.text)
    }

    @Test
    fun dividerGoesBelowTheLine() {
        val e = NoteLines.insertDivider("uno\ndue", 1)
        assertEquals("uno\n---\ndue", e.text)
        assertEquals("uno\n---\n".length, e.start)
        assertEquals("uno\n---\n", NoteLines.insertDivider("uno", 3).text)
    }
}

/** Parte 3 (1.586): ordina, tocco sotto, indice, modello. */
class NoteEditorListsTest {
    @Test
    fun sortAlphaKeepsChildrenAndDoneAtBottom() {
        val t = "Spesa\n[x] Uova\n[ ] burro\n  [ ] salato\n[ ] Arance\nfine"
        assertEquals("Spesa\n[ ] Arance\n[ ] burro\n  [ ] salato\n[x] Uova\nfine", NoteLines.sortAlpha(t))
        assertEquals("2. b\n1. a".let { NoteLines.sortAlpha(it) }, "1. a\n2. b")
    }

    @Test
    fun tapBelowOpensANewItem() {
        assertEquals("- latte\n- pane\n- ", NoteLines.tapBelow("- latte\n- pane").text)
        assertEquals("1. a\n2. ", NoteLines.tapBelow("1. a").text)
        assertEquals("  [x] a\n  [ ] ", NoteLines.tapBelow("  [x] a").text)
        assertEquals("ciao", NoteLines.tapBelow("ciao").text)
        assertEquals(4, NoteLines.tapBelow("ciao").start)
        assertEquals("- ", NoteLines.tapBelow("- ").text)
    }

    @Test
    fun headingsForTheIndex() {
        val t = "## Biglietti\nandata\n## **Hotel**\ntesto"
        assertEquals(listOf(0 to "Biglietti", 20 to "Hotel"), NoteLines.headings(t))
    }

    @Test
    fun copyTitleCounts() {
        assertEquals("Valigia (2)", NoteText.copyTitle("Valigia"))
        assertEquals("Valigia (3)", NoteText.copyTitle("Valigia (2)"))
        assertEquals("", NoteText.copyTitle(""))
    }
}
