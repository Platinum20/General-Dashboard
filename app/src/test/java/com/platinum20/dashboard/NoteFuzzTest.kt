package com.platinum20.dashboard

import androidx.compose.ui.graphics.Color
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.ui.screens.richText
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Note scritte a caso con tutti i segni dell'editor (voci, titoli, formati, colori, @, foto, colonne, bacheca):
 * nessuna funzione dell'editor deve mai chiudere l'app, qualunque testo ci sia e dovunque stia il cursore.
 */
class NoteFuzzTest {
    private val pieces = listOf(
        "a", "parola ", "x", " ", " ", "\n", "\n", "\n\n", "  ", "- ", "[ ] ", "[x] ", "[-] ", "1. ", "2. ", "## ", "> ", "!! ", "!! 🔥 ",
        "---", "**", "_", "~~", "==", "{r:", "{B:", "}", "{", "[", "]", "(", ")", "[nota](nota:12)", "[📄 Pagina](nota:3)",
        "[🖼 Foto](img:/x/y.jpg)", "[🎙 Audio 0:12](audio:/a.m4a)", "[🔗 a.it](https://a.it/x)", "https://e.it/p ", "[bacheca]",
        "‖ a ‖ b ‖", "| a | b |", "@", "@no", " ", "​", "😀", "👍🏽", "é", "\t", "[app](app:sim)",
    )

    private fun note(r: Random): String = buildString { repeat(r.nextInt(1, 30)) { append(pieces[r.nextInt(pieces.size)]) } }

    @Test
    fun editorNeverCrashes() {
        val r = Random(1592)
        val fails = mutableListOf<String>()
        repeat(4000) { n ->
            val t = note(r)
            val a = r.nextInt(0, t.length + 1)
            val b = r.nextInt(a, t.length + 1)
            fun check(what: String, f: () -> Unit) {
                try { f() } catch (e: Throwable) {
                    if (fails.size < 12) fails += "$what #$n ${e.javaClass.simpleName}: ${e.message} ← ${e.stackTrace.firstOrNull { it.className.startsWith("com.platinum20") }} | " +
                        t.replace("\n", "⏎") + " [$a,$b]"
                }
            }
            check("parse") { NoteLines.parse(t); NoteLines.lineAt(t, a); NoteFormat.spans(t); NoteFormat.hidden(t); NoteFormat.plain(t) }
            check("richText") {
                val folds = NoteLines.foldRanges(t, NoteLines.headings(t).map { it.second }.toSet())
                val tt = richText(t, Color.Gray, folds = folds)
                for (o in 0..t.length) tt.offsetMapping.originalToTransformed(o)
                for (o in 0..tt.text.length) tt.offsetMapping.transformedToOriginal(o)
            }
            check("format") {
                NoteFormat.toggle(t, a, b, listOf(NoteFormat.BOLD, NoteFormat.ITALIC, NoteFormat.STRIKE, NoteFormat.HIGHLIGHT)[n % 4])
                NoteFormat.heading(t, a, b); NoteFormat.active(t, a, b); NoteFormat.tidy(t, a, b)
                NoteFormat.color(t, a, b, if (n % 3 == 0) null else NoteFormat.COLORS[n % 5])
                NoteFormat.link(t, a, b, if (n % 2 == 0) null else "https://e.it"); NoteFormat.linkAt(t, a)
                NoteFormat.mentionSlots(t); NoteFormat.mentionEmojis(t)
            }
            check("lines") {
                NoteLines.setKind(t, a, b, NoteLines.Kind.entries[n % 6])
                NoteLines.transform(t, a, b, NoteLines.Kind.entries[n % 6])
                NoteLines.indent(t, a, b, if (n % 2 == 0) 1 else -1); NoteLines.canIndent(t, a, 1)
                val count = NoteLines.parse(t).size
                val i = r.nextInt(0, count.coerceAtLeast(1))
                NoteLines.toggle(t, i); NoteLines.move(t, i, n % 2 == 0); NoteLines.remove(t, i); NoteLines.sectionSize(t, i)
                NoteLines.removeDone(t); NoteLines.doneToBottom(t); NoteLines.sortAlpha(t); NoteLines.uncheckAll(t)
                NoteLines.insertDivider(t, a); NoteLines.blocks(t); NoteLines.boardRanges(t); NoteLines.tapBelow(t)
                NoteLines.outOfPrefix(t, a); NoteLines.cardLine(t)
            }
            // Scrivere: un pezzo al posto della selezione, come la tastiera.
            val typed = pieces[r.nextInt(pieces.size)].let { if (n % 5 == 0) "" else it }
            val new = t.substring(0, a) + typed + t.substring(b)
            val cursor = a + typed.length
            check("afterEdit") {
                NoteFormat.afterEdit(t, a, b, new, cursor)
                NoteLines.afterEdit(t, new, cursor)
                NoteLines.guardBlocks(t, new)
                NoteLines.autoBookmark(t, new, cursor)
                NoteFormat.deleteMention(t, new, cursor)
            }
        }
        assertEquals("", fails.joinToString("\n"))
    }
}
