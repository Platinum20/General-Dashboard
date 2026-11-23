package com.platinum20.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.ui.screens.RichNoteField
import com.platinum20.dashboard.ui.screens.keepLineDelta
import com.platinum20.dashboard.ui.screens.LineFollow
import com.platinum20.dashboard.ui.screens.followMode
import com.platinum20.dashboard.ui.screens.richText
import com.platinum20.dashboard.ui.screens.editorShown
import com.platinum20.dashboard.ui.screens.mentionMark
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.Note
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pulizia dell'editor (1.594): rientro sospeso di voci, sotto-voci e citazioni (le righe che vanno a capo restano
 * allineate al testo), nessuna riga vuota fra le voci, la riga in cui si scrive sempre sopra la tastiera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoteEditorTidyTest {
    @get:Rule
    val compose = createComposeRule()

    private val sample = "Titoletto\n- uno\n  - sotto voce lunga che va a capo\n> una citazione lunga\n[ ] spunta\ntesto\n"

    @Test
    fun oneParagraphPerLineWithItsIndent() {
        val view = NoteLines.Shown(sample, breathe = true)
        val paras = view.paragraphs()
        // Sette righe, anche l'ultima vuota (il suo carattere invisibile).
        assertEquals(7, paras.size)
        assertEquals(listOf(0, 1, 2, 1, 1, 0, 0), paras.map { it.indents })
        // I paragrafi si toccano e coprono tutto il testo.
        assertEquals(0, paras.first().start)
        assertEquals(view.shown.length, paras.last().end)
        paras.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }

    @Test
    fun drawnTextKeepsLengthAndHasNoNewlinesBetweenParagraphs() {
        val t = richText(sample, Color.Gray)
        val drawn = t.text
        val view = NoteLines.Shown(sample, breathe = true)
        assertEquals(view.shown.length, drawn.length)
        // Nessun "a capo" fra i paragrafi: diventerebbero righe vuote fra le voci.
        assertEquals(0, drawn.text.count { it == '\n' })
        val styles = drawn.paragraphStyles
        assertEquals(7, styles.size)
        styles.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start) }
        // La sotto-voce: la prima riga e quelle a capo partono allineate al testo, due rientri dentro.
        val sub = styles[2].item.textIndent ?: TextIndent.None
        assertEquals(60f, sub.firstLine.value, 0.01f)
        assertEquals(63f, sub.restLine.value, 0.01f)
        val quote = styles[3].item.textIndent ?: TextIndent.None
        assertTrue(quote.restLine.value > 30f)
        // Il testo normale non si sposta.
        assertEquals(0.sp, (styles[5].item.textIndent ?: TextIndent.None).restLine)
        // Le posizioni del cursore restano le stesse.
        for (o in 0..sample.length) assertEquals(view.toShown(o), t.offsetMapping.originalToTransformed(o))
    }

    @Test
    fun textWithoutTrailingNewline() {
        val t = richText("- uno\n- due", Color.Gray).text
        assertFalse(t.text.contains('\n'))
        assertEquals(2, t.paragraphStyles.size)
        assertEquals("", richText("", Color.Gray).text.text.replace("\u200B", ""))
    }

    @Test
    fun typingLineGoesUpNeverUnderTheKeyboard() {
        // Parte visibile da 100 a 500 (sopra la tastiera).
        assertEquals(0f, keepLineDelta(200f, 224f, LineFollow.TYPING, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
        // Scrivendo oltre metà: la riga torna a un terzo.
        assertEquals(400f - 220f, keepLineDelta(400f, 424f, LineFollow.TYPING, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
        // Sotto la tastiera (fuori dalla parte visibile): sale.
        assertTrue(keepLineDelta(700f, 724f, LineFollow.TYPING, visTop = 100f, visBottom = 500f, margin = 24f) > 400f)
        // Muovendo solo il cursore basta che si veda.
        assertEquals(0f, keepLineDelta(400f, 424f, LineFollow.MOVE, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
        assertEquals(700f + 24f - 476f, keepLineDelta(700f, 724f, LineFollow.MOVE, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
    }

    @Test
    fun tappedLineGoesToTheTop() {
        // Toccata a metà: sale fin sotto la testata (100 + metà margine).
        assertEquals(400f - 112f, keepLineDelta(400f, 424f, LineFollow.TAPPED, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
        // Toccata sotto la tastiera: sale lo stesso in cima.
        assertEquals(700f - 112f, keepLineDelta(700f, 724f, LineFollow.TAPPED, visTop = 100f, visBottom = 500f, margin = 24f), 0.01f)
        // Il tocco mette in cima; la tastiera che si apre lo lascia; scrivere o una selezione no.
        assertTrue(followMode(typing = false, moved = true, collapsed = true, sinceTapMs = 50, wasAtTop = false))
        assertTrue(followMode(typing = false, moved = false, collapsed = true, sinceTapMs = 5_000, wasAtTop = true))
        assertFalse(followMode(typing = true, moved = true, collapsed = true, sinceTapMs = 50, wasAtTop = true))
        assertFalse(followMode(typing = false, moved = true, collapsed = false, sinceTapMs = 50, wasAtTop = false))
        assertFalse(followMode(typing = false, moved = true, collapsed = true, sinceTapMs = 5_000, wasAtTop = true))
    }

    @Test
    fun markNeverAloneOnItsLine() {
        // Il posto del cerchio non ha punti dove andare a capo: una parola lunga resta sulla riga del cerchio.
        assertFalse(NoteLines.PLACE.contains('\u2009') || NoteLines.PLACE.contains('\u200B') || NoteLines.PLACE.contains(' '))
    }

    @Test
    fun mentionsGetIconAndEndSlots() {
        val t = "a [Spesa](nota:7)[Corsa](app:notes/item/HABIT/3) b"
        val slots = NoteFormat.mentionSlots(t)
        assertEquals(4, slots.size)
        val v = editorShown(t, emptyList())
        // Si vede: a ␣[icona]Spesa[aria][icona]Corsa[aria] b, senza segni.
        val o = NoteFormat.MENTION_OPEN
        val c = NoteFormat.MENTION_CLOSE
        assertEquals("a ${o}Spesa${c}${o}Corsa${c} b", v.shown.replace(NoteLines.BREATH, ""))
        // Ogni posto ha la sua posizione: il cursore e le icone li trovano.
        slots.forEach { (at, slot) -> assertEquals(slot, v.shown[v.toShown(at)].toString()) }
        val data = NotesData(notes = listOf(Note(7, "Spesa", emoji = "🛒")))
        assertEquals("🛒", mentionMark("nota:7", data).emoji)
        assertEquals(Icons.Outlined.LocalFireDepartment, mentionMark("app:notes/item/HABIT/3", data).icon)
        assertEquals(Icons.Outlined.WbSunny, mentionMark("app:" + com.platinum20.dashboard.Routes.WEATHER, data).icon)
    }

    @Test
    fun mentionAtLineStartHasItsSlot() {
        // A inizio riga prima del posto c'è il carattere dell'aria: il posto si trova lo stesso (1.597).
        val t = "a\n[Claude Pro termina](app:notes/item/TASK/2) \n[Compleanno](app:notes/item/TASK/3)"
        val v = editorShown(t, emptyList())
        NoteFormat.mentionSlots(t).forEach { (at, slot) ->
            val p = v.slotAt(at)
            assertTrue(p != null)
            assertEquals(slot, v.shown[p!!].toString())
        }
        val drawn = richText(t, Color.Gray).text
        val open = v.slotAt(t.indexOf('['))!!
        assertTrue(drawn.spanStyles.any { it.start == open && it.item.letterSpacing.value > 10f })
    }

    @Test
    fun emojiInMentionNameBecomesTheIcon() {
        val t = "[💡 PEPPE](nota:4) e [Ciao](nota:5)"
        val e = NoteFormat.mentionEmojis(t)
        assertEquals(1, e.size)
        assertEquals("💡", e[0].second)
        val v = editorShown(t, emptyList())
        // L'emoji non si scrive due volte: resta solo come icona.
        assertFalse(v.shown.contains("💡"))
        assertTrue(v.shown.contains("PEPPE"))
        assertTrue(NoteFormat.isEmoji("⭐"))
        assertFalse(NoteFormat.isEmoji("Ciao"))
        assertTrue(NoteFormat.mentionEmojis("[💡](nota:4)").isEmpty())
    }

    @Test
    fun phoneEventsCanBeTagged() {
        val ev = com.platinum20.dashboard.calendar.CalendarEvent(9, "Cena da Sara", 1_000L, 2_000L, false, "", 0)
        val again = ev.copy(begin = 9_000_000L, end = 9_001_000L)
        val hits = com.platinum20.dashboard.ui.screens.mentionTargets(NotesData(), emptyList(), "sara", listOf(again, ev), now = 0L)
        assertEquals(1, hits.size)
        assertEquals("Cena da Sara", hits[0].title)
        assertTrue(hits[0].url.contains("id=9&begin=1000"))
        assertEquals(androidx.compose.material.icons.Icons.Outlined.Event, mentionMark(hits[0].url, NotesData()).icon)
    }

    @Test
    fun tickingASubItemKeepsItsIndent() {
        val t = "[ ] a\n  [ ] b\n    [ ] c"
        val once = NoteLines.toggle(t, 1)
        assertEquals("[ ] a\n  [x] b\n    [ ] c", once)
        assertEquals(1, NoteLines.parse(once)[1].level)
        assertEquals(t, NoteLines.toggle(once, 1))
        assertEquals(2, NoteLines.parse(NoteLines.toggle(t, 2))[2].level)
        // Anche spuntata dalla Home o dal widget.
        assertEquals("Lista\n  [x] b", com.platinum20.dashboard.notes.Checklist.toggle("Lista\n  [ ] b", 1))
    }

    @Test
    fun fieldDrawsListsAndQuotesWithoutCrashing() {
        compose.setContent {
            MaterialTheme {
                var v by remember { mutableStateOf(TextFieldValue(sample + "1. numero\n> " + "lunga ".repeat(40) + "\n[ ] [Spesa](nota:7)[Meteo](app:home/weather) " + "papa".repeat(30), TextRange(3))) }
                RichNoteField(value = v, onValueChange = { v = it }, textStyle = MaterialTheme.typography.bodyLarge, placeholder = "Scrivi")
            }
        }
        compose.waitForIdle()
    }
}
